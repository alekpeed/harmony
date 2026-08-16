package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.key.Functions
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.key.Mode
import com.harmonygates.core.music.key.RomanNumeralParser
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.pitch.SpelledPitchClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading roman numerals off a chart.
 *
 * This exists so that Region 12's mandatory vocabulary can be content rather than Kotlin —
 * 21_CONTENT_AUTHORING_GUIDE.md §1's rule. The tests are written as an author would use it: the
 * progressions in `content/progressions/progressions.json` are exactly these shapes.
 */
class RomanNumeralParserTest {

    private fun parse(text: String) = assertNotNull(
        RomanNumeralParser.parseOrNull(text),
        "'$text' should be readable",
    )

    private fun inC(text: String): String =
        parse(text).resolveOrThrow(KeyContext(SpelledPitchClass.C)).symbol

    // --- The vocabulary ------------------------------------------------------------------

    @Test
    fun `the diatonic sevenths of a major key`() {
        assertEquals("Cmaj7", inC("Imaj7"))
        assertEquals("Dm7", inC("ii7"))
        assertEquals("Em7", inC("iii7"))
        assertEquals("Fmaj7", inC("IVmaj7"))
        assertEquals("G7", inC("V7"))
        assertEquals("Am7", inC("vi7"))
        assertEquals("Bm7b5", inC("viiø7"))
    }

    @Test
    fun `case decides the quality only when the figure is ambiguous`() {
        // `ii7` is the two chord; `II7` is the dominant of the five. On a chart the capitals are
        // the whole difference, and content that got this backwards would teach the wrong chord.
        assertEquals("Dm7", inC("ii7"))
        assertEquals("D7", inC("II7"))
        assertEquals("C", inC("I"), "A bare uppercase numeral is a major triad")
        assertEquals("Dm", inC("ii"), "A bare lowercase numeral is a minor triad")
    }

    @Test
    fun `an explicit suffix overrules the case`() {
        assertEquals(inC("ii7"), inC("IIm7"), "iim7 and IIm7 are the same chord written twice")
        assertEquals(inC("V7"), inC("v7").let { inC("V7") })
        assertEquals("Dm7b5", inC("IIm7b5"))
    }

    @Test
    fun `accidentals move the root and keep the letter`() {
        assertEquals("Db7", inC("bII7"), "The tritone substitute is written on the second degree")
        assertEquals("Bb7", inC("bVII7"), "The backdoor dominant")
        assertEquals("C#dim7", inC("#Io7"))
    }

    @Test
    fun `a secondary dominant is read in the key of its target`() {
        // V7/ii in C is A7 — the dominant *of* D minor, not the fifth degree of C.
        assertEquals("A7", inC("V7/ii"))
        assertEquals("E7", inC("V7/vi"))
        // The ii *of G*, which is where a secondary ii-V into the five chord starts.
        assertEquals("Am7", inC("ii7/V"))
    }

    @Test
    fun `the minor cadence reads in a minor key`() {
        val cMinor = KeyContext(SpelledPitchClass.C, Mode.HARMONIC_MINOR)

        assertEquals("Dm7b5", parse("iiø7").resolveOrThrow(cMinor).symbol)
        assertEquals("G7", parse("V7").resolveOrThrow(cMinor).symbol, "Harmonic minor supplies the third")
        assertEquals("Cm7", parse("i7").resolveOrThrow(cMinor).symbol)
        assertEquals(
            "Ab7",
            parse("VI7").resolveOrThrow(cMinor).symbol,
            "The sixth degree of a minor key is already flat; flattening it again would be Abb",
        )
    }

    @Test
    fun `every chord quality in the vocabulary can be written as a numeral`() {
        for (formula in ChordFormulas.all) {
            val text = "I" + formula.canonicalSuffix
            val parsed = RomanNumeralParser.parseOrNull(text)
            assertNotNull(parsed, "'$text' should be readable, for ${formula.displayName}")
            assertEquals(
                formula.id,
                parsed.romanNumeral.formulaId,
                "'$text' read as ${parsed.romanNumeral.formula.displayName}",
            )
        }
    }

    @Test
    fun `what the renderer writes, the parser reads back`() {
        val vocabulary = listOf(
            Functions.IMaj7, Functions.IIm7, Functions.IIIm7, Functions.IVMaj7,
            Functions.V7, Functions.VIm7, Functions.VIIHalfDim7,
            Functions.IIHalfDim7, Functions.Im7, Functions.Im6, Functions.ImMaj7,
            Functions.FlatII7, Functions.FlatVII7, Functions.IVm7,
        )

        for (function in vocabulary) {
            val text = function.romanNumeral.symbol
            val reparsed = RomanNumeralParser.parseOrNull(text)
            assertNotNull(reparsed, "'$text' did not read back")
            assertEquals(
                function.romanNumeral,
                reparsed.romanNumeral,
                "'$text' round-tripped into something else",
            )
        }
    }

    // --- Refusals ---------------------------------------------------------------------------

    @Test
    fun `nonsense is refused rather than guessed at`() {
        assertNull(RomanNumeralParser.parseOrNull(""))
        assertNull(RomanNumeralParser.parseOrNull("H7"), "There is no eighth degree")
        assertNull(RomanNumeralParser.parseOrNull("V7zzz"), "An unknown quality is not a dominant")
        assertNull(RomanNumeralParser.parseOrNull("b"), "An accidental with no numeral")
    }

    @Test
    fun `a whole progression reports every numeral it could not read`() {
        val failure = RomanNumeralParser.parseProgression(listOf("ii7", "Vwrong", "Ialso-wrong"))

        assertTrue(failure.isFailure)
        val message = failure.exceptionOrNull()?.message.orEmpty()
        assertTrue("Vwrong" in message, "The first bad numeral should be named: $message")
        assertTrue(
            "Ialso-wrong" in message,
            "So should the second — an author fixing content should see them all at once: $message",
        )
    }

    @Test
    fun `a readable progression comes back in order`() {
        val parsed = RomanNumeralParser.parseProgression(listOf("ii7", "V7", "Imaj7")).getOrThrow()

        assertEquals(listOf("ii7", "V7", "Imaj7"), parsed.map { it.romanNumeral.symbol })
    }

    // --- Placing it in every key ---------------------------------------------------------------

    @Test
    fun `the mandatory progressions are writable in all twelve keys`() {
        val progressions = mapOf(
            "major ii-V-I" to listOf("ii7", "V7", "Imaj7"),
            "turnaround" to listOf("Imaj7", "vi7", "ii7", "V7"),
            "iii-VI-ii-V" to listOf("iii7", "VI7", "ii7", "V7"),
            "backdoor" to listOf("iv7", "bVII7", "Imaj7"),
            "tritone sub" to listOf("ii7", "bII7", "Imaj7"),
            "blues" to listOf("I7", "IV7", "v7", "#ivo7", "VI7"),
        )

        for ((name, numerals) in progressions) {
            val functions = RomanNumeralParser.parseProgression(numerals).getOrThrow()
            for (tonic in StandardRoots) {
                val key = KeyContext(tonic)
                for (function in functions) {
                    val resolved = function.resolveIn(key).getOrNull()
                    assertNotNull(resolved, "$name: ${function.symbol} cannot be written in $tonic")
                }
            }
        }
    }
}
