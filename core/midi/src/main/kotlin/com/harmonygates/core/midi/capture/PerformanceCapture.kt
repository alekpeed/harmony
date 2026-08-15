package com.harmonygates.core.midi.capture

import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.time.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Turns a stream of MIDI events into discrete attempts.
 *
 * The contract from 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §4. All the judgement lives in
 * [OnsetAggregator]; this is the plumbing that gives it events and a heartbeat, and that
 * notices when the keyboard disappears.
 */
public interface PerformanceCapture {
    public val state: StateFlow<CaptureState>

    /** Completed attempts, one per armed capture. */
    public val attempts: Flow<PerformanceAttempt>

    /** Begins listening for an answer. */
    public fun arm(policy: CapturePolicy)

    /** Abandons the current capture without producing an attempt. */
    public fun cancel()

    /** Ends the current capture now — a beat boundary, or a submit button. */
    public fun submitNow()
}

/**
 * The production capture, over a [MidiInputSource].
 *
 * The tick loop only runs while a capture is armed. A background heartbeat that ran all the
 * time would keep the CPU awake through a session for no benefit — nothing can complete when
 * nothing is being captured.
 */
public class MidiPerformanceCapture(
    private val source: MidiInputSource,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
    private val aggregator: OnsetAggregator = OnsetAggregator(),
    /** How often to check the quiet window. Well below it, so the check is never the long pole. */
    private val tickIntervalMillis: Long = DEFAULT_TICK_MS,
) : PerformanceCapture {

    private val _state = MutableStateFlow(CaptureState.IDLE)
    override val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _attempts = MutableSharedFlow<PerformanceAttempt>(replay = 0, extraBufferCapacity = 8)
    override val attempts: Flow<PerformanceAttempt> = _attempts.asSharedFlow()

    private var captureJob: Job? = null

    override fun arm(policy: CapturePolicy) {
        captureJob?.cancel()
        aggregator.arm(clock.nowNanos(), policy)
        _state.value = aggregator.state

        captureJob = scope.launch {
            launch {
                source.events.collect { event ->
                    aggregator.onEvent(event)?.let { finish(it) }
                    _state.value = aggregator.state
                }
            }
            launch {
                // A keyboard lost mid-chord ends the attempt, but as a device loss rather than
                // a wrong answer (05_MIDI_INPUT_ENGINE.md §9).
                source.connectionState.collect { connection ->
                    if (connection is MidiConnectionState.Error &&
                        aggregator.state != CaptureState.COMPLETED
                    ) {
                        finish(aggregator.deviceLost(clock.nowNanos()))
                    }
                }
            }
            while (isActive) {
                delay(tickIntervalMillis)
                aggregator.onTick(clock.nowNanos())?.let { finish(it) }
                _state.value = aggregator.state
            }
        }
    }

    override fun cancel() {
        captureJob?.cancel()
        captureJob = null
        aggregator.cancel()
        _state.value = aggregator.state
    }

    override fun submitNow() {
        if (_state.value == CaptureState.COMPLETED || _state.value == CaptureState.IDLE) return
        scope.launch { finish(aggregator.submit(clock.nowNanos())) }
    }

    private suspend fun finish(attempt: PerformanceAttempt) {
        _state.value = CaptureState.COMPLETED
        captureJob?.cancel()
        captureJob = null
        _attempts.emit(attempt)
    }

    private companion object {
        const val DEFAULT_TICK_MS = 10L
    }
}
