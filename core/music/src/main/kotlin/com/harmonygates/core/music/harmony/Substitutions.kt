package com.harmonygates.core.music.harmony

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.pitch.SpelledInterval
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.transposeBy
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer

/** A way of replacing one chord with another. */
public enum class SubstitutionKind(public val label: String) {
    /** A dominant a tritone away. Shares the guide tones, moves the root by semitone. */
    TRITONE("Tritone substitution"),

    /** The dominant of the chord that follows, inserted before it. */
    SECONDARY_DOMINANT("Secondary dominant"),

    /** `ivm7 - bVII7 - Imaj7`: a dominant approaching from below. */
    BACKDOOR("Backdoor dominant"),

    /** The related ii before a dominant, making a full two-chord approach. */
    RELATED_TWO("Related ii"),

    /** A diminished seventh standing in for a dominant b9. */
    DIMINISHED("Diminished for dominant"),
}

/** One substitution, with the reason it works. */
public data class Substitution(
    val original: ChordSpec,
    val replacement: ChordSpec,
    val kind: SubstitutionKind,
    val explanation: String,
    /** Tones the two chords share. Why the ear accepts the swap. */
    val sharedTones: Set<SpelledPitchClass> = emptySet(),
)

/**
 * The substitution vocabulary of Region 12.
 *
 * Every one of these is a transformation of a chord in context rather than a chord in a list.
 * That matters for what a player learns: `Db7` is only a tritone substitution when it is
 * standing where `G7` stood, and a curriculum that presented it as a chord to memorise would
 * teach the shape without the reason.
 */
public class Substitutions(
    private val realizer: ChordRealizer = DefaultChordRealizer(),
    private val diminished: DiminishedSystems = DiminishedSystems(),
) {

    /**
     * The dominant a tritone away.
     *
     * The two chords share their third and seventh — the third of one is the seventh of the
     * other — which is exactly why the substitution works and what a player should be able to
     * hear. The explanation says so rather than asserting that it is allowed.
     */
    public fun tritone(chord: ChordSpec): Substitution? {
        if (!isDominant(chord)) return null
        // A diminished fifth rather than an augmented fourth: the substitute for `G7` is `Db7`,
        // which is how a chart writes it and how the semitone into the tonic reads.
        val replacement = chordOn(rootsFor(chord.root, SpelledInterval.DIMINISHED_FIFTH)) {
            ChordSpec(it, chord.formulaId, chord.alterations, chord.additions)
        } ?: return null

        return Substitution(
            original = chord,
            replacement = replacement,
            kind = SubstitutionKind.TRITONE,
            explanation = "${replacement.symbol} shares its guide tones with ${chord.symbol}: " +
                "the third of one is the seventh of the other, so the resolution still works.",
            sharedTones = sharedTones(chord, replacement),
        )
    }

    /** The dominant of a chord, to be inserted before it. */
    public fun secondaryDominant(target: ChordSpec): Substitution? {
        val replacement = chordOn(rootsFor(target.root, SpelledInterval.PERFECT_FIFTH)) {
            ChordSpec(it, ChordFormulas.DominantSeventh.id)
        } ?: return null

        return Substitution(
            original = target,
            replacement = replacement,
            kind = SubstitutionKind.SECONDARY_DOMINANT,
            explanation = "${replacement.symbol} is the dominant of ${target.symbol}; " +
                "putting it in front borrows a cadence into a chord that is not the tonic.",
            sharedTones = sharedTones(target, replacement),
        )
    }

    /**
     * The related ii, so a lone dominant becomes a two-chord approach.
     *
     * `G7` becomes `Dm7 - G7`. The commonest expansion in the language and the one that turns
     * every chord in a tune into a place a `ii-V` can be inserted.
     */
    public fun relatedTwo(dominant: ChordSpec): Substitution? {
        if (!isDominant(dominant)) return null
        val replacement = chordOn(rootsFor(dominant.root, SpelledInterval.PERFECT_FIFTH)) {
            ChordSpec(it, ChordFormulas.MinorSeventh.id)
        } ?: return null

        return Substitution(
            original = dominant,
            replacement = replacement,
            kind = SubstitutionKind.RELATED_TWO,
            explanation = "${replacement.symbol} is the ii of ${dominant.symbol}; " +
                "the pair approaches the same destination with twice the run-up.",
            sharedTones = sharedTones(dominant, replacement),
        )
    }

    /**
     * The backdoor dominant for a major tonic.
     *
     * `bVII7` resolving up to `I` — the other way into a major chord, and the reason `Bb7` can
     * precede `Cmaj7` without either being in the wrong key.
     */
    public fun backdoor(tonic: ChordSpec): Substitution? {
        val replacement = chordOn(rootsFor(tonic.root, -SpelledInterval.MAJOR_SECOND)) {
            ChordSpec(it, ChordFormulas.DominantSeventh.id)
        } ?: return null

        return Substitution(
            original = tonic,
            replacement = replacement,
            kind = SubstitutionKind.BACKDOOR,
            explanation = "${replacement.symbol} approaches ${tonic.symbol} from a whole step " +
                "below; its seventh falls a semitone into the tonic's fifth.",
            sharedTones = sharedTones(tonic, replacement),
        )
    }

    /** The diminished seventh that stands in for a dominant b9. */
    public fun diminishedFor(dominant: ChordSpec): Substitution? {
        if (!isDominant(dominant)) return null
        val replacement = diminished.fromDominant(dominant) ?: return null

        return Substitution(
            original = dominant,
            replacement = replacement,
            kind = SubstitutionKind.DIMINISHED,
            explanation = "${replacement.symbol} is the upper structure of ${dominant.symbol}b9 " +
                "with the root left out.",
            sharedTones = sharedTones(dominant, replacement),
        )
    }

    /** Every substitution available for a chord, in the order a curriculum introduces them. */
    public fun available(chord: ChordSpec): List<Substitution> = listOfNotNull(
        relatedTwo(chord),
        tritone(chord),
        secondaryDominant(chord),
        backdoor(chord),
        diminishedFor(chord),
    )

    /**
     * Rewrites a progression by substituting one chord.
     *
     * Returns the whole progression rather than the replacement alone, because a substitution is
     * only recognisable in context — `Db7` on its own is a chord, and `Dm7 Db7 Cmaj7` is a
     * tritone substitution.
     */
    public fun applyTo(
        progression: List<ChordSpec>,
        index: Int,
        substitution: Substitution,
    ): List<ChordSpec> {
        require(index in progression.indices) { "No chord at index $index" }
        return progression.toMutableList().apply { this[index] = substitution.replacement }
    }

    /**
     * Inserts a chord before another rather than replacing it.
     *
     * Secondary dominants and related iis expand a progression instead of altering it, and
     * treating them as replacements would silently delete the chord they were meant to approach.
     */
    public fun insertBefore(
        progression: List<ChordSpec>,
        index: Int,
        substitution: Substitution,
    ): List<ChordSpec> {
        require(index in progression.indices) { "No chord at index $index" }
        return progression.toMutableList().apply { add(index, substitution.replacement) }
    }

    private fun sharedTones(first: ChordSpec, second: ChordSpec): Set<SpelledPitchClass> {
        val firstTones = realizer.chordTones(first)
        val secondClasses = realizer.chordTones(second).map { it.pitchClass }.toSet()
        return firstTones.filter { it.pitchClass in secondClasses }.toSet()
    }

    private fun isDominant(chord: ChordSpec): Boolean =
        ChordDegree.THIRD in chord.degrees && ChordDegree.FLAT_SEVENTH in chord.degrees

    /**
     * Roots an interval away, best spelling first.
     *
     * The interval decides the letter, so the substitute for `G7` is `Db7` rather than `C#7`.
     * But the letter the interval implies is not always writable: a diminished fifth above
     * `Eb` is `Bbb`, and a chart would say `A7`. So the exact spelling is offered first and
     * only while it stays inside a single accidental, with the practical spelling behind it.
     */
    private fun rootsFor(root: SpelledPitchClass, interval: SpelledInterval): List<SpelledPitchClass> {
        val exact = (root.transposeBy(interval) as? SpellingResult.Spelled)?.value
            ?.takeIf { it.accidental.offset in READABLE_ACCIDENTALS }
        val target = (root.pitchClass.value + interval.semitones).mod(SEMITONES_PER_OCTAVE)
        val practical = PREFERRED_SPELLINGS.mapNotNull { SpelledPitchClass.parseOrNull(it) }
            .filter { it.pitchClass.value == target }
        return (listOfNotNull(exact) + practical).distinct()
    }

    /** The first of those roots whose chord can actually be written. */
    private fun chordOn(
        roots: List<SpelledPitchClass>,
        build: (SpelledPitchClass) -> ChordSpec,
    ): ChordSpec? = roots.asSequence()
        .map(build)
        .firstOrNull { realizer.trySpell(it) is SpellingResult.Spelled }

    private companion object {
        const val SEMITONES_PER_OCTAVE = 12

        /** Naturals, one sharp or one flat. Beyond that a chart respells the chord. */
        val READABLE_ACCIDENTALS = -1..1

        /** Flats before sharps, which is how a jazz chart writes most of these roots. */
        val PREFERRED_SPELLINGS = listOf(
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
        )
    }
}
