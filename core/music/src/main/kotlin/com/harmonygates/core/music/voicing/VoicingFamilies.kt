package com.harmonygates.core.music.voicing

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec

/**
 * A named voicing shape, resolved for one particular chord.
 *
 * The chord carried here is not always the chord that was asked for. A rootless voicing of `C7`
 * sounds a ninth and a thirteenth that `C7` on its own does not contain, so [chord] is the
 * symbol extended with the tones the family adds. Correctness is judged against that extended
 * chord; the player is still shown the symbol they were given, which is why the caller keeps
 * the original around rather than reading it back from here.
 */
public data class VoicingRecipe(
    val family: VoicingFamily,
    /** The chord as this family sounds it, extended with the tensions the shape supplies. */
    val chord: ChordSpec,
    val policy: VoicingPolicy,
    /** What the player is being asked to do, e.g. "Left-hand rootless A: 3 13 7 9". */
    val instruction: String,
)

/**
 * Builds the shell, guide-tone and rootless shapes of the jazz curriculum.
 *
 * 03_JAZZ_CURRICULUM.md Regions 4 and 7. These are the families where several different sets of
 * notes are all correct answers to the same chord symbol, so they exist as *policies* rather
 * than as fixed voicings: the evaluator is told what must sound and what must not, and any
 * arrangement satisfying that is accepted.
 *
 * Two shapes with identical pitch content are told apart by where they sit, not by their notes:
 *
 * - `1-3-7` and `1-7-3` are both root, third and seventh; the seventh is on top of the first
 *   and the third on top of the second.
 * - Rootless A and B are the same four tones rotated; A puts the third lowest, B the seventh.
 *
 * That is exactly how a player hears the difference, and it means a wrong rotation is reported
 * as a wrong bass or a wrong top note rather than as a vague miss.
 */
public object VoicingFamilies {

    /** The families this object can build. Anything else returns null from [recipe]. */
    public val supported: Set<VoicingFamily> = setOf(
        VoicingFamily.SHELL_1_3_7,
        VoicingFamily.SHELL_1_7_3,
        VoicingFamily.GUIDE_TONES,
        VoicingFamily.ROOTLESS_A,
        VoicingFamily.ROOTLESS_B,
    )

    /**
     * Resolves [family] for [chord], or null when the chord cannot support the shape.
     *
     * A triad has no seventh, so it has no shell and no guide tones; saying so with a null is
     * better than inventing a seventh the symbol never claimed.
     */
    public fun recipe(family: VoicingFamily, chord: ChordSpec): VoicingRecipe? = when (family) {
        VoicingFamily.SHELL_1_3_7 -> shell(chord, seventhOnTop = true)
        VoicingFamily.SHELL_1_7_3 -> shell(chord, seventhOnTop = false)
        VoicingFamily.GUIDE_TONES -> guideTones(chord)
        VoicingFamily.ROOTLESS_A -> rootless(chord, thirdLowest = true)
        VoicingFamily.ROOTLESS_B -> rootless(chord, thirdLowest = false)
        else -> null
    }

    // --- Region 4: shells and guide tones -----------------------------------------------------

    private fun shell(chord: ChordSpec, seventhOnTop: Boolean): VoicingRecipe? {
        val third = chord.quality() ?: return null
        val seventh = chord.seventh() ?: return null
        val required = setOf(ChordDegree.ROOT, third, seventh)
        val top = if (seventhOnTop) seventh else third
        return VoicingRecipe(
            family = if (seventhOnTop) VoicingFamily.SHELL_1_3_7 else VoicingFamily.SHELL_1_7_3,
            chord = chord,
            policy = VoicingPolicy(
                requiredDegrees = required,
                allowedOmissions = chord.degrees.toSet() - required,
                allowDoubling = false,
                requireRoot = true,
                bassRequirement = BassRequirement.RootInBass,
                topNoteRequirement = TopNoteRequirement.DegreeOnTop(top),
                maxVoices = required.size,
                namedFamily = if (seventhOnTop) VoicingFamily.SHELL_1_3_7 else VoicingFamily.SHELL_1_7_3,
                disallowedDegrees = chord.degrees.toSet() - required,
            ),
            instruction = "Shell: root, ${sequence(seventhOnTop, third, seventh)}",
        )
    }

    private fun guideTones(chord: ChordSpec): VoicingRecipe? {
        val third = chord.quality() ?: return null
        val seventh = chord.seventh() ?: return null
        val required = setOf(third, seventh)
        return VoicingRecipe(
            family = VoicingFamily.GUIDE_TONES,
            chord = chord,
            policy = VoicingPolicy(
                requiredDegrees = required,
                allowedOmissions = chord.degrees.toSet() - required,
                allowDoubling = false,
                // The whole point of the shape: the bass player has the root, so the hand does not.
                requireRoot = false,
                maxVoices = required.size,
                namedFamily = VoicingFamily.GUIDE_TONES,
                disallowedDegrees = chord.degrees.toSet() - required,
            ),
            instruction = "Guide tones only: ${third.symbol} and ${seventh.symbol}",
        )
    }

    // --- Region 7: rootless left-hand voicings ------------------------------------------------

    /**
     * Rootless A and B.
     *
     * Both sound third, seventh, ninth and one more tone — the fifth for minor and major
     * sonorities, the thirteenth for dominants, which is where the sound of the family comes
     * from. The chord is extended to contain those tensions so that the evaluator can name them
     * when they are missing; a ninth the chord does not know about would come back as "a note
     * that is not part of the chord", which teaches nothing.
     */
    private fun rootless(chord: ChordSpec, thirdLowest: Boolean): VoicingRecipe? {
        val third = chord.quality() ?: return null
        val seventh = chord.seventh() ?: return null
        val ninth = chord.tensionOrDefault(NINTH_NUMBER)
        val fourth = if (chord.isDominant()) {
            chord.tensionOrDefault(THIRTEENTH_NUMBER)
        } else {
            chord.degreeNumbered(FIFTH_NUMBER) ?: ChordDegree.FIFTH
        }

        val required = setOf(third, seventh, ninth, fourth)
        if (required.size < REQUIRED_ROOTLESS_TONES) return null

        // Additions the symbol did not already carry. `ChordSpec` treats an addition as part of
        // the chord, which is what lets `degreeOf` name it during evaluation.
        val extended = chord.copy(additions = chord.additions + (required - chord.degrees.toSet()))
        val family = if (thirdLowest) VoicingFamily.ROOTLESS_A else VoicingFamily.ROOTLESS_B

        return VoicingRecipe(
            family = family,
            chord = extended,
            policy = VoicingPolicy(
                requiredDegrees = required,
                allowedOmissions = emptySet(),
                allowDoubling = false,
                requireRoot = false,
                // A and B are the same four tones rotated. Which one is lowest is the difference.
                bassRequirement = BassRequirement.DegreeInBass(if (thirdLowest) third else seventh),
                maxVoices = required.size,
                namedFamily = family,
                disallowedDegrees = extended.degrees.toSet() - required,
            ),
            instruction = "Rootless ${if (thirdLowest) "A" else "B"}: " +
                required.sorted().joinToString(" ") { it.symbol } +
                ", ${if (thirdLowest) third.symbol else seventh.symbol} lowest",
        )
    }

    // --- Degree lookup ------------------------------------------------------------------------

    private fun sequence(seventhOnTop: Boolean, third: ChordDegree, seventh: ChordDegree): String =
        if (seventhOnTop) "${third.symbol}, ${seventh.symbol}" else "${seventh.symbol}, ${third.symbol}"

    /** The tone that fixes major or minor — the third, or the fourth of a suspended chord. */
    private fun ChordSpec.quality(): ChordDegree? =
        degreeNumbered(THIRD_NUMBER) ?: degreeNumbered(FOURTH_NUMBER)

    /**
     * The tone that fixes the seventh-chord family.
     *
     * A sixth chord is included deliberately: `C6` and `Cm6` are shell and rootless material in
     * exactly the same way, with the sixth playing the seventh's structural role.
     */
    private fun ChordSpec.seventh(): ChordDegree? =
        degreeNumbered(SEVENTH_NUMBER) ?: degreeNumbered(SIXTH_NUMBER)

    private fun ChordSpec.degreeNumbered(number: Int): ChordDegree? =
        degrees.firstOrNull { it.number == number }

    /** An existing tension of that number, altered or not, or the natural form if absent. */
    private fun ChordSpec.tensionOrDefault(number: Int): ChordDegree =
        degreeNumbered(number) ?: ChordDegree(number)

    /** A dominant sonority: a major third with a minor seventh. */
    private fun ChordSpec.isDominant(): Boolean =
        degreeNumbered(THIRD_NUMBER) == ChordDegree.THIRD &&
            degreeNumbered(SEVENTH_NUMBER) == ChordDegree.FLAT_SEVENTH

    private const val THIRD_NUMBER = 3
    private const val FOURTH_NUMBER = 4
    private const val FIFTH_NUMBER = 5
    private const val SIXTH_NUMBER = 6
    private const val SEVENTH_NUMBER = 7
    private const val NINTH_NUMBER = 9
    private const val THIRTEENTH_NUMBER = 13
    private const val REQUIRED_ROOTLESS_TONES = 4
}
