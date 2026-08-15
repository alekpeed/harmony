package com.harmonygates.core.music.session

import com.harmonygates.core.music.exercise.DefaultExerciseGenerator
import com.harmonygates.core.music.exercise.ExerciseGenerator
import com.harmonygates.core.music.exercise.ExerciseInstance
import com.harmonygates.core.music.exercise.GenerationContext
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceCapture
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

    override suspend fun requestHint() {
        // Hint usage is recorded now because 06_PERFORMANCE_EVALUATION_AND_SCORING.md §9 weights
        // "correct after hint" below "independent correct". The assistance channels a hint
        // reveals arrive with Phase 7; the evidence trail starts here so it is never backfilled.
        hintsThisExercise++
        totalHints++
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
        _state.value = ExerciseSessionState.Presenting(
            ExercisePresentationModel.of(instance, index, sessionConfig.exerciseCount),
        )
    }

    private fun evaluate(attempt: PerformanceAttempt) {
        val instance = current ?: return
        val sessionConfig = config ?: return
        if (_state.value is ExerciseSessionState.Feedback) return

        val result = evaluator.evaluate(instance.requirement, attempt, sessionConfig.policy.onsetPolicy)
        _records += AttemptRecord(instance, attempt, result, hintsUsed = hintsThisExercise)

        _state.value = ExerciseSessionState.Feedback(
            exercise = ExercisePresentationModel.of(instance, index, sessionConfig.exerciseCount),
            result = result,
            nextAvailable = true,
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
