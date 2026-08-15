package com.harmonygates.core.midi.capture

import app.cash.turbine.test
import com.harmonygates.core.midi.FakeMidiInputSource
import com.harmonygates.core.music.performance.CaptureCompletion
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.CaptureState
import com.harmonygates.core.music.performance.SustainCapturePolicy
import com.harmonygates.core.music.time.MonotonicClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The coroutine wiring between a MIDI source and the aggregator.
 *
 * The state machine itself is tested exhaustively in [OnsetAggregatorTest]; what is checked here
 * is that events reach it, that the heartbeat completes an attempt, and that a device loss ends
 * capture as a loss rather than as an answer.
 *
 * The clock is driven by the test scheduler's virtual time, so the delays inside the capture
 * loop and the timestamps on the events stay consistent with each other, and the whole thing
 * runs instantly.
 */
class MidiPerformanceCaptureTest {

    private fun TestScope.virtualClock() = MonotonicClock {
        testScheduler.currentTime * com.harmonygates.core.music.performance.PerformanceAttempt.NANOS_PER_MILLI
    }

    @Test
    fun `a chord reaches the aggregator and completes when the keyboard goes quiet`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        capture.attempts.test {
            capture.arm(CapturePolicy.GuidedChord)
            advanceTimeBy(5)
            source.chord(60, 64, 67, 71)
            advanceTimeBy(200)

            val attempt = awaitItem()
            assertEquals(
                listOf(60, 64, 67, 71),
                attempt.finalEffectiveNotes.map { it.value },
            )
            assertEquals(CaptureCompletion.OnsetsSettled, attempt.completion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `capture does not complete before the quiet window`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        capture.arm(CapturePolicy.GuidedChord)
        advanceTimeBy(5)
        source.noteOn(60)
        advanceTimeBy(40)

        assertEquals(CaptureState.COLLECTING, capture.state.value, "A chord is still arriving")
    }

    @Test
    fun `losing the keyboard mid-chord ends capture as a device loss`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        capture.attempts.test {
            capture.arm(CapturePolicy.GuidedChord)
            advanceTimeBy(5)
            source.chord(60, 64)
            advanceTimeBy(20)
            source.disconnect()
            advanceTimeBy(20)

            val attempt = awaitItem()
            assertEquals(CaptureCompletion.DeviceLost, attempt.completion)
            assertEquals(listOf(60, 64), attempt.finalEffectiveNotes.map { it.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an explicit submit ends capture immediately`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        capture.attempts.test {
            capture.arm(CapturePolicy.GuidedChord)
            advanceTimeBy(5)
            source.noteOn(60)
            advanceTimeBy(20)
            capture.submitNow()
            advanceTimeBy(20)

            val attempt = awaitItem()
            assertEquals(CaptureCompletion.ExplicitSubmit, attempt.completion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling produces no attempt`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        capture.arm(CapturePolicy.GuidedChord)
        advanceTimeBy(5)
        source.noteOn(60)
        capture.cancel()
        advanceTimeBy(300)

        assertEquals(CaptureState.IDLE, capture.state.value)
    }

    @Test
    fun `arming again captures a second chord independently`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        capture.attempts.test {
            capture.arm(CapturePolicy.GuidedChord)
            advanceTimeBy(5)
            source.chord(60, 64, 67)
            advanceTimeBy(200)
            assertEquals(listOf(60, 64, 67), awaitItem().finalEffectiveNotes.map { it.value })

            capture.arm(CapturePolicy.GuidedChord)
            advanceTimeBy(5)
            source.chord(65, 69, 72)
            advanceTimeBy(200)
            assertEquals(
                listOf(65, 69, 72),
                awaitItem().finalEffectiveNotes.map { it.value },
                "Nothing carries over from the first chord",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no heartbeat runs while nothing is armed`() = runTest {
        val clock = virtualClock()
        val source = FakeMidiInputSource(clock)
        val capture = MidiPerformanceCapture(source, clock, backgroundScope)

        advanceTimeBy(5_000)
        assertEquals(CaptureState.IDLE, capture.state.value)
        assertTrue(source.emitted.isEmpty())
    }
}
