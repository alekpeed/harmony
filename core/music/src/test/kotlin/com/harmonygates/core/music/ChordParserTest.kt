package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.ParseErrorReason
import com.harmonygates.core.music.parse.ParseResult
import com.harmonygates.core.music.parse.parseOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The parser's contract.
 *
 * The first test walks the exact symbol list from 04_HARMONY_DOMAIN_ENGINE.md §5 — that list
 * is the specification, so it is asserted verbatim rather than paraphrased.
 */
class ChordParserTest {

    private val parser = JazzChordParser

    @Test
    fun `every symbol required by the specification parses to the right formula`() {
        val required = listOf(
            "C" to ChordFormulas.MajorTriad,
            "Cm" to ChordFormulas.MinorTriad,
            "Cmaj7" to ChordFormulas.MajorSeventh,
            "CΔ7" to ChordFormulas.MajorSeventh,
            "C7" to ChordFormulas.DominantSeventh,
            "Cm7" to ChordFormulas.MinorSeventh,
            "Cm(maj7)" to ChordFormulas.MinorMajorSeventh,
            "Cø7" to ChordFormulas.HalfDiminishedSeventh,
            "Cm7b5" to ChordFormulas.HalfDiminishedSeventh,
            "Cdim7" to ChordFormulas.DiminishedSeventh,
            "C°7" to ChordFormulas.DiminishedSeventh,
            "C6" to ChordFormulas.MajorSixth,
            "Cm6" to ChordFormulas.MinorSixth,
            "C9" to ChordFormulas.DominantNinth,
            "Cmaj9" to ChordFormulas.MajorNinth,
            "Cm9" to ChordFormulas.MinorNinth,
            "C11" to ChordFormulas.DominantEleventh,
            "C13" to ChordFormulas.DominantThirteenth,
            "C7b9" to ChordFormulas.DominantSeventh,
            "C7#9" to ChordFormulas.DominantSeventh,
            "C7#11" to ChordFormulas.DominantSeventh,
            "C7b13" to ChordFormulas.DominantSeventh,
            "C7alt" to ChordFormulas.AlteredDominant,
            "C7sus4" to ChordFormulas.DominantSeventhSus4,
            "C13sus" to ChordFormulas.DominantThirteenthSus4,
            "C/E" to ChordFormulas.MajorTriad,
            "Dbmaj9/F" to ChordFormulas.MajorNinth,
        )

        for ((symbol, formula) in required) {
            val spec = parser.parseOrThrow(symbol)
            assertEquals(formula.id, spec.formulaId, "$symbol should be a ${formula.displayName}")
        }
    }

    @Test
    fun `alterations attach to the base chord rather than replacing it`() {
        assertEquals(setOf(ChordDegree.FLAT_NINTH), addedDegreesOf("C7b9"))
        assertEquals(setOf(ChordDegree.SHARP_NINTH), addedDegreesOf("C7#9"))
        assertEquals(setOf(ChordDegree.SHARP_ELEVENTH), addedDegreesOf("C7#11"))
        assertEquals(setOf(ChordDegree.FLAT_THIRTEENTH), addedDegreesOf("C7b13"))
    }

    @Test
    fun `an alteration of a tone the chord already has replaces it`() {
        val flatFive = parser.parseOrThrow("C7b5")
        assertTrue(ChordDegree.FLAT_FIFTH in flatFive.degrees, "C7b5 should contain a flat fifth")
        assertTrue(ChordDegree.FIFTH !in flatFive.degrees, "C7b5 must not also contain a natural fifth")
    }

    @Test
    fun `longest alias wins so m7b5 is never read as m7 plus rubbish`() {
        val halfDiminished = parser.parseOrThrow("Cm7b5")
        assertEquals(ChordFormulas.HalfDiminishedSeventh.id, halfDiminished.formulaId)
        assertTrue(halfDiminished.alterations.isEmpty(), "The b5 belongs to the formula, not to an alteration")
    }

    @Test
    fun `aliases of the same chord all produce the same value`() {
        val equivalences = listOf(
            listOf("Cmaj7", "CM7", "Cma7", "CΔ7", "C△7", "CMaj7"),
            listOf("Cm7", "Cmi7", "Cmin7", "C-7", "CMin7"),
            listOf("Cm7b5", "Cø7", "Cø", "C-7b5", "Cmin7b5"),
            listOf("Cdim7", "C°7", "Co7"),
            listOf("Cm", "Cmi", "Cmin", "C-"),
            listOf("Caug", "C+"),
            listOf("C7sus4", "C7sus"),
            listOf("C6/9", "C69"),
        )

        for (group in equivalences) {
            val parsed = group.map { parser.parseOrThrow(it) }
            val distinct = parsed.distinct()
            assertEquals(1, distinct.size, "$group should all parse identically, got $distinct")
        }
    }

    @Test
    fun `parentheses and whitespace are decoration`() {
        assertEquals(parser.parseOrThrow("C7b9"), parser.parseOrThrow("C7(b9)"))
        assertEquals(parser.parseOrThrow("Cm(maj7)"), parser.parseOrThrow("Cmmaj7"))
        assertEquals(parser.parseOrThrow("Cmaj7"), parser.parseOrThrow(" Cmaj7 "))
    }

    @Test
    fun `every canonical symbol round trips`() {
        val symbols = ChordFormulas.all.map { "C${it.canonicalSuffix}" } +
            listOf("C7b9", "C7#9", "C7#11", "C7b13", "C7b5", "Dbmaj9/F", "Am7/G", "Cadd9", "Cmaj7no5")

        for (symbol in symbols) {
            val spec = parser.parseOrThrow(symbol)
            val reparsed = parser.parseOrThrow(spec.symbol)
            assertEquals(spec, reparsed, "$symbol rendered as ${spec.symbol} and did not survive re-parsing")
        }
    }

    @Test
    fun `the slash in six-nine is not a bass note`() {
        val sixNine = parser.parseOrThrow("C6/9")
        assertEquals(ChordFormulas.SixNine.id, sixNine.formulaId)
        assertEquals(null, sixNine.explicitBass, "C6/9 has no slash bass")

        val overE = parser.parseOrThrow("C6/9/E")
        assertEquals(ChordFormulas.SixNine.id, overE.formulaId)
        assertEquals("E", overE.explicitBass.toString())
    }

    @Test
    fun `additions and omissions are recorded separately from the formula`() {
        val addNine = parser.parseOrThrow("Cadd9")
        assertEquals(ChordFormulas.MajorTriad.id, addNine.formulaId)
        assertEquals(setOf(ChordDegree.NINTH), addNine.additions)

        val noFifth = parser.parseOrThrow("Cmaj7no5")
        assertEquals(setOf(ChordDegree.FIFTH), noFifth.omissions)
        assertTrue(ChordDegree.FIFTH !in noFifth.degrees)
    }

    @Test
    fun `unreadable input fails with a position and a reason`() {
        val cases = mapOf(
            "" to ParseErrorReason.EMPTY_INPUT,
            "H7" to ParseErrorReason.UNKNOWN_ROOT,
            "Cmaj7zz" to ParseErrorReason.UNKNOWN_MODIFIER,
            "C/H" to ParseErrorReason.UNKNOWN_MODIFIER,
        )

        for ((input, reason) in cases) {
            val result = parser.parse(input)
            val failure = assertIs<ParseResult.Failure>(result, "'$input' should not parse")
            assertEquals(reason, failure.reason, "'$input' failed for the wrong reason")
            assertTrue(failure.position >= 0, "Failure position should be usable by a UI")
        }
    }

    @Test
    fun `case is never folded because M7 and m7 are different chords`() {
        assertEquals(ChordFormulas.MajorSeventh.id, parser.parseOrThrow("CM7").formulaId)
        assertEquals(ChordFormulas.MinorSeventh.id, parser.parseOrThrow("Cm7").formulaId)
    }

    private fun addedDegreesOf(symbol: String): Set<ChordDegree> {
        val spec = parser.parseOrThrow(symbol)
        return spec.degrees.toSet() - ChordFormulas.byId(spec.formulaId).canonicalDegrees
    }
}
