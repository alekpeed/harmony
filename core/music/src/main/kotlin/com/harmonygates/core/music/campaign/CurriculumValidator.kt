package com.harmonygates.core.music.campaign

import com.harmonygates.core.music.exercise.ExercisePolicyId

/** Something wrong with authored content. */
public sealed interface ContentProblem {
    /** True for problems that make the campaign unplayable rather than merely untidy. */
    public val isFatal: Boolean

    public val message: String

    /** Two gates, regions or policies share an id. */
    public data class DuplicateId(val id: String, val kind: String) : ContentProblem {
        override val isFatal: Boolean get() = true
        override val message: String get() = "Two ${kind}s share the id '$id'"
    }

    /** A prerequisite, policy or unlock names something that does not exist. */
    public data class DanglingReference(
        val from: String,
        val to: String,
        val kind: String,
    ) : ContentProblem {
        override val isFatal: Boolean get() = true
        override val message: String get() = "'$from' references the $kind '$to', which does not exist"
    }

    /** Prerequisites form a loop, so none of the gates in it can ever open. */
    public data class PrerequisiteCycle(val cycle: List<String>) : ContentProblem {
        override val isFatal: Boolean get() = true
        override val message: String get() = "Prerequisite cycle: ${cycle.joinToString(" -> ")}"
    }

    /** A gate whose prerequisites can never all be satisfied, so a player can never reach it. */
    public data class Unreachable(val gateId: String, val why: String) : ContentProblem {
        override val isFatal: Boolean get() = true
        override val message: String get() = "Gate '$gateId' can never be reached: $why"
    }

    /** The campaign has no starting point. */
    public data object NoEntryPoint : ContentProblem {
        override val isFatal: Boolean get() = true
        override val message: String get() = "No gate is available at the start: every gate has prerequisites"
    }

    /** Authored, valid, and probably not what the author meant. */
    public data class Suspicious(val what: String, override val message: String) : ContentProblem {
        override val isFatal: Boolean get() = false
    }
}

/** What the validator found. */
public data class ValidationResult(val problems: List<ContentProblem>) {
    public val isValid: Boolean get() = problems.none { it.isFatal }

    public val fatal: List<ContentProblem> get() = problems.filter { it.isFatal }

    public val warnings: List<ContentProblem> get() = problems.filterNot { it.isFatal }

    /** A report suitable for a build failure. */
    public fun report(): String = buildString {
        if (problems.isEmpty()) {
            append("Curriculum is valid.")
            return@buildString
        }
        fatal.forEach { appendLine("  ERROR   ${it.message}") }
        warnings.forEach { appendLine("  warning ${it.message}") }
    }.trimEnd()
}

/**
 * Checks that a curriculum is playable before a player finds out that it is not.
 *
 * 15_IMPLEMENTATION_PHASES.md makes this an acceptance criterion of Phase 6 — "no
 * unreachable/cyclic content" — and 21_CONTENT_AUTHORING_GUIDE.md §9 asks for it to run in CI
 * and "fail on invalid references or impossible content". A cycle of two gates each waiting for
 * the other is invisible in a JSON file and obvious to a player who can never start.
 *
 * @param knownPolicyIds the exercise policies that exist. An empty set skips the policy check,
 *   for callers validating a curriculum in isolation.
 */
public class CurriculumValidator(
    private val knownPolicyIds: Set<ExercisePolicyId> = emptySet(),
) {

    public fun validate(curriculum: Curriculum): ValidationResult {
        val problems = mutableListOf<ContentProblem>()
        problems += duplicateIds(curriculum)
        problems += danglingReferences(curriculum)

        // Cycles are found before reachability on purpose: a cyclic graph has no topological
        // order, and asking "can this be reached" of one would not terminate cleanly.
        val cycles = prerequisiteCycles(curriculum)
        problems += cycles
        if (cycles.isEmpty()) {
            problems += reachability(curriculum)
        }
        problems += suspicious(curriculum)
        return ValidationResult(problems)
    }

    /** Throws on fatal problems. For content that a build has already accepted. */
    public fun requireValid(curriculum: Curriculum): Curriculum {
        val result = validate(curriculum)
        check(result.isValid) { "Curriculum ${curriculum.contentVersion} is unplayable:\n${result.report()}" }
        return curriculum
    }

    private fun duplicateIds(curriculum: Curriculum): List<ContentProblem> {
        val gateDuplicates = curriculum.gates.groupingBy { it.id.value }.eachCount()
            .filterValues { it > 1 }.keys
            .map { ContentProblem.DuplicateId(it, "gate") }
        val regionDuplicates = curriculum.regions.groupingBy { it.id.value }.eachCount()
            .filterValues { it > 1 }.keys
            .map { ContentProblem.DuplicateId(it, "region") }
        return gateDuplicates + regionDuplicates
    }

    private fun danglingReferences(curriculum: Curriculum): List<ContentProblem> {
        val gateIds = curriculum.gates.map { it.id }.toSet()
        val regionIds = curriculum.regions.map { it.id }.toSet()
        val problems = mutableListOf<ContentProblem>()

        curriculum.gates.forEach { gate ->
            gate.prerequisites.filterNot { it in gateIds }.forEach {
                problems += ContentProblem.DanglingReference(gate.id.value, it.value, "gate")
            }
            if (knownPolicyIds.isNotEmpty() && gate.exercisePolicyId !in knownPolicyIds) {
                problems += ContentProblem.DanglingReference(
                    gate.id.value,
                    gate.exercisePolicyId.value,
                    "exercise policy",
                )
            }
            gate.remediation.values.filter { knownPolicyIds.isNotEmpty() && it !in knownPolicyIds }
                .forEach {
                    problems += ContentProblem.DanglingReference(
                        gate.id.value,
                        it.value,
                        "remediation policy",
                    )
                }
            gate.rewards.forEach { unlock ->
                val dangling = when (unlock) {
                    is Unlock.Region -> unlock.regionId.value.takeIf { unlock.regionId !in regionIds }
                        ?.let { it to "region" }

                    is Unlock.ChallengeGate -> unlock.gateId.value.takeIf { unlock.gateId !in gateIds }
                        ?.let { it to "gate" }

                    is Unlock.PracticePreset ->
                        unlock.policyId.value
                            .takeIf { knownPolicyIds.isNotEmpty() && unlock.policyId !in knownPolicyIds }
                            ?.let { it to "exercise policy" }

                    is Unlock.VoicingFamily, is Unlock.Instrument -> null
                }
                dangling?.let { (target, kind) ->
                    problems += ContentProblem.DanglingReference(gate.id.value, target, kind)
                }
            }
        }

        curriculum.regions.forEach { region ->
            region.prerequisites.filterNot { it in regionIds }.forEach {
                problems += ContentProblem.DanglingReference(region.id.value, it.value, "region")
            }
        }
        return problems
    }

    /**
     * Finds prerequisite loops.
     *
     * Iterative depth-first search with an explicit stack rather than recursion: authored content
     * is not adversarial, but a stack overflow inside a content validator would be a confusing way
     * to learn that a file has a mistake in it.
     */
    private fun prerequisiteCycles(curriculum: Curriculum): List<ContentProblem> {
        val edges = curriculum.gates.associate { gate ->
            gate.id to gate.prerequisites.filter { curriculum.gate(it) != null }
        }
        val colour = mutableMapOf<GateId, Colour>()
        val cycles = mutableListOf<ContentProblem>()

        for (start in edges.keys) {
            if (colour[start] != null) continue
            val path = mutableListOf<GateId>()
            val stack = ArrayDeque<Step>()
            stack.addLast(Step(start, edges.getValue(start).iterator()))
            colour[start] = Colour.OPEN
            path += start

            while (stack.isNotEmpty()) {
                val step = stack.last()
                if (step.remaining.hasNext()) {
                    val next = step.remaining.next()
                    when (colour[next]) {
                        Colour.OPEN -> {
                            // Back edge: everything from `next` onwards in the current path is
                            // the loop, reported in the order an author would read it.
                            val loop = path.dropWhile { it != next }.map { it.value } + next.value
                            cycles += ContentProblem.PrerequisiteCycle(loop)
                        }

                        Colour.CLOSED -> Unit
                        null -> {
                            colour[next] = Colour.OPEN
                            path += next
                            stack.addLast(Step(next, edges.getValue(next).iterator()))
                        }
                    }
                } else {
                    colour[step.gate] = Colour.CLOSED
                    path.removeAt(path.lastIndex)
                    stack.removeLast()
                }
            }
        }
        return cycles.distinctBy { (it as ContentProblem.PrerequisiteCycle).cycle.toSet() }
    }

    private enum class Colour { OPEN, CLOSED }

    private class Step(val gate: GateId, val remaining: Iterator<GateId>)

    /**
     * Every gate must be reachable from the start by passing gates one at a time.
     *
     * Modelled as the obvious simulation rather than a graph theorem: start with the gates that
     * have no prerequisites, then repeatedly open anything whose prerequisites are all open. What
     * is left over is unreachable, which is exactly what a player would discover.
     */
    private fun reachability(curriculum: Curriculum): List<ContentProblem> {
        val all = curriculum.gates
        if (all.isEmpty()) return emptyList()

        val opened = mutableSetOf<GateId>()
        var progressed = true
        while (progressed) {
            progressed = false
            all.filterNot { it.id in opened }
                .filter { gate -> gate.prerequisites.all { it in opened } }
                .forEach {
                    opened += it.id
                    progressed = true
                }
        }

        if (opened.isEmpty()) return listOf(ContentProblem.NoEntryPoint)
        return all.filterNot { it.id in opened }.map { gate ->
            val blocking = gate.prerequisites.filterNot { it in opened }.joinToString { it.value }
            ContentProblem.Unreachable(gate.id.value, "blocked by $blocking")
        }
    }

    /** Valid content that an author probably did not mean to write. */
    private fun suspicious(curriculum: Curriculum): List<ContentProblem> {
        val problems = mutableListOf<ContentProblem>()

        curriculum.gates.filter { it.skillIds.size > MANY_SKILLS }.forEach { gate ->
            problems += ContentProblem.Suspicious(
                gate.id.value,
                "Gate '${gate.id.value}' tests ${gate.skillIds.size} skills; a gate that tests " +
                    "everything cannot tell the player what they got wrong",
            )
        }

        // A skill taught by no gate is unreachable in a different way: nothing will ever build
        // evidence for it, so anything gating on it waits forever.
        val taught = curriculum.skillIds
        curriculum.gates.flatMap { it.skillIds }.distinct().filterNot { it in taught }.forEach {
            problems += ContentProblem.Suspicious(it.value, "Skill '${it.value}' is never practised")
        }

        curriculum.regions.forEach { region ->
            if (region.gates.none { it.prerequisites.isEmpty() } && region.prerequisites.isEmpty()) {
                problems += ContentProblem.Suspicious(
                    region.id.value,
                    "Region '${region.id.value}' has no prerequisites but no opening gate either",
                )
            }
        }
        return problems
    }

    private companion object {
        const val MANY_SKILLS = 6
    }
}
