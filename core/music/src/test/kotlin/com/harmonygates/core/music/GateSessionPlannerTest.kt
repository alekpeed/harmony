package com.harmonygates.core.music

import com.harmonygates.core.music.campaign.CompletionRule
import com.harmonygates.core.music.campaign.GateDefinition
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.campaign.GateSessionPlanner
import com.harmonygates.core.music.campaign.StageKind
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.SkillId
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
 * How a gate session is shaped for the player in front of it.
 *
 * 02_GAME_LOOP_AND_PROGRESSION.md §3: "Do not force a player who has already demonstrated
 * mastery to repeat tutorial trials." That sentence is the reason this class exists, and it is
 * what most of these tests check.
 */
class GateSessionPlannerTest {

    private val skill = SkillId("skill.dom7.build")
    private val planner = GateSessionPlanner()
    private val updater = MasteryUpdater()
    private val now: Instant = Instant.parse("2026-03-01T09:00:00Z")

    private val allRoots = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
        .map { requireNotNull(SpelledPitchClass.parseOrNull(it)) }

    private val gate = GateDefinition(
        id = GateId("gate.dom7"),
        title = "Dominant sevenths",
        objective = "Build a dominant seventh on any root",
        skillIds = setOf(skill),
        exercisePolicyId = ExercisePolicyId("policy.dom7"),
        completionRule = CompletionRule(minimumAttempts = 12, minimumRootCoverage = 8),
    )

    private val policy = ExercisePolicy(
        id = ExercisePolicyId("policy.dom7"),
        skillIds = setOf(skill),
        rootPool = allRoots,
        formulaPool = listOf(ChordFormulas.DominantSeventh.id),
        sessionLength = 20,
    )

    private fun masteryOf(evidence: List<Evidence>, roots: List<String> = listOf("C")) =
        mapOf(
            skill to updater.replay(
                skill,
                evidence.mapIndexed { index, item ->
                    MasteryEvent(
                        skillId = skill,
                        evidence = item,
                        at = now,
                        root = SpelledPitchClass.parseOrNull(roots[index % roots.size]),
                    )
                },
            ),
        )

    @Test
    fun `a first visit begins with guided trials`() {
        val plan = planner.plan(gate, policy, emptyMap(), seed = 1, now = now)

        assertEquals(StageKind.GUIDED, plan.stages.first().kind)
        assertTrue(
            plan.stages.first().presentation.showKeyboardTargets,
            "A guided trial shows the player where the notes are",
        )
    }

    @Test
    fun `a competent player is not made to sit through the tutorial again`() {
        val plan = planner.plan(
            gate,
            policy,
            masteryOf(List(12) { Evidence.INDEPENDENT_CORRECT }),
            seed = 1,
            now = now,
        )

        assertFalse(
            plan.stages.any { it.kind == StageKind.GUIDED },
            "02 §3: do not force a player who has demonstrated mastery to repeat tutorial trials",
        )
    }

    @Test
    fun `a struggling player gets the tutorial back`() {
        val plan = planner.plan(
            gate,
            policy,
            masteryOf(List(8) { Evidence.INCORRECT }),
            seed = 1,
            now = now,
        )

        assertTrue(
            plan.stages.any { it.kind == StageKind.GUIDED },
            "Repeating the tutorial is the right answer for exactly one player: the stuck one",
        )
    }

    @Test
    fun `every session ends with a scored gate check`() {
        val states = listOf(
            emptyMap(),
            masteryOf(List(6) { Evidence.INCORRECT }),
            masteryOf(List(12) { Evidence.INDEPENDENT_CORRECT }),
        )

        states.forEach { mastery ->
            val plan = planner.plan(gate, policy, mastery, seed = 1, now = now)
            assertEquals(
                StageKind.GATE_CHECK,
                plan.stages.last().kind,
                "The scored run is what the completion rule is about, so it is always last",
            )
        }
    }

    @Test
    fun `a competent player gets challenge trials instead of tutorial ones`() {
        val plan = planner.plan(
            gate,
            policy,
            masteryOf(List(12) { Evidence.INDEPENDENT_CORRECT }),
            seed = 1,
            now = now,
        )

        assertTrue(plan.stages.any { it.kind == StageKind.CHALLENGE }, "${plan.stages}")
    }

    @Test
    fun `a plan is never empty and never silently short`() {
        val plan = planner.plan(gate, policy, emptyMap(), seed = 1, now = now)

        assertTrue(plan.totalExercises > 0)
        assertTrue(
            plan.totalExercises >= policy.sessionLength / 2,
            "A session of ${plan.totalExercises} against a policy asking for ${policy.sessionLength}",
        )
    }

    @Test
    fun `roots the player has never been asked for come first`() {
        val seen = listOf("C", "F", "G")
        val plan = planner.plan(
            gate,
            policy,
            masteryOf(List(9) { Evidence.INDEPENDENT_CORRECT }, roots = seen),
            seed = 7,
            now = now,
        )

        val firstFew = plan.rootOrder.take(allRoots.size - seen.size).map { it.toString() }
        assertTrue(
            firstFew.none { it in seen },
            "Coverage need should push untested roots to the front: ${plan.rootOrder}",
        )
    }

    @Test
    fun `the root order covers the whole pool without losing or repeating one`() {
        val plan = planner.plan(gate, policy, emptyMap(), seed = 3, now = now)

        assertEquals(allRoots.toSet(), plan.rootOrder.toSet())
        assertEquals(allRoots.size, plan.rootOrder.size, "No root appears twice")
    }

    @Test
    fun `the same seed and the same player produce the same session`() {
        val mastery = masteryOf(List(5) { Evidence.INDEPENDENT_CORRECT }, roots = listOf("C", "F"))

        assertEquals(
            planner.plan(gate, policy, mastery, seed = 42, now = now),
            planner.plan(gate, policy, mastery, seed = 42, now = now),
        )
    }

    @Test
    fun `different seeds vary the order without changing the priorities`() {
        val one = planner.plan(gate, policy, emptyMap(), seed = 1, now = now).rootOrder
        val two = planner.plan(gate, policy, emptyMap(), seed = 2, now = now).rootOrder

        assertEquals(one.toSet(), two.toSet(), "Both sessions still cover the same pool")
    }

    @Test
    fun `a stage policy carries the stage's assistance and length`() {
        val plan = planner.plan(gate, policy, emptyMap(), seed = 1, now = now)
        val guided = assertNotNull(plan.stages.firstOrNull { it.kind == StageKind.GUIDED })
        val stagePolicy = plan.policyFor(guided, policy)

        assertEquals(guided.exerciseCount, stagePolicy.sessionLength)
        assertTrue(stagePolicy.presentation.showSpelledNoteNames)
        assertEquals(plan.rootOrder, stagePolicy.rootPool, "The stage inherits the planned coverage")
    }

    @Test
    fun `a review visit checks rather than re-teaches`() {
        val practised = updater.replay(
            skill,
            List(12) { MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now) },
        )
        val due = assertNotNull(practised.nextReviewAt)
        val plan = planner.plan(gate, policy, mapOf(skill to practised), seed = 1, now = due)

        assertFalse(plan.stages.any { it.kind == StageKind.GUIDED }, "A review is not a lesson")
    }

    @Test
    fun `an untouched skill map is treated as a first visit`() {
        val blank = mapOf(skill to SkillMastery(skill))
        val plan = planner.plan(gate, policy, blank, seed = 1, now = now)

        assertEquals(StageKind.GUIDED, plan.stages.first().kind)
    }
}
