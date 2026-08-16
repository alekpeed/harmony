package com.harmonygates.core.music

import com.harmonygates.core.music.campaign.CampaignEvaluator
import com.harmonygates.core.music.campaign.CompletionRule
import com.harmonygates.core.music.campaign.ContentProblem
import com.harmonygates.core.music.campaign.Curriculum
import com.harmonygates.core.music.campaign.CurriculumRegion
import com.harmonygates.core.music.campaign.CurriculumValidator
import com.harmonygates.core.music.campaign.GateDefinition
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.campaign.GateStatus
import com.harmonygates.core.music.campaign.RegionId
import com.harmonygates.core.music.campaign.Unlock
import com.harmonygates.core.music.campaign.Unmet
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.mastery.Evidence
import com.harmonygates.core.music.mastery.MasteryEvent
import com.harmonygates.core.music.mastery.MasteryUpdater
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.pitch.SpelledPitchClass
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The campaign graph, its validator and its unlock rules.
 *
 * 15_IMPLEMENTATION_PHASES.md gives Phase 6 two acceptance criteria — "prerequisites/unlocks
 * deterministic" and "no unreachable/cyclic content" — and both are tested here directly.
 */
class CampaignTest {

    private val updater = MasteryUpdater()
    private val now: Instant = Instant.parse("2026-02-01T12:00:00Z")

    private fun gate(
        id: String,
        skills: Set<String> = setOf("skill.$id"),
        prerequisites: Set<String> = emptySet(),
        rule: CompletionRule = CompletionRule(minimumAttempts = 2, recentWeightedAccuracy = 0.9),
        rewards: List<Unlock> = emptyList(),
    ) = GateDefinition(
        id = GateId(id),
        title = id,
        objective = "Play $id",
        skillIds = skills.map { SkillId(it) }.toSet(),
        prerequisites = prerequisites.map { GateId(it) }.toSet(),
        exercisePolicyId = ExercisePolicyId("policy.$id"),
        completionRule = rule,
        rewards = rewards,
    )

    private fun curriculum(vararg gates: GateDefinition, regionId: String = "region.one") = Curriculum(
        schemaVersion = 1,
        contentVersion = "test",
        regions = listOf(
            CurriculumRegion(RegionId(regionId), "Region", gates.toList()),
        ),
    )

    /** Mastery good enough to pass the default test rule. */
    private fun mastered(vararg skills: String): Map<SkillId, SkillMastery> = skills.associate { name ->
        val skill = SkillId(name)
        skill to updater.replay(
            skill,
            List(4) {
                MasteryEvent(
                    skillId = skill,
                    evidence = Evidence.INDEPENDENT_CORRECT,
                    at = now,
                    root = SpelledPitchClass.parseOrNull(listOf("C", "F", "G", "D")[it]),
                )
            },
        )
    }

    // --- Acceptance: no unreachable or cyclic content ------------------------------------------

    @Test
    fun `a well-formed curriculum validates`() {
        val result = CurriculumValidator().validate(
            curriculum(
                gate("triads"),
                gate("sevenths", prerequisites = setOf("triads")),
                gate("inversions", prerequisites = setOf("sevenths")),
            ),
        )

        assertTrue(result.isValid, result.report())
        assertEquals(emptyList(), result.fatal)
    }

    @Test
    fun `a prerequisite cycle is caught and named`() {
        val result = CurriculumValidator().validate(
            curriculum(
                gate("a", prerequisites = setOf("c")),
                gate("b", prerequisites = setOf("a")),
                gate("c", prerequisites = setOf("b")),
            ),
        )

        assertFalse(result.isValid)
        val cycle = assertNotNull(
            result.fatal.filterIsInstance<ContentProblem.PrerequisiteCycle>().firstOrNull(),
            "Expected a cycle in ${result.report()}",
        )
        assertEquals(setOf("a", "b", "c"), cycle.cycle.toSet())
    }

    @Test
    fun `a two-gate deadlock is a cycle`() {
        val result = CurriculumValidator().validate(
            curriculum(
                gate("left", prerequisites = setOf("right")),
                gate("right", prerequisites = setOf("left")),
            ),
        )

        assertTrue(result.fatal.any { it is ContentProblem.PrerequisiteCycle }, result.report())
    }

    @Test
    fun `a curriculum where everything has a prerequisite has no way in`() {
        val result = CurriculumValidator().validate(
            curriculum(
                gate("a", prerequisites = setOf("b")),
                gate("b", prerequisites = setOf("a")),
            ),
        )

        assertFalse(result.isValid, "A player could never start this campaign")
    }

    @Test
    fun `a prerequisite that does not exist is a dangling reference`() {
        val result = CurriculumValidator().validate(
            curriculum(gate("start"), gate("second", prerequisites = setOf("typo"))),
        )

        val dangling = result.fatal.filterIsInstance<ContentProblem.DanglingReference>()
        assertEquals(1, dangling.size, result.report())
        assertEquals("typo", dangling.first().to)
    }

    @Test
    fun `an exercise policy that does not exist is a dangling reference`() {
        val validator = CurriculumValidator(knownPolicyIds = setOf(ExercisePolicyId("policy.start")))
        val result = validator.validate(curriculum(gate("start"), gate("other")))

        assertTrue(
            result.fatal.any { it is ContentProblem.DanglingReference && it.to == "policy.other" },
            result.report(),
        )
    }

    @Test
    fun `an unlock pointing at a missing region is a dangling reference`() {
        val result = CurriculumValidator().validate(
            curriculum(gate("start", rewards = listOf(Unlock.Region(RegionId("region.ghost"))))),
        )

        assertTrue(
            result.fatal.any { it is ContentProblem.DanglingReference && it.to == "region.ghost" },
            result.report(),
        )
    }

    @Test
    fun `two gates cannot share an id`() {
        val result = CurriculumValidator().validate(curriculum(gate("same"), gate("same")))

        assertTrue(result.fatal.any { it is ContentProblem.DuplicateId }, result.report())
    }

    @Test
    fun `a gate testing everything at once is flagged but not fatal`() {
        val result = CurriculumValidator().validate(
            curriculum(gate("everything", skills = (1..9).map { "skill.$it" }.toSet())),
        )

        assertTrue(result.isValid, "Overreach is a warning, not a broken campaign")
        assertTrue(result.warnings.any { it is ContentProblem.Suspicious }, result.report())
    }

    @Test
    fun `requireValid throws on unplayable content and returns valid content`() {
        val good = curriculum(gate("start"))
        assertEquals(good, CurriculumValidator().requireValid(good))

        val bad = curriculum(gate("a", prerequisites = setOf("b")), gate("b", prerequisites = setOf("a")))
        val failure = runCatching { CurriculumValidator().requireValid(bad) }.exceptionOrNull()
        assertNotNull(failure, "An unplayable campaign must not be silently accepted")
    }

    // --- Acceptance: prerequisites and unlocks are deterministic --------------------------------

    @Test
    fun `a new player sees the first gate open and the rest locked`() {
        val state = CampaignEvaluator().evaluate(
            curriculum(gate("triads"), gate("sevenths", prerequisites = setOf("triads"))),
            mastery = emptyMap(),
        )

        assertEquals(GateStatus.AVAILABLE, assertNotNull(state.gate(GateId("triads"))).status)
        assertEquals(GateStatus.LOCKED, assertNotNull(state.gate(GateId("sevenths"))).status)
    }

    @Test
    fun `passing a gate opens the one behind it`() {
        val state = CampaignEvaluator().evaluate(
            curriculum(gate("triads"), gate("sevenths", prerequisites = setOf("triads"))),
            mastery = mastered("skill.triads"),
        )

        assertEquals(GateStatus.COMPLETE, assertNotNull(state.gate(GateId("triads"))).status)
        assertEquals(GateStatus.AVAILABLE, assertNotNull(state.gate(GateId("sevenths"))).status)
    }

    @Test
    fun `the same evidence always produces the same map`() {
        val content = curriculum(
            gate("triads"),
            gate("sevenths", prerequisites = setOf("triads")),
            gate("inversions", prerequisites = setOf("sevenths")),
        )
        val mastery = mastered("skill.triads", "skill.sevenths")
        val evaluator = CampaignEvaluator()

        assertEquals(evaluator.evaluate(content, mastery), evaluator.evaluate(content, mastery))
    }

    @Test
    fun `an attempted but unfinished gate reads as in progress`() {
        val skill = SkillId("skill.triads")
        val partial = mapOf(
            skill to updater.apply(
                SkillMastery(skill),
                MasteryEvent(skill, Evidence.INCORRECT, now),
            ),
        )
        val state = CampaignEvaluator().evaluate(curriculum(gate("triads")), partial)

        assertEquals(GateStatus.IN_PROGRESS, assertNotNull(state.gate(GateId("triads"))).status)
    }

    @Test
    fun `a gate testing two skills needs both of them`() {
        val content = curriculum(gate("both", skills = setOf("skill.one", "skill.two")))
        val half = CampaignEvaluator().evaluate(content, mastered("skill.one"))
        val whole = CampaignEvaluator().evaluate(content, mastered("skill.one", "skill.two"))

        assertEquals(GateStatus.IN_PROGRESS, assertNotNull(half.gate(GateId("both"))).status)
        assertEquals(GateStatus.COMPLETE, assertNotNull(whole.gate(GateId("both"))).status)
    }

    @Test
    fun `rewards arrive only when the gate that grants them is passed`() {
        val reward = Unlock.VoicingFamily("ROOTLESS_A")
        val content = curriculum(gate("rootless", rewards = listOf(reward)))

        assertEquals(emptySet(), CampaignEvaluator().evaluate(content, emptyMap()).unlocked)
        assertEquals(setOf(reward), CampaignEvaluator().evaluate(content, mastered("skill.rootless")).unlocked)
    }

    @Test
    fun `progress reports the weakest skill, not the average`() {
        val content = curriculum(
            gate("both", skills = setOf("skill.one", "skill.two"), rule = CompletionRule(minimumAttempts = 4)),
        )
        val progress = assertNotNull(
            CampaignEvaluator().evaluate(content, mastered("skill.one")).gate(GateId("both")),
        ).progress

        assertEquals(0.0, progress, "One skill untouched means the gate has not started")
    }

    // --- Completion rules ----------------------------------------------------------------------

    @Test
    fun `accuracy alone is not mastery`() {
        val skill = SkillId("skill.lucky")
        val lucky = updater.replay(
            skill,
            List(2) { MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now) },
        )
        val check = CompletionRule(minimumAttempts = 12).evaluate(lucky)

        assertFalse(check.passed)
        assertTrue(check.unmet.any { it is Unmet.NotEnoughAttempts }, "${check.unmet}")
    }

    @Test
    fun `a gate can require root coverage`() {
        val skill = SkillId("skill.roots")
        val narrow = updater.replay(
            skill,
            List(12) {
                MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now, root = SpelledPitchClass.parseOrNull("C"))
            },
        )
        val check = CompletionRule(minimumAttempts = 12, minimumRootCoverage = 8).evaluate(narrow)

        assertFalse(check.passed, "Twelve correct answers on one root is not twelve keys")
        assertTrue(check.unmet.any { it is Unmet.NotEnoughRoots })
    }

    @Test
    fun `repeated theory errors hold a gate shut even at a high accuracy`() {
        val skill = SkillId("skill.errors")
        val events = List(11) { MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now) } +
            List(3) {
                MasteryEvent(skill, Evidence.INCORRECT, now, errors = listOf(ErrorClass.WRONG_QUALITY))
            }
        val check = CompletionRule(
            minimumAttempts = 8,
            recentWeightedAccuracy = 0.0,
            maximumCriticalErrors = 1,
        ).evaluate(updater.replay(skill, events))

        assertFalse(check.passed)
        assertTrue(check.unmet.any { it is Unmet.TooManyCriticalErrors }, "${check.unmet}")
    }

    @Test
    fun `a gate reports everything still missing, not just the first thing`() {
        val check = CompletionRule(minimumAttempts = 12, minimumRootCoverage = 8).evaluate(null)

        assertEquals(listOf(Unmet.NoEvidence), check.unmet, "With no evidence there is one honest answer")
        val partial = CompletionRule(minimumAttempts = 12, minimumRootCoverage = 8)
            .evaluate(SkillMastery(SkillId("skill.new")))
        assertTrue(partial.unmet.size >= 2, "A fresh skill is short on attempts and on roots: ${partial.unmet}")
    }

    @Test
    fun `next gate is the one the player is furthest through`() {
        val content = curriculum(
            gate("triads"),
            gate("sevenths", prerequisites = setOf("triads")),
        )
        val state = CampaignEvaluator().evaluate(content, mastered("skill.triads"))

        assertEquals(GateId("sevenths"), assertNotNull(state.nextGate).id)
    }
}
