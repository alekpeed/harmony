package com.harmonygates.core.music.key

import com.harmonygates.core.music.pitch.Accidental
import com.harmonygates.core.music.pitch.LetterName
import com.harmonygates.core.music.pitch.ScaleDegree
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.nearestOffset

/**
 * Scale flavours the curriculum needs.
 *
 * The minor forms are listed separately rather than derived, because Region 3 teaches them as
 * distinct practical resources: a `iiø7 - V7 - i` needs the raised leading tone, a modal
 * vamp does not.
 */
public enum class Mode(
    /** Semitones above the tonic for scale degrees 1..7. */
    public val degreeSemitones: List<Int>,
) {
    MAJOR(listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR(listOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR(listOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR(listOf(0, 2, 3, 5, 7, 9, 11)),
    DORIAN(listOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN(listOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN(listOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN(listOf(0, 2, 4, 5, 7, 9, 10)),
    LOCRIAN(listOf(0, 1, 3, 5, 6, 8, 10)),
    ;

    /** True for the modes whose third is minor. Drives roman-numeral defaults. */
    public val hasMinorThird: Boolean get() = degreeSemitones[2] == 3
}

/** A tonic and a mode: everything needed to place a roman numeral. */
public data class KeyContext(
    val tonic: SpelledPitchClass,
    val mode: Mode = Mode.MAJOR,
) {
    /**
     * Spells scale degree [degree], optionally displaced chromatically.
     *
     * The letter always follows the degree number, so the flat second of C major is Db and
     * never C#, no matter which accidental is more convenient.
     */
    public fun degreeRoot(degree: ScaleDegree, chromaticAlteration: Int = 0): SpellingResult<SpelledPitchClass> {
        val letter = tonic.letter.transposeDiatonic(degree.value - 1)
        val target = (tonic.pitchClass.value + mode.degreeSemitones[degree.value - 1] + chromaticAlteration).mod(12)
        val offset = nearestOffset(letter.naturalPitchClass, target)
        val accidental = Accidental.ofOffsetOrNull(offset)
            ?: return SpellingResult.Overflow(
                letter = letter,
                requiredOffset = offset,
                message = "degree ${degree.value} of $this needs $letter with offset $offset",
            )
        return SpellingResult.Spelled(SpelledPitchClass(letter, accidental))
    }

    /** The seven scale tones, spelled. */
    public val scale: List<SpelledPitchClass>
        get() = (1..7).map { degree ->
            val result = degreeRoot(ScaleDegree(degree))
            check(result is SpellingResult.Spelled) { "$this is not a writable key: $result" }
            result.value
        }

    /**
     * Signed key signature: positive counts sharps, negative counts flats.
     *
     * Derived from the notes of the scale rather than a lookup table, so it stays correct for
     * the modal keys the later regions use.
     */
    public val signatureAccidentals: Int
        get() = scale.sumOf { it.accidental.offset }

    override fun toString(): String = "$tonic ${mode.name.lowercase().replace('_', ' ')}"

    public companion object {
        public fun major(tonic: String): KeyContext =
            KeyContext(requireNotNull(SpelledPitchClass.parseOrNull(tonic)) { "Not a note name: $tonic" }, Mode.MAJOR)

        public fun minor(tonic: String, mode: Mode = Mode.NATURAL_MINOR): KeyContext {
            require(mode.hasMinorThird) { "$mode is not a minor mode" }
            return KeyContext(
                requireNotNull(SpelledPitchClass.parseOrNull(tonic)) { "Not a note name: $tonic" },
                mode,
            )
        }

        /** The tonic spellings jazz lead sheets actually use, for content authoring. */
        public val StandardMajorKeys: List<KeyContext> = listOf(
            "C", "F", "Bb", "Eb", "Ab", "Db", "Gb", "B", "E", "A", "D", "G",
        ).map { major(it) }
    }
}

/** Moves a pitch class by scale steps inside a key, keeping it diatonic. */
public fun KeyContext.transposeDiatonic(
    pitchClass: SpelledPitchClass,
    steps: Int,
): SpellingResult<SpelledPitchClass> {
    val scaleTones = scale
    val index = scaleTones.indexOfFirst { it.letter == pitchClass.letter }
    if (index < 0) {
        return SpellingResult.Overflow(
            letter = pitchClass.letter,
            requiredOffset = pitchClass.accidental.offset,
            message = "$pitchClass is not a letter of $this, so it has no diatonic position",
        )
    }
    // Chromatic inflection travels with the note: a raised fourth transposed diatonically
    // stays raised relative to its new scale tone.
    val inflection = pitchClass.accidental.offset - scaleTones[index].accidental.offset
    val target = scaleTones[(index + steps).mod(7)]
    val offset = target.accidental.offset + inflection
    val accidental = Accidental.ofOffsetOrNull(offset)
        ?: return SpellingResult.Overflow(
            letter = target.letter,
            requiredOffset = offset,
            message = "$pitchClass moved $steps steps in $this needs ${target.letter} with offset $offset",
        )
    return SpellingResult.Spelled(SpelledPitchClass(target.letter, accidental))
}
