package com.harmonygates.core.music.campaign

import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.SkillMastery
import java.time.Instant

/** Where a gate stands for one player. */
public enum class GateStatus {
    /** Prerequisites are not met. Visible on the map, not yet playable. */
    LOCKED,

    /** Playable, never attempted. */
    AVAILABLE,

    /** Attempted, not yet passed. */
    IN_PROGRESS,

    /** Passed. */
    COMPLETE,
    ;

    public val isPlayable: Boolean get() = this != LOCKED
}

/** A gate, resolved against what the player has actually done. */
public data class GateProgress(
    val gate: GateDefinition,
    val status: GateStatus,
    /** 0..1 across every skill the gate tests. */
    val progress: Double,
    /** What is still missing, per skill. Empty once the gate is complete. */
    val unmetBySkill: Map<SkillId, List<Unmet>>,
    val completedAt: Instant? = null,
) {
    public val id: GateId get() = gate.id

    /** Everything still standing between the player and a pass, worst first. */
    public val remaining: List<Unmet>
        get() = unmetBySkill.values.flatten().distinct()
}

/** The map as a player sees it. */
public data class CampaignState(
    val gates: List<GateProgress>,
    val unlocked: Set<Unlock>,
    val visibleRegions: Set<RegionId>,
) {
    public fun gate(id: GateId): GateProgress? = gates.firstOrNull { it.id == id }

    public val available: List<GateProgress> get() = gates.filter { it.status == GateStatus.AVAILABLE }

    public val completed: List<GateProgress> get() = gates.filter { it.status == GateStatus.COMPLETE }

    /**
     * What to offer the player next.
     *
     * The gate furthest along that is not finished, or the first available one if nothing has
     * been started. Deterministic on ties — content order — so "continue" does not move around
     * between launches.
     */
    public val nextGate: GateProgress?
        get() = gates.filter { it.status == GateStatus.IN_PROGRESS }.maxByOrNull { it.progress }
            ?: gates.firstOrNull { it.status == GateStatus.AVAILABLE }
}

/**
 * Turns mastery into a map.
 *
 * 15_IMPLEMENTATION_PHASES.md requires that "prerequisites/unlocks [are] deterministic", so this
 * is a pure function of the curriculum, the mastery evidence and the recorded completion times.
 * There is no stored "unlocked" flag that could drift out of step with the evidence behind it —
 * a gate is complete because the evidence says so, and recomputing always agrees.
 */
public class CampaignEvaluator {

    /**
     * @param completions when each gate was first passed. Only used for display; whether a gate
     *   counts as complete is decided by the evidence, not by this map.
     */
    public fun evaluate(
        curriculum: Curriculum,
        mastery: Map<SkillId, SkillMastery>,
        completions: Map<GateId, Instant> = emptyMap(),
    ): CampaignState {
        val checks = curriculum.gates.associate { gate -> gate.id to checksFor(gate, mastery) }
        val passed = checks.filterValues { it.values.all(CompletionCheck::passed) }.keys

        val progress = curriculum.gates.map { gate ->
            val gateChecks = checks.getValue(gate.id)
            val complete = gate.id in passed
            val open = gate.prerequisites.all { it in passed }
            val attempted = gate.skillIds.any { (mastery[it]?.attempts ?: 0) > 0 }

            GateProgress(
                gate = gate,
                status = when {
                    complete -> GateStatus.COMPLETE
                    !open -> GateStatus.LOCKED
                    attempted -> GateStatus.IN_PROGRESS
                    else -> GateStatus.AVAILABLE
                },
                // The weakest skill, not the average: a gate is as finished as its worst part.
                progress = gateChecks.values.minOfOrNull { it.progress } ?: 0.0,
                unmetBySkill = gateChecks.filterValues { it.unmet.isNotEmpty() }
                    .mapValues { (_, check) -> check.unmet },
                completedAt = completions[gate.id].takeIf { complete },
            )
        }

        val unlocked = curriculum.gates.filter { it.id in passed }.flatMap { it.rewards }.toSet()
        return CampaignState(
            gates = progress,
            unlocked = unlocked,
            visibleRegions = visibleRegions(curriculum, passed, unlocked),
        )
    }

    private fun checksFor(
        gate: GateDefinition,
        mastery: Map<SkillId, SkillMastery>,
    ): Map<SkillId, CompletionCheck> = gate.skillIds.associateWith { skillId ->
        gate.completionRule.evaluate(mastery[skillId])
    }

    /**
     * Which regions the player can see.
     *
     * A region is visible when its own prerequisite regions are finished, or when some gate has
     * explicitly unlocked it. The first region is always visible, otherwise a new player opens
     * the app to an empty map.
     */
    private fun visibleRegions(
        curriculum: Curriculum,
        passed: Set<GateId>,
        unlocked: Set<Unlock>,
    ): Set<RegionId> {
        val explicit = unlocked.filterIsInstance<Unlock.Region>().map { it.regionId }.toSet()
        val finished = curriculum.regions
            .filter { region -> region.gates.all { it.id in passed } }
            .map { it.id }
            .toSet()

        val visible = mutableSetOf<RegionId>()
        curriculum.regions.firstOrNull()?.let { visible += it.id }
        visible += explicit
        curriculum.regions.filter { it.prerequisites.isNotEmpty() && it.prerequisites.all { p -> p in finished } }
            .forEach { visible += it.id }
        curriculum.regions.filter { it.prerequisites.isEmpty() }.forEach { visible += it.id }
        return visible
    }
}
