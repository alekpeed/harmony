package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.key.Functions
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.key.Mode
import com.harmonygates.core.music.pitch.ScaleDegree
import com.harmonygates.core.music.pitch.SpellingResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Functional harmony (04_HARMONY_DOMAIN_ENGINE.md §10).
 *
 * The four examples the specification names are asserted first, verbatim, then generalised
 * across all twelve keys.
 */
class FunctionalHarmonyTest {

    @Test
    fun `ii7 in C major is Dm7`() {
        val chord = Functions.IIm7.resolveOrThrow(KeyContext.major("C"))
        assertEquals("Dm7", chord.symbol)
        assertEquals(ChordFormulas.MinorSeventh.id, chord.formulaId)
    }

    @Test
    fun `V7 of ii in Eb major is C7`() {
        val chord = Functions.secondaryDominantOf(2).resolveOrThrow(KeyContext.major("Eb"))
        // The ii chord of Eb is F minor; the dominant of F is C7.
        assertEquals("C7", chord.symbol)
    }

    @Test
    fun `the tritone substitute bII7 in C is Db7`() {
        val chord = Functions.FlatII7.resolveOrThrow(KeyContext.major("C"))
        assertEquals("Db7", chord.symbol, "The flat second is written on D, never on C#")
    }

    @Test
    fun `the backdoor cadence in C is Fm7 to Bb7 to Cmaj7`() {
        val key = KeyContext.major("C")
        val symbols = Functions.BackdoorCadence.map { it.resolveOrThrow(key).symbol }
        assertEquals(listOf("Fm7", "Bb7", "Cmaj7"), symbols)
    }

    @Test
    fun `the diatonic seventh chords of C major are the textbook seven`() {
        val key = KeyContext.major("C")
        val symbols = Functions.majorDiatonicSevenths.map { it.resolveOrThrow(key).symbol }
        assertEquals(
            listOf("Cmaj7", "Dm7", "Em7", "Fmaj7", "G7", "Am7", "Bm7b5"),
            symbols,
        )
    }

    @Test
    fun `the diatonic sevenths spell correctly in a five-flat key`() {
        val key = KeyContext.major("Db")
        val symbols = Functions.majorDiatonicSevenths.map { it.resolveOrThrow(key).symbol }
        assertEquals(
            listOf("Dbmaj7", "Ebm7", "Fm7", "Gbmaj7", "Ab7", "Bbm7", "Cm7b5"),
            symbols,
        )
    }

    @Test
    fun `a two five one resolves in every standard key`() {
        for (key in KeyContext.StandardMajorKeys) {
            val chords = Functions.MajorTwoFiveOne.map { it.resolveOrThrow(key) }
            assertEquals(3, chords.size)

            val tonicPitchClass = key.tonic.pitchClass.value
            assertEquals((tonicPitchClass + 2).mod(12), chords[0].root.pitchClass.value, "ii of $key")
            assertEquals((tonicPitchClass + 7).mod(12), chords[1].root.pitchClass.value, "V of $key")
            assertEquals(tonicPitchClass, chords[2].root.pitchClass.value, "I of $key")
        }
    }

    @Test
    fun `a minor two five one uses a half diminished two and a raised leading tone`() {
        val key = KeyContext.minor("C")
        val symbols = Functions.MinorTwoFiveOne.map { it.resolveOrThrow(key).symbol }
        assertEquals(listOf("Dm7b5", "G7", "Cm7"), symbols)

        // The G7 supplies B natural, which C natural minor does not contain: that is the whole
        // point of the raised leading tone in Region 3.
        val dominant = Functions.V7OfMinor.resolveOrThrow(key)
        assertTrue(dominant.degrees.any { it.number == 3 && it.alteration == 0 })
    }

    @Test
    fun `roman numerals render with conventional casing`() {
        assertEquals("Imaj7", Functions.IMaj7.romanNumeral.symbol)
        assertEquals("ii7", Functions.IIm7.romanNumeral.symbol)
        assertEquals("V7", Functions.V7.romanNumeral.symbol)
        assertEquals("viiø7", Functions.VIIHalfDim7.romanNumeral.symbol)
        assertEquals("bII7", Functions.FlatII7.romanNumeral.symbol)
        assertEquals("bVII7", Functions.FlatVII7.romanNumeral.symbol)
        assertEquals("V7/ii", Functions.secondaryDominantOf(2).symbol)
    }

    @Test
    fun `every scale degree of every standard key is writable`() {
        val modes = listOf(Mode.MAJOR, Mode.NATURAL_MINOR, Mode.HARMONIC_MINOR, Mode.MELODIC_MINOR)
        for (key in KeyContext.StandardMajorKeys) {
            for (mode in modes) {
                val context = KeyContext(key.tonic, mode)
                for (degree in 1..7) {
                    val result = context.degreeRoot(ScaleDegree(degree))
                    assertIs<SpellingResult.Spelled<*>>(result, "$context degree $degree is unwritable")
                }
            }
        }
    }

    @Test
    fun `a scale uses each letter exactly once`() {
        for (key in KeyContext.StandardMajorKeys) {
            val letters = key.scale.map { it.letter }
            assertEquals(7, letters.distinct().size, "$key repeats or skips a letter: $letters")
        }
    }

    @Test
    fun `key signatures come out as the circle of fifths says they should`() {
        assertEquals(0, KeyContext.major("C").signatureAccidentals)
        assertEquals(-1, KeyContext.major("F").signatureAccidentals)
        assertEquals(-2, KeyContext.major("Bb").signatureAccidentals)
        assertEquals(-5, KeyContext.major("Db").signatureAccidentals)
        assertEquals(-6, KeyContext.major("Gb").signatureAccidentals)
        assertEquals(1, KeyContext.major("G").signatureAccidentals)
        assertEquals(2, KeyContext.major("D").signatureAccidentals)
        assertEquals(5, KeyContext.major("B").signatureAccidentals)
    }
}
