package com.harmonygates.core.midi

import com.harmonygates.core.music.pitch.MidiNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The byte-stream parser.
 *
 * Every case here is something real hardware does. The awkward ones — running status, a clock
 * byte landing between two data bytes, a message split across two reads — are exactly the ones
 * that never show up in a hand-written happy-path test and then break on a stage.
 */
class MidiMessageParserTest {

    private val parser = MidiMessageParser()
    private val at = 1_000L

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `a note on becomes a note on`() {
        val events = parser.parse(bytes(0x90, 60, 100), at)

        assertEquals(1, events.size)
        val noteOn = assertIs<MidiEvent.NoteOn>(events.single())
        assertEquals(MidiNote(60), noteOn.note)
        assertEquals(100, noteOn.velocity)
        assertEquals(0, noteOn.channel)
        assertEquals(at, noteOn.timestampNanos)
    }

    @Test
    fun `a note on at velocity zero is a note off`() {
        // 18_ACCEPTANCE_CRITERIA.md requires this. Almost every keyboard releases keys this way,
        // so reading it as a note-on would leave every note stuck down forever.
        val events = parser.parse(bytes(0x90, 60, 0), at)

        val noteOff = assertIs<MidiEvent.NoteOff>(events.single())
        assertEquals(MidiNote(60), noteOff.note)
        assertTrue(noteOff.fromZeroVelocityNoteOn, "The origin is recorded for diagnostics")
    }

    @Test
    fun `an explicit note off is a note off`() {
        val noteOff = assertIs<MidiEvent.NoteOff>(parser.parse(bytes(0x80, 60, 64), at).single())
        assertEquals(64, noteOff.velocity)
        assertTrue(!noteOff.fromZeroVelocityNoteOn)
    }

    @Test
    fun `channel is read from the status byte`() {
        val events = parser.parse(bytes(0x95, 60, 100), at)
        assertEquals(5, events.single().channel)
    }

    @Test
    fun `running status expands into separate messages`() {
        // A keyboard playing a fast chord omits the status byte after the first message.
        val events = parser.parse(bytes(0x90, 60, 100, 64, 100, 67, 100), at)

        assertEquals(3, events.size)
        assertEquals(
            listOf(60, 64, 67),
            events.map { (it as MidiEvent.NoteOn).note.value },
        )
    }

    @Test
    fun `running status survives a zero velocity release`() {
        val events = parser.parse(bytes(0x90, 60, 100, 60, 0), at)

        assertEquals(2, events.size)
        assertIs<MidiEvent.NoteOn>(events[0])
        assertIs<MidiEvent.NoteOff>(events[1])
    }

    @Test
    fun `a real-time byte between data bytes does not corrupt the message`() {
        // Clock (0xF8) is legal anywhere, including mid-message. Naive parsers read it as data
        // and turn a C into something else entirely.
        val events = parser.parse(bytes(0x90, 60, 0xF8, 100), at)

        val noteOn = assertIs<MidiEvent.NoteOn>(events.single())
        assertEquals(MidiNote(60), noteOn.note)
        assertEquals(100, noteOn.velocity)
    }

    @Test
    fun `active sensing is ignored and leaves running status intact`() {
        val events = parser.parse(bytes(0x90, 60, 100, 0xFE, 64, 100), at)

        assertEquals(2, events.size, "Active sensing must not become an event or break the run")
        assertEquals(listOf(60, 64), events.map { (it as MidiEvent.NoteOn).note.value })
    }

    @Test
    fun `a message split across two reads is assembled`() {
        // Android hands over whatever has arrived, which may be half a message.
        val first = parser.parse(bytes(0x90, 60), at)
        assertTrue(first.isEmpty(), "Half a message is not an event yet")

        val second = parser.parse(bytes(100), at + 1)
        val noteOn = assertIs<MidiEvent.NoteOn>(second.single())
        assertEquals(MidiNote(60), noteOn.note)
        assertEquals(at + 1, noteOn.timestampNanos)
    }

    @Test
    fun `running status carries across reads`() {
        parser.parse(bytes(0x90, 60, 100), at)
        val events = parser.parse(bytes(64, 100), at + 1)

        assertEquals(MidiNote(64), assertIs<MidiEvent.NoteOn>(events.single()).note)
    }

    @Test
    fun `control change is read, including the sustain pedal`() {
        val down = assertIs<MidiEvent.ControlChange>(parser.parse(bytes(0xB0, 64, 127), at).single())
        assertTrue(down.isSustainPedal)
        assertTrue(down.isSwitchedOn)

        val up = assertIs<MidiEvent.ControlChange>(parser.parse(bytes(0xB0, 64, 0), at).single())
        assertTrue(up.isSustainPedal)
        assertTrue(!up.isSwitchedOn)
    }

    @Test
    fun `a half depressed pedal counts as down`() {
        // The specification puts the switch threshold at 64. A half-pedal is still sustaining.
        val half = assertIs<MidiEvent.ControlChange>(parser.parse(bytes(0xB0, 64, 64), at).single())
        assertTrue(half.isSwitchedOn)

        val nearlyUp = assertIs<MidiEvent.ControlChange>(parser.parse(bytes(0xB0, 64, 63), at).single())
        assertTrue(!nearlyUp.isSwitchedOn)
    }

    @Test
    fun `pitch bend is centred at zero`() {
        val centre = assertIs<MidiEvent.PitchBend>(parser.parse(bytes(0xE0, 0x00, 0x40), at).single())
        assertEquals(0, centre.value)

        val bottom = assertIs<MidiEvent.PitchBend>(parser.parse(bytes(0xE0, 0x00, 0x00), at).single())
        assertEquals(-8192, bottom.value)

        val top = assertIs<MidiEvent.PitchBend>(parser.parse(bytes(0xE0, 0x7F, 0x7F), at).single())
        assertEquals(8191, top.value)
    }

    @Test
    fun `system exclusive payload is skipped without emitting anything`() {
        val events = parser.parse(bytes(0xF0, 0x7E, 0x00, 0x06, 0x01, 0xF7, 0x90, 60, 100), at)

        val noteOn = assertIs<MidiEvent.NoteOn>(events.single())
        assertEquals(MidiNote(60), noteOn.note)
    }

    @Test
    fun `a status byte aborts an unterminated system exclusive`() {
        // Some devices stop a dump without sending the terminator.
        val events = parser.parse(bytes(0xF0, 0x7E, 0x00, 0x90, 60, 100), at)
        assertEquals(MidiNote(60), assertIs<MidiEvent.NoteOn>(events.single()).note)
    }

    @Test
    fun `system common cancels running status`() {
        parser.parse(bytes(0x90, 60, 100), at)
        // Song position select, then two orphan data bytes that must not become a note.
        val events = parser.parse(bytes(0xF2, 0x00, 0x00, 64, 100), at)
        assertTrue(events.isEmpty(), "Nothing here is a channel message, got $events")
    }

    @Test
    fun `program change is parsed but not emitted`() {
        // One data byte, and no performance meaning. The point is that it consumes the right
        // number of bytes so the note after it still reads correctly.
        val events = parser.parse(bytes(0xC0, 42, 0x90, 60, 100), at)
        assertEquals(MidiNote(60), assertIs<MidiEvent.NoteOn>(events.single()).note)
    }

    @Test
    fun `channel pressure consumes one data byte`() {
        val events = parser.parse(bytes(0xD0, 90, 0x90, 60, 100), at)
        assertEquals(2, events.size)
        assertIs<MidiEvent.ChannelPressure>(events[0])
        assertIs<MidiEvent.NoteOn>(events[1])
    }

    @Test
    fun `orphan data bytes at the start of a stream are discarded`() {
        // Joining a stream mid-message: there is no status to attach these to.
        val events = parser.parse(bytes(60, 100), at)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `reset discards a partial message`() {
        parser.parse(bytes(0x90, 60), at)
        parser.reset()
        val events = parser.parse(bytes(100), at)
        assertTrue(events.isEmpty(), "A half message from before a reconnect must not become a note")
    }

    @Test
    fun `a system reset byte clears parser state`() {
        parser.parse(bytes(0x90, 60), at)
        val events = parser.parse(bytes(0xFF, 100), at)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a whole chord arrives in one buffer`() {
        val events = parser.parse(bytes(0x90, 60, 80, 0x90, 64, 80, 0x90, 67, 80, 0x90, 71, 80), at)

        assertEquals(listOf(60, 64, 67, 71), events.map { (it as MidiEvent.NoteOn).note.value })
        assertTrue(events.all { it.timestampNanos == at }, "One buffer, one arrival time")
    }

    @Test
    fun `parsing respects the offset and count given`() {
        val buffer = bytes(0xFF, 0xFF, 0x90, 60, 100, 0xFF)
        val events = parser.parse(buffer, offset = 2, count = 3, timestampNanos = at)
        assertEquals(MidiNote(60), assertIs<MidiEvent.NoteOn>(events.single()).note)
    }

    @Test
    fun `an empty buffer produces nothing`() {
        assertTrue(parser.parse(ByteArray(0), at).isEmpty())
    }
}
