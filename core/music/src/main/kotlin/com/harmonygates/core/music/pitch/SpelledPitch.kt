package com.harmonygates.core.music.pitch

/**
 * A pitch class that remembers how it is written.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §2: "`PitchClass(1)` is not sufficient to decide whether the
 * music should display C# or Db. Preserve spelling."
 */
public data class SpelledPitchClass(
    val letter: LetterName,
    val accidental: Accidental = Accidental.NATURAL,
) : Comparable<SpelledPitchClass> {

    /** Sounding pitch class; C# and Db both yield 1. */
    public val pitchClass: PitchClass get() = PitchClass((letter.naturalPitchClass + accidental.offset).mod(12))

    /**
     * Chromatic value *without* wrapping, so that Cb stays one semitone below C rather than
     * jumping to B in the octave above. Octave arithmetic must use this, never [pitchClass].
     */
    internal val unwrappedChromaticValue: Int get() = letter.naturalPitchClass + accidental.offset

    /** True when the two spellings sound identical, e.g. C# and Db. */
    public infix fun isEnharmonicWith(other: SpelledPitchClass): Boolean = pitchClass == other.pitchClass

    /** Places this pitch class in a specific scientific-pitch-notation octave. */
    public fun inOctave(octave: Int): SpelledPitch = SpelledPitch(this, octave)

    override fun compareTo(other: SpelledPitchClass): Int =
        compareValuesBy(this, other, { it.letter.diatonicIndex }, { it.accidental.offset })

    override fun toString(): String = "$letter${accidental.symbol}"

    public companion object {
        public val C: SpelledPitchClass = SpelledPitchClass(LetterName.C)
        public val D: SpelledPitchClass = SpelledPitchClass(LetterName.D)
        public val E: SpelledPitchClass = SpelledPitchClass(LetterName.E)
        public val F: SpelledPitchClass = SpelledPitchClass(LetterName.F)
        public val G: SpelledPitchClass = SpelledPitchClass(LetterName.G)
        public val A: SpelledPitchClass = SpelledPitchClass(LetterName.A)
        public val B: SpelledPitchClass = SpelledPitchClass(LetterName.B)

        /**
         * Parses `C`, `Db`, `F#`, `Bbb`, `Cx`, `E♭`, `G♯`.
         *
         * Returns null when the text is not exactly one spelled pitch class.
         */
        public fun parseOrNull(text: String): SpelledPitchClass? {
            // The double-sharp and double-flat glyphs are surrogate pairs, so they are folded
            // into their ASCII forms before the per-character scan.
            val trimmed = text.trim().replace("𝄪", "x").replace("𝄫", "bb")
            if (trimmed.isEmpty()) return null
            val letter = LetterName.parse(trimmed[0]) ?: return null
            var offset = 0
            for (char in trimmed.drop(1)) {
                offset += when (char) {
                    'b', '♭' -> -1
                    '#', '♯' -> 1
                    'x' -> 2
                    '♮' -> 0
                    else -> return null
                }
            }
            val accidental = Accidental.ofOffsetOrNull(offset) ?: return null
            return SpelledPitchClass(letter, accidental)
        }
    }
}

/** A spelled pitch class fixed to an octave, i.e. a concrete sounding note with a spelling. */
public data class SpelledPitch(
    val pitchClass: SpelledPitchClass,
    val octave: Int,
) : Comparable<SpelledPitch> {

    /**
     * The sounding MIDI note.
     *
     * Uses the unwrapped chromatic value so Cb4 is B3's pitch (59) while still being written
     * as a C in octave 4 — the behaviour notation software expects.
     */
    public val midiNote: MidiNote
        get() = MidiNote((octave + 1) * 12 + pitchClass.unwrappedChromaticValue)

    /** Returns null when the spelling falls outside the MIDI range instead of throwing. */
    public val midiNoteOrNull: MidiNote?
        get() = MidiNote.orNull((octave + 1) * 12 + pitchClass.unwrappedChromaticValue)

    /** Ordering by staff position, so Cb4 sorts below C4 and above B3's staff line. */
    public val diatonicStaffStep: Int get() = octave * 7 + pitchClass.letter.diatonicIndex

    /** Moves by whole octaves, preserving spelling. */
    public operator fun plus(octaves: Int): SpelledPitch = copy(octave = octave + octaves)

    /** Moves down by whole octaves, preserving spelling. */
    public operator fun minus(octaves: Int): SpelledPitch = copy(octave = octave - octaves)

    override fun compareTo(other: SpelledPitch): Int =
        compareValuesBy(this, other, { it.diatonicStaffStep }, { it.pitchClass.accidental.offset })

    override fun toString(): String = "$pitchClass$octave"
}

/**
 * Places this pitch class in whichever octave makes it sound as [note], preserving spelling.
 *
 * Fails loudly rather than respelling: asking for Cb at MIDI 60 is a bug in the caller, and
 * silently handing back C would be exactly the enharmonic slip the spelling rules exist to
 * prevent.
 */
public fun SpelledPitchClass.atMidiNote(note: MidiNote): SpelledPitch {
    val octave = (note.value - unwrappedChromaticValue).floorDiv(12) - 1
    val spelled = SpelledPitch(this, octave)
    check(spelled.midiNote == note) { "$this cannot be written at MIDI ${note.value}" }
    return spelled
}
