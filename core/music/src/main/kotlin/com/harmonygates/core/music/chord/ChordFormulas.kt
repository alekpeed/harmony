package com.harmonygates.core.music.chord

import com.harmonygates.core.music.chord.ChordDegree.Companion.DIMINISHED_SEVENTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.ELEVENTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.FIFTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.FLAT_FIFTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.FLAT_NINTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.FLAT_SEVENTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.FLAT_THIRD
import com.harmonygates.core.music.chord.ChordDegree.Companion.FLAT_THIRTEENTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.FOURTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.NINTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.ROOT
import com.harmonygates.core.music.chord.ChordDegree.Companion.SECOND
import com.harmonygates.core.music.chord.ChordDegree.Companion.SEVENTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.SHARP_ELEVENTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.SHARP_FIFTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.SHARP_NINTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.SIXTH
import com.harmonygates.core.music.chord.ChordDegree.Companion.THIRD
import com.harmonygates.core.music.chord.ChordDegree.Companion.THIRTEENTH

/**
 * The chord vocabulary of the curriculum, as data.
 *
 * Every required/optional decision below is a pedagogical choice, not an objective fact, and
 * each contentious one is commented. Regions 5 and 7 of 03_JAZZ_CURRICULUM.md depend on
 * these splits being deliberate: an eleventh chord that demanded its third would mark correct
 * rootless voicings wrong, and a thirteenth chord that forbade its eleventh would encode a
 * style preference as a law.
 */
public object ChordFormulas {

    // --- Triads --------------------------------------------------------------------------

    public val MajorTriad: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_triad"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, FIFTH),
        aliases = setOf("", "maj", "M", "ma", "major"),
        canonicalSuffix = "",
        displayName = "major triad",
    )

    public val MinorTriad: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_triad"),
        quality = ChordQuality.MINOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FIFTH),
        aliases = setOf("m", "mi", "min", "-", "minor"),
        canonicalSuffix = "m",
        displayName = "minor triad",
    )

    public val DiminishedTriad: ChordFormula = ChordFormula(
        id = ChordFormulaId("diminished_triad"),
        quality = ChordQuality.DIMINISHED,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FLAT_FIFTH),
        aliases = setOf("dim", "o", "0"),
        canonicalSuffix = "dim",
        displayName = "diminished triad",
    )

    public val AugmentedTriad: ChordFormula = ChordFormula(
        id = ChordFormulaId("augmented_triad"),
        quality = ChordQuality.AUGMENTED,
        requiredDegrees = setOf(ROOT, THIRD, SHARP_FIFTH),
        aliases = setOf("aug", "+"),
        canonicalSuffix = "aug",
        displayName = "augmented triad",
    )

    public val Sus2: ChordFormula = ChordFormula(
        id = ChordFormulaId("sus2"),
        quality = ChordQuality.SUSPENDED,
        requiredDegrees = setOf(ROOT, SECOND, FIFTH),
        forbiddenDegrees = setOf(THIRD, FLAT_THIRD),
        aliases = setOf("sus2"),
        canonicalSuffix = "sus2",
        displayName = "suspended second",
    )

    public val Sus4: ChordFormula = ChordFormula(
        id = ChordFormulaId("sus4"),
        quality = ChordQuality.SUSPENDED,
        requiredDegrees = setOf(ROOT, FOURTH, FIFTH),
        forbiddenDegrees = setOf(THIRD, FLAT_THIRD),
        aliases = setOf("sus4", "sus"),
        canonicalSuffix = "sus4",
        displayName = "suspended fourth",
    )

    // --- Sixth chords --------------------------------------------------------------------

    public val MajorSixth: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_sixth"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, SIXTH),
        // The fifth is optional so that a three-note C6 shell counts; the sixth is not.
        optionalDegrees = setOf(FIFTH),
        aliases = setOf("6", "maj6", "M6"),
        canonicalSuffix = "6",
        displayName = "major sixth",
    )

    public val MinorSixth: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_sixth"),
        quality = ChordQuality.MINOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, SIXTH),
        optionalDegrees = setOf(FIFTH),
        aliases = setOf("m6", "mi6", "min6", "-6"),
        canonicalSuffix = "m6",
        displayName = "minor sixth",
    )

    public val SixNine: ChordFormula = ChordFormula(
        id = ChordFormulaId("six_nine"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, SIXTH, NINTH),
        optionalDegrees = setOf(FIFTH),
        aliases = setOf("6/9", "69", "6add9"),
        canonicalSuffix = "6/9",
        displayName = "six-nine",
    )

    // --- Seventh chords ------------------------------------------------------------------

    public val MajorSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_seventh"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, FIFTH, SEVENTH),
        aliases = setOf("maj7", "ma7", "M7", "j7", "major7"),
        canonicalSuffix = "maj7",
        displayName = "major seventh",
    )

    public val DominantSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_seventh"),
        quality = ChordQuality.DOMINANT,
        requiredDegrees = setOf(ROOT, THIRD, FIFTH, FLAT_SEVENTH),
        aliases = setOf("7", "dom7"),
        canonicalSuffix = "7",
        displayName = "dominant seventh",
    )

    public val MinorSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_seventh"),
        quality = ChordQuality.MINOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FIFTH, FLAT_SEVENTH),
        aliases = setOf("m7", "mi7", "min7", "-7"),
        canonicalSuffix = "m7",
        displayName = "minor seventh",
    )

    public val MinorMajorSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_major_seventh"),
        quality = ChordQuality.MINOR_MAJOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FIFTH, SEVENTH),
        aliases = setOf("mmaj7", "mM7", "minmaj7", "miMa7", "-maj7", "mma7"),
        canonicalSuffix = "m(maj7)",
        displayName = "minor-major seventh",
    )

    public val HalfDiminishedSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("half_diminished_seventh"),
        quality = ChordQuality.HALF_DIMINISHED,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FLAT_FIFTH, FLAT_SEVENTH),
        aliases = setOf("m7b5", "mi7b5", "min7b5", "-7b5", "halfdim", "halfdim7"),
        canonicalSuffix = "m7b5",
        displayName = "half-diminished seventh",
    )

    public val DiminishedSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("diminished_seventh"),
        quality = ChordQuality.DIMINISHED,
        // The seventh is a double-flatted seventh, not a sixth. Region 11 relies on this to
        // relate a diminished seventh to the dominant b9 a major third below.
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FLAT_FIFTH, DIMINISHED_SEVENTH),
        aliases = setOf("dim7", "o7", "07"),
        canonicalSuffix = "dim7",
        displayName = "diminished seventh",
    )

    public val AugmentedSeventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("augmented_seventh"),
        quality = ChordQuality.AUGMENTED,
        requiredDegrees = setOf(ROOT, THIRD, SHARP_FIFTH, FLAT_SEVENTH),
        aliases = setOf("7#5", "aug7", "+7", "7+5"),
        canonicalSuffix = "7#5",
        displayName = "augmented seventh",
    )

    public val MajorSeventhSharpFive: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_seventh_sharp_five"),
        quality = ChordQuality.AUGMENTED,
        requiredDegrees = setOf(ROOT, THIRD, SHARP_FIFTH, SEVENTH),
        aliases = setOf("maj7#5", "M7#5", "maj7+5", "augmaj7"),
        canonicalSuffix = "maj7#5",
        displayName = "major seventh sharp five",
    )

    // --- Ninths --------------------------------------------------------------------------

    public val DominantNinth: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_ninth"),
        quality = ChordQuality.DOMINANT,
        requiredDegrees = setOf(ROOT, THIRD, FLAT_SEVENTH, NINTH),
        optionalDegrees = setOf(FIFTH),
        aliases = setOf("9", "dom9"),
        canonicalSuffix = "9",
        displayName = "dominant ninth",
    )

    public val MajorNinth: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_ninth"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, SEVENTH, NINTH),
        optionalDegrees = setOf(FIFTH),
        aliases = setOf("maj9", "ma9", "M9", "major9"),
        canonicalSuffix = "maj9",
        displayName = "major ninth",
    )

    public val MinorNinth: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_ninth"),
        quality = ChordQuality.MINOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FLAT_SEVENTH, NINTH),
        optionalDegrees = setOf(FIFTH),
        aliases = setOf("m9", "mi9", "min9", "-9"),
        canonicalSuffix = "m9",
        displayName = "minor ninth",
    )

    // --- Elevenths -----------------------------------------------------------------------

    public val DominantEleventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_eleventh"),
        quality = ChordQuality.DOMINANT,
        // The third is optional, not required: a played C11 is normally Gm7/C, and demanding
        // E against the F would fail correct answers. It stays *available* rather than
        // forbidden because the engine must not declare a context-dependent clash illegal.
        requiredDegrees = setOf(ROOT, FLAT_SEVENTH, NINTH, ELEVENTH),
        optionalDegrees = setOf(THIRD, FIFTH),
        aliases = setOf("11", "dom11"),
        canonicalSuffix = "11",
        displayName = "dominant eleventh",
    )

    public val MinorEleventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_eleventh"),
        quality = ChordQuality.MINOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FLAT_SEVENTH, ELEVENTH),
        optionalDegrees = setOf(FIFTH, NINTH),
        aliases = setOf("m11", "mi11", "min11", "-11"),
        canonicalSuffix = "m11",
        displayName = "minor eleventh",
    )

    public val MajorEleventh: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_eleventh"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, SEVENTH, ELEVENTH),
        optionalDegrees = setOf(FIFTH, NINTH),
        aliases = setOf("maj11", "ma11", "M11"),
        canonicalSuffix = "maj11",
        displayName = "major eleventh",
    )

    // --- Thirteenths ---------------------------------------------------------------------

    public val DominantThirteenth: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_thirteenth"),
        quality = ChordQuality.DOMINANT,
        requiredDegrees = setOf(ROOT, THIRD, FLAT_SEVENTH, THIRTEENTH),
        // The natural eleventh is listed as available rather than forbidden. It is avoided in
        // most voicings, which is an exercise policy decision, not a property of the chord.
        optionalDegrees = setOf(FIFTH, NINTH, ELEVENTH),
        stackOverride = setOf(ROOT, THIRD, FIFTH, FLAT_SEVENTH, NINTH, THIRTEENTH),
        aliases = setOf("13", "dom13"),
        canonicalSuffix = "13",
        displayName = "dominant thirteenth",
    )

    public val MajorThirteenth: ChordFormula = ChordFormula(
        id = ChordFormulaId("major_thirteenth"),
        quality = ChordQuality.MAJOR,
        requiredDegrees = setOf(ROOT, THIRD, SEVENTH, THIRTEENTH),
        optionalDegrees = setOf(FIFTH, NINTH),
        aliases = setOf("maj13", "ma13", "M13"),
        canonicalSuffix = "maj13",
        displayName = "major thirteenth",
    )

    public val MinorThirteenth: ChordFormula = ChordFormula(
        id = ChordFormulaId("minor_thirteenth"),
        quality = ChordQuality.MINOR,
        requiredDegrees = setOf(ROOT, FLAT_THIRD, FLAT_SEVENTH, THIRTEENTH),
        optionalDegrees = setOf(FIFTH, NINTH, ELEVENTH),
        stackOverride = setOf(ROOT, FLAT_THIRD, FIFTH, FLAT_SEVENTH, NINTH, THIRTEENTH),
        aliases = setOf("m13", "mi13", "min13", "-13"),
        canonicalSuffix = "m13",
        displayName = "minor thirteenth",
    )

    // --- Suspended dominants -------------------------------------------------------------

    public val DominantSeventhSus4: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_seventh_sus4"),
        quality = ChordQuality.SUSPENDED,
        requiredDegrees = setOf(ROOT, FOURTH, FLAT_SEVENTH),
        optionalDegrees = setOf(FIFTH),
        forbiddenDegrees = setOf(THIRD, FLAT_THIRD),
        aliases = setOf("7sus4", "7sus"),
        canonicalSuffix = "7sus4",
        displayName = "dominant seventh suspended fourth",
    )

    public val DominantNinthSus4: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_ninth_sus4"),
        quality = ChordQuality.SUSPENDED,
        requiredDegrees = setOf(ROOT, FOURTH, FLAT_SEVENTH, NINTH),
        optionalDegrees = setOf(FIFTH),
        forbiddenDegrees = setOf(THIRD, FLAT_THIRD),
        aliases = setOf("9sus4", "9sus"),
        canonicalSuffix = "9sus4",
        displayName = "dominant ninth suspended fourth",
    )

    public val DominantThirteenthSus4: ChordFormula = ChordFormula(
        id = ChordFormulaId("dominant_thirteenth_sus4"),
        quality = ChordQuality.SUSPENDED,
        requiredDegrees = setOf(ROOT, FOURTH, FLAT_SEVENTH, THIRTEENTH),
        optionalDegrees = setOf(FIFTH, NINTH),
        forbiddenDegrees = setOf(THIRD, FLAT_THIRD),
        aliases = setOf("13sus4", "13sus"),
        canonicalSuffix = "13sus4",
        displayName = "dominant thirteenth suspended fourth",
    )

    // --- Altered dominant ----------------------------------------------------------------

    public val AlteredDominant: ChordFormula = ChordFormula(
        id = ChordFormulaId("altered_dominant"),
        quality = ChordQuality.DOMINANT,
        // `alt` names a colour space rather than a fixed stack. The guide tones are required;
        // which alterations appear is chosen by the exercise policy, so all four are optional.
        // The unaltered fifth, ninth and thirteenth are genuinely excluded — they contradict
        // the altered scale, which is what the symbol asserts.
        requiredDegrees = setOf(ROOT, THIRD, FLAT_SEVENTH),
        optionalDegrees = setOf(FLAT_NINTH, SHARP_NINTH, SHARP_ELEVENTH, FLAT_THIRTEENTH),
        forbiddenDegrees = setOf(FIFTH, NINTH, ELEVENTH, THIRTEENTH),
        aliases = setOf("alt", "7alt", "altered"),
        canonicalSuffix = "7alt",
        displayName = "altered dominant",
    )

    /** Every formula in the vocabulary, in curriculum order. */
    public val all: List<ChordFormula> = listOf(
        MajorTriad,
        MinorTriad,
        DiminishedTriad,
        AugmentedTriad,
        Sus2,
        Sus4,
        MajorSixth,
        MinorSixth,
        SixNine,
        MajorSeventh,
        DominantSeventh,
        MinorSeventh,
        MinorMajorSeventh,
        HalfDiminishedSeventh,
        DiminishedSeventh,
        AugmentedSeventh,
        MajorSeventhSharpFive,
        DominantNinth,
        MajorNinth,
        MinorNinth,
        DominantEleventh,
        MinorEleventh,
        MajorEleventh,
        DominantThirteenth,
        MajorThirteenth,
        MinorThirteenth,
        DominantSeventhSus4,
        DominantNinthSus4,
        DominantThirteenthSus4,
        AlteredDominant,
    )

    private val byId: Map<ChordFormulaId, ChordFormula> = all.associateBy { it.id }

    init {
        require(byId.size == all.size) { "Duplicate chord formula id in the registry" }
        val duplicatedAliases = all.flatMap { it.aliases }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
        require(duplicatedAliases.isEmpty()) { "Chord symbol aliases must be unique: $duplicatedAliases" }
    }

    public fun byIdOrNull(id: ChordFormulaId): ChordFormula? = byId[id]

    public fun byId(id: ChordFormulaId): ChordFormula =
        requireNotNull(byId[id]) { "Unknown chord formula: $id" }

    /** Aliases longest-first, so `m7b5` wins over `m7` during parsing. */
    internal val aliasesByLengthDescending: List<Pair<String, ChordFormula>> =
        all.flatMap { formula -> formula.aliases.map { it to formula } }
            .sortedWith(compareByDescending<Pair<String, ChordFormula>> { it.first.length }.thenBy { it.first })
}
