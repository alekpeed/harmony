package com.harmonygates.core.music

import com.harmonygates.core.music.pitch.Accidental
import com.harmonygates.core.music.pitch.LetterName
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitch
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.atMidiNote
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.time.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pitch arithmetic, MIDI bounds, and the determinism guarantees generation depends on. */
class PitchAndDeterminismTest {

    @Test
    fun `middle C is MIDI 60 in octave 4`() {
        assertEquals(60, SpelledPitch(SpelledPitchClass.C, 4).midiNote.value)
        assertEquals(4, MidiNote(60).octave)
        assertEquals(0, MidiNote(60).pitchClass.value)
        assertEquals(-1, MidiNote(0).octave)
    }

    @Test
    fun `an enharmonic spelling keeps its own letter and octave`() {
        // Cb4 sounds as B3 but is written in octave 4. Notation software depends on this;
        // using the wrapped pitch class for octave arithmetic would put it an octave high.
        val cFlat4 = SpelledPitch(SpelledPitchClass(LetterName.C, Accidental.FLAT), 4)
        assertEquals(59, cFlat4.midiNote.value)

        val bSharp3 = SpelledPitch(SpelledPitchClass(LetterName.B, Accidental.SHARP), 3)
        assertEquals(60, bSharp3.midiNote.value)

        assertNotEquals(cFlat4.pitchClass, SpelledPitchClass.B)
    }

    @Test
    fun `placing a spelling at a MIDI note round trips`() {
        val cases = listOf(
            SpelledPitchClass.C to 60,
            SpelledPitchClass(LetterName.C, Accidental.FLAT) to 59,
            SpelledPitchClass(LetterName.B, Accidental.SHARP) to 60,
            SpelledPitchClass(LetterName.E, Accidental.SHARP) to 65,
            SpelledPitchClass(LetterName.B, Accidental.DOUBLE_FLAT) to 57,
        )

        for ((pitchClass, note) in cases) {
            val placed = pitchClass.atMidiNote(MidiNote(note))
            assertEquals(note, placed.midiNote.value)
            assertEquals(pitchClass, placed.pitchClass, "Placement must not respell")
        }
    }

    @Test
    fun `a spelling that cannot sound at a note fails loudly`() {
        assertFailsWith<IllegalStateException> {
            SpelledPitchClass.C.atMidiNote(MidiNote(61))
        }
    }

    @Test
    fun `MIDI notes are bounded`() {
        assertFailsWith<IllegalArgumentException> { MidiNote(128) }
        assertFailsWith<IllegalArgumentException> { MidiNote(-1) }
        assertNull(MidiNote.orNull(128))
        assertEquals(127, MidiNote.orNull(127)?.value)
    }

    @Test
    fun `pitch class names parse and render`() {
        val cases = listOf("C", "Db", "F#", "Bbb", "Cx", "E♭", "G♯")
        for (text in cases) {
            val parsed = requireNotNull(SpelledPitchClass.parseOrNull(text)) { "'$text' should parse" }
            val reparsed = SpelledPitchClass.parseOrNull(parsed.toString())
            assertEquals(parsed, reparsed, "'$text' did not survive a round trip")
        }
        assertNull(SpelledPitchClass.parseOrNull("H"))
        assertNull(SpelledPitchClass.parseOrNull("C###"))
        assertNull(SpelledPitchClass.parseOrNull(""))
    }

    @Test
    fun `the same seed always produces the same sequence`() {
        val first = DefaultSeededRandomFactory.create(SEED)
        val second = DefaultSeededRandomFactory.create(SEED)

        val items = (1..20).toList()
        repeat(100) {
            assertEquals(first.nextInt(1000), second.nextInt(1000))
            assertEquals(first.pick(items), second.pick(items))
            assertEquals(first.shuffled(items), second.shuffled(items))
            assertEquals(first.sample(items, 5), second.sample(items, 5))
        }
    }

    @Test
    fun `different seeds diverge`() {
        val first = DefaultSeededRandomFactory.create(SEED)
        val second = DefaultSeededRandomFactory.create(SEED + 1)
        val drawsFirst = (1..50).map { first.nextInt(1_000_000) }
        val drawsSecond = (1..50).map { second.nextInt(1_000_000) }
        assertNotEquals(drawsFirst, drawsSecond)
    }

    @Test
    fun `weighted picking respects zero weights`() {
        val random = DefaultSeededRandomFactory.create(SEED)
        val items = listOf("never", "always")
        repeat(200) {
            assertEquals("always", random.pickWeighted(items) { if (it == "never") 0.0 else 1.0 })
        }
    }

    @Test
    fun `sampling never returns more than it was given`() {
        val random = DefaultSeededRandomFactory.create(SEED)
        val items = listOf(1, 2, 3)
        assertEquals(3, random.sample(items, 10).size)
        assertTrue(random.sample(items, 2).distinct().size == 2)
    }

    @Test
    fun `a test clock can drive timing without sleeping`() {
        var now = 0L
        val clock = MonotonicClock { now }

        assertEquals(0L, clock.nowNanos())
        now = 250_000_000L
        assertEquals(250_000_000L, clock.nowNanos())
    }

    private companion object {
        const val SEED = 4242L
    }
}
