package com.harmonygates.core.music.performance

import com.harmonygates.core.music.pitch.MidiNote

/**
 * Whether an answer satisfied the requirement.
 *
 * 06_PERFORMANCE_EVALUATION_AND_SCORING.md §2. Note that "correct" has two forms: an exercise
 * that permits several renderings needs to be able to say yes *and* record which one was taken,
 * because Region 7's rootless voicings and Region 13's voice-leading answers both have many
 * right answers and a mastery model that treated them as identical would learn nothing.
 */
public enum class Verdict {
    CORRECT,

    /** Right, by one of the alternatives the exercise allows rather than the canonical one. */
    CORRECT_WITH_ACCEPTED_VARIATION,

    /** Some of the required tones are there. Worth partial credit and a specific diagnosis. */
    PARTIAL,

    INCORRECT,

    /** Capture was armed and nothing was played. Not a wrong answer. */
    NO_ATTEMPT,

    /** The keyboard went away mid-chord. Never counted against the player. */
    ABORTED_DEVICE_LOSS,
    ;

    /** True for both forms of correct. */
    public val isCorrect: Boolean
        get() = this == CORRECT || this == CORRECT_WITH_ACCEPTED_VARIATION

    /** True when the attempt tells us nothing about the player's skill. */
    public val isInconclusive: Boolean
        get() = this == NO_ATTEMPT || this == ABORTED_DEVICE_LOSS
}

/**
 * How the timing went.
 *
 * §6 insists these stay separate: "Do not conflate response speed with rhythmic accuracy." A
 * player who thinks slowly and then plays cleanly has one problem; a player who answers
 * instantly and rushes has another.
 */
public data class TimingEvaluation(
    /** Armed to first note. Thinking time, not rhythm. */
    val responseLatencyMillis: Long?,
    /** First onset to last onset. */
    val onsetSpreadMillis: Long?,
    val onsetPolicy: OnsetPolicy,
    val spreadWithinPolicy: Boolean,
    /** Set only for exercises played against a beat. */
    val beatErrorMillis: Long? = null,
)

/** Numbers worth keeping about an attempt, for progress trends and diagnostics. */
public data class PerformanceMetrics(
    val notesPlayed: Int,
    val requiredToneCount: Int,
    val matchedToneCount: Int,
    val extraNoteCount: Int,
    val sustainUsed: Boolean,
    /** 0..1. Coverage of what the exercise required, before any penalty for extras. */
    val completeness: Double,
) {
    public companion object {
        internal fun of(
            attempt: PerformanceAttempt,
            required: Int,
            matched: Int,
            extra: Int,
        ): PerformanceMetrics = PerformanceMetrics(
            notesPlayed = attempt.finalEffectiveNotes.size,
            requiredToneCount = required,
            matchedToneCount = matched,
            extraNoteCount = extra,
            sustainUsed = attempt.sustainUsed,
            completeness = if (required == 0) 1.0 else matched.toDouble() / required,
        )
    }
}

/**
 * Structured feedback, ready for a UI to word.
 *
 * Deliberately not a sentence. §10 wants the musical diagnosis to survive whatever game
 * presentation sits on top, and a pre-formatted string would force every surface to accept one
 * reading level and one language.
 */
public data class FeedbackModel(
    val headline: Headline,
    val errors: List<PerformanceError>,
    /** The single most useful thing to say, when there is room for only one. */
    val primaryError: PerformanceError? = errors.minByOrNull { it.rank },
) {
    public enum class Headline {
        CORRECT,
        CORRECT_VARIATION,
        ALMOST,
        NOT_YET,
        NOTHING_PLAYED,
        DEVICE_LOST,
    }
}

/** Everything the evaluator concluded (§1). */
public data class EvaluationResult(
    val verdict: Verdict,
    val matched: Set<MidiNote>,
    val missing: Set<ExpectedTone>,
    val extra: Set<MidiNote>,
    val semanticErrors: List<PerformanceError>,
    val timing: TimingEvaluation?,
    val metrics: PerformanceMetrics,
    val explanation: FeedbackModel,
) {
    public val isCorrect: Boolean get() = verdict.isCorrect
}
