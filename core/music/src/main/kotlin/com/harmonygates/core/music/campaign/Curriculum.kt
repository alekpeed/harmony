package com.harmonygates.core.music.campaign

import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.mastery.SkillMastery

/** Stable content identifiers (21_CONTENT_AUTHORING_GUIDE.md §6). */
@JvmInline
public value class GateId(public val value: String) {
    init {
        require(value.isNotBlank()) { "A gate id must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class RegionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "A region id must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Something a gate gives the player for passing it.
 *
 * 02_GAME_LOOP_AND_PROGRESSION.md §11: "Useful rewards are educational access rather than
 * currencies... Avoid artificial energy systems, timers, streak pressure, or monetization
 * mechanics in v1." So every case here opens something up, and none of them is a number that
 * goes up.
 */
public sealed interface Unlock {
    /** Another region of the map becomes visible. */
    public data class Region(val regionId: RegionId) : Unlock

    /** A voicing family becomes available to practise. */
    public data class VoicingFamily(val familyName: String) : Unlock

    /** A practice preset appears in quick practice. */
    public data class PracticePreset(val policyId: ExercisePolicyId) : Unlock

    /** An optional harder gate, off the main path. */
    public data class ChallengeGate(val gateId: GateId) : Unlock

    /** A new instrument sound, once Phase 8 has sounds to give. */
    public data class Instrument(val instrumentId: String) : Unlock
}

/**
 * When a gate counts as passed.
 *
 * 02 §4 gives the shape and then insists: "Exact thresholds are content data, not hard-coded UI
 * constants." Every field here is therefore authored per gate, and the defaults are only what an
 * author gets if they say nothing.
 *
 * @param minimumAttempts evidence floor: mastery on three lucky answers is not mastery.
 * @param recentWeightedAccuracy the bar, over the recent window rather than all time.
 * @param maximumCriticalErrors how many wrong-chord errors the recent window may still contain.
 * @param minimumRootCoverage how many of the twelve roots must have been seen. Null where the
 *   skill is not about roots at all.
 * @param maximumMedianResponseMillis a speed bar, for skills where fluency is the point.
 */
public data class CompletionRule(
    val minimumAttempts: Int = DEFAULT_MINIMUM_ATTEMPTS,
    val recentWeightedAccuracy: Double = DEFAULT_ACCURACY,
    val maximumCriticalErrors: Int = DEFAULT_MAXIMUM_CRITICAL_ERRORS,
    val minimumRootCoverage: Int? = null,
    val maximumMedianResponseMillis: Long? = null,
) {
    init {
        require(minimumAttempts > 0) { "A gate needs at least one attempt of evidence" }
        require(recentWeightedAccuracy in 0.0..1.0) {
            "Required accuracy is a fraction: $recentWeightedAccuracy"
        }
        require(maximumCriticalErrors >= 0) { "A negative error allowance is meaningless" }
        require(minimumRootCoverage == null || minimumRootCoverage in 1..CHROMATIC_ROOTS) {
            "Root coverage must be 1..$CHROMATIC_ROOTS: $minimumRootCoverage"
        }
    }

    /**
     * Judges the evidence for one skill against this rule.
     *
     * Returns every reason the gate is not yet passed rather than the first, so a player is told
     * what is actually left instead of being sent back one requirement at a time.
     */
    public fun evaluate(mastery: SkillMastery?): CompletionCheck {
        if (mastery == null) {
            return CompletionCheck(passed = false, unmet = listOf(Unmet.NoEvidence), progress = 0.0)
        }
        val unmet = buildList {
            if (mastery.attempts < minimumAttempts) {
                add(Unmet.NotEnoughAttempts(mastery.attempts, minimumAttempts))
            }
            if (mastery.recentWeightedAccuracy < recentWeightedAccuracy) {
                add(Unmet.AccuracyBelowBar(mastery.recentWeightedAccuracy, recentWeightedAccuracy))
            }
            if (mastery.criticalErrorsInWindow > maximumCriticalErrors) {
                add(Unmet.TooManyCriticalErrors(mastery.criticalErrorsInWindow, maximumCriticalErrors))
            }
            minimumRootCoverage?.let { required ->
                if (mastery.rootsCovered.size < required) {
                    add(Unmet.NotEnoughRoots(mastery.rootsCovered.size, required))
                }
            }
            maximumMedianResponseMillis?.let { limit ->
                val median = mastery.medianResponseMillis
                if (median != null && median > limit) add(Unmet.TooSlow(median, limit))
            }
        }
        return CompletionCheck(
            passed = unmet.isEmpty(),
            unmet = unmet,
            progress = progressOf(mastery),
        )
    }

    /**
     * How far along this skill is, 0..1.
     *
     * The minimum of the requirements rather than their average: a player at 100% accuracy with
     * two attempts is at the start of the gate, not halfway through it, and a progress bar that
     * said otherwise would be lying in the player's favour.
     */
    private fun progressOf(mastery: SkillMastery): Double {
        val fractions = buildList {
            add(mastery.attempts.toDouble() / minimumAttempts)
            if (recentWeightedAccuracy > 0.0) {
                add(mastery.recentWeightedAccuracy / recentWeightedAccuracy)
            }
            minimumRootCoverage?.let { add(mastery.rootsCovered.size.toDouble() / it) }
        }
        return fractions.minOrNull()?.coerceIn(0.0, 1.0) ?: 0.0
    }

    public companion object {
        public const val CHROMATIC_ROOTS: Int = 12
        public const val DEFAULT_MINIMUM_ATTEMPTS: Int = 12
        public const val DEFAULT_ACCURACY: Double = 0.9
        public const val DEFAULT_MAXIMUM_CRITICAL_ERRORS: Int = 1
    }
}

/** Why a gate is not passed yet. */
public sealed interface Unmet {
    public data object NoEvidence : Unmet

    public data class NotEnoughAttempts(val have: Int, val need: Int) : Unmet

    public data class AccuracyBelowBar(val have: Double, val need: Double) : Unmet

    public data class TooManyCriticalErrors(val have: Int, val allowed: Int) : Unmet

    public data class NotEnoughRoots(val have: Int, val need: Int) : Unmet

    public data class TooSlow(val medianMillis: Long, val limitMillis: Long) : Unmet
}

/** The verdict on one skill against one rule. */
public data class CompletionCheck(
    val passed: Boolean,
    val unmet: List<Unmet>,
    /** 0..1, for a progress bar. */
    val progress: Double,
)

/**
 * One competency, and how it is taught and tested.
 *
 * The authoring checklist in 21_CONTENT_AUTHORING_GUIDE.md §3, as a type. A gate answers "what
 * competency is being tested, what comes first, and how is mastery decided" — and deliberately
 * does not answer "what material may be generated", which is the exercise policy's job.
 */
public data class GateDefinition(
    val id: GateId,
    val title: String,
    /** One sentence, in the player's language, about what they will be able to do. */
    val objective: String,
    val skillIds: Set<SkillId>,
    val prerequisites: Set<GateId> = emptySet(),
    val exercisePolicyId: ExercisePolicyId,
    val completionRule: CompletionRule = CompletionRule(),
    val rewards: List<Unlock> = emptyList(),
    /** Shown before the first exercise. 02 §3 phase 1: "one concise explanation". */
    val preview: String? = null,
    /**
     * What to practise when a particular mistake keeps happening.
     *
     * 02 §6's recovery loop needs somewhere to send a player who keeps omitting the third, and
     * this is the mapping an author supplies for it. Phase 7 acts on it.
     */
    val remediation: Map<ErrorClass, ExercisePolicyId> = emptyMap(),
    /** Off the main path: reachable, but not required by anything. */
    val isChallenge: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Gate ${id.value} needs a title" }
        require(skillIds.isNotEmpty()) { "Gate ${id.value} tests no skills" }
        require(id !in prerequisites) { "Gate ${id.value} cannot require itself" }
    }
}

/** A themed group of gates. The map's regions in 02 §10. */
public data class CurriculumRegion(
    val id: RegionId,
    val title: String,
    val gates: List<GateDefinition>,
    /** Regions this one follows. Unlocked when every gate of every prerequisite region passes. */
    val prerequisites: Set<RegionId> = emptySet(),
    val summary: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Region ${id.value} needs a title" }
        require(gates.isNotEmpty()) { "Region ${id.value} has no gates" }
        require(id !in prerequisites) { "Region ${id.value} cannot require itself" }
    }
}

/**
 * The whole campaign.
 *
 * 02 §2: "Represent the campaign as a directed acyclic graph rather than a single linear level
 * number." Nothing here carries an order — position on the map is derived from prerequisites,
 * so inserting a gate is an edit to one file rather than a renumbering.
 */
public data class Curriculum(
    val schemaVersion: Int,
    val contentVersion: String,
    val regions: List<CurriculumRegion>,
) {
    public val gates: List<GateDefinition> = regions.flatMap { it.gates }

    private val gatesById: Map<GateId, GateDefinition> = gates.associateBy { it.id }

    private val regionOfGate: Map<GateId, RegionId> =
        regions.flatMap { region -> region.gates.map { it.id to region.id } }.toMap()

    public fun gate(id: GateId): GateDefinition? = gatesById[id]

    public fun region(id: RegionId): CurriculumRegion? = regions.firstOrNull { it.id == id }

    public fun regionOf(gateId: GateId): RegionId? = regionOfGate[gateId]

    /** Gates that name [id] as a prerequisite. */
    public fun dependentsOf(id: GateId): List<GateDefinition> = gates.filter { id in it.prerequisites }

    /** Every skill the campaign teaches. */
    public val skillIds: Set<SkillId> get() = gates.flatMap { it.skillIds }.toSet()

    /** Every exercise policy the campaign references. */
    public val policyIds: Set<ExercisePolicyId>
        get() = gates.flatMapTo(mutableSetOf()) { gate ->
            listOf(gate.exercisePolicyId) + gate.remediation.values
        }
}
