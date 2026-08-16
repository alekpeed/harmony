package com.harmonygates.core.midi

import com.harmonygates.core.music.pitch.MidiNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sustain semantics and note-state bookkeeping.
 *
 * The load-bearing case is [a pedal held across a chord change keeps the old notes sounding]:
 * that is the situation where a naive tracker marks a correct new chord wrong, which
 * 05_MIDI_INPUT_ENGINE.md §5 explicitly says must not happen.
 */
class ActiveNoteTrackerTest {

    private val tracker = ActiveNoteTracker()
    private var clock = 0L

    private fun tick() = clock++

    private fun noteOn(note: Int, velocity: Int = 80) =
        tracker.apply(MidiEvent.NoteOn(MidiNote(note), velocity, 0, tick()))

    private fun noteOff(note: Int) =
        tracker.apply(MidiEvent.NoteOff(MidiNote(note), 0, 0, tick(), fromZeroVelocityNoteOn = true))

    private fun sustain(down: Boolean) = tracker.apply(
        MidiEvent.ControlChange(Controller.SUSTAIN_PEDAL, if (down) 127 else 0, 0, tick()),
    )

    private fun controlChange(controller: Int, value: Int = 0) =
        tracker.apply(MidiEvent.ControlChange(controller, value, 0, tick()))

    private fun notes(vararg values: Int) = values.map { MidiNote(it) }.toSet()

    @Test
    fun `a held key is sounding`() {
        val state = noteOn(60)
        assertEquals(notes(60), state.physicallyHeld)
        assertEquals(notes(60), state.sounding)
        assertTrue(state.pedalSustained.isEmpty())
    }

    @Test
    fun `releasing a key stops it`() {
        noteOn(60)
        val state = noteOff(60)
        assertTrue(state.isSilent)
    }

    @Test
    fun `a chord is tracked as a set, in pitch order`() {
        noteOn(64)
        noteOn(60)
        val state = noteOn(67)

        assertEquals(notes(60, 64, 67), state.sounding)
        assertEquals(listOf(60, 64, 67), state.soundingAscending.map { it.value })
    }

    @Test
    fun `a duplicate note on is not a second note`() {
        // A device quirk 05_MIDI_INPUT_ENGINE.md §8 names directly.
        noteOn(60, velocity = 80)
        val state = noteOn(60, velocity = 110)

        assertEquals(1, state.physicallyHeld.size)
        assertEquals(110, state.velocities[MidiNote(60)], "A re-strike updates the velocity")
    }

    @Test
    fun `a repeated note off is harmless`() {
        noteOn(60)
        noteOff(60)
        val state = noteOff(60)
        assertTrue(state.isSilent)
    }

    @Test
    fun `a note off for a key that was never down is harmless`() {
        val state = noteOff(60)
        assertTrue(state.isSilent)
    }

    @Test
    fun `the pedal keeps a released note sounding`() {
        noteOn(60)
        sustain(down = true)
        val state = noteOff(60)

        assertTrue(state.physicallyHeld.isEmpty(), "The finger has left the key")
        assertEquals(notes(60), state.pedalSustained)
        assertEquals(notes(60), state.sounding, "But a listener still hears it")
    }

    @Test
    fun `lifting the pedal stops what only the pedal was holding`() {
        noteOn(60)
        sustain(down = true)
        noteOff(60)
        val state = sustain(down = false)

        assertTrue(state.isSilent)
        assertTrue(!state.sustainPedalDown)
    }

    @Test
    fun `lifting the pedal leaves keys that are still held`() {
        noteOn(60)
        noteOn(64)
        sustain(down = true)
        noteOff(60)
        val state = sustain(down = false)

        assertEquals(notes(64), state.sounding, "64 is still under a finger")
    }

    @Test
    fun `a pedal held across a chord change keeps the old notes sounding`() {
        // Play C major, hold the pedal, release, play F major. Everything is still ringing.
        noteOn(60); noteOn(64); noteOn(67)
        sustain(down = true)
        noteOff(60); noteOff(64); noteOff(67)
        val state = tracker.applyAll(
            listOf(
                MidiEvent.NoteOn(MidiNote(65), 80, 0, tick()),
                MidiEvent.NoteOn(MidiNote(69), 80, 0, tick()),
                MidiEvent.NoteOn(MidiNote(72), 80, 0, tick()),
            ),
        )

        assertEquals(notes(60, 64, 65, 67, 69, 72), state.sounding)
        assertEquals(
            notes(65, 69, 72),
            state.physicallyHeld,
            "Only the new chord is under the fingers, which is what an exercise should judge",
        )
    }

    @Test
    fun `clearing sustained remnants keeps what the fingers are on`() {
        // The §5 policy: arming a new attempt must not inherit the last one's pedal wash.
        noteOn(60); noteOn(64)
        sustain(down = true)
        noteOff(60)
        val state = tracker.clearSustainedRemnants()

        assertEquals(notes(64), state.sounding)
        assertTrue(state.pedalSustained.isEmpty())
        assertTrue(state.sustainPedalDown, "The pedal is still physically down")
    }

    @Test
    fun `restriking a pedal held note takes it back under the finger`() {
        noteOn(60)
        sustain(down = true)
        noteOff(60)
        val state = noteOn(60)

        assertEquals(notes(60), state.physicallyHeld)
        assertTrue(state.pedalSustained.isEmpty(), "It is a held key now, not a remnant")
        assertEquals(notes(60), state.sounding)
    }

    @Test
    fun `all notes off silences everything`() {
        noteOn(60); noteOn(64)
        sustain(down = true)
        noteOff(60)
        val state = controlChange(Controller.ALL_NOTES_OFF)

        assertTrue(state.isSilent)
    }

    @Test
    fun `reset clears everything including the pedal`() {
        noteOn(60)
        sustain(down = true)
        val state = tracker.reset()

        assertTrue(state.isSilent)
        assertTrue(!state.sustainPedalDown)
    }

    @Test
    fun `reset all controllers lifts the pedal`() {
        noteOn(60)
        sustain(down = true)
        noteOff(60)
        val state = controlChange(Controller.RESET_ALL_CONTROLLERS)

        assertTrue(state.isSilent)
        assertTrue(!state.sustainPedalDown)
    }

    @Test
    fun `expression events do not change what is sounding`() {
        noteOn(60)
        val before = tracker.snapshot()

        tracker.apply(MidiEvent.PitchBend(2000, 0, tick()))
        tracker.apply(MidiEvent.ChannelPressure(90, 0, tick()))
        tracker.apply(MidiEvent.PolyphonicAftertouch(MidiNote(60), 90, 0, tick()))

        assertEquals(before.sounding, tracker.snapshot().sounding)
    }

    @Test
    fun `a snapshot does not change under later events`() {
        noteOn(60)
        val snapshot = tracker.snapshot()
        noteOn(64)

        assertEquals(notes(60), snapshot.sounding, "A handed-out value must be immutable")
    }

    @Test
    fun `velocity is remembered for held notes`() {
        noteOn(60, velocity = 120)
        noteOn(64, velocity = 40)
        val state = tracker.snapshot()

        assertEquals(120, state.velocities[MidiNote(60)])
        assertEquals(40, state.velocities[MidiNote(64)])
    }
}
