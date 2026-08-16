package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.realize.DefaultChordRealizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Structural coverage of the whole vocabulary.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §13 asks for "all 12 roots for every supported formula". The
 * golden fixtures pin down specific spellings; this suite asserts the invariants that must
 * hold for all 360 combinations, which is the part no hand-written fixture set can cover.
 */
class ChordCoverageTest {

    private val realizer = DefaultChordRealizer()

    private val allChords: List<ChordSpec> = ChordFormulas.all.flatMap { formula ->
        StandardRoots.map { root -> ChordSpec(root = root, formulaId = formula.id) }
    }

    @Test
    fun `the vocabulary covers every formula in every standard key`() {
        assertEquals(ChordFormulas.all.size * 12, allChords.size)
    }

    @Test
    fun `every chord in every key is writable`() {
        val unwritable = allChords.mapNotNull { spec ->
            when (val result = realizer.trySpell(spec)) {
                is SpellingResult.Spelled -> null
                is SpellingResult.Overflow -> "${spec.symbol}: ${result.message}"
            }
        }
        assertTrue(unwritable.isEmpty(), "Chords that cannot be notated:\n${unwritable.joinToString("\n")}")
    }

    @Test
    fun `every degree uses its own letter exactly once`() {
        for (spec in allChords) {
            val spelled = realizer.spelledDegrees(spec)
            for ((degree, spelling) in spelled) {
                val expectedLetter = spec.root.letter.transposeDiatonic(degree.diatonicStepsFromRoot)
                assertEquals(
                    expectedLetter,
                    spelling.letter,
                    "${spec.symbol}: degree ${degree.symbol} must be written on ${expectedLetter}",
                )
            }
        }
    }

    @Test
    fun `every degree sounds at its defined distance above the root`() {
        for (spec in allChords) {
            for ((degree, spelling) in realizer.spelledDegrees(spec)) {
                assertEquals(
                    (spec.root.pitchClass.value + degree.semitonesFromRoot).mod(12),
                    spelling.pitchClass.value,
                    "${spec.symbol}: degree ${degree.symbol} sounds wrong",
                )
            }
        }
    }

    @Test
    fun `every chord renders a symbol that parses back to itself`() {
        for (spec in allChords) {
            val reparsed = JazzChordParser.parse(spec.symbol)
            val success = assertIs<com.harmonygates.core.music.parse.ParseResult.Success<ChordSpec>>(
                reparsed,
                "${spec.symbol} did not parse back",
            )
            assertEquals(spec, success.value, "${spec.symbol} did not survive a symbol round trip")
        }
    }

    @Test
    fun `required tones are always part of the written stack`() {
        for (formula in ChordFormulas.all) {
            assertTrue(
                formula.requiredDegrees.all { it in formula.canonicalDegrees },
                "${formula.id} omits a required tone from its written stack",
            )
        }
    }

    @Test
    fun `the root is present in every chord and never doubled as a degree`() {
        for (spec in allChords) {
            val roots = spec.degrees.filter { it.number == 1 }
            assertEquals(listOf(ChordDegree.ROOT), roots, "${spec.symbol} has an odd root degree set")
        }
    }

    @Test
    fun `a sharp nine and a flat three are never treated as the same tone`() {
        val sharpNine = ChordDegree.SHARP_NINTH
        val flatThree = ChordDegree.FLAT_THIRD
        assertEquals(
            sharpNine.semitonesFromRoot.mod(12),
            flatThree.semitonesFromRoot.mod(12),
            "This test is only meaningful because the two sound alike",
        )
        assertTrue(sharpNine != flatThree, "#9 and b3 must stay distinct values")
        assertEquals("#9", sharpNine.symbol)
        assertEquals("b3", flatThree.symbol)
    }

    @Test
    fun `an altered dominant permits its alterations and refuses the natural fifth`() {
        val altered = ChordFormulas.AlteredDominant
        assertTrue(ChordDegree.FLAT_NINTH in altered.optionalDegrees)
        assertTrue(ChordDegree.SHARP_NINTH in altered.optionalDegrees)
        assertTrue(ChordDegree.SHARP_ELEVENTH in altered.optionalDegrees)
        assertTrue(ChordDegree.FLAT_THIRTEENTH in altered.optionalDegrees)
        assertTrue(ChordDegree.FIFTH in altered.forbiddenDegrees)
        assertTrue(ChordDegree.NINTH in altered.forbiddenDegrees)
    }

    @Test
    fun `a dominant thirteenth permits the natural eleventh without writing it`() {
        val thirteenth = ChordFormulas.DominantThirteenth
        assertTrue(
            ChordDegree.ELEVENTH in thirteenth.permittedDegrees,
            "The eleventh is avoided by convention, not forbidden by theory",
        )
        assertTrue(
            ChordDegree.ELEVENTH !in thirteenth.canonicalDegrees,
            "A written C13 does not include an F",
        )
        assertTrue(ChordDegree.ELEVENTH !in thirteenth.forbiddenDegrees)
    }

    @Test
    fun `suspended chords forbid the third they replace`() {
        for (formula in ChordFormulas.all.filter { "sus" in it.canonicalSuffix }) {
            assertTrue(
                ChordDegree.THIRD in formula.forbiddenDegrees && ChordDegree.FLAT_THIRD in formula.forbiddenDegrees,
                "${formula.id} suspends the third, so both thirds must be excluded",
            )
        }
    }
}
