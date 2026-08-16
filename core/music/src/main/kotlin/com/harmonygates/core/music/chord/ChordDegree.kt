package com.harmonygates.core.music.chord

import com.harmonygates.core.music.pitch.SpelledInterval

/**
 * A tone's functional role inside a chord.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §4: "Do not collapse `#9` into `b3` just because they share a
 * pitch class. Their harmonic roles differ." Splitting the degree into a diatonic [number]
 * and a chromatic [alteration] makes that structural rather than a convention — `#9` is
 * `ChordDegree(9, +1)` and `b3` is `ChordDegree(3, -1)`, two values that are never equal.
 *
 * The pair also drives spelling for free: the number fixes the letter, the alteration and
 * the root fix the accidental. See [com.harmonygates.core.music.spelling.PitchSpeller].
 *
 * @param number diatonic degree, one of 1, 2, 3, 4, 5, 6, 7, 9, 11, 13.
 * @param alteration chromatic displacement from the major-scale form, -2..+2.
 */
public data class ChordDegree(
    val number: Int,
    val alteration: Int = 0,
) : Comparable<ChordDegree> {

    init {
        require(number in ALLOWED_NUMBERS) { "Unsupported chord degree number: $number" }
        require(alteration in -2..2) { "Chord degree alteration must be -2..2: $alteration" }
    }

    /** Distance above the root in semitones; a 13th is 21, not 9. */
    public val semitonesFromRoot: Int get() = MAJOR_SCALE_SEMITONES.getValue(number) + alteration

    /** Letter steps above the root's letter; a 9th is 8 steps, i.e. an octave plus a second. */
    public val diatonicStepsFromRoot: Int get() = number - 1

    /** The interval from root to this degree, spelled. */
    public val intervalFromRoot: SpelledInterval
        get() = SpelledInterval(diatonicStepsFromRoot, semitonesFromRoot)

    /** True for tones above the octave: 9, 11 and 13. */
    public val isExtension: Boolean get() = number > 7

    /** True when the degree is chromatically altered, e.g. `b9` or `#11`. */
    public val isAltered: Boolean get() = alteration != 0

    /** The unaltered form of this degree, e.g. `b9` -> `9`. */
    public val natural: ChordDegree get() = if (alteration == 0) this else ChordDegree(number)

    /** Returns this degree respelled with a different chromatic alteration. */
    public fun altered(alteration: Int): ChordDegree = copy(alteration = alteration)

    /** Conventional symbol: `1`, `b3`, `#11`, `bb7`. */
    public val symbol: String
        get() {
            val prefix = when {
                alteration < 0 -> "b".repeat(-alteration)
                alteration > 0 -> "#".repeat(alteration)
                else -> ""
            }
            return "$prefix$number"
        }

    /** Orders by stack position so a rendered chord reads bottom-up. */
    override fun compareTo(other: ChordDegree): Int =
        compareValuesBy(this, other, { it.number }, { it.alteration })

    override fun toString(): String = symbol

    public companion object {
        private val ALLOWED_NUMBERS = setOf(1, 2, 3, 4, 5, 6, 7, 9, 11, 13)

        /** Semitone distance of each unaltered degree above the root. */
        private val MAJOR_SCALE_SEMITONES = mapOf(
            1 to 0,
            2 to 2,
            3 to 4,
            4 to 5,
            5 to 7,
            6 to 9,
            7 to 11,
            9 to 14,
            11 to 17,
            13 to 21,
        )

        public val ROOT: ChordDegree = ChordDegree(1)
        public val SECOND: ChordDegree = ChordDegree(2)
        public val FLAT_THIRD: ChordDegree = ChordDegree(3, -1)
        public val THIRD: ChordDegree = ChordDegree(3)
        public val FOURTH: ChordDegree = ChordDegree(4)
        public val FLAT_FIFTH: ChordDegree = ChordDegree(5, -1)
        public val FIFTH: ChordDegree = ChordDegree(5)
        public val SHARP_FIFTH: ChordDegree = ChordDegree(5, 1)
        public val SIXTH: ChordDegree = ChordDegree(6)
        public val DIMINISHED_SEVENTH: ChordDegree = ChordDegree(7, -2)
        public val FLAT_SEVENTH: ChordDegree = ChordDegree(7, -1)
        public val SEVENTH: ChordDegree = ChordDegree(7)
        public val FLAT_NINTH: ChordDegree = ChordDegree(9, -1)
        public val NINTH: ChordDegree = ChordDegree(9)
        public val SHARP_NINTH: ChordDegree = ChordDegree(9, 1)
        public val ELEVENTH: ChordDegree = ChordDegree(11)
        public val SHARP_ELEVENTH: ChordDegree = ChordDegree(11, 1)
        public val FLAT_THIRTEENTH: ChordDegree = ChordDegree(13, -1)
        public val THIRTEENTH: ChordDegree = ChordDegree(13)

        /** Parses degree symbols such as `1`, `b3`, `#11`, `bb7`. Returns null if malformed. */
        public fun parseOrNull(symbol: String): ChordDegree? {
            val trimmed = symbol.trim()
            if (trimmed.isEmpty()) return null
            var alteration = 0
            var index = 0
            while (index < trimmed.length) {
                when (trimmed[index]) {
                    'b', '♭' -> alteration--
                    '#', '♯' -> alteration++
                    else -> break
                }
                index++
            }
            val number = trimmed.substring(index).toIntOrNull() ?: return null
            if (number !in ALLOWED_NUMBERS || alteration !in -2..2) return null
            return ChordDegree(number, alteration)
        }
    }
}

/**
 * Which chord tones are structurally load-bearing.
 *
 * Used by voicing policies and, later, by the evaluator's error ranking: telling a player
 * they missed the third matters more than telling them they missed the fifth.
 */
public enum class DegreeRole {
    /** Root: names the chord. */
    ROOT,

    /** Third, or the fourth in a suspended chord: fixes major/minor identity. */
    QUALITY,

    /** Seventh: fixes the seventh-chord family. */
    GUIDE_TONE,

    /** Fifth: usually the first tone a jazz voicing drops. */
    FIFTH,

    /** Ninths, elevenths and thirteenths: colour. */
    TENSION,
    ;

    public companion object {
        public fun of(degree: ChordDegree): DegreeRole = when (degree.number) {
            1 -> ROOT
            2, 3, 4 -> QUALITY
            5 -> FIFTH
            6, 7 -> GUIDE_TONE
            else -> TENSION
        }
    }
}
