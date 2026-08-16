package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.eartraining.DefaultEarExerciseGenerator
import com.harmonygates.core.music.eartraining.EarTaskFamily
import com.harmonygates.core.music.eartraining.ListeningRecord
import com.harmonygates.core.music.eartraining.ReplayRule
import com.harmonygates.core.music.eartraining.StimulusSettings
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.realize.DefaultChordRealizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ear training.
 *
 * The two things Phase 8's acceptance asks of this half — a deterministic stimulus, and MIDI
 * answers judged by the core evaluator — are both tested directly. The third, that nothing
 * blocks on decode during playback, is a property of `core:audio` and is covered there.
 */
class EarTrainingTest {

    private val generator = DefaultEarExerciseGenerator()
    private val evaluator = DefaultPerformanceEvaluator()
    private val realizer = DefaultChordRealizer()

    private fun root(name: String) = requireNotNull(SpelledPitchClass.parseOrNull(name))

    private val policy = ExercisePolicy(
        id = ExercisePolicyId("policy.ear.sevenths"),
        skillIds = setOf(SkillId("skill.ear.seventh")),
        rootPool = listOf("C", "F", "G", "D", "Bb", "Eb").map(::root),
        formulaPool = listOf(
            ChordFormulas.MajorSeventh.id,
            ChordFormulas.DominantSeventh.id,
            ChordFormulas.MinorSeventh.id,
        ),
        sessionLength = 12,
    )

    private fun generate(family: EarTaskFamily, seed: Long = 1, key: KeyContext? = null) =
        generator.generate(family, policy, seed, StimulusSettings(), key)

    // --- Acceptance: deterministic stimulus ------------------------------------------------------

    @Test
    fun `the same seed produces the same stimulus`() {
        val first = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 4242))
        val second = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 4242))

        assertEquals(first.stimulus, second.stimulus, "A stimulus must be reproducible from its seed")
        assertEquals(first.requirement, second.requirement)
    }

    @Test
    fun `different seeds produce different stimuli`() {
        val chords = (1L..40L).mapNotNull { generate(EarTaskFamily.REPRODUCE, seed = it)?.stimulus?.chords }

        assertTrue(chords.distinct().size > 1, "Every seed produced the same chord")
    }

    @Test
    fun `a stimulus records everything needed to hear it again`() {
        val stimulus = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 9)).stimulus

        // 07 §3's list: seed, chord spec, realized voicing, instrument, velocities, tempo.
        assertEquals(9L, stimulus.seed)
        assertTrue(stimulus.chords.isNotEmpty())
        assertTrue(stimulus.events.first().voicing.pitches.isNotEmpty(), "The realised voicing is stored")
        assertTrue(stimulus.instrumentId.isNotBlank())
        assertTrue(stimulus.events.first().velocities.isNotEmpty())
        assertTrue(stimulus.tempoBpm > 0)
    }

    @Test
    fun `a stimulus the player will be judged against is not humanised`() {
        val stimulus = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 11)).stimulus

        // 09 §8: never humanise anything used as a timing ground truth.
        assertNull(stimulus.humanisationSeed)
        assertEquals(
            1,
            stimulus.events.first().velocities.distinct().size,
            "A stimulus should not vary its own velocities",
        )
        assertEquals(0L, stimulus.events.first().atMillis, "The first event is exactly at zero")
    }

    // --- Acceptance: the core evaluator judges the answer ----------------------------------------

    @Test
    fun `playing what you heard is correct, judged by the ordinary evaluator`() {
        val exercise = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 21))
        val chord = exercise.stimulus.chords.single()
        val played = realizer.chordTones(chord).map { 60 + it.pitchClass.value }

        val result = evaluator.evaluate(exercise.requirement, attemptOf(played))

        assertTrue(result.verdict.isCorrect, "${chord.symbol} played as $played: ${result.semanticErrors}")
    }

    @Test
    fun `playing something else is wrong, and diagnosed the same way`() {
        val exercise = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 21))

        // A C major triad, whatever the stimulus was.
        val result = evaluator.evaluate(exercise.requirement, attemptOf(listOf(60, 64, 67)))

        assertFalse(result.verdict.isCorrect)
        assertTrue(result.semanticErrors.isNotEmpty(), "An ear answer gets a musical diagnosis too")
    }

    @Test
    fun `an ear exercise uses the same requirement type as a chord gate`() {
        val exercise = assertNotNull(generate(EarTaskFamily.REPRODUCE, seed = 3))

        // 07 §1: ear training must use the same harmonic objects, not a parallel list of labels.
        assertTrue(
            exercise.requirement is ExerciseRequirement.PitchSet,
            "Got ${exercise.requirement::class.simpleName}",
        )
    }

    // --- Task families ----------------------------------------------------------------------------

    @Test
    fun `identify then play asks for both`() {
        val exercise = assertNotNull(generate(EarTaskFamily.IDENTIFY_THEN_PLAY, seed = 5))

        assertTrue(exercise.instruction.contains("Name"), exercise.instruction)
        assertTrue(exercise.instruction.contains("play"), exercise.instruction)
    }

    @Test
    fun `difference detection plays two chords that differ in one way`() {
        val exercise = assertNotNull(generate(EarTaskFamily.DIFFERENCE_DETECTION, seed = 8))
        val stimulus = exercise.stimulus

        assertEquals(2, stimulus.events.size)
        assertTrue(stimulus.events[1].atMillis > stimulus.events[0].atMillis, "B follows A")
        assertEquals(
            stimulus.chords[0],
            stimulus.chords[1],
            "The two chords are the same harmony; only the voicing moved",
        )
        assertNotNull(exercise.differenceDescription)
    }

    @Test
    fun `function hearing needs a key and says which one`() {
        assertNull(
            generate(EarTaskFamily.FUNCTION_HEARING, seed = 2, key = null),
            "A function has no meaning without a key",
        )

        val inEb = assertNotNull(
            generate(EarTaskFamily.FUNCTION_HEARING, seed = 2, key = KeyContext(root("Eb"))),
        )
        assertEquals(KeyContext(root("Eb")), inEb.stimulus.key)
        assertTrue(inEb.instruction.contains("Eb"), inEb.instruction)
    }

    @Test
    fun `the families that need a melodic line say so rather than half-doing it`() {
        assertNull(generate(EarTaskFamily.BASS_HEARING, seed = 1))
        assertNull(generate(EarTaskFamily.VOICE_LEADING_HEARING, seed = 1))
    }

    // --- Replay rules -----------------------------------------------------------------------------

    @Test
    fun `replay rules allow what they say they allow`() {
        assertTrue(ReplayRule.Unlimited.permits(50))

        assertTrue(ReplayRule.Once.permits(0))
        assertFalse(ReplayRule.Once.permits(1), "Challenge mode is one hearing")

        val limited = ReplayRule.Limited(3)
        assertTrue(limited.permits(2))
        assertFalse(limited.permits(3))
    }

    @Test
    fun `a limit of zero plays is refused when it is written`() {
        assertNull(
            runCatching { ReplayRule.Limited(0) }.getOrNull(),
            "An exercise nobody may hear is not an ear exercise",
        )
    }

    @Test
    fun `one hearing is independent and five is not`() {
        // 07 §5: a correct answer after five replays is useful practice, and is not the same
        // evidence as a one-hearing response.
        assertTrue(ListeningRecord(plays = 1, heardBeforeAnswering = true).isIndependentHearing)
        assertFalse(ListeningRecord(plays = 5, heardBeforeAnswering = true).isIndependentHearing)
    }

    @Test
    fun `a gate can hand its replay rule to the exercise`() {
        val once = assertNotNull(
            generator.generate(
                EarTaskFamily.REPRODUCE,
                policy,
                seed = 1,
                settings = StimulusSettings(replayRule = ReplayRule.Once),
            ),
        )

        assertEquals(ReplayRule.Once, once.replayRule)
    }

    @Test
    fun `the instrument is drawn from the approved set and recorded`() {
        val settings = StimulusSettings(instrumentIds = listOf("instrument.a", "instrument.b"))
        val chosen = (1L..30L).mapNotNull {
            generator.generate(EarTaskFamily.REPRODUCE, policy, it, settings)?.stimulus?.instrumentId
        }

        assertTrue(chosen.isNotEmpty())
        assertTrue(
            chosen.all { it in settings.instrumentIds },
            "07 §4 wants a small approved set, not free choice: ${chosen.distinct()}",
        )
    }
}
