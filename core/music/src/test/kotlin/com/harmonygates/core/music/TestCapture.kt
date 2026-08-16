package com.harmonygates.core.music

import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.CaptureState
import com.harmonygates.core.music.performance.NormalizedNoteEvent
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceCapture
import com.harmonygates.core.music.pitch.MidiNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A capture the test drives by hand.
 *
 * Both loops in this module — the exercise session and the progression run — take
 * [PerformanceCapture] as an interface precisely so they can be driven exactly rather than
 * played at, so the fake lives next to both of them rather than being written twice.
 */
class TestCapture : PerformanceCapture {

    private val _state = MutableStateFlow(CaptureState.IDLE)
    override val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _attempts = MutableSharedFlow<PerformanceAttempt>(extraBufferCapacity = ATTEMPT_BUFFER)
    override val attempts: Flow<PerformanceAttempt> = _attempts.asSharedFlow()

    var armCount = 0
        private set

    var cancelCount = 0
        private set

    var lastPolicy: CapturePolicy? = null
        private set

    override fun arm(policy: CapturePolicy) {
        armCount++
        lastPolicy = policy
        _state.value = CaptureState.ARMED
    }

    override fun cancel() {
        cancelCount++
        _state.value = CaptureState.IDLE
    }

    override fun submitNow() = Unit

    /** Delivers a settled attempt, as a real capture does when a chord goes quiet. */
    suspend fun deliver(attempt: PerformanceAttempt) {
        _state.value = CaptureState.COMPLETED
        _attempts.emit(attempt)
    }

    /** Plays these MIDI notes together. */
    suspend fun play(vararg notes: Int) = deliver(attemptOf(notes.toList()))

    private companion object {
        const val ATTEMPT_BUFFER = 8
    }
}

/** A settled attempt containing exactly these notes, struck together. */
fun attemptOf(notes: List<Int>, startedAtNanos: Long = 0L): PerformanceAttempt = PerformanceAttempt(
    startedAtNanos = startedAtNanos,
    completedAtNanos = startedAtNanos + 1,
    noteEvents = notes.map { NormalizedNoteEvent(MidiNote(it), VELOCITY, startedAtNanos) },
    finalEffectiveNotes = notes.map { MidiNote(it) }.sorted(),
    onsetSpreadNanos = 0,
    sustainUsed = false,
)

private const val VELOCITY = 80
