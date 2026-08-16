package com.harmonygates.core.music.progression

import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceCapture
import com.harmonygates.core.music.performance.PerformanceEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public enum class ProgressionRunStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
}

public enum class AdvanceReason {
    CORRECT_CHORD,
    MANUAL,
}

public data class ProgressionRunState(
    val progression: Progression? = null,
    val activeChordIndex: Int = 0,
    val status: ProgressionRunStatus = ProgressionRunStatus.IDLE,
    val advanceCount: Int = 0,
    val lastAdvance: AdvanceReason? = null,
    val lastResult: EvaluationResult? = null,
    val awaitingRelease: Boolean = false,
    val attempts: Int = 0,
    val clean: Int = 0,
) {
    public val activeEvent: ChordEvent? get() = progression?.eventAt(activeChordIndex)
    public val isRunning: Boolean get() = status == ProgressionRunStatus.RUNNING
    public fun visibleChords(): List<VisibleChord> = progression?.window(activeChordIndex).orEmpty()
}

public interface ProgressionRunEngine {
    public val state: StateFlow<ProgressionRunState>
    public suspend fun start(progression: Progression)
    public suspend fun submit(attempt: PerformanceAttempt)
    public suspend fun advanceManually()
    public suspend fun pause()
    public suspend fun resume()
    public suspend fun stop()
}

public class DefaultProgressionRunEngine(
    private val capture: PerformanceCapture,
    private val scope: CoroutineScope,
    private val evaluator: PerformanceEvaluator = DefaultPerformanceEvaluator(),
    private val soundingNotes: StateFlow<List<Int>> = MutableStateFlow(emptyList()),
) : ProgressionRunEngine {

    private val _state = MutableStateFlow(ProgressionRunState())
    override val state: StateFlow<ProgressionRunState> = _state.asStateFlow()

    private var collectionJob: Job? = null
    private var attemptsOnThisChord = 0

    override suspend fun start(progression: Progression) {
        stop()
        _state.value = ProgressionRunState(
            progression = progression,
            activeChordIndex = 0,
            status = ProgressionRunStatus.RUNNING,
        )
        attemptsOnThisChord = 0

        collectionJob = scope.launch {
            launch { capture.attempts.collect { attempt -> submit(attempt) } }
            launch { soundingNotes.collect { refreshReleaseGate() } }
        }

        armForActiveChord()
    }

    override suspend fun submit(attempt: PerformanceAttempt) {
        val current = _state.value
        val event = current.activeEvent ?: return
        if (!current.isRunning || current.awaitingRelease) return

        val result = evaluator.evaluate(event.requirement, attempt, event.onsetPolicy)
        attemptsOnThisChord++

        if (result.verdict.isCorrect) {
            advance(AdvanceReason.CORRECT_CHORD, result)
            return
        }

        _state.value = current.copy(
            lastResult = result,
            attempts = current.attempts + 1,
        )
        armForActiveChord()
    }

    override suspend fun advanceManually() {
        if (!_state.value.isRunning) return
        advance(AdvanceReason.MANUAL, result = null)
    }

    override suspend fun pause() {
        val current = _state.value
        if (current.status != ProgressionRunStatus.RUNNING) return
        capture.cancel()
        _state.value = current.copy(status = ProgressionRunStatus.PAUSED)
    }

    override suspend fun resume() {
        val current = _state.value
        if (current.status != ProgressionRunStatus.PAUSED) return
        _state.value = current.copy(status = ProgressionRunStatus.RUNNING, awaitingRelease = false)
        armForActiveChord()
    }

    override suspend fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        capture.cancel()
        _state.value = ProgressionRunState()
        attemptsOnThisChord = 0
    }

    private fun advance(reason: AdvanceReason, result: EvaluationResult?) {
        val current = _state.value
        val progression = current.progression ?: return
        val cleanlyPlayed = reason == AdvanceReason.CORRECT_CHORD && attemptsOnThisChord <= 1
        val next = current.activeChordIndex + 1
        attemptsOnThisChord = 0

        val finished = next >= progression.size && !progression.loop
        val resolvedIndex = when {
            progression.loop -> next.mod(progression.size)
            finished -> progression.size - 1
            else -> next
        }

        val advanced = current.copy(
            activeChordIndex = resolvedIndex,
            status = if (finished) ProgressionRunStatus.COMPLETED else current.status,
            advanceCount = current.advanceCount + 1,
            lastAdvance = reason,
            lastResult = result,
            attempts = if (result != null) current.attempts + 1 else current.attempts,
            clean = current.clean + if (cleanlyPlayed) 1 else 0,
            awaitingRelease = !finished &&
                reason == AdvanceReason.CORRECT_CHORD &&
                soundingNotes.value.isNotEmpty(),
        )
        _state.value = advanced

        if (finished) {
            capture.cancel()
        } else if (advanced.awaitingRelease) {
            refreshReleaseGate()
        } else {
            armForActiveChord()
        }
    }

    private fun refreshReleaseGate() {
        val current = _state.value
        if (!current.awaitingRelease || !current.isRunning) return
        if (soundingNotes.value.isNotEmpty()) return
        _state.value = current.copy(awaitingRelease = false)
        armForActiveChord()
    }

    private fun armForActiveChord() {
        val current = _state.value
        val event = current.activeEvent ?: return
        if (!current.isRunning || current.awaitingRelease) return
        capture.arm(
            CapturePolicy.GuidedChord.copy(
                onsetPolicy = event.onsetPolicy,
                acceptedRange = event.policy.pitchRange,
            ),
        )
    }
}
