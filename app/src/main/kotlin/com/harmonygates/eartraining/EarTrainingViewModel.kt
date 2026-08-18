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
import com.harmonygates.core.data.progress.ProfileId
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.data.HarmonyGraph
import com.harmonygates.data.RecordedAttempts
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the ear training console can ask for. */
sealed interface EarTrainingIntent {
    /** Play the stimulus, or play it again. */
    data object Play : EarTrainingIntent

    data object Next : EarTrainingIntent

    data object Skip : EarTrainingIntent

    data object Restart : EarTrainingIntent

    /** Leave the setup console and begin the session. */
    data object StartTraining : EarTrainingIntent

    /** End the session and return to the setup console. */
    data object ExitToSetup : EarTrainingIntent

    data class ChooseFamily(val family: EarTaskFamily) : EarTrainingIntent
}

/**
 * Which of the two interfaces is on screen.
 *
 * The approved design is explicit that the large console exists only while a session is being
 * configured, and that training replaces it with the compact bar over the same room.
 */
enum class EarMode { SETUP, TRAINING }

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
    val mode: EarMode = EarMode.SETUP,
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
    /** The key this exercise was placed in, as the app spells it. */
    val keySpelling: String? = null,
    val message: String? = null,
) {
    val progressLabel: String
        get() = if (sessionLength == 0) "" else "$exerciseNumber / $sessionLength"

    val canStart: Boolean get() = family != null && phase != EarPhase.UNAVAILABLE
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
 * This is the single authority for the screen. Touch and MIDI both resolve into this one
 * `StateFlow`, so the illustrated console can never drift from the exercise engine, and swapping
 * artwork state cannot restart a session.
 *
 * **Recorded.** Every judged answer is written through `RecordedAttempts`, which builds the
 * `ExerciseInstance` the attempt store wants out of what this loop already has. The attempt and
 * the verdict are the real ones from the capture and the evaluator, so an ear answer is the same
 * grade of evidence as a chord gate's and moves mastery the same way.
 */
class EarTrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val content = HarmonyGraph.content(application)
    private val progress: ProgressRepository = HarmonyGraph.progress(application)
    private var profile: ProfileId? = null
    private val midi: MidiInputSource = AndroidMidiInputSource(application, viewModelScope)
    private val soundingNotes: StateFlow<List<Int>> = midi.activeNotes
        .map { notes -> notes.soundingAscending.map { it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val capture = MidiPerformanceCapture(midi, SystemMonotonicClock, viewModelScope)
    private val generator = DefaultEarExerciseGenerator()
    private val evaluator = DefaultPerformanceEvaluator()
    private val player = AudioTrackPlayer(viewModelScope)

    private val session = MutableStateFlow(EarSession())

    /** One policy per authored ear family, so the console can offer all four without a campaign. */
    private var policies: Map<EarTaskFamily, ExercisePolicy> = emptyMap()
    private var playbackJob: Job? = null

    val state: StateFlow<EarTrainingUiState> = combine(
        session,
        midi.connectionState,
        soundingNotes,
    ) { current, connection, sounding ->
        EarTrainingUiState(
            mode = current.mode,
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
            keySpelling = current.keySpelling,
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EarTrainingUiState())

    init {
        viewModelScope.launch {
            midi.start()
            profile = runCatching { progress.currentProfile(HarmonyGraph.CONTENT_VERSION) }.getOrNull()
            loadPolicies()
        }
        viewModelScope.launch {
            capture.attempts.collect { attempt ->
                val current = session.value
                val exercise = current.exercise ?: return@collect
                val policy = current.policy ?: return@collect
                if (current.phase != EarPhase.LISTENING) return@collect

                val result = evaluator.evaluate(exercise.requirement, attempt, policy.onsetPolicy)

                // The same evidence a chord gate stores, from the same evaluator. An ear answer
                // now moves mastery instead of being reported and discarded.
                val chord = exercise.stimulus.chords.last()
                profile?.let { id ->
                    runCatching {
                        RecordedAttempts.record(
                            progress = progress,
                            profile = id,
                            sessionId = current.sessionId,
                            definitionId = policy.id.value,
                            skillIds = RecordedAttempts.policySkills(policy, FALLBACK_SKILL),
                            chord = chord,
                            requirement = exercise.requirement,
                            attempt = attempt,
                            result = result,
                            seed = exercise.stimulus.seed,
                            presentation = policy.presentation,
                            targetNotes = exercise.stimulus.events.last().voicing.pitches.map { it.value },
                        )
                    }
                }

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
            EarTrainingIntent.Restart -> beginSession()
            EarTrainingIntent.StartTraining -> beginSession()
            EarTrainingIntent.ExitToSetup -> returnToSetup()
            is EarTrainingIntent.ChooseFamily -> chooseFamily(intent.family)
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
        val first = policies.keys.first()
        session.value = EarSession(
            mode = EarMode.SETUP,
            phase = EarPhase.LOADING,
            families = policies.keys.toList(),
            family = first,
            policy = policies[first],
            sessionLength = policies[first]?.sessionLength ?: 0,
        )
    }

    /** In setup, choosing a family only changes what a session would be. Nothing starts. */
    private fun chooseFamily(family: EarTaskFamily) {
        val policy = policies[family] ?: return
        val current = session.value
        if (current.mode == EarMode.TRAINING) return
        session.value = current.copy(
            family = family,
            policy = policy,
            sessionLength = policy.sessionLength,
            message = null,
            phase = EarPhase.LOADING,
        )
    }

    private fun beginSession() {
        val current = session.value
        val family = current.family ?: return
        val policy = policies[family] ?: return
        playbackJob?.cancel()
        capture.cancel()

        session.value = EarSession(
            mode = EarMode.TRAINING,
            phase = EarPhase.LOADING,
            families = policies.keys.toList(),
            family = family,
            policy = policy,
            sessionLength = policy.sessionLength,
            // A different session every time it is started, so practising the same family twice
            // is not the same sixteen chords twice.
            baseSeed = System.currentTimeMillis(),
            sessionId = UUID.randomUUID().toString(),
        )
        val opened = session.value
        profile?.let { id ->
            viewModelScope.launch {
                runCatching {
                    RecordedAttempts.startSession(
                        progress = progress,
                        profile = id,
                        sessionId = opened.sessionId,
                        policyId = policy.id.value,
                        seed = opened.baseSeed,
                        planned = policy.sessionLength,
                    )
                }
            }
        }
        presentExercise(0)
    }

    private fun returnToSetup() {
        playbackJob?.cancel()
        capture.cancel()
        player.allNotesOff()
        val current = session.value
        session.value = EarSession(
            mode = EarMode.SETUP,
            phase = EarPhase.LOADING,
            families = current.families,
            family = current.family,
            policy = current.policy,
            sessionLength = current.sessionLength,
        )
    }

    private fun presentExercise(index: Int) {
        val current = session.value
        val policy = current.policy ?: return
        val family = current.family ?: return

        val key = keyFor(index)
        val exercise = generate(family, policy, current.baseSeed, index, key)
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
            keySpelling = exercise.stimulus.key?.tonic?.toString() ?: key.tonic.toString(),
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
        key: KeyContext,
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
                key = key,
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
            viewModelScope.launch {
                runCatching { RecordedAttempts.endSession(progress, current.sessionId) }
            }
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
        val mode: EarMode = EarMode.SETUP,
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
        val keySpelling: String? = null,
        val message: String? = null,
        val baseSeed: Long = 0,
        val sessionId: String = "",
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long the last chord is left ringing before it is released. */
        const val RING_MILLIS = 1_200L
        const val GENERATION_ATTEMPTS = 8
        const val SEED_STRIDE = 1_000L

        val KEYS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

        /** Used only if a policy names no skills, which the authored ear policies all do. */
        const val FALLBACK_SKILL = "skill.ear.reproduce"
    }
}
