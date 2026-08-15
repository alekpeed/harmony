package com.harmonygates.core.midi.capture

import com.harmonygates.core.midi.Controller
import com.harmonygates.core.midi.MidiEvent
import com.harmonygates.core.music.performance.CaptureCompletion
import com.harmonygates.core.music.performance.OnsetPolicy
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.pitch.MidiNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scripted MIDI scenarios from 14_TESTING_AND_QUALITY.md §5.
 *
 * The spec lists nine of them and asks that each have "an expected normalized
 * `PerformanceAttempt`". They are the scenarios that separate a chord capture that works on a
 * bench from one that works under fingers: a hand spread, a roll, a brushed neighbouring key,
 * sustain left over from the previous exercise.
 *
 * Time is a plain counter. Nothing here sleeps, so the 250 ms roll costs nothing to test.
 */
class OnsetAggregatorTest {

    private val aggregator = OnsetAggregator()
    private var now = 0L

    private fun advance(millis: Long) {
        now += millis * PerformanceAttempt.NANOS_PER_MILLI
    }

    private fun noteOn(note: Int, velocity: Int = 80) =
        aggregator.onEvent(MidiEvent.NoteOn(MidiNote(note), velocity, 0, now))

    private fun noteOff(note: Int) =
        aggregator.onEvent(MidiEvent.NoteOff(MidiNote(note), 0, 0, now, fromZeroVelocityNoteOn = true))

    private fun sustain(down: Boolean) =
        aggregator.onEvent(MidiEvent.ControlChange(Controller.SUSTAIN_PEDAL, if (down) 127 else 0, 0, now))

    private fun tick() = aggregator.onTick(now)

    private fun notes(attempt: PerformanceAttempt) = attempt.finalEffectiveNotes.map { it.value }

    // --- Script 1: a perfectly simultaneous chord -------------------------------------------

    @Test
    fun `a perfectly simultaneous chord is captured with no spread`() {
        aggregator.arm(now)
        listOf(60, 64, 67, 71).forEach { noteOn(it) }

        assertNull(tick(), "Nothing completes while the chord is still fresh")
        advance(SETTLE_MS)
        val attempt = assertNotNull(tick())

        assertEquals(listOf(60, 64, 67, 71), notes(attempt))
        assertEquals(0L, attempt.onsetSpreadNanos)
        assertEquals(CaptureCompletion.OnsetsSettled, attempt.completion)
    }

    @Test
    fun `the first note never completes an attempt on its own`() {
        // 06_PERFORMANCE_EVALUATION_AND_SCORING.md §7: never auto-submit on the first note.
        // An evaluator handed the first note of a Cmaj7 would confidently mark it a C.
        aggregator.arm(now)
        assertNull(noteOn(60))
        advance(SHORT_MS)
        assertNull(tick(), "A chord is still arriving 20 ms in")
    }

    // --- Script 2: an 80 ms hand spread -----------------------------------------------------

    @Test
    fun `an 80 millisecond hand spread is one chord`() {
        aggregator.arm(now)
        noteOn(60)
        advance(40)
        noteOn(64)
        advance(40)
        noteOn(67)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(60, 64, 67), notes(attempt))
        assertEquals(80L, attempt.onsetSpreadMillis)
        assertTrue(OnsetPolicy.NormalRoll.permits(attempt.onsetSpreadNanos))
    }

    // --- Script 3: a 250 ms rolled chord ----------------------------------------------------

    @Test
    fun `a 250 millisecond roll stays one chord`() {
        aggregator.arm(now)
        noteOn(60)
        advance(80); noteOn(64)
        advance(80); noteOn(67)
        advance(90); noteOn(71)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(60, 64, 67, 71), notes(attempt))
        assertEquals(250L, attempt.onsetSpreadMillis)
    }

    @Test
    fun `the roll threshold behaves predictably at its edges`() {
        // The Phase 3 acceptance criterion. A roll inside the policy passes, one outside fails,
        // and the boundary itself is inclusive.
        val policy = OnsetPolicy.RolledAllowed(maxSpreadMillis = 250)
        assertTrue(policy.permits(249 * PerformanceAttempt.NANOS_PER_MILLI))
        assertTrue(policy.permits(250 * PerformanceAttempt.NANOS_PER_MILLI), "The limit itself is allowed")
        assertTrue(!policy.permits(251 * PerformanceAttempt.NANOS_PER_MILLI))
        assertTrue(policy.permits(null), "A single note has no spread to judge")

        val tight = OnsetPolicy.Simultaneous(maxSpreadMillis = 60)
        assertTrue(tight.permits(60 * PerformanceAttempt.NANOS_PER_MILLI))
        assertTrue(!tight.permits(61 * PerformanceAttempt.NANOS_PER_MILLI))
        assertTrue(OnsetPolicy.Unrestricted.permits(9_999 * PerformanceAttempt.NANOS_PER_MILLI))
    }

    @Test
    fun `a chord that outlasts the roll window is taken as it stands`() {
        aggregator.arm(now, CapturePolicy(maxRollWindowMillis = 200, quietWindowMillis = 80))
        noteOn(60)
        advance(90)
        noteOn(64)
        advance(90)
        noteOn(67)
        advance(40)

        val attempt = assertNotNull(tick())
        assertEquals(CaptureCompletion.MaxRollWindowReached, attempt.completion)
        assertEquals(listOf(60, 64, 67), notes(attempt))
    }

    // --- Script 4: an accidental neighbour, released immediately -----------------------------

    @Test
    fun `a brushed neighbouring key is not part of the answer`() {
        aggregator.arm(now)
        noteOn(60)
        noteOn(61) // caught the black key next to it
        advance(20)
        noteOff(61) // and let go straight away
        advance(20)
        noteOn(64)
        noteOn(67)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(60, 64, 67), notes(attempt), "The slip is excluded from the answer")
        assertTrue(
            attempt.noteEvents.any { it.note.value == 61 },
            "But the history keeps it, so the feedback can name what happened",
        )
    }

    @Test
    fun `challenge mode counts a brushed key immediately`() {
        // §7 allows exactly this: in a timed mode, an accidental note can count at once.
        aggregator.arm(now, CapturePolicy.Challenge)
        noteOn(60)
        noteOn(61)
        advance(20)
        noteOff(61)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertTrue(61 in notes(attempt), "No grace period means the slip counts")
    }

    // --- Script 5: sustain leftovers --------------------------------------------------------

    @Test
    fun `sustain left over from the previous exercise does not join the answer`() {
        // The failure 05_MIDI_INPUT_ENGINE.md §5 names: a correct new chord marked wrong because
        // the pedal was still down from the last one.
        aggregator.arm(now)
        sustain(down = true)
        noteOn(53) // an F from the previous exercise, still ringing
        advance(100) // held well beyond the accidental-slip grace period
        noteOff(53)
        advance(20)
        listOf(60, 64, 67, 71).forEach { noteOn(it) }
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(
            listOf(60, 64, 67, 71),
            notes(attempt),
            "The remnant is ringing under the pedal, not being played",
        )
        assertTrue(attempt.sustainUsed, "The pedal was used, and that is recorded")
        assertTrue(attempt.noteEvents.any { it.note.value == 53 }, "The history still has it")
    }

    @Test
    fun `an exercise about pedalling can include sustained notes`() {
        aggregator.arm(now, CapturePolicy(sustain = SustainCapturePolicy.IncludeSustained))
        sustain(down = true)
        noteOn(60)
        advance(100)
        noteOff(60)
        advance(20)
        noteOn(64)
        noteOn(67)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(60, 64, 67), notes(attempt), "What a listener hears is the answer here")
    }

    // --- Script 6: a duplicate note-on ------------------------------------------------------

    @Test
    fun `a duplicate note on is one note`() {
        aggregator.arm(now)
        noteOn(60)
        noteOn(60) // device quirk
        noteOn(64)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(60, 64), notes(attempt))
        assertEquals(2, attempt.noteEvents.size, "And it is one event, not a phantom second voice")
    }

    // --- Script 7 and 8: disconnect mid-chord, reconnect before the next trial ---------------

    @Test
    fun `losing the keyboard mid-chord is not a wrong answer`() {
        aggregator.arm(now)
        noteOn(60)
        noteOn(64)
        advance(20)

        val attempt = aggregator.deviceLost(now)
        assertEquals(CaptureCompletion.DeviceLost, attempt.completion)
        assertEquals(listOf(60, 64), notes(attempt), "What was played is still recorded")
    }

    @Test
    fun `arming again after a reconnect starts clean`() {
        aggregator.arm(now)
        noteOn(60)
        noteOn(64)
        aggregator.deviceLost(now)

        advance(500)
        aggregator.arm(now)
        listOf(65, 69, 72).forEach { noteOn(it) }
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(65, 69, 72), notes(attempt), "Nothing survives from the lost attempt")
    }

    // --- Script 9: a fast repeated chord ----------------------------------------------------

    @Test
    fun `two fast repeats of a chord are two attempts`() {
        aggregator.arm(now)
        listOf(60, 64, 67).forEach { noteOn(it) }
        advance(30)
        // The release of the last key is what finishes the chord.
        val first = assertNotNull(listOf(60, 64, 67).firstNotNullOfOrNull { noteOff(it) })
        assertEquals(CaptureCompletion.AllKeysReleased, first.completion)

        advance(40)
        aggregator.arm(now)
        listOf(60, 64, 67).forEach { noteOn(it) }
        advance(SETTLE_MS)

        val second = assertNotNull(tick())
        assertEquals(listOf(60, 64, 67), notes(second))
        assertEquals(notes(first), notes(second), "The same chord twice reads the same both times")
    }

    // --- Completion triggers ----------------------------------------------------------------

    @Test
    fun `lifting every key completes the attempt`() {
        aggregator.arm(now)
        noteOn(60)
        noteOn(64)
        advance(30)
        assertNull(noteOff(60), "One key up is not the end of a chord")
        val attempt = assertNotNull(noteOff(64))

        assertEquals(CaptureCompletion.AllKeysReleased, attempt.completion)
        assertEquals(listOf(60, 64), notes(attempt))
    }

    @Test
    fun `releases do not count as accidental slips`() {
        // Every note of a short chord is "released quickly". Letting go is how a player
        // finishes, so the grace period must not eat the whole answer.
        aggregator.arm(now)
        listOf(60, 64, 67).forEach { noteOn(it) }
        advance(20)
        noteOff(60); noteOff(64)
        val attempt = assertNotNull(noteOff(67))

        assertEquals(listOf(60, 64, 67), notes(attempt))
    }

    @Test
    fun `an explicit submit ends capture wherever it is`() {
        aggregator.arm(now)
        noteOn(60)
        advance(10)

        val attempt = aggregator.submit(now)
        assertEquals(CaptureCompletion.ExplicitSubmit, attempt.completion)
        assertEquals(listOf(60), notes(attempt))
    }

    @Test
    fun `an armed capture with nothing played yields an empty attempt on submit`() {
        aggregator.arm(now)
        advance(2_000)

        val attempt = aggregator.submit(now)
        assertTrue(attempt.isEmpty)
        assertNull(attempt.onsetSpreadNanos)
    }

    @Test
    fun `notes outside the accepted range are ignored`() {
        // A stuck key on a broken controller must not join every chord.
        aggregator.arm(now, CapturePolicy(acceptedRange = 48..84))
        noteOn(21)
        noteOn(60)
        noteOn(64)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(listOf(60, 64), notes(attempt))
    }

    @Test
    fun `response latency is measured from arming, separately from spread`() {
        aggregator.arm(now)
        advance(900) // the player thinks
        noteOn(60)
        advance(40)
        noteOn(64)
        advance(SETTLE_MS)

        val attempt = assertNotNull(tick())
        assertEquals(900L, attempt.responseLatencyNanos!! / PerformanceAttempt.NANOS_PER_MILLI)
        assertEquals(40L, attempt.onsetSpreadMillis, "Thinking time is not rhythm")
    }

    @Test
    fun `a cancelled capture produces nothing`() {
        aggregator.arm(now)
        noteOn(60)
        aggregator.cancel()
        advance(SETTLE_MS)

        assertNull(tick())
        assertEquals(CaptureState.IDLE, aggregator.state)
    }

    @Test
    fun `events before arming are ignored`() {
        assertNull(noteOn(60))
        assertEquals(CaptureState.IDLE, aggregator.state)
    }

    private companion object {
        /** Comfortably past the default quiet window plus stabilisation. */
        const val SETTLE_MS = 140L
        const val SHORT_MS = 20L
    }
}
