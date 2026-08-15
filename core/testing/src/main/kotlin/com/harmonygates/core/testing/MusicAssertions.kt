package com.harmonygates.core.testing

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitch
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingFamily
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Readable helpers for theory tests.
 *
 * A failing assertion should read like a musical statement, because the person debugging it is
 * usually asking a musical question: "did it spell the seventh right?", not "did element 3 of
 * the list match?".
 */
public object Music {

    private val realizer = DefaultChordRealizer()

    /** Parses a chord symbol, failing the test if it is malformed. */
    public fun chord(symbol: String): ChordSpec = JazzChordParser.parseOrThrow(symbol)

    /** Parses a space-separated note list such as `"C4 E4 G4 B4"`. */
    public fun notes(text: String): List<MidiNote> = pitches(text).map { it.midiNote }

    /** Parses a space-separated note list, keeping the spellings. */
    public fun pitches(text: String): List<SpelledPitch> = text.trim().split(WHITESPACE).map { token ->
        val octaveStart = token.indexOfFirst { it.isDigit() || it == '-' }
        require(octaveStart > 0) { "'$token' has no octave; write notes like C4 or Bb3" }
        val pitchClass = requireNotNull(SpelledPitchClass.parseOrNull(token.take(octaveStart))) {
            "'$token' does not start with a note name"
        }
        val octave = requireNotNull(token.substring(octaveStart).toIntOrNull()) {
            "'$token' does not end with an octave number"
        }
        SpelledPitch(pitchClass, octave)
    }

    /** Builds a voicing of [symbol] from a note list such as `"C4 E4 G4 B4"`. */
    public fun voicing(symbol: String, notes: String, family: VoicingFamily? = null): Voicing =
        realizer.analyze(chord(symbol), notes(notes), family)

    /** Asserts that a chord spells exactly as written, e.g. `"Db" to "Db F Ab Cb"`. */
    public fun assertSpells(symbol: String, expected: String) {
        val actual = realizer.chordTones(chord(symbol)).joinToString(" ")
        assertEquals(expected, actual, "$symbol should spell '$expected'")
    }

    /** Asserts the degrees a chord is built from, e.g. `"C7b9" to "1 3 5 b7 b9"`. */
    public fun assertDegrees(symbol: String, expected: String) {
        val actual = chord(symbol).degrees.joinToString(" ") { it.symbol }
        assertEquals(expected, actual, "$symbol should be built from '$expected'")
    }

    /** Asserts that a voicing contains a chord degree. */
    public fun assertContainsDegree(voicing: Voicing, degree: ChordDegree) {
        assertTrue(
            degree in voicing.metadata.includedDegrees,
            "${voicing.chord.symbol} voiced as ${voicing.spelledPitches.joinToString(" ")} " +
                "is missing its ${degree.symbol}",
        )
    }

    private val WHITESPACE = Regex("\\s+")
}
