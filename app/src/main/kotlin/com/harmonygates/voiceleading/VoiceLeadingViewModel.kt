package com.harmonygates.voiceleading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.audio.AudioTrackPlayer
import com.harmonygates.core.audio.InstrumentId
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.midi.capture.MidiPerformanceCapture
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.progression.ChordEvent
import com.harmonygates.core.music.progression.DefaultProgressionGenerator
import com.harmonygates.core.music.progression.Progression
import com.harmonygates.core.music.progression.ProgressionTemplate
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.time.SystemMonotonicClock
import com.harmonygates.core.music.voiceleading.VoiceLeadingAnalysis
import com.harmonygates.core.music.voiceleading.VoiceLeadingAnalyzer
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.voiceleadingmenu.VoiceLeadingExercise
import com.harmonygates.voiceleadingmenu.VoiceLeadingMenuState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the voice leading screen can ask for. */
sealed interface VoiceLeadingIntent {
    /** Sound the chord the player is moving from. */
    data object PlaySource : VoiceLeadingIntent

    data object Next : VoiceLeadingIntent

    data object Skip : VoiceLeadingIntent

    data object StartExercise : VoiceLeadingIntent

    /** Start from the setup menu, carrying its complete configuration. */
    data class StartFromMenu(val menu: VoiceLeadingMenuState) : VoiceLeadingIntent

    data object ExitToSetup : VoiceLeadingIntent

    data class ChooseProgression(val template: ProgressionTemplate) : VoiceLeadingIntent

    data class ChooseStyle(val style: VoicingStyle) : VoiceLeadingIntent
}

/** Setup configures the run; practice is the run. The pack's two-surface architecture. */
enum class VoiceLeadingMode { SETUP, PRACTICE }

/** Mirrors the pack's ExerciseUiState contract. */
enum class VoiceLeadingPhase { READY, LISTENING, EVALUATING, COMPLETE }

/** One voice's journey, as the screen draws it. */
data class VoiceMotionUi(
    val fromNote: Int,
    val toNote: Int,
    val semitones: Int,
) {
    val isCommonTone: Boolean get() = semitones == 0
    val isLeap: Boolean get() = kotlin.math.abs(semitones) > STEP_LIMIT
    val isUp: Boolean get() = semitones > 0

    private companion object {
        const val STEP_LIMIT = 2
    }
}

data class VoiceLeadingUiState(
    val mode: VoiceLeadingMode = VoiceLeadingMode.SETUP,
    val phase: VoiceLeadingPhase = VoiceLeadingPhase.READY,
    val template: ProgressionTemplate = ProgressionTemplates.MajorTwoFiveOne,
    val style: VoicingStyle = VoicingStyle.ROOTLESS_A,
    val availableTemplates: List<ProgressionTemplate> = emptyList(),
    val availableStyles: List<VoicingStyle> = emptyList(),
    /** The chord the player is moving from, and the notes of it. */
    val sourceSymbol: String? = null,
    val sourceNotes: List<Int> = emptyList(),
    /** The chord being asked for. */
    val targetSymbol: String? = null,
    val targetFunction: String? = null,
    val instruction: String? = null,
    val stepNumber: Int = 0,
    val stepCount: Int = 0,
    val soundingNotes: List<Int> = emptyList(),
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
    val result: EvaluationResult? = null,
    val motions: List<VoiceMotionUi> = emptyList(),
    val totalMotionSemitones: Int? = null,
    val maxLeapSemitones: Int? = null,
    val commonToneCount: Int? = null,
    /** How the answer compared with the smoothest available answer. */
    val bestTotalMotionSemitones: Int? = null,
    val attempted: Int = 0,
    val correct: Int = 0,
    val message: String? = null,
    /** The setup menu's configuration, kept whole even where the engine cannot yet read it. */
    val menu: VoiceLeadingMenuState? = null,
) {
    val progress: Float get() = if (stepCount == 0) 0f else stepNumber.toFloat() / stepCount
    val accuracy: Float? get() = if (attempted == 0) null else correct.toFloat() / attempted
    val isSmoothest: Boolean
        get() = totalMotionSemitones != null && bestTotalMotionSemitones != null &&
            totalMotionSemitones <= bestTotalMotionSemitones
}

/**
 * Voice leading: play the next chord, moving as little as possible.
 *
 * `VoiceLeadingAnalyzer` and `ExerciseRequirement.VoiceLeadingTarget` have existed since Phase 1
 * and Phase 3 and nothing ever asked a player to satisfy one. This is the screen the status doc
 * has been describing as missing: "shows a starting voicing and asks for the next one".
 *
 * What makes it a voice leading exercise rather than another chord gate is the second judgement.
 * The evaluator decides whether the right chord was played; the analyzer then measures *how* the
 * hand got there — total motion, largest leap, retained common tones — and the answer is compared
 * against the smoothest voicing the policy actually allows. Playing the correct chord badly is a
 * different result from playing it well, and 04 §11 exists so that difference is measurable.
 *
 * The same authority rule as the other screens: touch and MIDI both resolve into this one
 * StateFlow, so nothing the artwork shows can disagree with the engine.
 *
 * **Not persisted.** Like ear training, an attempt here has no `ExerciseInstance` to hang an
 * `AttemptRecord` on, so a run reports how it went and is then forgotten. Mastery and gate
 * completion are untouched.
 */
class VoiceLeadingViewModel(application: Application) : AndroidViewModel(application) {

    private val midi: MidiInputSource = AndroidMidiInputSource(application, viewModelScope)
    private val soundingNotes: StateFlow<List<Int>> = midi.activeNotes
        .map { notes -> notes.soundingAscending.map { it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val capture = MidiPerformanceCapture(midi, SystemMonotonicClock, viewModelScope)
    private val evaluator = DefaultPerformanceEvaluator()
    private val analyzer = VoiceLeadingAnalyzer()
    private val generator = DefaultProgressionGenerator()
    private val realizer = DefaultChordRealizer()
    private val player = AudioTrackPlayer(viewModelScope)

    private val run = MutableStateFlow(VoiceLeadingRun())
    private var playbackJob: Job? = null

    val state: StateFlow<VoiceLeadingUiState> = combine(
        run,
        midi.connectionState,
        soundingNotes,
    ) { current, connection, sounding ->
        val source = current.sourceVoicing
        VoiceLeadingUiState(
            mode = current.mode,
            phase = current.phase,
            template = current.template,
            style = current.style,
            availableTemplates = TEMPLATES,
            availableStyles = STYLES,
            sourceSymbol = current.sourceEvent?.displaySymbol,
            sourceNotes = source?.pitches?.map { it.value }.orEmpty(),
            targetSymbol = current.targetEvent?.displaySymbol,
            targetFunction = current.targetEvent?.functionLabel,
            instruction = current.targetEvent?.instruction,
            stepNumber = current.index + 1,
            stepCount = current.stepCount,
            soundingNotes = sounding,
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
            result = current.result,
            motions = current.motions,
            totalMotionSemitones = current.analysis?.totalMotionSemitones,
            maxLeapSemitones = current.analysis?.maxLeapSemitones,
            commonToneCount = current.analysis?.commonToneCount,
            bestTotalMotionSemitones = current.bestTotalMotion,
            attempted = current.attempted,
            correct = current.correct,
            message = current.message,
            menu = current.menu,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VoiceLeadingUiState())

    init {
        viewModelScope.launch { midi.start() }
        viewModelScope.launch {
            capture.attempts.collect { attempt ->
                val current = run.value
                val source = current.sourceVoicing ?: return@collect
                val target = current.targetEvent ?: return@collect
                if (current.phase != VoiceLeadingPhase.LISTENING) return@collect

                run.value = current.copy(phase = VoiceLeadingPhase.EVALUATING)

                val requirement = ExerciseRequirement.VoiceLeadingTarget(
                    startingVoicing = source,
                    destination = target.chord,
                    policy = target.policy,
                )
                val result = evaluator.evaluate(requirement, attempt, target.onsetPolicy)

                // The evaluator answers "was that the chord". The analyzer answers "how did the
                // hand get there", which is the whole subject of this screen.
                val played = attempt.finalEffectiveNotes
                val analysis = if (played.isEmpty()) {
                    null
                } else {
                    runCatching { analyzer.analyze(source.pitches, played) }.getOrNull()
                }

                run.value = run.value.copy(
                    phase = VoiceLeadingPhase.EVALUATING,
                    result = result,
                    analysis = analysis,
                    motions = analysis?.motions?.map {
                        VoiceMotionUi(it.from.value, it.to.value, it.semitones)
                    }.orEmpty(),
                    attempted = current.attempted + 1,
                    correct = current.correct + if (result.verdict.isCorrect) 1 else 0,
                )
            }
        }
    }

    fun onIntent(intent: VoiceLeadingIntent) {
        when (intent) {
            VoiceLeadingIntent.PlaySource -> playSource()
            VoiceLeadingIntent.Next, VoiceLeadingIntent.Skip -> advance()
            VoiceLeadingIntent.StartExercise -> begin()
            is VoiceLeadingIntent.StartFromMenu -> beginFromMenu(intent.menu)
            VoiceLeadingIntent.ExitToSetup -> returnToSetup()
            is VoiceLeadingIntent.ChooseProgression -> {
                if (run.value.mode == VoiceLeadingMode.SETUP) {
                    run.value = run.value.copy(template = intent.template)
                }
            }
            is VoiceLeadingIntent.ChooseStyle -> {
                if (run.value.mode == VoiceLeadingMode.SETUP) {
                    run.value = run.value.copy(style = intent.style)
                }
            }
        }
    }

    private fun begin() = start(run.value.template, run.value.style, run.value.key, moves = null)

    /**
     * Starts from the setup menu, honouring everything the engine can act on.
     *
     * The menu offers more than the engine reads today. Key, exercise shape, repetition count and
     * tempo all reach the run; voice motion, register, difficulty, metronome and hints do not,
     * because nothing in `DefaultProgressionGenerator` or the evaluator consumes them yet. Rather
     * than quietly drop them, the whole [VoiceLeadingMenuState] is kept on the run state and
     * surfaced in the UI state, so the configuration a player chose survives and the day the
     * engine grows those knobs the value is already there.
     */
    private fun beginFromMenu(menu: VoiceLeadingMenuState) {
        val key = SpelledPitchClass.parseOrNull(menu.key) ?: DEFAULT_KEY
        run.value = run.value.copy(menu = menu, key = key, style = styleFor(menu.exercise))
        start(
            template = run.value.template,
            style = styleFor(menu.exercise),
            key = key,
            moves = menu.repetitions,
            tempoBpm = menu.tempoBpm,
        )
    }

    /**
     * The menu's exercise names map onto the voicing shapes the generator can build.
     *
     * Drop 2, triad movement and common-tone work are named in the menu and are not shapes
     * `VoicingStyle` distinguishes, so they run as any correct voicing rather than being refused:
     * the motion measurement is still the point, and an unbuildable shape would just be a dead
     * option. The choice itself is preserved on the state either way.
     */
    private fun styleFor(exercise: VoiceLeadingExercise): VoicingStyle = when (exercise) {
        VoiceLeadingExercise.GuideTones -> VoicingStyle.GUIDE_TONES
        VoiceLeadingExercise.Shells -> VoicingStyle.SHELL
        VoiceLeadingExercise.SeventhChords -> VoicingStyle.ROOTLESS_A
        VoiceLeadingExercise.Triads -> VoicingStyle.ROOT_POSITION
        VoiceLeadingExercise.DropTwo, VoiceLeadingExercise.CommonTone -> VoicingStyle.ANY_VOICING
    }

    private fun start(
        template: ProgressionTemplate,
        style: VoicingStyle,
        key: SpelledPitchClass,
        moves: Int?,
        tempoBpm: Int? = null,
    ) {
        val current = run.value
        val placed = progressionFor(template, style, key)
        if (placed == null || placed.size < 2) {
            run.value = current.copy(
                message = "That progression could not be placed in this key with this voicing.",
            )
            return
        }
        // A repetition count longer than the progression cycles it, so asking for sixteen moves
        // through a four-chord ii-V-I keeps going round rather than stopping after three.
        val progression = tempoBpm?.let { placed.copy(tempoBpm = it) } ?: placed
        run.value = current.copy(
            mode = VoiceLeadingMode.PRACTICE,
            phase = VoiceLeadingPhase.READY,
            progression = progression,
            template = template,
            style = style,
            key = key,
            // Each pair of adjacent chords is one move, so a four-chord run asks three questions
            // unless the menu asked for a specific number.
            stepCount = moves ?: (progression.size - 1),
            index = 0,
            attempted = 0,
            correct = 0,
            message = null,
        )
        present(0)
    }

    private fun returnToSetup() {
        playbackJob?.cancel()
        capture.cancel()
        player.allNotesOff()
        run.value = run.value.copy(
            mode = VoiceLeadingMode.SETUP,
            phase = VoiceLeadingPhase.READY,
            result = null,
            analysis = null,
            motions = emptyList(),
        )
    }

    private fun present(index: Int) {
        val current = run.value
        val progression = current.progression ?: return
        val sourceEvent = progression.eventAt(index.mod(progression.size)) ?: return
        val targetEvent = progression.eventAt((index + 1).mod(progression.size)) ?: return

        val source = realizer.generateVoicings(sourceEvent.chord, sourceEvent.policy).firstOrNull()
        if (source == null) {
            run.value = current.copy(message = "No voicing could be built for ${sourceEvent.displaySymbol}.")
            return
        }

        // The benchmark: of every voicing the policy allows for the destination, the one a hand
        // reaches with least total motion. Shown after the answer so the player can see whether a
        // smoother route existed.
        val best = realizer.generateVoicings(targetEvent.chord, targetEvent.policy)
            .mapNotNull { candidate ->
                runCatching { analyzer.analyze(source.pitches, candidate.pitches).totalMotionSemitones }
                    .getOrNull()
            }
            .minOrNull()

        run.value = current.copy(
            phase = VoiceLeadingPhase.LISTENING,
            index = index,
            sourceEvent = sourceEvent,
            sourceVoicing = source,
            targetEvent = targetEvent,
            bestTotalMotion = best,
            result = null,
            analysis = null,
            motions = emptyList(),
            message = null,
        )
        playSource()
        arm()
    }

    /** Sounds the chord being moved from, so the ear has the starting point too. */
    private fun playSource() {
        val source = run.value.sourceVoicing ?: return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            try {
                player.load(InstrumentId(PRACTICE_INSTRUMENT))
                source.pitches.forEach { player.noteOn(it, SOURCE_VELOCITY) }
                delay(SOURCE_RING_MILLIS)
            } finally {
                player.allNotesOff()
            }
        }
    }

    private fun arm() {
        val target = run.value.targetEvent ?: return
        capture.arm(
            CapturePolicy.GuidedChord.copy(
                onsetPolicy = target.onsetPolicy,
                acceptedRange = target.policy.pitchRange,
            ),
        )
    }

    private fun advance() {
        val current = run.value
        val next = current.index + 1
        if (next >= current.stepCount) {
            playbackJob?.cancel()
            capture.cancel()
            run.value = current.copy(phase = VoiceLeadingPhase.COMPLETE)
            return
        }
        present(next)
    }

    private fun progressionFor(
        template: ProgressionTemplate,
        style: VoicingStyle,
        key: SpelledPitchClass,
    ): Progression? =
        when (val placed = generator.generate(template, KeyContext(key), style)) {
            is SpellingResult.Spelled -> placed.value
            is SpellingResult.Overflow -> null
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
        player.release()
        playbackJob?.cancel()
        viewModelScope.launch { midi.stop() }
    }

    private data class VoiceLeadingRun(
        val mode: VoiceLeadingMode = VoiceLeadingMode.SETUP,
        val phase: VoiceLeadingPhase = VoiceLeadingPhase.READY,
        val template: ProgressionTemplate = ProgressionTemplates.MajorTwoFiveOne,
        val style: VoicingStyle = VoicingStyle.ROOTLESS_A,
        val key: SpelledPitchClass = DEFAULT_KEY,
        val progression: Progression? = null,
        val index: Int = 0,
        val stepCount: Int = 0,
        val sourceEvent: ChordEvent? = null,
        val sourceVoicing: Voicing? = null,
        val targetEvent: ChordEvent? = null,
        val result: EvaluationResult? = null,
        val analysis: VoiceLeadingAnalysis? = null,
        val motions: List<VoiceMotionUi> = emptyList(),
        val bestTotalMotion: Int? = null,
        val attempted: Int = 0,
        val correct: Int = 0,
        val message: String? = null,
        val menu: VoiceLeadingMenuState? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SOURCE_RING_MILLIS = 1_400L
        const val SOURCE_VELOCITY = 78
        const val PRACTICE_INSTRUMENT = "instrument.practice_tone"

        val DEFAULT_KEY: SpelledPitchClass = requireNotNull(SpelledPitchClass.parseOrNull("C"))

        /** The progressions whose whole point is the movement between chords. */
        val TEMPLATES = listOf(
            ProgressionTemplates.MajorTwoFiveOne,
            ProgressionTemplates.MinorTwoFiveOne,
            ProgressionTemplates.Turnaround,
        )

        /** Styles that fix the shape enough for motion to mean something. */
        val STYLES = listOf(
            VoicingStyle.ROOTLESS_A,
            VoicingStyle.ROOTLESS_B,
            VoicingStyle.SHELL,
            VoicingStyle.GUIDE_TONES,
        )
    }
}
