package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.DefaultExerciseGenerator
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.exercise.GenerationContext
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.realize.DefaultChordRealizer
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The performance budgets of 14_TESTING_AND_QUALITY.md §8.
 *
 * "Measure rather than assume." Two of the five budgets are pure computation and can be measured
 * here; the other three — MIDI-to-pixel latency, audio glitches, dropped Compose frames — need
 * the tablet, and phase 14's status note says so rather than pretending otherwise.
 *
 * **On timing in a test.** A JVM under a cold JIT on a shared build machine is not a tablet, so
 * these are deliberately loose: they exist to catch an accidental order-of-magnitude regression —
 * a realizer that started allocating per note, an evaluator that went quadratic — and not to
 * certify a number. Each measurement warms up first, then takes the *median* of several runs, so
 * one unlucky garbage collection cannot fail a build.
 */
class PerformanceBudgetTest {

    private val realizer = DefaultChordRealizer()
    private val evaluator = DefaultPerformanceEvaluator()
    private val generator = DefaultExerciseGenerator()

    @Test
    fun `evaluating a chord answer stays well inside its budget`() {
        // §8: "candidate chord evaluation: <10 ms for normal chord tasks".
        val chord = JazzChordParser.parse("Cmaj7").let {
            (it as com.harmonygates.core.music.parse.ParseResult.Success).value
        }
        val requirement = ExerciseRequirement.PitchSet(
            pitchClasses = realizer.chordTones(chord).toSet(),
            chord = chord,
        )
        val attempt = attemptOf(listOf(48, 52, 55, 59))

        val median = medianMillis(runs = 25) {
            evaluator.evaluate(requirement, attempt)
        }

        assertTrue(
            median < EVALUATION_BUDGET_MILLIS,
            "Evaluating one chord took ${median}ms, against a ${EVALUATION_BUDGET_MILLIS}ms budget",
        )
    }

    @Test
    fun `evaluating the largest chord in the vocabulary is still inside the budget`() {
        // A thirteenth against a seven-note performance is the worst normal case: the most
        // degrees to match and the most notes to explain.
        val chord = ChordSpec(StandardRoots.first(), ChordFormulas.DominantThirteenth.id)
        val requirement = ExerciseRequirement.PitchSet(
            pitchClasses = realizer.chordTones(chord).toSet(),
            chord = chord,
        )
        val attempt = attemptOf(listOf(36, 48, 52, 58, 62, 69, 74))

        val median = medianMillis(runs = 25) { evaluator.evaluate(requirement, attempt) }

        assertTrue(
            median < EVALUATION_BUDGET_MILLIS,
            "Evaluating a thirteenth took ${median}ms, against ${EVALUATION_BUDGET_MILLIS}ms",
        )
    }

    @Test
    fun `generating an exercise stays well inside its budget`() {
        // §8: "exercise generation: <50 ms typical".
        val policy = ExercisePolicy(
            id = ExercisePolicyId("policy.budget"),
            skillIds = setOf(SkillId("skill.budget")),
            formulaPool = ChordFormulas.all.map { it.id },
        )

        var seed = 0L
        val median = medianMillis(runs = 25) {
            generator.generate(policy, seed = seed++, context = GenerationContext(index = 0))
        }

        assertTrue(
            median < GENERATION_BUDGET_MILLIS,
            "Generating one exercise took ${median}ms, against a ${GENERATION_BUDGET_MILLIS}ms budget",
        )
    }

    @Test
    fun `a whole session generates in the time one exercise is allowed`() {
        // A gate session is planned up front, so twenty exercises are generated in one go. If
        // that took twenty times the single-exercise budget the player would feel it as a pause
        // between pressing start and the first chord appearing.
        val policy = ExercisePolicy(
            id = ExercisePolicyId("policy.budget.session"),
            skillIds = setOf(SkillId("skill.budget")),
            formulaPool = ChordFormulas.all.map { it.id },
        )

        val median = medianMillis(runs = 10) {
            var context = GenerationContext(index = 0)
            repeat(SESSION_LENGTH) { index ->
                val instance = generator.generate(policy, seed = index.toLong(), context = context)
                context = GenerationContext(
                    index = index + 1,
                    previousChord = instance.chord,
                    rootsAlreadySeen = context.rootsAlreadySeen + instance.chord.root,
                )
            }
        }

        assertTrue(
            median < GENERATION_BUDGET_MILLIS,
            "Generating a $SESSION_LENGTH-exercise session took ${median}ms",
        )
    }

    @Test
    fun `spelling every chord in every key is not accidentally expensive`() {
        // Not a specified budget, but the operation everything else sits on: 360 chords is what
        // the coverage test does on every run, and a regression here slows the whole suite.
        val specs = ChordFormulas.all.flatMap { formula ->
            StandardRoots.map { ChordSpec(it, formula.id) }
        }

        val median = medianMillis(runs = 5) {
            specs.forEach { realizer.chordTones(it) }
        }

        assertTrue(
            median < VOCABULARY_BUDGET_MILLIS,
            "Spelling ${specs.size} chords took ${median}ms, against ${VOCABULARY_BUDGET_MILLIS}ms",
        )
    }

    /**
     * The median of [runs] timed executions, after a warm-up.
     *
     * The median rather than the mean: on a shared machine one run in twenty will be interrupted
     * by something that has nothing to do with this code, and a mean lets that one run fail a
     * build that is not broken.
     */
    private fun medianMillis(runs: Int, block: () -> Unit): Double {
        repeat(WARMUP_RUNS) { block() }
        val timings = List(runs) { measureNanoTime(block) }.sorted()
        return timings[timings.size / 2] / NANOS_PER_MILLI
    }

    private companion object {
        const val WARMUP_RUNS = 50
        const val NANOS_PER_MILLI = 1_000_000.0
        const val SESSION_LENGTH = 20

        // The spec's own numbers. Not padded: the point is to notice if the real figure ever
        // approaches them.
        const val EVALUATION_BUDGET_MILLIS = 10.0
        const val GENERATION_BUDGET_MILLIS = 50.0
        const val VOCABULARY_BUDGET_MILLIS = 100.0
    }
}
