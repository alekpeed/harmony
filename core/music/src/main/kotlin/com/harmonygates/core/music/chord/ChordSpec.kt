package com.harmonygates.core.music.chord

import com.harmonygates.core.music.pitch.SpelledPitchClass

/**
 * A chromatic change to one diatonic degree of a chord, e.g. the `b9` in `C7b9`.
 *
 * Applying an alteration *replaces* the degree with the same [degreeNumber] when the formula
 * already contains it (`C7b5` moves the fifth), and *adds* it when the formula does not
 * (`C7b9` gains a ninth). One rule covers both cases.
 */
public data class DegreeAlteration(
    val degreeNumber: Int,
    val alteration: Int,
) {
    init {
        require(alteration in -2..2) { "Alteration must be -2..2: $alteration" }
    }

    public val degree: ChordDegree get() = ChordDegree(degreeNumber, alteration)

    override fun toString(): String = degree.symbol

    public companion object {
        public fun of(degree: ChordDegree): DegreeAlteration =
            DegreeAlteration(degree.number, degree.alteration)
    }
}

/**
 * The intent behind a chord symbol, independent of how it is played.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §3 keeps symbol intent and realised voicing apart, so this
 * type never carries octaves, doublings or a register. Those belong to
 * [com.harmonygates.core.music.voicing.Voicing].
 */
public data class ChordSpec(
    val root: SpelledPitchClass,
    val formulaId: ChordFormulaId,
    val alterations: Set<DegreeAlteration> = emptySet(),
    val additions: Set<ChordDegree> = emptySet(),
    val omissions: Set<ChordDegree> = emptySet(),
    /**
     * Bass note for a slash chord.
     *
     * Non-null for both `C/E` (an inversion) and `C/A` (an independent bass note). Which of
     * the two it is is answered by `ChordRealizer.degreeOf`; the parser does not guess.
     */
    val explicitBass: SpelledPitchClass? = null,
) {
    val formula: ChordFormula get() = ChordFormulas.byId(formulaId)

    /**
     * Degrees the chord is built from, after alterations, additions and omissions.
     *
     * Includes optional degrees: this is the full stacked spelling used for display and for
     * generating a canonical voicing. Use [requiredDegrees] when deciding correctness.
     */
    public val degrees: List<ChordDegree>
        get() = resolveDegrees(formula.canonicalDegrees).sorted()

    /** Degrees a performance must contain for the chord to be that chord. */
    public val requiredDegrees: Set<ChordDegree>
        get() = resolveDegrees(formula.requiredDegrees)

    /** Degrees that belong but may be left out. */
    public val optionalDegrees: Set<ChordDegree>
        get() = degrees.toSet() - requiredDegrees

    /** Degrees that contradict the sonority, e.g. the natural fifth of an altered dominant. */
    public val forbiddenDegrees: Set<ChordDegree>
        get() = formula.forbiddenDegrees - degrees.toSet()

    private fun resolveDegrees(base: Set<ChordDegree>): Set<ChordDegree> {
        val alteredNumbers = alterations.map { it.degreeNumber }.toSet()
        val kept = base.filterNot { it.number in alteredNumbers }
        val applied = alterations.map { it.degree }
        return (kept + applied + additions - omissions).toSet()
    }

    /** Renders a display symbol such as `Dbmaj9/F`. Round-trips through the parser. */
    public val symbol: String
        get() = buildString {
            append(root)
            append(formula.canonicalSuffix)
            alterations.map { it.degree }
                .filterNot { it in formula.canonicalDegrees }
                .sorted()
                .forEach { append(it.symbol) }
            additions.sorted().forEach { append("add${it.symbol}") }
            omissions.sorted().forEach { append("no${it.symbol}") }
            explicitBass?.let { append("/$it") }
        }

    override fun toString(): String = symbol
}
