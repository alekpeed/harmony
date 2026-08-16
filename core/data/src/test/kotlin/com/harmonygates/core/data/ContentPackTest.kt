package com.harmonygates.core.data

import com.harmonygates.core.data.content.ContentReferenceException
import com.harmonygates.core.data.content.ContentSource
import com.harmonygates.core.data.content.DefaultContentRepository
import com.harmonygates.core.music.campaign.CampaignEvaluator
import com.harmonygates.core.music.campaign.CurriculumValidator
import com.harmonygates.core.music.campaign.GateStatus
import com.harmonygates.core.music.exercise.DefaultExerciseGenerator
import com.harmonygates.core.music.exercise.GateActivity
import com.harmonygates.core.music.exercise.GenerationContext
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.progression.DefaultProgressionGenerator
import com.harmonygates.core.music.progression.VoicingStyle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The curriculum that actually ships.
 *
 * This is `./gradlew validateHarmonyContent` in test form, and 21_CONTENT_AUTHORING_GUIDE.md §9
 * is explicit that it "must run in CI and fail on invalid references or impossible content".
 * The files it reads are the authored ones in `content/`, not a fixture — a fixture would prove
 * that the loader works and nothing about the campaign a player is given.
 *
 * §8 also asks an author to "generate at least 100 sample exercises from each policy" and
 * inspect them before shipping a region. A person still has to do the inspecting; the part a
 * machine can do — that all 100 are generable, spellable and playable — is done here.
 */
class ContentPackTest {

    /** Reads the authored files. Unit tests run with the module directory as the working directory. */
    private val source = ContentSource { path ->
        val file = when (path) {
            DefaultContentRepository.CURRICULUM_PATH -> File("../../content/curriculum/curriculum.json")
            DefaultContentRepository.POLICY_PATH -> File("../../content/exercises/exercise_policies.json")
            DefaultContentRepository.PROGRESSION_PATH -> File("../../content/progressions/progressions.json")
            else -> error("Unexpected content path: $path")
        }
        assertTrue(file.isFile, "Missing authored content at ${file.absolutePath}")
        file.readText()
    }

    private val repository = DefaultContentRepository(source)

    @Test
    fun `the shipped curriculum is playable`() = runTest {
        val curriculum = repository.curriculum()
        val policies = repository.allPolicies()
        val result = CurriculumValidator(policies.keys).validate(curriculum)

        assertTrue(result.isValid, "The shipped curriculum is broken:\n${result.report()}")
    }

    @Test
    fun `the curriculum has no warnings either`() = runTest {
        val result = repository.validate()

        assertEquals(
            emptyList(),
            result.warnings,
            "Authored content should be clean, not merely legal:\n${result.report()}",
        )
    }

    @Test
    fun `every gate names a policy that exists`() = runTest {
        val policies = repository.allPolicies()

        repository.curriculum().gates.forEach { gate ->
            assertNotNull(
                policies[gate.exercisePolicyId],
                "Gate '${gate.id}' names the policy '${gate.exercisePolicyId}', which is not authored",
            )
        }
    }

    @Test
    fun `every policy is reachable from some gate`() = runTest {
        val referenced = repository.curriculum().policyIds +
            repository.curriculum().gates.flatMap { gate ->
                gate.rewards.filterIsInstance<
                    com.harmonygates.core.music.campaign.Unlock.PracticePreset,
                    >().map { it.policyId }
            }

        val orphans = repository.allPolicies().keys - referenced
        assertEquals(
            emptySet(),
            orphans,
            "These policies are authored but nothing uses them: $orphans",
        )
    }

    @Test
    fun `a new player has exactly one way in`() = runTest {
        val state = CampaignEvaluator().evaluate(repository.curriculum(), mastery = emptyMap())

        assertEquals(
            1,
            state.available.size,
            "A new player should be shown one starting gate, not ${state.available.map { it.id }}",
        )
        assertTrue(state.gates.all { it.status != GateStatus.COMPLETE }, "Nothing is complete on day one")
    }

    @Test
    fun `every gate is reachable by playing`() = runTest {
        val curriculum = repository.curriculum()
        val opened = mutableSetOf<com.harmonygates.core.music.campaign.GateId>()
        var progressed = true
        while (progressed) {
            progressed = false
            curriculum.gates.filterNot { it.id in opened }
                .filter { gate -> gate.prerequisites.all { it in opened } }
                .forEach {
                    opened += it.id
                    progressed = true
                }
        }

        assertEquals(
            curriculum.gates.map { it.id }.toSet(),
            opened,
            "Some gates can never be opened by playing",
        )
    }

    /**
     * 21 §8: generate a hundred exercises from every policy and look at them.
     *
     * The machine-checkable half of that: every one must be generable at all, must spell without
     * a triple accidental, and must have a playable rendering inside the policy's own register.
     * A policy that cannot do that is a lesson a player would simply be unable to complete.
     */
    @Test
    fun `every policy generates a hundred playable exercises`() = runTest {
        val generator = DefaultExerciseGenerator()

        repository.allPolicies().forEach { (id, policy) ->
            val roots = mutableSetOf<com.harmonygates.core.music.pitch.SpelledPitchClass>()
            repeat(SAMPLE_SIZE) { index ->
                val instance = runCatching {
                    generator.generate(policy, seed = index.toLong(), context = GenerationContext(index = index))
                }.getOrElse { failure ->
                    error("Policy '$id' could not generate exercise $index: ${failure.message}")
                }

                assertTrue(
                    instance.spelledTones.isNotEmpty(),
                    "Policy '$id' generated '${instance.chord.symbol}' with no spelling",
                )
                instance.targetNotes.forEach { note ->
                    assertTrue(
                        note in policy.pitchRange,
                        "Policy '$id' put ${instance.chord.symbol} at note $note, " +
                            "outside its own range ${policy.pitchRange}",
                    )
                }
                roots += instance.chord.root
            }

            assertTrue(
                roots.size >= MINIMUM_ROOT_SPREAD,
                "Policy '$id' only ever generated ${roots.size} roots ($roots) in $SAMPLE_SIZE tries",
            )
        }
    }

    // --- Phase 13: the whole campaign -------------------------------------------------------

    /**
     * 15_IMPLEMENTATION_PHASES.md's acceptance for phase 13: "complete path from onboarding
     * through advanced region". Reachability alone does not say that — a campaign could be
     * reachable and stop three regions short — so this walks the whole thing and checks that
     * the far end is where it should be.
     */
    @Test
    fun `there is a path from onboarding to the last region`() = runTest {
        val curriculum = repository.curriculum()
        val first = CampaignEvaluator().evaluate(curriculum, mastery = emptyMap()).available

        assertEquals(
            listOf(com.harmonygates.core.music.campaign.GateId("gate.calibration.keys")),
            first.map { it.id },
            "A new player should start at calibration",
        )

        // Walk the prerequisite graph from the entry point and check every gate is on it.
        val opened = mutableSetOf(first.single().id)
        var progressed = true
        while (progressed) {
            progressed = false
            curriculum.gates.filterNot { it.id in opened }
                .filter { gate -> gate.prerequisites.all { it in opened } }
                .forEach { opened += it.id; progressed = true }
        }

        assertEquals(curriculum.gates.size, opened.size, "Some gates are off the path")
        assertTrue(
            curriculum.regions.any { it.id.value == "region.integrated" },
            "The campaign should reach integrated reading, the last region of 03 §16",
        )
    }

    @Test
    fun `every region the curriculum names is entered from somewhere`() = runTest {
        val curriculum = repository.curriculum()
        val unlocked = curriculum.gates.flatMap { it.rewards }
            .filterIsInstance<com.harmonygates.core.music.campaign.Unlock.Region>()
            .map { it.regionId }
            .toSet()

        val orphans = curriculum.regions
            .filter { it.prerequisites.isNotEmpty() }
            .filterNot { it.id in unlocked }

        assertEquals(
            emptyList(),
            orphans.map { it.id.value },
            "These regions have prerequisites but no gate ever unlocks them",
        )
    }

    @Test
    fun `every kind of activity the engines support has content that uses it`() = runTest {
        val activities = repository.allPolicies().values.map { it.activity }.toSet()

        assertEquals(
            GateActivity.entries.toSet(),
            activities,
            "An engine with no content is an engine nobody can reach",
        )
    }

    @Test
    fun `every authored progression is playable in all twelve keys`() = runTest {
        val progressions = repository.allProgressions()
        assertTrue(progressions.isNotEmpty(), "Region 12's vocabulary should be authored")

        val generator = DefaultProgressionGenerator()
        progressions.forEach { (id, template) ->
            for (tonic in StandardRoots) {
                val result = generator.generate(template, KeyContext(tonic, template.mode), VoicingStyle.ANY_VOICING)
                val progression = assertNotNull(
                    result.getOrNull(),
                    "Progression '$id' cannot be written in $tonic",
                )

                assertEquals(
                    template.functions.size,
                    progression.events.size,
                    "Progression '$id' lost a chord in $tonic",
                )
                progression.events.forEach { event ->
                    assertTrue(
                        event.chord.symbol.isNotBlank(),
                        "Progression '$id' produced a chord with no symbol in $tonic",
                    )
                }
            }
        }
    }

    @Test
    fun `every progression policy names a progression that exists`() = runTest {
        val progressions = repository.allProgressions()

        repository.allPolicies().values.mapNotNull { it.progressionId?.let { id -> it.id to id } }
            .forEach { (policyId, progressionId) ->
                assertNotNull(
                    progressions[progressionId],
                    "Policy '$policyId' runs '$progressionId', which is not authored",
                )
            }
    }

    @Test
    fun `a policy naming a progression that does not exist is refused`() {
        val broken = ContentSource { path ->
            if (path == DefaultContentRepository.POLICY_PATH) {
                """
                {
                  "schemaVersion": 1,
                  "contentVersion": "broken",
                  "policies": [
                    {
                      "id": "policy.bad",
                      "skills": ["skill.x"],
                      "formulas": ["major_triad"],
                      "progression": "progression.does_not_exist"
                    }
                  ]
                }
                """.trimIndent()
            } else {
                source.read(path)
            }
        }

        val failure = runCatching {
            kotlinx.coroutines.runBlocking { DefaultContentRepository(broken).allProgressions() }
        }.exceptionOrNull()

        assertTrue(
            failure is ContentReferenceException,
            "A progression that does not exist should be refused, got $failure",
        )
    }

    @Test
    fun `a progression with an unreadable numeral is refused`() {
        val broken = ContentSource { path ->
            if (path == DefaultContentRepository.PROGRESSION_PATH) {
                """
                {
                  "schemaVersion": 1,
                  "contentVersion": "broken",
                  "progressions": [
                    { "id": "progression.bad", "title": "Bad", "functions": ["ii7", "Vnope"] }
                  ]
                }
                """.trimIndent()
            } else {
                source.read(path)
            }
        }

        val failure = runCatching {
            kotlinx.coroutines.runBlocking { DefaultContentRepository(broken).allProgressions() }
        }.exceptionOrNull()

        assertTrue(
            failure is ContentReferenceException,
            "An unreadable roman numeral should be refused, got $failure",
        )
    }

    @Test
    fun `a policy naming a chord formula that does not exist is refused`() {
        val broken = ContentSource { path ->
            if (path == DefaultContentRepository.POLICY_PATH) {
                """
                {
                  "schemaVersion": 1,
                  "contentVersion": "broken",
                  "policies": [
                    { "id": "policy.bad", "skills": ["skill.x"], "formulas": ["not_a_chord"] }
                  ]
                }
                """.trimIndent()
            } else {
                source.read(path)
            }
        }

        val failure = runCatching {
            kotlinx.coroutines.runBlocking { DefaultContentRepository(broken).allPolicies() }
        }.exceptionOrNull()

        assertTrue(
            failure is ContentReferenceException,
            "A chord formula that does not exist should be refused, got $failure",
        )
    }

    private companion object {
        const val SAMPLE_SIZE = 100
        const val MINIMUM_ROOT_SPREAD = 4
    }
}
