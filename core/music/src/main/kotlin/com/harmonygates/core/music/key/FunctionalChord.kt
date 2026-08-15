package com.harmonygates.core.music.key

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormula
import com.harmonygates.core.music.chord.ChordFormulaId
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordQuality
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.chord.DegreeAlteration
import com.harmonygates.core.music.pitch.ScaleDegree
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult

/**
 * A chord named by its function rather than by its letter.
 *
 * @param degree scale degree of the chord root, 1..7.
 * @param chromaticAlteration displacement of that root, e.g. -1 for the `bII` of a tritone
 *   substitution.
 * @param formulaId the sonority built on it.
 */
public data class RomanNumeral(
    val degree: ScaleDegree,
    val chromaticAlteration: Int = 0,
    val formulaId: ChordFormulaId,
) {
    public val formula: ChordFormula get() = ChordFormulas.byId(formulaId)

    /** Conventional rendering: `ii7`, `V7`, `bII7`, `viiø7`. */
    public val symbol: String
        get() {
            val numeral = ROMAN[degree.value - 1]
            val cased = if (isLowerCase) numeral.lowercase() else numeral
            val prefix = when {
                chromaticAlteration < 0 -> "b".repeat(-chromaticAlteration)
                chromaticAlteration > 0 -> "#".repeat(chromaticAlteration)
                else -> ""
            }
            return prefix + cased + figuredSuffix
        }

    private val isLowerCase: Boolean
        get() = formula.quality in setOf(
            ChordQuality.MINOR,
            ChordQuality.MINOR_MAJOR,
            ChordQuality.DIMINISHED,
            ChordQuality.HALF_DIMINISHED,
        )

    /**
     * The part written after the numeral.
     *
     * Diminished and half-diminished sonorities carry their symbol on the numeral itself, so
     * `viiø7` rather than `viim7b5`; everything else reuses the chord suffix.
     */
    private val figuredSuffix: String
        get() = when (formula.quality) {
            ChordQuality.HALF_DIMINISHED -> "ø7"
            ChordQuality.DIMINISHED -> if (formula.id == ChordFormulas.DiminishedSeventh.id) "°7" else "°"
            ChordQuality.MINOR, ChordQuality.MINOR_MAJOR ->
                formula.canonicalSuffix.removePrefix("m")
            else -> formula.canonicalSuffix
        }

    override fun toString(): String = symbol
}

/**
 * A roman numeral plus the extra colour a lead sheet would write next to it.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §10 requires these to be expressible:
 * `ii7` in C major, `V7/ii` in Eb major, the `bII7` tritone substitute in C, and the
 * `ivm7 -> bVII7` backdoor. The domain tests assert each of those four cases.
 *
 * @param secondaryTarget when set, the numeral is read in the key of that scale degree, which
 *   is how `V7/ii` works: dominant *of* the two chord, not the fifth degree of the home key.
 */
public data class FunctionalChord(
    val romanNumeral: RomanNumeral,
    val alterations: Set<DegreeAlteration> = emptySet(),
    val secondaryTarget: ScaleDegree? = null,
    val additions: Set<ChordDegree> = emptySet(),
) {
    /** Renders `V7/ii`. */
    public val symbol: String
        get() = buildString {
            append(romanNumeral.symbol)
            alterations.map { it.degree }.sorted().forEach { append(it.symbol) }
            secondaryTarget?.let { append("/${ROMAN[it.value - 1].lowercase()}") }
        }

    /**
     * Places this function in a concrete key.
     *
     * A secondary function is resolved in two hops: first find the target chord's root in the
     * home key, then read the numeral in a major key built on that root. That is what makes
     * `V7/ii` in Eb resolve to C7 rather than to something in Eb.
     */
    public fun resolveIn(key: KeyContext): SpellingResult<ChordSpec> {
        val effectiveKey = when (secondaryTarget) {
            null -> key
            else -> when (val target = key.degreeRoot(secondaryTarget)) {
                is SpellingResult.Spelled -> KeyContext(target.value, Mode.MAJOR)
                is SpellingResult.Overflow -> return target
            }
        }
        val root = when (
            val result = effectiveKey.degreeRoot(romanNumeral.degree, romanNumeral.chromaticAlteration)
        ) {
            is SpellingResult.Spelled -> result.value
            is SpellingResult.Overflow -> return result
        }
        return SpellingResult.Spelled(
            ChordSpec(
                root = root,
                formulaId = romanNumeral.formulaId,
                alterations = alterations,
                additions = additions,
            ),
        )
    }

    /** Resolves, treating an unwritable result as a content authoring error. */
    public fun resolveOrThrow(key: KeyContext): ChordSpec =
        when (val result = resolveIn(key)) {
            is SpellingResult.Spelled -> result.value
            is SpellingResult.Overflow -> error("$symbol cannot be written in $key: ${result.message}")
        }

    override fun toString(): String = symbol
}

/**
 * The functional vocabulary Region 3 and Region 12 teach, ready to be placed in any key.
 *
 * Keeping these as data means a `ii-V-I` in twelve keys is one generator plus twelve tonics,
 * not twelve hand-written chord lists.
 */
public object Functions {
    public fun of(
        degree: Int,
        formula: ChordFormula,
        chromaticAlteration: Int = 0,
        secondaryTarget: Int? = null,
        alterations: Set<DegreeAlteration> = emptySet(),
    ): FunctionalChord = FunctionalChord(
        romanNumeral = RomanNumeral(ScaleDegree(degree), chromaticAlteration, formula.id),
        alterations = alterations,
        secondaryTarget = secondaryTarget?.let { ScaleDegree(it) },
    )

    // --- Major-key diatonic sevenths: Imaj7 ii7 iii7 IVmaj7 V7 vi7 viiø7 -------------------

    public val IMaj7: FunctionalChord = of(1, ChordFormulas.MajorSeventh)
    public val IIm7: FunctionalChord = of(2, ChordFormulas.MinorSeventh)
    public val IIIm7: FunctionalChord = of(3, ChordFormulas.MinorSeventh)
    public val IVMaj7: FunctionalChord = of(4, ChordFormulas.MajorSeventh)
    public val V7: FunctionalChord = of(5, ChordFormulas.DominantSeventh)
    public val VIm7: FunctionalChord = of(6, ChordFormulas.MinorSeventh)
    public val VIIHalfDim7: FunctionalChord = of(7, ChordFormulas.HalfDiminishedSeventh)

    /** The diatonic seventh chords of a major key, in scale order. */
    public val majorDiatonicSevenths: List<FunctionalChord> =
        listOf(IMaj7, IIm7, IIIm7, IVMaj7, V7, VIm7, VIIHalfDim7)

    // --- Minor-key practical forms --------------------------------------------------------

    public val IIHalfDim7: FunctionalChord = of(2, ChordFormulas.HalfDiminishedSeventh)
    public val Im7: FunctionalChord = of(1, ChordFormulas.MinorSeventh)
    public val ImMaj7: FunctionalChord = of(1, ChordFormulas.MinorMajorSeventh)
    public val Im6: FunctionalChord = of(1, ChordFormulas.MinorSixth)

    /** `V7` in a minor key: the raised leading tone that natural minor does not supply. */
    public val V7OfMinor: FunctionalChord = of(5, ChordFormulas.DominantSeventh)

    // --- Chromatic vocabulary -------------------------------------------------------------

    /** `bII7`, the tritone substitute for `V7`. */
    public val FlatII7: FunctionalChord = of(2, ChordFormulas.DominantSeventh, chromaticAlteration = -1)

    /** `bVII7`, the backdoor dominant. */
    public val FlatVII7: FunctionalChord = of(7, ChordFormulas.DominantSeventh, chromaticAlteration = -1)

    /** `ivm7`, the other half of the backdoor cadence. */
    public val IVm7: FunctionalChord = of(4, ChordFormulas.MinorSeventh)

    /** `V7/ii`, `V7/iii` and friends. */
    public fun secondaryDominantOf(target: Int): FunctionalChord =
        of(5, ChordFormulas.DominantSeventh, secondaryTarget = target)

    /** `ii7/V` and friends, for full secondary `ii-V` insertions. */
    public fun secondaryTwoOf(target: Int): FunctionalChord =
        of(2, ChordFormulas.MinorSeventh, secondaryTarget = target)

    // --- Progressions (Region 12) ---------------------------------------------------------

    public val MajorTwoFiveOne: List<FunctionalChord> = listOf(IIm7, V7, IMaj7)

    public val MinorTwoFiveOne: List<FunctionalChord> = listOf(IIHalfDim7, V7OfMinor, Im7)

    public val OneSixTwoFive: List<FunctionalChord> = listOf(IMaj7, VIm7, IIm7, V7)

    public val ThreeSixTwoFive: List<FunctionalChord> =
        listOf(IIIm7, secondaryDominantOf(2), IIm7, V7)

    public val BackdoorCadence: List<FunctionalChord> = listOf(IVm7, FlatVII7, IMaj7)

    public val TritoneSubTurnaround: List<FunctionalChord> = listOf(IIm7, FlatII7, IMaj7)
}

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII")
