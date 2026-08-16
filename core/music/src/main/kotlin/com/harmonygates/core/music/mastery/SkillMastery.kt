package com.harmonygates.core.music.mastery

import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.pitch.SpelledPitchClass
import java.time.Duration
import java.time.Instant

/**
 * The kinds of mistake worth counting separately.
 *
 * 02_GAME_LOOP_AND_PROGRESSION.md §5 asks for errors to be stored semantically "to enable
 * targeted review". [PerformanceError] already is semantic, but it carries the particulars —
 * which note, which degree, how many milliseconds — and a histogram of those would have a bucket
 * per chord. This is the same taxonomy with the particulars dropped.
 */
public enum class ErrorClass {
    WRONG_ROOT,
    WRONG_QUALITY,
    MISSING_CHORD_TONE,
    WRONG_ALTERATION,
    WRONG_BASS,
    WRONG_TOP_NOTE,
    EXTRA_NOTE,
    REGISTER,
    ONSET_TIMING,
    RHYTHM,
    NOTHING_PLAYED,
    ;

    public companion object {
        public fun of(error: PerformanceError): ErrorClass = when (error) {
            is PerformanceError.WrongRoot -> WRONG_ROOT
            is PerformanceError.WrongQuality -> WRONG_QUALITY
            is PerformanceError.MissingDegree -> MISSING_CHORD_TONE
            is PerformanceError.WrongAlteration -> WRONG_ALTERATION
            is PerformanceError.WrongBass -> WRONG_BASS
            is PerformanceError.WrongTopNote -> WRONG_TOP_NOTE
            is PerformanceError.ExtraTone -> EXTRA_NOTE
            is PerformanceError.RegisterViolation -> REGISTER
            is PerformanceError.OnsetSpreadViolation -> ONSET_TIMING
            is PerformanceError.RhythmViolation -> RHYTHM
            PerformanceError.NoNotesPlayed -> NOTHING_PLAYED
        }
    }

    /**
     * True for the mistakes that mean the player played a different chord.
     *
     * The gate rule in 02 §4 caps "critical-theory errors in last 8" separately from ordinary
     * ones, because a rushed onset and a wrong third are not the same kind of wrong.
     */
    public val isCriticalTheoryError: Boolean
        get() = this == WRONG_ROOT || this == WRONG_QUALITY ||
            this == MISSING_CHORD_TONE || this == WRONG_ALTERATION
}

/**
 * How much a single attempt says about the player.
 *
 * 06_PERFORMANCE_EVALUATION_AND_SCORING.md §9 gives the weights directly: an independent correct
 * answer is worth 1.0, correct with reduced assistance 0.8, correct after a hint 0.5, and an
 * incorrect answer 0.0. They are values rather than a formula because they are a teaching
 * judgement, and a curriculum author is allowed to disagree with them.
 */
public enum class Evidence(public val weight: Double) {
    /** Right, with no assistance at all. */
    INDEPENDENT_CORRECT(1.0),

    /** Right, with some assistance channels still on. */
    CORRECT_WITH_REDUCED_ASSISTANCE(0.8),

    /** Right, but only after asking for a hint on this exercise. */
    CORRECT_AFTER_HINT(0.5),

    /** Wrong. */
    INCORRECT(0.0),
    ;

    public val isCorrect: Boolean get() = this != INCORRECT
}

/**
 * What is known about one skill.
 *
 * 06 §9. [estimate] is the number a screen shows; everything else is the evidence it came from,
 * kept so that a mastery figure can always be explained rather than merely displayed.
 */
public data class SkillMastery(
    val skillId: SkillId,
    /** 0..1. Recency-weighted, so recent independent work outweighs old guided work. */
    val estimate: Double = 0.0,
    val attempts: Int = 0,
    val successfulAttempts: Int = 0,
    /** 0..1 over the recent window only. The gate rule reads this, not [estimate]. */
    val recentWeightedAccuracy: Double = 0.0,
    val medianResponseMillis: Long? = null,
    val lastPracticedAt: Instant? = null,
    val nextReviewAt: Instant? = null,
    val errorHistogram: Map<ErrorClass, Int> = emptyMap(),
    /** Chord roots this skill has been tested on. Gate rules require breadth, not just accuracy. */
    val rootsCovered: Set<SpelledPitchClass> = emptySet(),
    /** Verdicts of the recent window, newest last. Kept so the estimate can be recomputed. */
    val recentEvidence: List<Evidence> = emptyList(),
) {
    init {
        require(estimate in 0.0..1.0) { "A mastery estimate is a fraction: $estimate" }
        require(attempts >= 0 && successfulAttempts >= 0) { "Attempt counts cannot be negative" }
        require(successfulAttempts <= attempts) {
            "More successes ($successfulAttempts) than attempts ($attempts)"
        }
    }

    /** Mistakes ordered by how often they happen, worst first. */
    public val recurringErrors: List<Pair<ErrorClass, Int>>
        get() = errorHistogram.entries.sortedWith(
            compareByDescending<Map.Entry<ErrorClass, Int>> { it.value }.thenBy { it.key.name },
        ).map { it.key to it.value }

    public val criticalErrorsInWindow: Int
        get() = errorHistogram.entries.filter { it.key.isCriticalTheoryError }.sumOf { it.value }

    public fun isDueForReview(now: Instant): Boolean =
        nextReviewAt?.let { !now.isBefore(it) } == true
}

/**
 * One attempt's worth of evidence about one skill.
 *
 * Deliberately not [com.harmonygates.core.music.session.AttemptRecord]: that carries the whole
 * exercise instance and the raw MIDI, which the mastery update has no business reading. This is
 * the projection of an attempt onto a skill.
 */
public data class MasteryEvent(
    val skillId: SkillId,
    val evidence: Evidence,
    val at: Instant,
    val responseMillis: Long? = null,
    val errors: List<ErrorClass> = emptyList(),
    val root: SpelledPitchClass? = null,
)

/**
 * Turns evidence into a mastery estimate.
 *
 * 06 §9: "Use a transparent deterministic update algorithm first. Do not add opaque ML." So this
 * is an exponentially-weighted mean of the evidence weights, and nothing else — you can work out
 * by hand what any sequence of attempts produces, which matters because the number gates a
 * player's progress and they are entitled to an explanation of it.
 *
 * @param windowSize how many recent attempts count towards [SkillMastery.recentWeightedAccuracy].
 * @param recencyHalfLife how many attempts back an attempt's influence halves. Smaller means the
 *   estimate moves faster and forgives faster.
 * @param reviewIntervals spacing between reviews, indexed by consecutive successes.
 */
public class MasteryUpdater(
    private val windowSize: Int = DEFAULT_WINDOW,
    private val recencyHalfLife: Double = DEFAULT_HALF_LIFE,
    private val reviewIntervals: List<Duration> = DEFAULT_REVIEW_INTERVALS,
) {
    init {
        require(windowSize > 0) { "The recent window must hold at least one attempt" }
        require(recencyHalfLife > 0) { "A half-life must be positive" }
        require(reviewIntervals.isNotEmpty()) { "There must be at least one review interval" }
    }

    /** Folds one attempt into what was already known. */
    public fun apply(current: SkillMastery, event: MasteryEvent): SkillMastery {
        require(current.skillId == event.skillId) {
            "Evidence for ${event.skillId} cannot update ${current.skillId}"
        }

        val evidenceWindow = (current.recentEvidence + event.evidence).takeLast(windowSize)
        val histogram = current.errorHistogram.toMutableMap()
        event.errors.distinct().forEach { histogram[it] = (histogram[it] ?: 0) + 1 }

        val estimate = weightedMean(evidenceWindow)
        return current.copy(
            estimate = estimate,
            attempts = current.attempts + 1,
            successfulAttempts = current.successfulAttempts + if (event.evidence.isCorrect) 1 else 0,
            recentWeightedAccuracy = estimate,
            medianResponseMillis = medianOf(current.medianResponseMillis, event.responseMillis),
            lastPracticedAt = event.at,
            nextReviewAt = event.at.plus(reviewInterval(evidenceWindow)),
            errorHistogram = histogram,
            rootsCovered = current.rootsCovered + listOfNotNull(event.root),
            recentEvidence = evidenceWindow,
        )
    }

    /** Rebuilds a mastery record from scratch. Used when the window size or weights change. */
    public fun replay(skillId: SkillId, events: List<MasteryEvent>): SkillMastery =
        events.fold(SkillMastery(skillId)) { mastery, event -> apply(mastery, event) }

    /**
     * The recency-weighted mean of the window.
     *
     * The newest attempt carries full weight and each older one carries half as much per
     * [recencyHalfLife] attempts, which is how "recent independent performances should matter
     * more than old guided performances" is expressed as arithmetic.
     */
    private fun weightedMean(window: List<Evidence>): Double {
        if (window.isEmpty()) return 0.0
        var weightedTotal = 0.0
        var weightTotal = 0.0
        window.reversed().forEachIndexed { age, evidence ->
            val weight = Math.pow(0.5, age / recencyHalfLife)
            weightedTotal += evidence.weight * weight
            weightTotal += weight
        }
        return (weightedTotal / weightTotal).coerceIn(0.0, 1.0)
    }

    /**
     * When to come back to this skill.
     *
     * Expands with each consecutive success and collapses to the shortest interval the moment
     * one is missed — the standard spaced-repetition shape, kept deliberately simple because
     * Phase 7's assistance system is what will make review worth tuning.
     */
    private fun reviewInterval(window: List<Evidence>): Duration {
        val streak = window.reversed().takeWhile { it.isCorrect }.size
        return reviewIntervals[streak.coerceAtMost(reviewIntervals.lastIndex)]
    }

    /**
     * A running median approximated by nudging towards the newest sample.
     *
     * The true median would need every response time kept forever. This converges on it, never
     * drifts on a single outlier, and costs one number — which is what §2 of
     * 11_DATA_MODEL_AND_PERSISTENCE.md asks for: "avoid storing every derived metric".
     */
    private fun medianOf(current: Long?, sample: Long?): Long? {
        if (sample == null) return current
        if (current == null) return sample
        val step = ((sample - current) / MEDIAN_CONVERGENCE).coerceIn(-MEDIAN_MAX_STEP, MEDIAN_MAX_STEP)
        return current + if (step == 0L) (sample - current).coerceIn(-1L, 1L) else step
    }

    public companion object {
        public const val DEFAULT_WINDOW: Int = 12
        public const val DEFAULT_HALF_LIFE: Double = 6.0

        private const val MEDIAN_CONVERGENCE = 4
        private const val MEDIAN_MAX_STEP = 250L

        /** Indexed by consecutive successes: miss one and you are back to ten minutes. */
        public val DEFAULT_REVIEW_INTERVALS: List<Duration> = listOf(
            Duration.ofMinutes(10),
            Duration.ofHours(4),
            Duration.ofDays(1),
            Duration.ofDays(3),
            Duration.ofDays(7),
            Duration.ofDays(21),
        )
    }
}
