package com.harmonygates.eartraining

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.audio.AudioTrackPlayer
import com.harmonygates.core.audio.InstrumentId
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.midi.capture.MidiPerformanceCapture
import com.harmonygates.core.music.eartraining.DefaultEarExerciseGenerator
import com.harmonygates.core.music.eartraining.EarExercise
import com.harmonygates.core.music.eartraining.EarTaskFamily
import com.harmonygates.core.music.eartraining.StimulusEvent
import com.harmonygates.core.music.eartraining.StimulusSettings
import com.harmonygates.core.music.eartraining.StimulusSpec
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.time.SystemMonotonicClock
import com.harmonygates.data.HarmonyGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the ear training screen can ask for. */
sealed interface EarTrainingIntent {
    /** Play the stimulus, or play it again. */
    data object Play : EarTrainingIntent

    data object Next : EarTrainingIntent

    data object Skip : EarTrainingIntent

    data object Restart : EarTrainingIntent

    data class ChooseFamily(val family: EarTaskFamily) : EarTrainingIntent
}

/** Where the exercise loop currently is. */
enum class EarPhase {
    LOADING,

    /** A stimulus is sounding. */
    PLAYING,

    /** Waiting for the player's answer on the keyboard. */
    LISTENING,

    FEEDBACK,

    COMPLETED,

    /** Content is missing, or this family cannot be generated here. */
    UNAVAILABLE,
}

data class EarTrainingUiState(
    val phase: EarPhase = EarPhase.LOADING,
    val families: List<EarTaskFamily> = emptyList(),
    val family: EarTaskFamily? = null,
    val instruction: String = "",
    val exerciseNumber: Int = 0,
    val sessionLength: Int = 0,
    val plays: Int = 0,
    val canReplay: Boolean = false,
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
    val soundingNotes: List<Int> = emptyList(),
    val result: EvaluationResult? = null,
    /** The chord that was played, revealed once the answer is in. */
    val answerSymbol: String? = null,
    val differenceDescription: String? = null,
    val correctCount: Int = 0,
    val message: String? = null,
) {
    val progressLabel: String
        get() = if (sessionLength == 0) "" else "Exercise $exerciseNumber of $sessionLength"

    val isBusy: Boolean get() = phase == EarPhase.LOADING || phase == EarPhase.PLAYING
}

/**
 * Ear training: the app plays a chord, the player plays it back.
 *
 * The engine, the generator and the sampler all existed from Phase 8 and nothing had ever called
 * them from a screen. This is that call.
 *
 * It deliberately does **not** go through `DefaultExerciseSessionEngine`, the way the chord gate
 * does. That engine is built around `ExerciseGenerator`/`ExerciseInstance`, and
 * `DefaultEarExerciseGenerator` produces neither — handing an ear policy to the chord-gate engine
 * would silently generate an ordinary written chord exercise and never play a sound. So this
 * follows `ProgressionRunViewModel` instead: hold the loop here, drive `MidiPerformanceCapture`
 * directly, and judge with the same `DefaultPerformanceEvaluator` everything else uses — which is
 * what 07 §1 asks for, that an ear answer be judged by the chord evaluator rather than by a
 * second idea of what a chord is.
 *
 * **Not persisted.** `ProgressRepository.recordAttempt` wants an `AttemptRecord` built around an
 * `ExerciseInstance`, which an `EarExercise` has no equivalent of. Rather than invent a
 * half-convincing one, an ear session reports how it went and is then forgotten — the same place
 * Progression Run stood at the end of Phase 10. Mastery and gate completion are unaffected by
 * what happens here.
 */
class EarTrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val content = HarmonyGraph.content(application)
    private val midi: MidiInputSource = AndroidMidiInputSource(application, viewModelScope)
    private val soundingNotes: StateFlow<List<Int>> = midi.activeNotes
        .map { notes -> notes.soundingAscending.map { it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val capture = MidiPerformanceCapture(midi, SystemMonotonicClock, viewModelScope)
    private val generator = DefaultEarExerciseGenerator()
    private val evaluator = DefaultPerformanceEvaluator()
    private val player = AudioTrackPlayer(viewModelScope)

    private val session = MutableStateFlow(EarSession())

    /** One policy per authored ear family, so the screen can offer all four without a campaign. */
    private var policies: Map<EarTaskFamily, ExercisePolicy> = emptyMap()
    private var playbackJob: Job? = null

    val state: StateFlow<EarTrainingUiState> = combine(
        session,
        midi.connectionState,
        soundingNotes,
    ) { current, connection, sounding ->
        EarTrainingUiState(
            phase = current.phase,
            families = current.families,
            family = current.family,
            instruction = current.exercise?.instruction.orEmpty(),
            exerciseNumber = current.index + 1,
            sessionLength = current.sessionLength,
            plays = current.plays,
            canReplay = current.exercise?.replayRule?.permits(current.plays) == true &&
                current.phase != EarPhase.PLAYING &&
                current.phase != EarPhase.COMPLETED,
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
            soundingNotes = sounding,
            result = current.result,
            answerSymbol = current.answerSymbol,
            differenceDescription = current.differenceDescription,
            correctCount = current.correctCount,
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EarTrainingUiState())

    init {
        viewModelScope.launch {
            midi.start()
            loadPolicies()
        }
        viewModelScope.launch {
            capture.attempts.collect { attempt ->
                val current = session.value
                val exercise = current.exercise ?: return@collect
                val policy = current.policy ?: return@collect
                if (current.phase != EarPhase.LISTENING) return@collect

                val result = evaluator.evaluate(exercise.requirement, attempt, policy.onsetPolicy)
                session.value = current.copy(
                    phase = EarPhase.FEEDBACK,
                    result = result,
                    answerSymbol = exercise.stimulus.chords.last().symbol,
                    differenceDescription = exercise.differenceDescription,
                    correctCount = current.correctCount + if (result.verdict.isCorrect) 1 else 0,
                )
            }
        }
    }

    fun onIntent(intent: EarTrainingIntent) {
        when (intent) {
            EarTrainingIntent.Play -> play()
            EarTrainingIntent.Next -> advance()
            EarTrainingIntent.Skip -> advance()
            EarTrainingIntent.Restart -> startSession(session.value.family)
            is EarTrainingIntent.ChooseFamily -> startSession(intent.family)
        }
    }

    private suspend fun loadPolicies() {
        val all = runCatching { content.allPolicies() }.getOrNull()
        if (all == null) {
            session.value = session.value.copy(
                phase = EarPhase.UNAVAILABLE,
                message = "The content pack could not be loaded.",
            )
            return
        }
        // One policy per family, in the curriculum's own family order. Several policies can share
        // a family (the practice preset shares one with a gate); the first is enough to play.
        policies = EarTaskFamily.entries
            .mapNotNull { family -> all.values.firstOrNull { it.earTaskFamily == family }?.let { family to it } }
            .toMap()

        if (policies.isEmpty()) {
            session.value = session.value.copy(
                phase = EarPhase.UNAVAILABLE,
                message = "No ear-training policies are authored in this content pack.",
            )
            return
        }
        startSession(policies.keys.first())
    }

    private fun startSession(family: EarTaskFamily?) {
        val chosen = family ?: policies.keys.firstOrNull() ?: return
        val policy = policies[chosen] ?: return
        playbackJob?.cancel()
        capture.cancel()

        session.value = EarSession(
            phase = EarPhase.LOADING,
            families = policies.keys.toList(),
            family = chosen,
            policy = policy,
            sessionLength = policy.sessionLength,
            // A different session every time it is started, so practising the same family twice
            // is not the same sixteen chords twice.
            baseSeed = System.currentTimeMillis(),
        )
        presentExercise(0)
    }

    private fun presentExercise(index: Int) {
        val current = session.value
        val policy = current.policy ?: return
        val family = current.family ?: return

        val exercise = generate(family, policy, current.baseSeed, index)
        if (exercise == null) {
            session.value = current.copy(
                phase = EarPhase.UNAVAILABLE,
                exercise = null,
                message = unavailableMessage(family),
            )
            return
        }

        session.value = current.copy(
            phase = EarPhase.LOADING,
            index = index,
            exercise = exercise,
            plays = 0,
            result = null,
            answerSymbol = null,
            differenceDescription = null,
            message = null,
        )
        play()
    }

    /**
     * Generates one exercise, trying a few seeds before giving up.
     *
     * The generator returns null for a chord it cannot spell as well as for a family it does not
     * build, so a single null is not evidence that the family is unsupported — only that this
     * seed landed badly.
     */
    private fun generate(
        family: EarTaskFamily,
        policy: ExercisePolicy,
        baseSeed: Long,
        index: Int,
    ): EarExercise? {
        repeat(GENERATION_ATTEMPTS) { attempt ->
            val seed = baseSeed + index * SEED_STRIDE + attempt
            val exercise = generator.generate(
                family = family,
                policy = policy,
                seed = seed,
                settings = StimulusSettings(),
                // Function hearing is the one family that needs a key, and no policy carries one.
                // Cycling through the twelve keeps a session from being sixteen exercises in C.
                key = keyFor(index),
            )
            if (exercise != null) return exercise
        }
        return null
    }

    private fun play() {
        val exercise = session.value.exercise ?: return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            capture.cancel()
            session.value = session.value.copy(
                phase = EarPhase.PLAYING,
                plays = session.value.plays + 1,
            )
            try {
                player.load(InstrumentId(exercise.stimulus.instrumentId))
                render(exercise.stimulus)
            } finally {
                // A cancelled playback must not leave a note sounding under the answer.
                player.allNotesOff()
            }
            session.value = session.value.copy(phase = EarPhase.LISTENING)
            arm()
        }
    }

    /** Plays a stimulus as written: each event at its own offset, the previous one released. */
    private suspend fun render(stimulus: StimulusSpec) {
        val events = stimulus.events.sortedBy { it.atMillis }
        var clockMillis = 0L
        var previous: StimulusEvent? = null

        for (event in events) {
            val wait = event.atMillis - clockMillis
            if (wait > 0) delay(wait)
            clockMillis = event.atMillis

            previous?.voicing?.pitches?.forEach { player.noteOff(it) }
            event.voicing.pitches.forEachIndexed { voice, note ->
                player.noteOn(note, event.velocities[voice])
            }
            previous = event
        }
        delay(RING_MILLIS)
        previous?.voicing?.pitches?.forEach { player.noteOff(it) }
    }

    private fun arm() {
        val current = session.value
        val policy = current.policy ?: return
        capture.arm(
            CapturePolicy.GuidedChord.copy(
                onsetPolicy = policy.onsetPolicy,
                acceptedRange = policy.pitchRange,
            ),
        )
    }

    private fun advance() {
        val current = session.value
        val next = current.index + 1
        if (next >= current.sessionLength) {
            playbackJob?.cancel()
            capture.cancel()
            session.value = current.copy(phase = EarPhase.COMPLETED, exercise = null)
            return
        }
        presentExercise(next)
    }

    private fun keyFor(index: Int): KeyContext =
        KeyContext(requireNotNull(SpelledPitchClass.parseOrNull(KEYS[index.mod(KEYS.size)])))

    private fun unavailableMessage(family: EarTaskFamily): String = when (family) {
        EarTaskFamily.BASS_HEARING, EarTaskFamily.VOICE_LEADING_HEARING ->
            "${family.label} needs a moving line, which the sight-reading score domain models. " +
                "The generator declines this family rather than inventing half of it."
        else -> "No playable exercise could be generated for ${family.label} from this policy."
    }

    private fun statusFor(connection: MidiConnectionState): String = when (connection) {
        is MidiConnectionState.Connected -> connection.endpoint.displayName
        is MidiConnectionState.Unsupported -> "No MIDI support"
        is MidiConnectionState.NoDevice -> "No keyboard"
        is MidiConnectionState.DevicesAvailable -> "Keyboard available"
        is MidiConnectionState.Connecting -> "Connecting"
        is MidiConnectionState.Error -> "Keyboard disconnected"
    }

    override fun onCleared() {
        // Released here rather than in a coroutine: the scope is already cancelling, and an
        // AudioTrack that outlives its view model keeps a render thread and a device buffer.
        player.release()
        playbackJob?.cancel()
        viewModelScope.launch { midi.stop() }
    }

    private data class EarSession(
        val phase: EarPhase = EarPhase.LOADING,
        val families: List<EarTaskFamily> = emptyList(),
        val family: EarTaskFamily? = null,
        val policy: ExercisePolicy? = null,
        val exercise: EarExercise? = null,
        val index: Int = 0,
        val sessionLength: Int = 0,
        val plays: Int = 0,
        val result: EvaluationResult? = null,
        val answerSymbol: String? = null,
        val differenceDescription: String? = null,
        val correctCount: Int = 0,
        val message: String? = null,
        val baseSeed: Long = 0,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long the last chord is left ringing before it is released. */
        const val RING_MILLIS = 1_200L
        const val GENERATION_ATTEMPTS = 8
        const val SEED_STRIDE = 1_000L

        val KEYS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    }
}
