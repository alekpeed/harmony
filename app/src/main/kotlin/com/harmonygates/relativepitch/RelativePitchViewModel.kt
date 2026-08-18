package com.harmonygates.relativepitch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.audio.AudioTrackPlayer
import com.harmonygates.core.audio.InstrumentId
import com.harmonygates.core.data.progress.ProfileId
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.data.relativepitch.RelativePitchProgressRepository
import com.harmonygates.core.music.relativepitch.DefaultRelativePitchExerciseGenerator
import com.harmonygates.core.music.relativepitch.LevelStat
import com.harmonygates.core.music.relativepitch.LevelStatus
import com.harmonygates.core.music.relativepitch.RelativePitchCurriculum
import com.harmonygates.core.music.relativepitch.RelativePitchEvaluator
import com.harmonygates.core.music.relativepitch.RelativePitchExercise
import com.harmonygates.core.music.relativepitch.RelativePitchExerciseGenerator
import com.harmonygates.core.music.relativepitch.RelativePitchLevel
import com.harmonygates.core.music.relativepitch.RelativePitchStimulus
import com.harmonygates.core.music.relativepitch.ToneEvent
import com.harmonygates.data.HarmonyGraph
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the relative-pitch ladder screen can ask for. */
sealed interface RelativePitchIntent {
    data class SelectLevel(val levelId: String) : RelativePitchIntent
    data object Play : RelativePitchIntent
    data class Answer(val choiceId: String) : RelativePitchIntent
    data object Next : RelativePitchIntent
    data object BackToLadder : RelativePitchIntent
}

enum class RelativePitchMode { LADDER, PRACTICE }

/** Where the current question is in its lifecycle. */
enum class RelativePitchPhase { LOADING, PLAYING, ANSWERING, FEEDBACK }

data class RelativePitchUiState(
    val mode: RelativePitchMode = RelativePitchMode.LADDER,
    val levels: List<RelativePitchLevel> = RelativePitchCurriculum.levels,
    val statuses: Map<String, LevelStatus> = emptyMap(),
    val stats: Map<String, LevelStat> = emptyMap(),
    val activeLevel: RelativePitchLevel? = null,
    val phase: RelativePitchPhase = RelativePitchPhase.LOADING,
    val exercise: RelativePitchExercise? = null,
    val selectedChoiceId: String? = null,
    val wasCorrect: Boolean? = null,
    val exerciseNumber: Int = 0,
    val sessionCorrect: Int = 0,
) {
    val canReplay: Boolean get() = phase == RelativePitchPhase.ANSWERING || phase == RelativePitchPhase.FEEDBACK
    val progressLabel: String
        get() = activeLevel?.let { "$exerciseNumber / ${it.sessionLength}" }.orEmpty()
}

/**
 * The relative-pitch ladder: a graded, multiple-choice curriculum underneath `eartraining`'s
 * keyboard chord-reproduction exercises.
 *
 * Two things are deliberately not shared with [com.harmonygates.eartraining.EarTrainingViewModel]
 * even though the shape looks similar: this reads and writes
 * [RelativePitchProgressRepository] rather than the chord-attempt pipeline, because a multiple-
 * choice answer has no chord or MIDI note event behind it to record there; and playback here
 * walks a [RelativePitchStimulus] — a bare sequence of notes — rather than a `StimulusSpec`,
 * because an isolated interval has no [com.harmonygates.core.music.chord.ChordSpec] to hang a
 * voicing off. Everything else — generate, play, capture-free-because-there-is-no-MIDI-capture-
 * here, judge, record, advance — is the same loop shape as ear training's, on purpose.
 */
class RelativePitchViewModel(application: Application) : AndroidViewModel(application) {

    private val progress: ProgressRepository = HarmonyGraph.progress(application)
    private val levelStats: RelativePitchProgressRepository = HarmonyGraph.relativePitchProgress(application)
    private val generator: RelativePitchExerciseGenerator = DefaultRelativePitchExerciseGenerator()
    private val player = AudioTrackPlayer(viewModelScope)

    private var profile: ProfileId? = null
    private val statsFlow = MutableStateFlow<Map<String, LevelStat>>(emptyMap())
    private val session = MutableStateFlow(PracticeSession())
    private var playbackJob: Job? = null

    val state: StateFlow<RelativePitchUiState> = combine(statsFlow, session) { stats, current ->
        RelativePitchUiState(
            mode = current.mode,
            statuses = RelativePitchEvaluator.statuses(stats = stats),
            stats = stats,
            activeLevel = current.level,
            phase = current.phase,
            exercise = current.exercise,
            selectedChoiceId = current.selectedChoiceId,
            wasCorrect = current.wasCorrect,
            exerciseNumber = current.index + 1,
            sessionCorrect = current.sessionCorrect,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RelativePitchUiState())

    init {
        viewModelScope.launch {
            profile = runCatching { progress.currentProfile(HarmonyGraph.CONTENT_VERSION) }.getOrNull()
            profile?.let { id -> levelStats.observeStats(id).collect { statsFlow.value = it } }
        }
    }

    fun onIntent(intent: RelativePitchIntent) {
        when (intent) {
            is RelativePitchIntent.SelectLevel -> selectLevel(intent.levelId)
            RelativePitchIntent.Play -> play()
            is RelativePitchIntent.Answer -> answer(intent.choiceId)
            RelativePitchIntent.Next -> advance()
            RelativePitchIntent.BackToLadder -> backToLadder()
        }
    }

    private fun selectLevel(levelId: String) {
        val level = RelativePitchCurriculum.level(levelId) ?: return
        val status = RelativePitchEvaluator.statuses(stats = statsFlow.value)[levelId]
        if (status == LevelStatus.LOCKED) return
        session.value = PracticeSession(mode = RelativePitchMode.PRACTICE, level = level)
        presentExercise(0)
    }

    private fun presentExercise(index: Int) {
        val current = session.value
        val level = current.level ?: return
        // Wall-clock rather than a fixed seed: unlike a chord gate, nothing here needs to
        // reproduce a specific past question, and a fixed seed would replay the same interval
        // in the same direction on every attempt at a level.
        val exercise = generator.generate(level, seed = System.currentTimeMillis() + index)
        session.value = current.copy(
            phase = RelativePitchPhase.LOADING,
            index = index,
            exercise = exercise,
            selectedChoiceId = null,
            wasCorrect = null,
        )
        if (exercise != null) play()
    }

    private fun play() {
        val exercise = session.value.exercise ?: return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            session.value = session.value.copy(phase = RelativePitchPhase.PLAYING)
            try {
                player.load(InstrumentId(exercise.stimulus.instrumentId))
                render(exercise.stimulus)
            } finally {
                player.allNotesOff()
            }
            // A replay after the question is answered must not reopen it for a new answer.
            if (session.value.selectedChoiceId == null) {
                session.value = session.value.copy(phase = RelativePitchPhase.ANSWERING)
            }
        }
    }

    /** Plays a bare note sequence: each event's notes on, the previous event's notes released. */
    private suspend fun render(stimulus: RelativePitchStimulus) {
        val events = stimulus.events.sortedBy { it.atMillis }
        var clockMillis = 0L
        var previous: ToneEvent? = null

        for (event in events) {
            val wait = event.atMillis - clockMillis
            if (wait > 0) delay(wait)
            clockMillis = event.atMillis

            previous?.notes?.forEach { player.noteOff(it) }
            event.notes.forEach { player.noteOn(it, event.velocity) }
            previous = event
        }
        delay(RING_MILLIS)
        previous?.notes?.forEach { player.noteOff(it) }
    }

    private fun answer(choiceId: String) {
        val current = session.value
        val exercise = current.exercise ?: return
        if (current.phase != RelativePitchPhase.ANSWERING) return

        val correct = choiceId == exercise.correctChoiceId
        session.value = current.copy(
            phase = RelativePitchPhase.FEEDBACK,
            selectedChoiceId = choiceId,
            wasCorrect = correct,
            sessionCorrect = current.sessionCorrect + if (correct) 1 else 0,
        )
        profile?.let { id ->
            viewModelScope.launch {
                runCatching { levelStats.recordAnswer(id, exercise.level.id, correct, Instant.now()) }
            }
        }
    }

    private fun advance() {
        val current = session.value
        val level = current.level ?: return
        val next = current.index + 1
        if (next >= level.sessionLength) {
            backToLadder()
            return
        }
        presentExercise(next)
    }

    private fun backToLadder() {
        playbackJob?.cancel()
        player.allNotesOff()
        session.value = PracticeSession()
    }

    override fun onCleared() {
        // Released here rather than in a coroutine: the scope is already cancelling, and an
        // AudioTrack that outlives its view model keeps a render thread and a device buffer.
        player.release()
        playbackJob?.cancel()
    }

    private data class PracticeSession(
        val mode: RelativePitchMode = RelativePitchMode.LADDER,
        val level: RelativePitchLevel? = null,
        val phase: RelativePitchPhase = RelativePitchPhase.LOADING,
        val exercise: RelativePitchExercise? = null,
        val index: Int = 0,
        val selectedChoiceId: String? = null,
        val wasCorrect: Boolean? = null,
        val sessionCorrect: Int = 0,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long the last note or chord is left ringing before it is released. */
        const val RING_MILLIS = 900L
    }
}
