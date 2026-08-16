package com.harmonygates.core.music

import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.performance.Verdict
import com.harmonygates.core.music.voicing.VoicingFamilies
import com.harmonygates.core.music.voicing.VoicingFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shells, guide tones and rootless voicings.
 *
 * 03_JAZZ_CURRICULUM.md Regions 4 and 7. These are the families where the same chord symbol has
 * several right answers, so the tests are written the way the curriculum teaches them: play the
 * shape, and check that the shape — not merely the pitch classes — is what was accepted.
 */
class VoicingFamiliesTest {

    private val evaluator = DefaultPerformanceEvaluator()

    private fun verdictOf(family: VoicingFamily, symbol: String, notes: List<Int>): Verdict {
        val recipe = assertNotNull(
            VoicingFamilies.recipe(family, JazzChordParser.parseOrThrow(symbol)),
            "$symbol should support $family",
        )
        return evaluator.evaluate(
            ExerciseRequirement.ChordPolicyMatch(recipe.chord, recipe.policy),
            attemptOf(notes),
        ).verdict
    }

    private fun errorsOf(family: VoicingFamily, symbol: String, notes: List<Int>): List<PerformanceError> {
        val recipe = assertNotNull(VoicingFamilies.recipe(family, JazzChordParser.parseOrThrow(symbol)))
        return evaluator.evaluate(
            ExerciseRequirement.ChordPolicyMatch(recipe.chord, recipe.policy),
            attemptOf(notes),
        ).semanticErrors
    }

    // --- Region 4 ---------------------------------------------------------------------------

    @Test
    fun `a shell is root third seventh with the seventh on top`() {
        // C3 E3 B3
        assertEquals(Verdict.CORRECT, verdictOf(VoicingFamily.SHELL_1_3_7, "Cmaj7", listOf(48, 52, 59)))
    }

    @Test
    fun `the other shell inversion puts the third on top`() {
        // C3 Bb3 E4 — the 1-7-3 shape.
        assertEquals(Verdict.CORRECT, verdictOf(VoicingFamily.SHELL_1_7_3, "C7", listOf(48, 58, 64)))
    }

    @Test
    fun `a shell played the wrong way up is diagnosed as a wrong top note`() {
        // C3 Bb3 E4 asked for as 1-3-7: the notes are right and the shape is not.
        val errors = errorsOf(VoicingFamily.SHELL_1_3_7, "C7", listOf(48, 58, 64))
        assertTrue(
            errors.any { it is PerformanceError.WrongTopNote },
            "Playing 1-7-3 when 1-3-7 was asked for should name the top note: $errors",
        )
    }

    @Test
    fun `a shell rejects the fifth`() {
        // C3 E3 G3 B3 — a full maj7, which is not the exercise.
        val errors = errorsOf(VoicingFamily.SHELL_1_3_7, "Cmaj7", listOf(48, 52, 55, 59))
        assertTrue(
            errors.filterIsInstance<PerformanceError.ExtraTone>().any { it.degree?.number == 5 },
            "The fifth is what a shell leaves out: $errors",
        )
    }

    @Test
    fun `guide tones are the third and the seventh alone`() {
        // B3 F4 — the guide tones of G7.
        assertEquals(Verdict.CORRECT, verdictOf(VoicingFamily.GUIDE_TONES, "G7", listOf(59, 65)))
    }

    @Test
    fun `guide tones reject the root`() {
        // G3 B3 F4: the root belongs to the bass player, not to this hand.
        val errors = errorsOf(VoicingFamily.GUIDE_TONES, "G7", listOf(55, 59, 65))
        assertTrue(
            errors.filterIsInstance<PerformanceError.ExtraTone>().any { it.degree?.number == 1 },
            "A guide-tone exercise should name the root as the extra note: $errors",
        )
    }

    @Test
    fun `a triad has no shell`() {
        assertNull(
            VoicingFamilies.recipe(VoicingFamily.SHELL_1_3_7, JazzChordParser.parseOrThrow("C")),
            "A triad has no seventh, so it has no shell to play",
        )
    }

    // --- Region 7 ---------------------------------------------------------------------------

    @Test
    fun `rootless A on a dominant is three thirteen seven nine`() {
        // G7 -> B3 E4 F4 A4. The thirteenth and ninth are not in the symbol; the shape supplies
        // them, which is why the recipe extends the chord before judging.
        assertEquals(Verdict.CORRECT, verdictOf(VoicingFamily.ROOTLESS_A, "G7", listOf(59, 64, 65, 69)))
    }

    @Test
    fun `rootless A on a minor seventh is three five seven nine`() {
        // Dm7 -> F3 A3 C4 E4
        assertEquals(Verdict.CORRECT, verdictOf(VoicingFamily.ROOTLESS_A, "Dm7", listOf(53, 57, 60, 64)))
    }

    @Test
    fun `rootless B is the same tones with the seventh lowest`() {
        // Dm7 -> C4 E4 F4 A4
        assertEquals(Verdict.CORRECT, verdictOf(VoicingFamily.ROOTLESS_B, "Dm7", listOf(60, 64, 65, 69)))
    }

    @Test
    fun `playing the B rotation when A was asked for is diagnosed as a wrong bass`() {
        val errors = errorsOf(VoicingFamily.ROOTLESS_B, "Dm7", listOf(53, 57, 60, 64))
        assertTrue(
            errors.any { it is PerformanceError.WrongBass },
            "A and B differ only in what is lowest, so that is what should be reported: $errors",
        )
    }

    @Test
    fun `a rootless voicing rejects the root`() {
        // D3 F3 A3 C4 E4 — a rooted Dm9, not a rootless voicing.
        val errors = errorsOf(VoicingFamily.ROOTLESS_A, "Dm7", listOf(50, 53, 57, 60, 64))
        assertTrue(
            errors.filterIsInstance<PerformanceError.ExtraTone>().any { it.degree?.number == 1 },
            "The root is the one note a rootless voicing must not contain: $errors",
        )
    }

    @Test
    fun `a rootless voicing names the tension that is missing`() {
        // F3 A3 C4 — the ninth left off a Dm7 rootless A.
        val errors = errorsOf(VoicingFamily.ROOTLESS_A, "Dm7", listOf(53, 57, 60))
        assertTrue(
            errors.filterIsInstance<PerformanceError.MissingDegree>().any { it.tone.degree?.number == 9 },
            "A missing ninth should be named as a ninth, not as an unknown gap: $errors",
        )
    }

    @Test
    fun `an altered dominant keeps its own alterations in the rootless shape`() {
        val recipe = assertNotNull(
            VoicingFamilies.recipe(VoicingFamily.ROOTLESS_A, JazzChordParser.parseOrThrow("G7b9")),
        )
        assertTrue(
            recipe.policy.requiredDegrees.any { it.number == 9 && it.alteration == -1 },
            "G7b9 asks for a flat ninth, not a natural one: ${recipe.policy.requiredDegrees}",
        )
    }
}
