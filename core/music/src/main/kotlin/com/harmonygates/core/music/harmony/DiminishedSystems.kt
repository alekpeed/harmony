package com.harmonygates.core.music.harmony

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.chord.DegreeAlteration
import com.harmonygates.core.music.pitch.PitchClass
import com.harmonygates.core.music.pitch.SpelledInterval
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.transposeBy
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer

/** How a diminished chord is being used. */
public enum class DiminishedFunction(public val label: String) {
    /** Between two diatonic chords a step apart: `Imaj7 - #Idim7 - ii7`. */
    PASSING("Passing"),

    /** Sharing tones with the chord it decorates, over a static bass. */
    COMMON_TONE("Common tone"),

    /** Standing in for the dominant a major third below it. */
    DOMINANT_SUBSTITUTE("Dominant substitute"),

    /** Leading up by semitone into the next chord. */
    APPROACH("Approach"),
}

/**
 * One diminished chord in context.
 *
 * The label alone is not enough to practise from: `C#dim7` between `Cmaj7` and `Dm7` is a
 * different musical event from the same chord standing in for `A7b9`, and a gate that showed
 * only the symbol would be teaching a fingering rather than a function.
 */
public data class DiminishedUse(
    val chord: ChordSpec,
    val function: DiminishedFunction,
    val explanation: String,
    /** The dominant this chord can stand in for, when it can. */
    val relatedDominant: ChordSpec? = null,
)

/**
 * Diminished systems, as a submodule of its own.
 *
 * 03_JAZZ_CURRICULUM.md §13 is explicit that this should be "a dedicated submodule rather than
 * silently mixing pedagogy systems" — which is why diminished behaviour lives here rather than
 * being scattered through the chord vocabulary. A player learning passing diminished chords is
 * learning one system; a player learning Barry Harris's sixth-diminished scale is learning a
 * different one, and the two should not be shuffled together in the same exercise.
 *
 * The symmetry is the thing that makes all of it work: a fully diminished seventh divides the
 * octave into four equal parts, so it has only three distinct forms and each one can be spelled
 * from any of its four notes.
 */
public class DiminishedSystems(
    private val realizer: ChordRealizer = DefaultChordRealizer(),
) {

    /**
     * The pitch classes of a diminished seventh, as a set.
     *
     * Used for identity: two diminished sevenths are the same chord when their pitch classes
     * match, whatever they are called.
     */
    public fun pitchClasses(chord: ChordSpec): Set<PitchClass> =
        realizer.chordTones(chord).map { it.pitchClass }.toSet()

    /**
     * True when two diminished sevenths are the same four notes.
     *
     * `Cdim7`, `Ebdim7`, `Gbdim7` and `Bbbdim7` are one chord written four ways. A player who
     * has learned three diminished sevenths has learned all of them, and this is what lets a
     * gate say so.
     */
    public fun isSameChord(first: ChordSpec, second: ChordSpec): Boolean =
        isDiminishedSeventh(first) && isDiminishedSeventh(second) &&
            pitchClasses(first) == pitchClasses(second)

    /**
     * The other three ways to spell this diminished seventh.
     *
     * Only the spellings that can actually be written are returned. A diminished seventh rooted
     * somewhere awkward needs a triple flat, and 04_HARMONY_DOMAIN_ENGINE.md §6 refuses to
     * invent one rather than respelling it into a lie.
     */
    public fun equivalentSpellings(chord: ChordSpec): List<ChordSpec> {
        if (!isDiminishedSeventh(chord)) return emptyList()
        val tones = realizer.chordTones(chord)

        return tones.drop(1).mapNotNull { tone ->
            val candidate = ChordSpec(tone, ChordFormulas.DiminishedSeventh.id)
            candidate.takeIf { realizer.trySpell(it) is SpellingResult.Spelled }
        }
    }

    /**
     * The dominant this diminished seventh can stand in for.
     *
     * A `B°7` is the top four notes of `G7b9` — the b9 supplies the fourth note and the root is
     * simply absent. So the dominant is a major third below any of the diminished chord's tones,
     * and the natural one to name is the one below the written root.
     *
     * §13 lists "dominant b9 relationship" as content, and this is the whole of it.
     */
    public fun asDominantSubstitute(chord: ChordSpec): ChordSpec? {
        if (!isDiminishedSeventh(chord)) return null
        val root = transposeSpelled(chord.root, -SpelledInterval.MAJOR_THIRD) ?: return null
        return ChordSpec(
            root = root,
            formulaId = ChordFormulas.DominantSeventh.id,
            alterations = setOf(DegreeAlteration.of(ChordDegree.FLAT_NINTH)),
        )
    }

    /**
     * The diminished seventh built on the third of a dominant.
     *
     * The inverse of [asDominantSubstitute]: the upper structure of `G7b9` is `B°7`.
     */
    public fun fromDominant(chord: ChordSpec): ChordSpec? {
        if (ChordDegree.THIRD !in chord.degrees || ChordDegree.FLAT_SEVENTH !in chord.degrees) return null
        val third = realizer.spelledDegrees(chord)[ChordDegree.THIRD] ?: return null
        val candidate = ChordSpec(third, ChordFormulas.DiminishedSeventh.id)
        return candidate.takeIf { realizer.trySpell(it) is SpellingResult.Spelled }
    }

    /**
     * A passing diminished chord between two chords a whole step apart.
     *
     * `Cmaj7 - C#dim7 - Dm7` is the archetype: the diminished chord sits a semitone above the
     * first and a semitone below the second, and every voice moves by a semitone. Returns null
     * when the two chords are not a whole step apart, because there is nothing to pass through.
     */
    public fun passingBetween(from: ChordSpec, to: ChordSpec): DiminishedUse? {
        val distance = (to.root.pitchClass.value - from.root.pitchClass.value).mod(SEMITONES_PER_OCTAVE)
        if (distance != WHOLE_STEP) return null

        val root = transposeSpelled(from.root, CHROMATIC_STEP_UP) ?: return null
        val chord = ChordSpec(root, ChordFormulas.DiminishedSeventh.id)
        if (realizer.trySpell(chord) !is SpellingResult.Spelled) return null

        return DiminishedUse(
            chord = chord,
            function = DiminishedFunction.PASSING,
            explanation = "${chord.symbol} passes between ${from.symbol} and ${to.symbol}; " +
                "every voice moves by a semitone.",
            relatedDominant = asDominantSubstitute(chord),
        )
    }

    /**
     * A common-tone diminished chord for a major chord.
     *
     * Built a semitone above the root — `C - C#dim7 - C` over a static bass. The two chords
     * share the third and the fifth, which is where the name comes from and why it resolves
     * back into the chord it decorates instead of moving on. Distinct from a passing chord,
     * which goes somewhere.
     */
    public fun commonToneFor(chord: ChordSpec): DiminishedUse? {
        if (ChordDegree.THIRD !in chord.degrees) return null
        val root = transposeSpelled(chord.root, CHROMATIC_STEP_UP) ?: return null
        val diminished = ChordSpec(root, ChordFormulas.DiminishedSeventh.id)
        if (realizer.trySpell(diminished) !is SpellingResult.Spelled) return null

        val shared = pitchClasses(diminished) intersect
            realizer.chordTones(chord).map { it.pitchClass }.toSet()
        if (shared.isEmpty()) return null

        return DiminishedUse(
            chord = diminished,
            function = DiminishedFunction.COMMON_TONE,
            explanation = "${diminished.symbol} shares ${shared.size} tones with ${chord.symbol} " +
                "and resolves back into it without the bass moving.",
        )
    }

    /**
     * The three distinct diminished sevenths.
     *
     * Every diminished seventh in existence is one of these three, which is the most useful
     * single fact in Region 11 and the reason it is worth a submodule.
     */
    public fun distinctFamilies(): List<ChordSpec> = FAMILY_ROOTS.mapNotNull { name ->
        SpelledPitchClass.parseOrNull(name)?.let { ChordSpec(it, ChordFormulas.DiminishedSeventh.id) }
    }

    /** Which of the three families a diminished seventh belongs to, 0..2. */
    public fun familyOf(chord: ChordSpec): Int? {
        if (!isDiminishedSeventh(chord)) return null
        return chord.root.pitchClass.value.mod(MINOR_THIRD)
    }

    private fun isDiminishedSeventh(chord: ChordSpec): Boolean =
        chord.formulaId == ChordFormulas.DiminishedSeventh.id

    /**
     * Moves a spelling by an interval, keeping it writable.
     *
     * The interval rather than a semitone count is what decides the letter: a passing chord a
     * chromatic semitone above `C` is `C#`, and the same pitch reached by a minor second would
     * be `Db`. Falls back to the practical spelling when the letter the interval implies would
     * need more than one accidental — a diminished seventh rooted on a double flat is correct
     * arithmetic and unreadable notation.
     */
    private fun transposeSpelled(
        pitchClass: SpelledPitchClass,
        interval: SpelledInterval,
    ): SpelledPitchClass? {
        val exact = (pitchClass.transposeBy(interval) as? SpellingResult.Spelled)?.value
        if (exact != null && exact.accidental.offset in READABLE_ACCIDENTALS) return exact

        val target = (pitchClass.pitchClass.value + interval.semitones).mod(SEMITONES_PER_OCTAVE)
        return ALL_SPELLINGS.mapNotNull { SpelledPitchClass.parseOrNull(it) }
            .firstOrNull { it.pitchClass.value == target }
    }

    private companion object {
        const val SEMITONES_PER_OCTAVE = 12
        const val MINOR_THIRD = 3
        const val WHOLE_STEP = 2

        /** An augmented unison: the same letter, raised. `C` becomes `C#`, never `Db`. */
        val CHROMATIC_STEP_UP: SpelledInterval = SpelledInterval(diatonicSteps = 0, semitones = 1)

        /** Naturals, one sharp or one flat. Beyond that a chart respells the chord. */
        val READABLE_ACCIDENTALS = -1..1

        /** One root per distinct diminished seventh. Any three a semitone apart would do. */
        val FAMILY_ROOTS = listOf("C", "Db", "D")

        /** Preferred spellings, flats before sharps, so a transposition stays writable. */
        val ALL_SPELLINGS = listOf(
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
        )
    }
}
