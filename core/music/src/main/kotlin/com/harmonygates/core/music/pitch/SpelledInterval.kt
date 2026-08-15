package com.harmonygates.core.music.pitch

/**
 * An interval that knows both how far it moves the ear and how far it moves on the staff.
 *
 * Two separate quantities are needed because a diminished fourth and a major third sound
 * alike but are written differently. Transposing with semitones alone destroys spelling,
 * which is exactly the failure 04_HARMONY_DOMAIN_ENGINE.md §6 forbids.
 *
 * @param diatonicSteps letter steps: 0 = unison, 2 = a third, 4 = a fifth, 7 = an octave.
 * @param semitones sounding distance.
 */
public data class SpelledInterval(
    val diatonicSteps: Int,
    val semitones: Int,
) {
    public operator fun unaryMinus(): SpelledInterval = SpelledInterval(-diatonicSteps, -semitones)

    public operator fun plus(other: SpelledInterval): SpelledInterval =
        SpelledInterval(diatonicSteps + other.diatonicSteps, semitones + other.semitones)

    /** Conventional interval number: unison = 1, third = 3, ninth = 9. */
    public val number: Int get() = if (diatonicSteps >= 0) diatonicSteps + 1 else diatonicSteps - 1

    public companion object {
        public val PERFECT_UNISON: SpelledInterval = SpelledInterval(0, 0)
        public val MINOR_SECOND: SpelledInterval = SpelledInterval(1, 1)
        public val MAJOR_SECOND: SpelledInterval = SpelledInterval(1, 2)
        public val AUGMENTED_SECOND: SpelledInterval = SpelledInterval(1, 3)
        public val MINOR_THIRD: SpelledInterval = SpelledInterval(2, 3)
        public val MAJOR_THIRD: SpelledInterval = SpelledInterval(2, 4)
        public val PERFECT_FOURTH: SpelledInterval = SpelledInterval(3, 5)
        public val AUGMENTED_FOURTH: SpelledInterval = SpelledInterval(3, 6)
        public val DIMINISHED_FIFTH: SpelledInterval = SpelledInterval(4, 6)
        public val PERFECT_FIFTH: SpelledInterval = SpelledInterval(4, 7)
        public val AUGMENTED_FIFTH: SpelledInterval = SpelledInterval(4, 8)
        public val MINOR_SIXTH: SpelledInterval = SpelledInterval(5, 8)
        public val MAJOR_SIXTH: SpelledInterval = SpelledInterval(5, 9)
        public val DIMINISHED_SEVENTH: SpelledInterval = SpelledInterval(6, 9)
        public val MINOR_SEVENTH: SpelledInterval = SpelledInterval(6, 10)
        public val MAJOR_SEVENTH: SpelledInterval = SpelledInterval(6, 11)
        public val PERFECT_OCTAVE: SpelledInterval = SpelledInterval(7, 12)

        /** The twelve ascending chromatic steps expressed as sharps, for pitch-class work. */
        public fun chromaticAscending(semitones: Int): SpelledInterval {
            val diatonic = when (semitones.mod(12)) {
                0 -> 0
                1, 2 -> 1
                3, 4 -> 2
                5, 6 -> 3
                7, 8 -> 4
                9, 10 -> 5
                else -> 6
            }
            return SpelledInterval(diatonic + 7 * semitones.floorDiv(12), semitones)
        }
    }
}

/**
 * Result of a spelling operation that may need an accidental this system cannot write.
 *
 * Triple sharps and flats are refused rather than silently re-spelled: silently swapping in
 * an enharmonic equivalent is precisely the bug class the spelling rules exist to catch.
 */
public sealed interface SpellingResult<out T> {
    public data class Spelled<T>(val value: T) : SpellingResult<T>

    /** The exact spelling would need a triple accidental. */
    public data class Overflow(
        val letter: LetterName,
        val requiredOffset: Int,
        val message: String,
    ) : SpellingResult<Nothing>

    public fun getOrNull(): T? = (this as? Spelled)?.value
}

/** Transposes a spelled pitch class, preserving letter identity. */
public fun SpelledPitchClass.transposeBy(interval: SpelledInterval): SpellingResult<SpelledPitchClass> {
    val newLetter = letter.transposeDiatonic(interval.diatonicSteps)
    val targetPitchClass = (pitchClass.value + interval.semitones).mod(12)
    val offset = nearestOffset(newLetter.naturalPitchClass, targetPitchClass)
    val accidental = Accidental.ofOffsetOrNull(offset)
        ?: return SpellingResult.Overflow(
            letter = newLetter,
            requiredOffset = offset,
            message = "$this transposed by $interval needs $newLetter with offset $offset",
        )
    return SpellingResult.Spelled(SpelledPitchClass(newLetter, accidental))
}

/** Transposes a spelled pitch, tracking the octave the letter wrapping implies. */
public fun SpelledPitch.transposeBy(interval: SpelledInterval): SpellingResult<SpelledPitch> {
    val transposedClass = when (val result = pitchClass.transposeBy(interval)) {
        is SpellingResult.Spelled -> result.value
        is SpellingResult.Overflow -> return result
    }
    val staffStep = diatonicStaffStep + interval.diatonicSteps
    return SpellingResult.Spelled(SpelledPitch(transposedClass, staffStep.floorDiv(7)))
}

/**
 * Smallest signed accidental offset turning [naturalPitchClass] into [targetPitchClass].
 *
 * Chooses the representative nearest zero so that C -> 11 becomes Cb (-1) rather than
 * C-with-eleven-sharps.
 */
internal fun nearestOffset(naturalPitchClass: Int, targetPitchClass: Int): Int =
    (targetPitchClass - naturalPitchClass + 6).mod(12) - 6
