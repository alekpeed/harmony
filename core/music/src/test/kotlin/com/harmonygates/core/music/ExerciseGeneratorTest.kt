package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.exercise.AnswerMode
import com.harmonygates.core.music.exercise.DefaultExerciseGenerator
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.exercise.GenerationContext
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.harmony.DominantAlteration
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.Inversion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercise generation.
 *
 * Determinism is the load-bearing property: non-negotiable design rule 6 says a seed must
 * reproduce an exercise, and everything downstream — a bug report naming a chord, a replayed
 * session, a diagnostic export — depends on it holding.
 */
class ExerciseGeneratorTest {

    private val generator = DefaultExerciseGenerator()
    private val realizer = DefaultChordRealizer()

    private fun policy(
        formulas: List<com.harmonygates.core.music.chord.ChordFormulaId> = listOf(
            ChordFormulas.MajorSeventh.id,
            ChordFormulas.DominantSeventh.id,
            ChordFormulas.MinorSeventh.id,
        ),
        inversions: List<Inversion> = listOf(Inversion.ROOT),
        answerMode: AnswerMode = AnswerMode.PitchClasses,
        presentation: PresentationSpec = PresentationSpec.Independent,
        alterations: List<Set<DominantAlteration>> = emptyList(),
    ) = ExercisePolicy(
        id = ExercisePolicyId("policy.test"),
        skillIds = setOf(SkillId("skill.test")),
        formulaPool = formulas,
        inversionPool = inversions,
        alterationPool = alterations,
        answerMode = answerMode,
        presentation = presentation,
    )

    // --- Altered dominants (Region 10) -----------------------------------------------------

    @Test
    fun `an alteration pool dresses the dominant it generated`() {
        val altered = policy(
            formulas = listOf(ChordFormulas.DominantSeventh.id),
            alterations = listOf(setOf(DominantAlteration.FLAT_NINE)),
        )

        repeat(SAMPLES) { seed ->
            val instance = generator.generate(altered, seed = seed.toLong())

            assertTrue(
                ChordDegree.FLAT_NINTH in instance.chord.degrees,
                "${instance.chord.symbol} was generated from a pool that asks for a b9",
            )
            assertTrue(
                instance.spelledTones.size > PLAIN_DOMINANT_TONES,
                "${instance.chord.symbol} should sound the alteration it names",
            )
        }
    }

    @Test
    fun `an alteration pool asks for one of its sets, not all of them at once`() {
        val altered = policy(
            formulas = listOf(ChordFormulas.DominantSeventh.id),
            alterations = listOf(setOf(DominantAlteration.FLAT_NINE), setOf(DominantAlteration.SHARP_NINE)),
        )

        val symbols = (0 until SAMPLES).map { generator.generate(altered, seed = it.toLong()).chord }

        assertTrue(symbols.any { ChordDegree.FLAT_NINTH in it.degrees }, "The b9 was never chosen")
        assertTrue(symbols.any { ChordDegree.SHARP_NINTH in it.degrees }, "The #9 was never chosen")
        assertTrue(
            symbols.none { ChordDegree.FLAT_NINTH in it.degrees && ChordDegree.SHARP_NINTH in it.degrees },
            "A ninth cannot be flat and sharp at once",
        )
    }

    @Test
    fun `an alteration pool leaves a chord that is not a dominant alone`() {
        val altered = policy(
            formulas = listOf(ChordFormulas.MajorSeventh.id),
            alterations = listOf(setOf(DominantAlteration.FLAT_NINE)),
        )

        repeat(SAMPLES) { seed ->
            val instance = generator.generate(altered, seed = seed.toLong())

            assertEquals(
                ChordFormulas.MajorSeventh.id,
                instance.chord.formulaId,
                "The authored formula is the lesson; a major seventh with a b9 is a different one",
            )
            assertTrue(ChordDegree.FLAT_NINTH !in instance.chord.degrees)
        }
    }

    @Test
    fun `an empty alteration set is refused rather than silently meaning nothing`() {
        val failure = runCatching {
            policy(formulas = listOf(ChordFormulas.DominantSeventh.id), alterations = listOf(emptySet()))
        }

        assertTrue(failure.isFailure)
    }

    @Test
    fun `the same seed produces the same exercise`() {
        val first = generator.generate(policy(), seed = 4242)
        val second = generator.generate(policy(), seed = 4242)

        assertEquals(first.chord, second.chord)
        assertEquals(first.inversion, second.inversion)
        assertEquals(first.requirement, second.requirement)
        assertEquals(first.id, second.id)
    }

    @Test
    fun `different seeds produce different exercises`() {
        val chords = (1L..40L).map { generator.generate(policy(), seed = it).chord }
        assertTrue(chords.distinct().size > 1, "A single chord for forty seeds is not generation")
    }

    @Test
    fun `every generated chord is writable`() {
        // A chord standard notation cannot spell must never reach a player.
        for (seed in 1L..200L) {
            val instance = generator.generate(
                policy(formulas = ChordFormulas.all.map { it.id }),
                seed = seed,
            )
            assertIs<SpellingResult.Spelled<*>>(
                realizer.trySpell(instance.chord),
                "Seed $seed produced the unwritable ${instance.chord.symbol}",
            )
        }
    }

    @Test
    fun `roots the session has not used are preferred`() {
        // 02_GAME_LOOP_AND_PROGRESSION.md §9's coverage term: a session that asked for C six
        // times would teach one root well and eleven not at all.
        val seen = StandardRoots.take(11).toSet()
        val instance = generator.generate(
            policy(),
            seed = 1,
            context = GenerationContext(rootsAlreadySeen = seen),
        )
        assertEquals(StandardRoots.last(), instance.chord.root, "The one untested root is chosen")
    }

    @Test
    fun `the same root is not asked twice in a row`() {
        val previous = generator.generate(policy(), seed = 7)
        val next = generator.generate(
            policy(),
            seed = 8,
            context = GenerationContext(previousChord = previous.chord),
        )
        assertTrue(next.chord.root != previous.chord.root)
    }

    @Test
    fun `a session covers many roots rather than repeating one`() {
        var context = GenerationContext()
        val roots = mutableSetOf<com.harmonygates.core.music.pitch.SpelledPitchClass>()
        repeat(12) { index ->
            val instance = generator.generate(policy(), seed = 1000L + index, context = context)
            roots += instance.chord.root
            context = GenerationContext(
                rootsAlreadySeen = roots.toSet(),
                previousChord = instance.chord,
                index = index + 1,
            )
        }
        assertEquals(12, roots.size, "Twelve exercises should reach all twelve roots")
    }

    @Test
    fun `an inversion pool produces the requested inversions`() {
        val inversions = (1L..40L).map {
            generator.generate(
                policy(inversions = listOf(Inversion.ROOT, Inversion.FIRST, Inversion.SECOND)),
                seed = it,
            ).inversion
        }.toSet()
        assertTrue(inversions.size > 1, "An inversion pool should actually vary, got $inversions")
        assertTrue(inversions.all { it in setOf(Inversion.ROOT, Inversion.FIRST, Inversion.SECOND) })
    }

    @Test
    fun `an inversion exercise requires the right bass`() {
        val instance = (1L..40L)
            .map { generator.generate(policy(inversions = listOf(Inversion.FIRST)), seed = it) }
            .first()

        val requirement = assertIs<ExerciseRequirement.PitchSet>(instance.requirement)
        val third = instance.chord.degrees.first { it.number == 3 }
        assertEquals(
            realizer.spelledDegrees(instance.chord)[third],
            requirement.requiredBass,
            "First inversion means the third is in the bass",
        )
    }

    @Test
    fun `root position does not constrain the bass`() {
        val instance = generator.generate(policy(inversions = listOf(Inversion.ROOT)), seed = 5)
        val requirement = assertIs<ExerciseRequirement.PitchSet>(instance.requirement)
        assertEquals(null, requirement.requiredBass, "Root position is the default reading")
    }

    @Test
    fun `the answer mode decides the requirement type`() {
        assertIs<ExerciseRequirement.PitchSet>(
            generator.generate(policy(answerMode = AnswerMode.PitchClasses), seed = 1).requirement,
        )
        assertIs<ExerciseRequirement.ExactVoicing>(
            generator.generate(policy(answerMode = AnswerMode.ExactVoicing), seed = 1).requirement,
        )
    }

    @Test
    fun `presentation channels are carried onto the instance`() {
        val guided = generator.generate(policy(presentation = PresentationSpec.Guided), seed = 3)
        assertTrue(guided.spelledTones.isNotEmpty())
        assertTrue(guided.targetNotes.isNotEmpty(), "Guided trials highlight the keys")
        assertTrue(guided.presentation.showSpelledNoteNames)

        val independent = generator.generate(policy(presentation = PresentationSpec.Independent), seed = 3)
        assertTrue(!independent.presentation.showSpelledNoteNames)
    }

    @Test
    fun `the target voicing sits inside the requested register`() {
        val range = 48..72
        val policy = ExercisePolicy(
            id = ExercisePolicyId("policy.range"),
            skillIds = setOf(SkillId("skill.test")),
            formulaPool = listOf(ChordFormulas.MajorSeventh.id),
            pitchRange = range,
            presentation = PresentationSpec.Guided,
        )
        for (seed in 1L..30L) {
            val instance = generator.generate(policy, seed = seed)
            assertTrue(
                instance.targetNotes.all { it in range },
                "Seed $seed produced ${instance.targetNotes} outside $range",
            )
        }
    }

    @Test
    fun `an instance carries the seed that made it`() {
        val instance = generator.generate(policy(), seed = 99)
        assertEquals(99L, instance.seed, "A bug report naming a chord has to be reproducible")
    }

    private companion object {
        const val SAMPLES = 24
        const val PLAIN_DOMINANT_TONES = 4
    }
}
