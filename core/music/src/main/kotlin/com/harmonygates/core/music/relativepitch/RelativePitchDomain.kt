package com.harmonygates.core.music.relativepitch

import com.harmonygates.core.music.chord.ChordFormula
import com.harmonygates.core.music.chord.ChordFormulas

/**
 * Relative pitch, from nothing.
 *
 * The chord-reproduction exercises in `eartraining` ask a player to play back a chord they just
 * heard, cold — which only works once the ear already knows what it is listening for. This is
 * the training that comes before that: a graded ladder that starts from "can these two notes be
 * told apart at all" and builds, one level at a time, to hearing a chord's quality, a note's
 * place in a key, and finally a function in context — the actual skill "relative pitch" names,
 * rather than a single exercise pretending to teach it in one step.
 */

/** A melodic or harmonic distance, named the way a player would ask for it. */
public enum class IntervalClass(public val semitones: Int, public val shortLabel: String, public val label: String) {
    UNISON(0, "P1", "Unison"),
    MINOR_SECOND(1, "m2", "Minor 2nd"),
    MAJOR_SECOND(2, "M2", "Major 2nd"),
    MINOR_THIRD(3, "m3", "Minor 3rd"),
    MAJOR_THIRD(4, "M3", "Major 3rd"),
    PERFECT_FOURTH(5, "P4", "Perfect 4th"),
    TRITONE(6, "TT", "Tritone"),
    PERFECT_FIFTH(7, "P5", "Perfect 5th"),
    MINOR_SIXTH(8, "m6", "Minor 6th"),
    MAJOR_SIXTH(9, "M6", "Major 6th"),
    MINOR_SEVENTH(10, "m7", "Minor 7th"),
    MAJOR_SEVENTH(11, "M7", "Major 7th"),
    OCTAVE(12, "P8", "Octave"),
}

/** A degree of the major scale, heard against a sounded tonic. Diatonic only; v1 stops there. */
public enum class ScaleDegree(public val semitonesFromTonic: Int, public val label: String) {
    ONE(0, "1"),
    TWO(2, "2"),
    THREE(4, "3"),
    FOUR(5, "4"),
    FIVE(7, "5"),
    SIX(9, "6"),
    SEVEN(11, "7"),
}

/** A chord quality, identified by ear alone — nothing is played back on this tier. */
public enum class ChordQuality(public val formula: ChordFormula, public val label: String) {
    MAJOR(ChordFormulas.MajorTriad, "Major"),
    MINOR(ChordFormulas.MinorTriad, "Minor"),
    DIMINISHED(ChordFormulas.DiminishedTriad, "Diminished"),
    AUGMENTED(ChordFormulas.AugmentedTriad, "Augmented"),
    MAJOR_SEVENTH(ChordFormulas.MajorSeventh, "Major 7th"),
    DOMINANT_SEVENTH(ChordFormulas.DominantSeventh, "Dominant 7th"),
    MINOR_SEVENTH(ChordFormulas.MinorSeventh, "Minor 7th"),
    HALF_DIMINISHED(ChordFormulas.HalfDiminishedSeventh, "Half-diminished"),
    DIMINISHED_SEVENTH(ChordFormulas.DiminishedSeventh, "Diminished 7th"),
}

/** Where a level sits in the ladder, and therefore what it takes to generate and answer it. */
public enum class RelativePitchTier {
    /** Two notes, heard one after another (or together). Which interval was that? */
    INTERVALS,

    /** One note, heard against a sounded tonic. Which scale degree? */
    SCALE_DEGREES,

    /** One chord. Which quality — by ear alone, nothing reproduced. */
    CHORD_QUALITY,

    /** A function inside a key, in context. Reuses `eartraining`'s existing generator and screen. */
    FUNCTION_HEARING,

    /** Play back what you hear, on the keyboard. The capstone; reuses the existing screen. */
    REPRODUCE,
}

/**
 * One rung of the ladder.
 *
 * Multiple-choice levels (the first three tiers) carry a cumulative, growing set of choices —
 * each level adds to what the previous one already asked for, rather than replacing it, so
 * mastering a level never un-teaches what came before it. [FUNCTION_HEARING] and [REPRODUCE]
 * carry none of the three choice lists; they are markers the screen uses to route into the
 * existing keyboard-based ear-training flow instead of generating a multiple-choice question.
 */
public data class RelativePitchLevel(
    val id: String,
    val tier: RelativePitchTier,
    val title: String,
    val prompt: String,
    val intervalChoices: List<IntervalClass> = emptyList(),
    val degreeChoices: List<ScaleDegree> = emptyList(),
    val qualityChoices: List<ChordQuality> = emptyList(),
    val minimumAttempts: Int = DEFAULT_MINIMUM_ATTEMPTS,
    val requiredAccuracy: Double = DEFAULT_REQUIRED_ACCURACY,
    val sessionLength: Int = DEFAULT_SESSION_LENGTH,
) {
    init {
        require(id.isNotBlank()) { "A level id must not be blank" }
        require(minimumAttempts > 0) { "A level needs at least one attempt of evidence" }
        require(requiredAccuracy in 0.0..1.0) { "Required accuracy is a fraction: $requiredAccuracy" }
        val choiceCount = intervalChoices.size + degreeChoices.size + qualityChoices.size
        val message = { "Level '$id' is tier $tier but carries choices from the wrong tier(s), or fewer than two" }
        when (tier) {
            RelativePitchTier.INTERVALS -> require(intervalChoices.size == choiceCount && choiceCount >= 2, message)
            RelativePitchTier.SCALE_DEGREES -> require(degreeChoices.size == choiceCount && choiceCount >= 2, message)
            RelativePitchTier.CHORD_QUALITY -> require(qualityChoices.size == choiceCount && choiceCount >= 2, message)
            RelativePitchTier.FUNCTION_HEARING, RelativePitchTier.REPRODUCE ->
                require(choiceCount == 0) { "Level '$id' is a routing marker and should carry no choices" }
        }
    }

    public companion object {
        public const val DEFAULT_MINIMUM_ATTEMPTS: Int = 10
        public const val DEFAULT_REQUIRED_ACCURACY: Double = 0.85
        public const val DEFAULT_SESSION_LENGTH: Int = 12
    }
}

/** Accuracy evidence for one level, however it is stored. */
public data class LevelStat(val attempts: Int, val correct: Int) {
    init {
        require(attempts >= 0) { "Attempts cannot be negative" }
        require(correct in 0..attempts) { "Cannot have more correct answers ($correct) than attempts ($attempts)" }
    }

    public val accuracy: Double get() = if (attempts == 0) 0.0 else correct.toDouble() / attempts

    public fun passes(level: RelativePitchLevel): Boolean =
        attempts >= level.minimumAttempts && accuracy >= level.requiredAccuracy

    public companion object {
        public val NONE: LevelStat = LevelStat(0, 0)
    }
}

/** Where a level stands for a player right now. */
public enum class LevelStatus {
    /** The level before it in the ladder is not yet mastered. */
    LOCKED,

    /** Open to practise, not yet mastered. */
    AVAILABLE,

    /** [LevelStat.passes] is true for this level's evidence. */
    MASTERED,
}
