package com.harmonygates.core.music.campaign

import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.random.SeededRandomFactory
import java.time.Instant

/** One run of exercises within a gate session, at one assistance level. */
public data class SessionStage(
    val kind: StageKind,
    val exerciseCount: Int,
    val presentation: PresentationSpec,
) {
    init {
        require(exerciseCount > 0) { "A stage with no exercises should not be in the plan" }
    }
}

/**
 * The phases of 02_GAME_LOOP_AND_PROGRESSION.md §3.
 *
 * Preview and result are not stages here: they are screens either side of the playing, and
 * modelling them as exercise runs of length zero would only invite a division by it.
 */
public enum class StageKind(public val label: String) {
    /** High assistance: the notes are on the screen and on the keyboard. */
    GUIDED("Guided"),

    /** Symbol only. */
    INDEPENDENT("Independent"),

    /** Randomised roots and inversions. */
    CHALLENGE("Challenge"),

    /** Scored, uninterrupted, no tutorial. */
    GATE_CHECK("Gate check"),
}

/**
 * What a gate session will contain.
 *
 * @param rootOrder the order roots are preferred in, most-needed first. The generator draws from
 *   the front of this, which is how coverage and weakness steer the session.
 */
public data class SessionPlan(
    val gateId: GateId,
    val stages: List<SessionStage>,
    val rootOrder: List<SpelledPitchClass>,
    val seed: Long,
) {
    public val totalExercises: Int get() = stages.sumOf { it.exerciseCount }

    /** The policy to run one stage with, derived from the gate's own policy. */
    public fun policyFor(stage: SessionStage, basePolicy: ExercisePolicy): ExercisePolicy =
        basePolicy.copy(
            presentation = stage.presentation,
            rootPool = rootOrder.ifEmpty { basePolicy.rootPool },
            sessionLength = stage.exerciseCount,
        )
}

/**
 * Decides what a gate session should contain for this player, right now.
 *
 * Two rules from the specification do the shaping:
 *
 * - 02 §3: "Do not force a player who has already demonstrated mastery to repeat tutorial
 *   trials." A player returning to a gate they nearly passed gets the gate check, not the
 *   tutorial. A player who has never seen it gets the tutorial.
 * - 02 §9: sessions are sampled with weights — coverage need, weakness, review due, novelty,
 *   minus a penalty for immediate duplicates. Here that decides the order roots are offered in,
 *   which is where coverage actually comes from.
 *
 * Deterministic from the seed, as everything generated in this project is: the same player state
 * and the same seed produce the same session, so a player can be asked to repeat one and a bug
 * report can be replayed.
 */
public class GateSessionPlanner(
    private val randomFactory: SeededRandomFactory = DefaultSeededRandomFactory,
) {

    public fun plan(
        gate: GateDefinition,
        policy: ExercisePolicy,
        mastery: Map<com.harmonygates.core.music.exercise.SkillId, SkillMastery>,
        seed: Long,
        now: Instant,
    ): SessionPlan {
        val relevant = gate.skillIds.mapNotNull { mastery[it] }
        val estimate = relevant.minOfOrNull { it.estimate } ?: 0.0
        val attempted = relevant.sumOf { it.attempts }
        val dueForReview = relevant.any { it.isDueForReview(now) }

        return SessionPlan(
            gateId = gate.id,
            stages = stagesFor(policy, estimate, attempted, dueForReview),
            rootOrder = rootOrder(gate, policy, relevant, seed),
            seed = seed,
        )
    }

    /**
     * How the session is shaped, given what the player already knows.
     *
     * The tutorial shrinks as the estimate rises and disappears entirely once the player is
     * clearly competent, leaving challenge trials and the scored check. The gate check is always
     * present and always last: it is the only part the completion rule is really about.
     */
    private fun stagesFor(
        policy: ExercisePolicy,
        estimate: Double,
        attempted: Int,
        dueForReview: Boolean,
    ): List<SessionStage> {
        val total = policy.sessionLength
        val guidedShare = when {
            attempted == 0 -> FIRST_VISIT_GUIDED_SHARE
            estimate >= CONFIDENT -> 0.0
            estimate >= FAMILIAR -> RETURNING_GUIDED_SHARE
            // A player who has attempted this and is still struggling gets the tutorial back,
            // which is the one case where repeating it is the helpful thing to do.
            else -> STRUGGLING_GUIDED_SHARE
        }
        // A review visit is a check that the skill survived, not a re-teach.
        val guided = if (dueForReview && estimate >= FAMILIAR) 0 else (total * guidedShare).toInt()
        val check = maxOf(MINIMUM_GATE_CHECK, (total * GATE_CHECK_SHARE).toInt())
        val challenge = if (estimate >= FAMILIAR) (total * CHALLENGE_SHARE).toInt() else 0
        val independent = (total - guided - check - challenge).coerceAtLeast(MINIMUM_INDEPENDENT)

        return buildList {
            if (guided > 0) {
                add(SessionStage(StageKind.GUIDED, guided, PresentationSpec.Guided))
            }
            add(SessionStage(StageKind.INDEPENDENT, independent, PresentationSpec.Independent))
            if (challenge > 0) {
                add(
                    SessionStage(
                        StageKind.CHALLENGE,
                        challenge,
                        // The chord symbol stays: challenge means harder material, not a
                        // guessing game. Hiding it is what the ear-training mode is for.
                        PresentationSpec(showChordSymbol = true),
                    ),
                )
            }
            add(SessionStage(StageKind.GATE_CHECK, check, PresentationSpec.Independent))
        }
    }

    /**
     * The order roots are offered in.
     *
     * Scored the way 02 §9 describes and then shuffled *within* score bands, so the session is
     * varied without ever putting a root the player has already proved ahead of one they have
     * never seen. Ties break on the pool's own order, so the result is reproducible.
     */
    private fun rootOrder(
        gate: GateDefinition,
        policy: ExercisePolicy,
        mastery: List<SkillMastery>,
        seed: Long,
    ): List<SpelledPitchClass> {
        val pool = policy.rootPool.ifEmpty { return emptyList() }
        val covered = mastery.flatMap { it.rootsCovered }.toSet()
        val needed = gate.completionRule.minimumRootCoverage ?: pool.size

        val random = randomFactory.create(seed)
        val scored = pool.map { root ->
            var score = 0.0
            // Coverage need: an untested root is the most valuable thing the session can ask for,
            // and only while coverage is actually short of the bar.
            if (root !in covered && covered.size < needed) score += COVERAGE_WEIGHT
            if (root !in covered) score += NOVELTY_WEIGHT
            root to score
        }

        return scored
            .groupBy { it.second }
            .toSortedMap(compareByDescending { it })
            .flatMap { (_, band) -> random.shuffled(band.map { it.first }) }
    }

    private companion object {
        /** Estimates above this need no tutorial at all. */
        const val CONFIDENT = 0.85

        /** Above this the player has seen the material and gets a short reminder at most. */
        const val FAMILIAR = 0.6

        const val FIRST_VISIT_GUIDED_SHARE = 0.3
        const val RETURNING_GUIDED_SHARE = 0.1
        const val STRUGGLING_GUIDED_SHARE = 0.25
        const val CHALLENGE_SHARE = 0.2
        const val GATE_CHECK_SHARE = 0.3

        const val MINIMUM_GATE_CHECK = 4
        const val MINIMUM_INDEPENDENT = 2

        const val COVERAGE_WEIGHT = 3.0
        const val NOVELTY_WEIGHT = 1.0
    }
}
