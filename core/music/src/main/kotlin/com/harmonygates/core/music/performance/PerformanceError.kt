package com.harmonygates.core.music.performance

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass

/**
 * A tone the exercise expected, with enough context to explain its absence.
 *
 * Carries the degree as well as the pitch, because "you are missing the seventh" teaches
 * something and "you are missing B" teaches less.
 */
public data class ExpectedTone(
    val pitchClass: SpelledPitchClass,
    val degree: ChordDegree? = null,
    /** Set when the exercise wanted one specific octave rather than any. */
    val exactNote: MidiNote? = null,
) {
    override fun toString(): String = degree?.let { "$pitchClass (${it.symbol})" } ?: pitchClass.toString()
}

/**
 * What went wrong, musically.
 *
 * 06_PERFORMANCE_EVALUATION_AND_SCORING.md §8. These are diagnoses rather than messages: the
 * evaluator says what is true and the UI decides how to word it, so the same finding can be
 * phrased one way for a beginner gate and another in a diagnostic export.
 *
 * §8 also asks for explanations ordered "by educational importance, not arbitrary collection
 * order", which is what [rank] is for. Playing the wrong chord entirely matters more than
 * spreading its onsets, and a player told both at once should read the first one first.
 */
public sealed interface PerformanceError {
    /** Lower sorts first. */
    public val rank: Int

    /** The player is on a different chord altogether. */
    public data class WrongRoot(
        val expected: SpelledPitchClass,
        val played: SpelledPitchClass?,
    ) : PerformanceError {
        override val rank: Int get() = 0
    }

    /** Right root, wrong colour: major for minor, or a seventh family confused. */
    public data class WrongQuality(
        val expectedDegree: ChordDegree,
        val playedDegree: ChordDegree?,
    ) : PerformanceError {
        override val rank: Int get() = 1
    }

    /** A required tone is absent. */
    public data class MissingDegree(val tone: ExpectedTone) : PerformanceError {
        override val rank: Int
            // A missing guide tone changes what the chord is; a missing fifth usually does not.
            get() = when (tone.degree?.number) {
                3, 4 -> 2
                7, 6 -> 3
                5 -> 6
                else -> 4
            }
    }

    /** A tension is present but altered the wrong way — a natural ninth where a flat one was wanted. */
    public data class WrongAlteration(
        val expected: ChordDegree,
        val played: ChordDegree,
    ) : PerformanceError {
        override val rank: Int get() = 5
    }

    /** The lowest note is not the one the exercise asked for. */
    public data class WrongBass(
        val expected: SpelledPitchClass,
        val played: MidiNote?,
    ) : PerformanceError {
        override val rank: Int get() = 7
    }

    /** The melody note is not the one the exercise asked for. */
    public data class WrongTopNote(
        val expected: MidiNote?,
        val played: MidiNote?,
    ) : PerformanceError {
        override val rank: Int get() = 8
    }

    /** A note that does not belong to the chord, or that this exercise disallows. */
    public data class ExtraTone(
        val note: MidiNote,
        val degree: ChordDegree? = null,
    ) : PerformanceError {
        override val rank: Int get() = 9
    }

    /** Right notes, wrong register for the exercise — a left-hand voicing played up high. */
    public data class RegisterViolation(
        val note: MidiNote,
        val allowedRange: IntRange,
    ) : PerformanceError {
        override val rank: Int get() = 10
    }

    /** The chord was spread wider than the exercise allows. */
    public data class OnsetSpreadViolation(
        val spreadMillis: Long,
        val allowedMillis: Int,
    ) : PerformanceError {
        override val rank: Int get() = 11
    }

    /** The notes were right but not in time. Kept separate from spread, and from response speed. */
    public data class RhythmViolation(
        val expectedBeat: Int,
        val errorMillis: Long,
    ) : PerformanceError {
        override val rank: Int get() = 12
    }

    /** Nothing was played within the time allowed. */
    public data object NoNotesPlayed : PerformanceError {
        override val rank: Int get() = 13
    }
}

/** Orders errors the way a teacher would mention them. */
public fun List<PerformanceError>.byEducationalImportance(): List<PerformanceError> =
    sortedBy { it.rank }
