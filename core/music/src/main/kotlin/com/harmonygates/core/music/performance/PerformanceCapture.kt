package com.harmonygates.core.music.performance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Turns a stream of MIDI events into discrete attempts.
 *
 * The contract from 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §4. All the judgement lives in
 * the onset aggregator in `core:midi`; this is the plumbing that gives it events and a heartbeat, and that
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
