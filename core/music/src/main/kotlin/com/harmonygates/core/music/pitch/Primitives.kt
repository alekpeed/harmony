package com.harmonygates.core.music.pitch

/**
 * Primitive musical value types.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §2: "Do not use raw integers and strings throughout the
 * codebase." Every quantity below is a distinct type so that a semitone count can never
 * be passed where a MIDI note number is expected.
 */

/** A MIDI note number. Middle C (C4) is 60, matching scientific pitch notation. */
@JvmInline
public value class MidiNote(public val value: Int) : Comparable<MidiNote> {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "MIDI note out of range 0..127: $value" }
    }

    /** Pitch class 0..11, discarding octave *and* spelling. */
    public val pitchClass: PitchClass get() = PitchClass(value.mod(12))

    /** Scientific-pitch-notation octave; MIDI 60 -> 4. */
    public val octave: Int get() = value.floorDiv(12) - 1

    public operator fun plus(semitones: Semitones): MidiNote = MidiNote(value + semitones.value)

    public operator fun minus(semitones: Semitones): MidiNote = MidiNote(value - semitones.value)

    /** Signed distance to [other] in semitones. */
    public infix fun distanceTo(other: MidiNote): Semitones = Semitones(other.value - this.value)

    override fun compareTo(other: MidiNote): Int = value.compareTo(other.value)

    override fun toString(): String = "MidiNote($value)"

    public companion object {
        public const val MIN_VALUE: Int = 0
        public const val MAX_VALUE: Int = 127

        /** Returns null instead of throwing when [value] falls outside the MIDI range. */
        public fun orNull(value: Int): MidiNote? = if (value in MIN_VALUE..MAX_VALUE) MidiNote(value) else null
    }
}

/** A pitch class normalised to 0..11. Carries no spelling information by design. */
@JvmInline
public value class PitchClass(public val value: Int) : Comparable<PitchClass> {
    init {
        require(value in 0..11) { "Pitch class must be normalised to 0..11: $value" }
    }

    public operator fun plus(semitones: Semitones): PitchClass = PitchClass((value + semitones.value).mod(12))

    public operator fun minus(semitones: Semitones): PitchClass = PitchClass((value - semitones.value).mod(12))

    override fun compareTo(other: PitchClass): Int = value.compareTo(other.value)

    override fun toString(): String = "PitchClass($value)"

    public companion object {
        /** Normalises any integer into 0..11 rather than throwing. */
        public fun of(value: Int): PitchClass = PitchClass(value.mod(12))
    }
}

/**
 * A scale degree 1..7 within some key or mode.
 *
 * Distinct from [com.harmonygates.core.music.chord.ChordDegree], which describes a tone's
 * role inside a chord and may reach the 13th.
 */
@JvmInline
public value class ScaleDegree(public val value: Int) : Comparable<ScaleDegree> {
    init {
        require(value in 1..7) { "Scale degree must be 1..7: $value" }
    }

    override fun compareTo(other: ScaleDegree): Int = value.compareTo(other.value)

    override fun toString(): String = "ScaleDegree($value)"
}

/** A signed semitone distance. */
@JvmInline
public value class Semitones(public val value: Int) : Comparable<Semitones> {
    public val absoluteValue: Semitones get() = Semitones(kotlin.math.abs(value))

    public operator fun plus(other: Semitones): Semitones = Semitones(value + other.value)

    public operator fun minus(other: Semitones): Semitones = Semitones(value - other.value)

    public operator fun unaryMinus(): Semitones = Semitones(-value)

    override fun compareTo(other: Semitones): Int = value.compareTo(other.value)

    override fun toString(): String = "Semitones($value)"

    public companion object {
        public val OCTAVE: Semitones = Semitones(12)
        public val ZERO: Semitones = Semitones(0)
    }
}

/** Position of a voice within an ordered voicing, counted from the bass upwards. */
@JvmInline
public value class VoiceIndex(public val value: Int) : Comparable<VoiceIndex> {
    init {
        require(value >= 0) { "Voice index must be non-negative: $value" }
    }

    override fun compareTo(other: VoiceIndex): Int = value.compareTo(other.value)

    override fun toString(): String = "VoiceIndex($value)"
}

/** The seven natural letter names. */
public enum class LetterName(
    /** Pitch class of the unaltered letter. */
    public val naturalPitchClass: Int,
    /** Position in the diatonic cycle, C = 0. Used for all spelling arithmetic. */
    public val diatonicIndex: Int,
) {
    C(0, 0),
    D(2, 1),
    E(4, 2),
    F(5, 3),
    G(7, 4),
    A(9, 5),
    B(11, 6),
    ;

    /** Moves [steps] letters through the alphabet, wrapping. */
    public fun transposeDiatonic(steps: Int): LetterName = fromDiatonicIndex(diatonicIndex + steps)

    public companion object {
        private val BY_DIATONIC_INDEX = entries.associateBy { it.diatonicIndex }

        public fun fromDiatonicIndex(index: Int): LetterName =
            requireNotNull(BY_DIATONIC_INDEX[index.mod(7)]) { "Unreachable: diatonic index $index" }

        public fun parse(char: Char): LetterName? = entries.firstOrNull { it.name[0] == char.uppercaseChar() }
    }
}

/** Accidental applied to a [LetterName]. Limited to the double-flat..double-sharp range. */
public enum class Accidental(public val offset: Int, public val symbol: String) {
    DOUBLE_FLAT(-2, "bb"),
    FLAT(-1, "b"),
    NATURAL(0, ""),
    SHARP(1, "#"),
    DOUBLE_SHARP(2, "x"),
    ;

    public companion object {
        private val BY_OFFSET = entries.associateBy { it.offset }

        /** Returns null when [offset] would need a triple accidental. */
        public fun ofOffsetOrNull(offset: Int): Accidental? = BY_OFFSET[offset]

        public fun ofOffset(offset: Int): Accidental =
            requireNotNull(BY_OFFSET[offset]) { "No accidental for offset $offset (triple accidentals unsupported)" }
    }
}
