package com.harmonygates.core.music.session

import com.harmonygates.core.music.assistance.AssistanceProfile
import com.harmonygates.core.music.assistance.HintLadder
import com.harmonygates.core.music.assistance.Recovery
import com.harmonygates.core.music.assistance.RecoveryPolicy
import com.harmonygates.core.music.exercise.DefaultExerciseGenerator
import com.harmonygates.core.music.exercise.ExerciseGenerator
import com.harmonygates.core.music.exercise.ExerciseInstance
import com.harmonygates.core.music.exercise.GenerationContext
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceCapture
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.performance.PerformanceEvaluator
import com.harmonygates.core.music.pitch.SpelledPitchClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The loop, once.
 *
 * 10_ANDROID_ARCHITECTURE.md §4 asks for one reusable engine rather than a lifecycle per screen:
 * "The chord, ear and reading features may have specialized wrappers, but session lifecycle
 * should not be duplicated." So this owns generate → present → arm → capture → evaluate →
 * feedback → next, and knows nothing about chords specifically, screens, or MIDI.
 */
public interface ExerciseSessionEngine {
    public val state: StateFlow<ExerciseSessionState>

    /** Everything that has happened, for the summary and for Phase 5's mastery update. */
    public val records: List<AttemptRecord>

    public suspend fun start(config: SessionConfig)

    /** Arms capture for the exercise on screen. */
    public suspend fun arm()

    /** Evaluates an attempt the host supplies, rather than waiting for capture. */
    public suspend fun submit(attempt: PerformanceAttempt)

    public suspend fun requestHint()

    /** Takes the scaffolded retry the recovery loop offered, if it offered one. */
    public suspend fun acceptRecovery()

    public suspend fun next()

    public suspend fun skip(reason: SkipReason)

    public suspend fun pause(reason: PauseReason)

    public suspend fun resume()

    public suspend fun stop()
}

/**
 * The production session engine.
 *
 * Deterministic: a [SessionConfig]'s seed reproduces the whole session, exercise for exercise.
 * Each exercise derives its own seed from the session seed and its index, so replaying a session
 * and jumping to exercise seven both give the same chord.
 *
 * The engine takes [PerformanceCapture] as an interface, so a test drives it with scripted
 * attempts and the app drives it with a keyboard, and neither knows which the other uses.
 */
public class DefaultExerciseSessionEngine(
    private val capture: PerformanceCapture,
    private val scope: CoroutineScope,
    private val generator: ExerciseGenerator = DefaultExerciseGenerator(),
    private val evaluator: PerformanceEvaluator = DefaultPerformanceEvaluator(),
    private val hints: HintLadder = HintLadder(),
    private val recovery: RecoveryPolicy = RecoveryPolicy(),
    /** Live sounding notes, for the capturing state. Empty when the host does not supply them. */
    private val soundingNotes: StateFlow<List<Int>> = MutableStateFlow(emptyList()),
) : ExerciseSessionEngine {

    private val _state = MutableStateFlow<ExerciseSessionState>(ExerciseSessionState.Idle)
    override val state: StateFlow<ExerciseSessionState> = _state.asStateFlow()

    private val _records = mutableListOf<AttemptRecord>()
    override val records: List<AttemptRecord> get() = _records.toList()

    private var config: SessionConfig? = null
    private var current: ExerciseInstance? = null
    private var index = 0
    private var hintsThisExercise = 0
    private var totalHints = 0
    private var rootsSeen = mutableSetOf<SpelledPitchClass>()

    /** What is on screen for the current exercise. Raised by a hint, reset by the next chord. */
    private var assistance: AssistanceProfile = AssistanceProfile.Nothing
    private var lastHint: String? = null

    /** Diagnoses of consecutive failures on the current exercise, newest last. */
    private val recentErrors = mutableListOf<ErrorClass>()
    private var collectionJob: Job? = null
    private var pausedFrom: ExerciseSessionState? = null

    override suspend fun start(config: SessionConfig) {
        stop()
        this.config = config
        _state.value = ExerciseSessionState.Loading(config.policy.id.value)
        _records.clear()
        index = 0
        totalHints = 0
        rootsSeen = mutableSetOf()

        collectionJob = scope.launch {
            launch {
                capture.attempts.collect { attempt -> evaluate(attempt) }
            }
            launch {
                soundingNotes.collect { notes ->
                    val presenting = _state.value
                    if (presenting is ExerciseSessionState.Armed && notes.isNotEmpty()) {
                        _state.value = ExerciseSessionState.Capturing(
                            LivePerformanceState(presenting.exercise, notes),
                        )
                    } else if (presenting is ExerciseSessionState.Capturing) {
                        _state.value = ExerciseSessionState.Capturing(
                            presenting.live.copy(soundingNotes = notes),
                        )
                    }
                }
            }
        }

        present()
    }

    override suspend fun arm() {
        val presenting = _state.value as? ExerciseSessionState.Presenting ?: return
        val policy = config?.policy ?: return
        capture.arm(
            CapturePolicy.GuidedChord.copy(
                onsetPolicy = policy.onsetPolicy,
                acceptedRange = policy.pitchRange,
            ),
        )
        _state.value = ExerciseSessionState.Armed(presenting.exercise)
    }

    override suspend fun submit(attempt: PerformanceAttempt) {
        evaluate(attempt)
    }

    /**
     * Reveals one more thing.
     *
     * One channel per request, never the whole answer: 02_GAME_LOOP_AND_PROGRESSION.md §6 asks
     * assistance to come down "by only one dimension at a time", which is also what makes the
     * next attempt evidence about that one dimension.
     *
     * The count rises whether or not there was anything left to reveal — asking is what
     * 06_PERFORMANCE_EVALUATION_AND_SCORING.md §9 weights, and a player who asked and found no
     * more help still asked.
     */
    override suspend fun requestHint() {
        hintsThisExercise++
        totalHints++

        val hint = hints.next(assistance) ?: return
        assistance = hint.profile
        lastHint = hint.label
        represent()
    }

    /**
     * Accepts the help the recovery loop offered.
     *
     * Separate from [requestHint] because it is not the player asking: §6's scaffolded retry is
     * offered, and taking it should not be counted against them as a hint they went looking for.
     */
    override suspend fun acceptRecovery() {
        val offered = (_state.value as? ExerciseSessionState.Feedback)?.recovery
        val scaffold = offered as? Recovery.Scaffold ?: return
        assistance = scaffold.hint.profile
        lastHint = scaffold.hint.label
    }

    override suspend fun next() {
        val sessionConfig = config ?: return
        index++
        if (index >= sessionConfig.exerciseCount) {
            complete()
        } else {
            present()
        }
    }

    override suspend fun skip(reason: SkipReason) {
        val instance = current ?: return
        val emptyAttempt = PerformanceAttempt.empty(startedAtNanos = 0, completedAtNanos = 0)
        _records += AttemptRecord(
            instance = instance,
            attempt = emptyAttempt,
            result = evaluator.evaluate(instance.requirement, emptyAttempt),
            skipped = true,
            hintsUsed = hintsThisExercise,
        )
        capture.cancel()
        next()
    }

    override suspend fun pause(reason: PauseReason) {
        if (_state.value is ExerciseSessionState.Paused) return
        pausedFrom = _state.value
        capture.cancel()
        _state.value = ExerciseSessionState.Paused(reason)
    }

    /**
     * Returns to the exercise that was on screen.
     *
     * Deliberately drops back to [ExerciseSessionState.Presenting] rather than straight to armed:
     * 10_ANDROID_ARCHITECTURE.md §9 requires a re-arm after a background trip, so a chord held
     * across the interruption cannot be scored as an answer.
     */
    override suspend fun resume() {
        val previous = pausedFrom ?: return
        pausedFrom = null
        val exercise = previous.visibleExercise
        _state.value = if (exercise != null) {
            ExerciseSessionState.Presenting(exercise)
        } else {
            ExerciseSessionState.Idle
        }
    }

    override suspend fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        capture.cancel()
        current = null
        config = null
        _state.value = ExerciseSessionState.Idle
    }

    // --- Internals --------------------------------------------------------------------------

    private fun present() {
        val sessionConfig = config ?: return
        hintsThisExercise = 0
        val instance = generator.generate(
            policy = sessionConfig.policy,
            // Derived per exercise, so a session replays identically and any single exercise
            // can be reproduced on its own from the session seed and its index.
            seed = sessionConfig.seed + index,
            context = GenerationContext(
                rootsAlreadySeen = rootsSeen.toSet(),
                previousChord = current?.chord,
                index = index,
            ),
        )
        current = instance
        rootsSeen += instance.chord.root
        assistance = AssistanceProfile.from(instance.presentation)
        lastHint = null
        recentErrors.clear()
        _state.value = ExerciseSessionState.Presenting(
            ExercisePresentationModel.of(instance, index, sessionConfig.exerciseCount),
        )
    }

    /**
     * Redraws the exercise at the assistance now in force.
     *
     * The instance is copied with a new presentation rather than regenerated: the chord, the
     * seed and above all the requirement stay exactly as they were, which is Phase 7's whole
     * acceptance criterion — the same exercise at a different assistance level is the same
     * question.
     */
    private fun represent() {
        val instance = current ?: return
        val sessionConfig = config ?: return
        val revealed = instance.copy(presentation = assistance.presentation)
        val model = ExercisePresentationModel.of(revealed, index, sessionConfig.exerciseCount, lastHint)

        _state.value = when (val state = _state.value) {
            is ExerciseSessionState.Presenting -> ExerciseSessionState.Presenting(model)
            is ExerciseSessionState.Armed -> ExerciseSessionState.Armed(model)
            is ExerciseSessionState.Capturing -> ExerciseSessionState.Capturing(state.live.copy(exercise = model))
            else -> return
        }
    }

    private fun evaluate(attempt: PerformanceAttempt) {
        val instance = current ?: return
        val sessionConfig = config ?: return
        if (_state.value is ExerciseSessionState.Feedback) return

        val result = evaluator.evaluate(instance.requirement, attempt, sessionConfig.policy.onsetPolicy)
        _records += AttemptRecord(instance, attempt, result, hintsUsed = hintsThisExercise)

        // A run of the *same* diagnosis is what the recovery loop reacts to, so the list is
        // cleared by a correct answer rather than accumulating across the whole session.
        if (result.verdict.isCorrect) {
            recentErrors.clear()
        } else {
            result.semanticErrors.firstOrNull()?.let { recentErrors += ErrorClass.of(it) }
        }

        _state.value = ExerciseSessionState.Feedback(
            exercise = ExercisePresentationModel.of(
                instance.copy(presentation = assistance.presentation),
                index,
                sessionConfig.exerciseCount,
                lastHint,
            ),
            result = result,
            nextAvailable = true,
            recovery = recovery.decide(recentErrors.toList(), assistance),
        )
    }

    private fun complete() {
        val sessionConfig = config ?: return
        capture.cancel()
        _state.value = ExerciseSessionState.Completed(
            SessionSummary.of(_records.toList(), sessionConfig.exerciseCount, totalHints),
        )
    }
}
