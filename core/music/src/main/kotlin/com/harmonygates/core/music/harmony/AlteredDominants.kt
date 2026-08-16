package com.harmonygates.core.music.harmony

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.chord.DegreeAlteration

/** The colour tones a dominant can be altered with (03_JAZZ_CURRICULUM.md §12, Region 10). */
public enum class DominantAlteration(
    public val degree: ChordDegree,
    public val symbol: String,
) {
    FLAT_NINE(ChordDegree.FLAT_NINTH, "b9"),
    SHARP_NINE(ChordDegree.SHARP_NINTH, "#9"),
    SHARP_ELEVEN(ChordDegree.SHARP_ELEVENTH, "#11"),
    FLAT_THIRTEEN(ChordDegree.FLAT_THIRTEENTH, "b13"),
    ;

    /**
     * True for the pair that cannot both be the fifth.
     *
     * `#11` and `b13` are both displacements of the fifth in the ear even though they are
     * different degrees, so a chord asking for both is asking for a sound with no fifth at all —
     * which is legal and worth flagging as deliberate rather than accidental.
     */
    public val displacesTheFifth: Boolean get() = this == SHARP_ELEVEN || this == FLAT_THIRTEEN
}

/**
 * Building altered dominants from plain ones.
 *
 * Region 10's required tasks are specific: "build alteration from base dominant" and "retain
 * guide tones while changing color tones". Both are transformations rather than a table of
 * chords — `C7b9`, `C7#9`, `C7#5b9` and the rest are one operation applied to `C7`, and writing
 * them as a lookup would mean thirty entries per root that could disagree with each other.
 */
public object AlteredDominants {

    /**
     * Applies alterations to a dominant.
     *
     * Returns null for a chord that is not a dominant seventh: altering the ninth of a major
     * seventh produces something, but not something this vocabulary describes, and quietly
     * doing it would let a gate teach the wrong lesson.
     */
    public fun alter(chord: ChordSpec, vararg alterations: DominantAlteration): ChordSpec? {
        if (!isDominant(chord)) return null
        if (alterations.isEmpty()) return chord

        return chord.copy(
            alterations = chord.alterations + alterations.map { DegreeAlteration.of(it.degree) },
        )
    }

    /** Every single-alteration form of a dominant. The first rung of Region 10. */
    public fun singleAlterations(chord: ChordSpec): List<ChordSpec> =
        DominantAlteration.entries.mapNotNull { alter(chord, it) }

    /**
     * The combined forms an altered dominant actually uses.
     *
     * Not every pair: a ninth cannot be both flat and sharp, so those two are never combined.
     * Region 10 lists "combined alterations" as content, and these are the combinations a player
     * will meet rather than the full cross product.
     */
    public fun combinedAlterations(chord: ChordSpec): List<ChordSpec> {
        if (!isDominant(chord)) return emptyList()
        val ninths = listOf(DominantAlteration.FLAT_NINE, DominantAlteration.SHARP_NINE)
        val fifths = listOf(DominantAlteration.SHARP_ELEVEN, DominantAlteration.FLAT_THIRTEEN)

        return ninths.flatMap { ninth -> fifths.mapNotNull { fifth -> alter(chord, ninth, fifth) } }
    }

    /**
     * The `alt` chord: guide tones fixed, every colour tone free.
     *
     * `C7alt` is not a stack, it is a permission. The formula says so — root, third and flat
     * seventh required, the four alterations optional and the natural fifth, ninth, eleventh
     * and thirteenth forbidden — and this just puts a root on it.
     */
    public fun altered(chord: ChordSpec): ChordSpec? {
        if (!isDominant(chord)) return null
        return ChordSpec(root = chord.root, formulaId = ChordFormulas.AlteredDominant.id)
    }

    /**
     * The tones that must survive any alteration.
     *
     * "Retain guide tones while changing color tones" is Region 10's second required task, and
     * this is what it means concretely: the third and the seventh are the chord, and everything
     * a player is asked to change is something else.
     */
    public fun guideTones(chord: ChordSpec): Set<ChordDegree> =
        setOf(ChordDegree.THIRD, ChordDegree.FLAT_SEVENTH).filter { it in chord.degrees }.toSet()

    /** True when the alterations left the guide tones alone. */
    public fun retainsGuideTones(original: ChordSpec, altered: ChordSpec): Boolean =
        guideTones(original).all { it in altered.degrees }

    private fun isDominant(chord: ChordSpec): Boolean =
        ChordDegree.THIRD in chord.degrees && ChordDegree.FLAT_SEVENTH in chord.degrees
}
