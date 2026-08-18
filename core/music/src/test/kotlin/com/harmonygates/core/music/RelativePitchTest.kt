package com.harmonygates.core.music

import com.harmonygates.core.music.eartraining.StimulusSettings
import com.harmonygates.core.music.relativepitch.DefaultRelativePitchExerciseGenerator
import com.harmonygates.core.music.relativepitch.IntervalClass
import com.harmonygates.core.music.relativepitch.LevelStat
import com.harmonygates.core.music.relativepitch.LevelStatus
import com.harmonygates.core.music.relativepitch.RelativePitchCurriculum
import com.harmonygates.core.music.relativepitch.RelativePitchEvaluator
import com.harmonygates.core.music.relativepitch.RelativePitchTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The relative-pitch ladder: a graded, multiple-choice curriculum underneath the keyboard
 * chord-reproduction exercises in `eartraining`. Three things need covering — the ladder is a
 * real linear order with no gaps, a level's evidence gates exactly the level after it and no
 * other, and every multiple-choice tier generates a playable, answerable, seed-reproducible
 * question.
 */
class RelativePitchTest {

    private val generator = DefaultRelativePitchExerciseGenerator()

    // --- The ladder itself -------------------------------------------------------------------

    @Test
    fun `the ladder has no duplicate ids and ends with reproduction`() {
        val levels = RelativePitchCurriculum.levels

        assertEquals(levels.size, levels.map { it.id }.distinct().size, "Every level id must be unique")
        assertEquals(RelativePitchTier.REPRODUCE, levels.last().tier, "Keyboard reproduction is the capstone")
        assertTrue(
            levels.first().tier == RelativePitchTier.INTERVALS,
            "Interval recognition is the ground floor",
        )
    }

    @Test
    fun `each multiple-choice level's options are a superset of the previous level's`() {
        val byTier = RelativePitchCurriculum.levels.groupBy { it.tier }

        listOf(RelativePitchTier.INTERVALS, RelativePitchTier.SCALE_DEGREES, RelativePitchTier.CHORD_QUALITY)
            .forEach { tier ->
                val levels = byTier.getValue(tier)
                levels.zipWithNext().forEach { (earlier, later) ->
                    val earlierChoices = earlier.intervalChoices + earlier.degreeChoices + earlier.qualityChoices
                    val laterChoices = later.intervalChoices + later.degreeChoices + later.qualityChoices
                    assertTrue(
                        laterChoices.containsAll(earlierChoices),
                        "${later.id} dropped a choice ${earlier.id} had: $earlierChoices vs $laterChoices",
                    )
                    assertTrue(laterChoices.size > earlierChoices.size, "${later.id} added nothing new")
                }
            }
    }

    @Test
    fun `the hardest intervals to tell apart are introduced late, not first`() {
        val minorSecondLevel = RelativePitchCurriculum.levels.first { IntervalClass.MINOR_SECOND in it.intervalChoices }
        val firstLevel = RelativePitchCurriculum.levels.first { it.tier == RelativePitchTier.INTERVALS }

        assertTrue(minorSecondLevel.id != firstLevel.id, "A minor 2nd cannot be the very first thing taught")
    }

    // --- Gating --------------------------------------------------------------------------------

    @Test
    fun `with no evidence, only the first level is available`() {
        val statuses = RelativePitchEvaluator.statuses(stats = emptyMap())
        val levels = RelativePitchCurriculum.levels

        assertEquals(LevelStatus.AVAILABLE, statuses.getValue(levels.first().id))
        assertEquals(LevelStatus.LOCKED, statuses.getValue(levels[1].id))
        assertTrue(levels.drop(1).all { statuses.getValue(it.id) == LevelStatus.LOCKED })
    }

    @Test
    fun `mastering a level unlocks exactly the next one`() {
        val levels = RelativePitchCurriculum.levels
        val stats = mapOf(levels[0].id to LevelStat(attempts = 20, correct = 20))

        val statuses = RelativePitchEvaluator.statuses(stats = stats)

        assertEquals(LevelStatus.MASTERED, statuses.getValue(levels[0].id))
        assertEquals(LevelStatus.AVAILABLE, statuses.getValue(levels[1].id))
        assertEquals(LevelStatus.LOCKED, statuses.getValue(levels[2].id))
    }

    @Test
    fun `attempts below the floor do not count as mastery even at perfect accuracy`() {
        val level = RelativePitchCurriculum.levels.first()
        val stat = LevelStat(attempts = 2, correct = 2)

        assertTrue(stat.accuracy == 1.0)
        assertTrue(!stat.passes(level), "Two lucky answers is not mastery")
    }

    @Test
    fun `currentLevel resumes at the first level not yet mastered`() {
        val levels = RelativePitchCurriculum.levels
        val stats = mapOf(
            levels[0].id to LevelStat(20, 20),
            levels[1].id to LevelStat(20, 20),
            levels[2].id to LevelStat(5, 1),
        )

        assertEquals(levels[2].id, RelativePitchEvaluator.currentLevel(stats = stats)?.id)
    }

    // --- Exercise generation ---------------------------------------------------------------------

    @Test
    fun `an interval exercise plays two notes and offers the answer among the choices`() {
        val level = RelativePitchCurriculum.level("interval.5")!!
        val exercise = assertNotNull(generator.generate(level, seed = 7))

        assertEquals(2, exercise.stimulus.events.size)
        assertTrue(exercise.stimulus.events.all { it.notes.size == 1 }, "A melodic interval is one note at a time")
        assertTrue(exercise.correctChoiceId in exercise.choiceIds)
        assertEquals(level.intervalChoices.map { it.name }.toSet(), exercise.choiceIds.toSet())
    }

    @Test
    fun `a degree exercise establishes a tonic before the target note`() {
        val level = RelativePitchCurriculum.level("degree.3")!!
        val exercise = assertNotNull(generator.generate(level, seed = 3))

        assertEquals(2, exercise.stimulus.events.size)
        assertTrue(exercise.stimulus.events[1].atMillis > exercise.stimulus.events[0].atMillis)
        assertTrue(exercise.correctChoiceId in exercise.choiceIds)
    }

    @Test
    fun `a quality exercise plays one chord of more than one note`() {
        val level = RelativePitchCurriculum.level("quality.3")!!
        val exercise = assertNotNull(generator.generate(level, seed = 11))

        assertEquals(1, exercise.stimulus.events.size)
        assertTrue(exercise.stimulus.events.single().notes.size >= 3, "A quality needs at least a triad")
        assertTrue(exercise.correctChoiceId in exercise.choiceIds)
    }

    @Test
    fun `the same seed reproduces the same exercise`() {
        val level = RelativePitchCurriculum.level("interval.5")!!

        val first = assertNotNull(generator.generate(level, seed = 99))
        val second = assertNotNull(generator.generate(level, seed = 99))

        assertEquals(first.stimulus, second.stimulus)
        assertEquals(first.correctChoiceId, second.correctChoiceId)
    }

    @Test
    fun `function hearing and reproduce levels generate nothing, they route elsewhere`() {
        assertNull(generator.generate(RelativePitchCurriculum.level("function.1")!!, seed = 1))
        assertNull(generator.generate(RelativePitchCurriculum.level("reproduce.1")!!, seed = 1))
    }

    @Test
    fun `every multiple-choice level in the ladder actually generates`() {
        RelativePitchCurriculum.levels
            .filter { it.tier != RelativePitchTier.FUNCTION_HEARING && it.tier != RelativePitchTier.REPRODUCE }
            .forEach { level ->
                repeat(20) { attempt ->
                    assertNotNull(
                        generator.generate(level, seed = attempt.toLong(), settings = StimulusSettings()),
                        "${level.id} produced nothing for seed $attempt",
                    )
                }
            }
    }
}
