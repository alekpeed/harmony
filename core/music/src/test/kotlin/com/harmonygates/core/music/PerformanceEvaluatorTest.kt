package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.performance.CaptureCompletion
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.NormalizedNoteEvent
import com.harmonygates.core.music.performance.OnsetPolicy
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.performance.Verdict
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.TopNoteRequirement
import com.harmonygates.core.music.voicing.VoicingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The evaluator.
 *
 * The three modes 06_PERFORMANCE_EVALUATION_AND_SCORING.md describes are tested separately
 * because they answer different questions: "play any Cmaj7" is about pitch classes, "play this
 * voicing" is about exact notes including doublings, and "play a rootless G13" is about degrees.
 * A single comparison routine would get at least two of the three wrong.
 */
class PerformanceEvaluatorTest {

    private val evaluator = DefaultPerformanceEvaluator()
    private val realizer = DefaultChordRealizer()

    private fun attempt(
        vararg notes: Int,
        spreadMillis: Long = 0,
        completion: CaptureCompletion = CaptureCompletion.OnsetsSettled,
        sustainUsed: Boolean = false,
    ): PerformanceAttempt {
        val step = if (notes.size <= 1) 0 else spreadMillis / (notes.size - 1)
        val events = notes.mapIndexed { index, note ->
            NormalizedNoteEvent(
                note = MidiNote(note),
                velocity = 80,
                onsetNanos = index * step * PerformanceAttempt.NANOS_PER_MILLI,
            )
        }
        return PerformanceAttempt(
            startedAtNanos = 0,
            completedAtNanos = 500 * PerformanceAttempt.NANOS_PER_MILLI,
            noteEvents = events,
            finalEffectiveNotes = notes.map { MidiNote(it) }.sorted(),
            onsetSpreadNanos = if (notes.size <= 1) {
                null
            } else {
                spreadMillis * PerformanceAttempt.NANOS_PER_MILLI
            },
            sustainUsed = sustainUsed,
            completion = completion,
        )
    }

    private fun pitchSetOf(symbol: String, allowExtraNotes: Boolean = false): ExerciseRequirement.PitchSet {
        val chord = JazzChordParser.parseOrThrow(symbol)
        return ExerciseRequirement.PitchSet(
            pitchClasses = realizer.chordTones(chord).toSet(),
            allowExtraNotes = allowExtraNotes,
            chord = chord,
        )
    }

    // --- Pitch-class exercises (§3) ---------------------------------------------------------

    @Test
    fun `play any Cmaj7 accepts any octave`() {
        val requirement = pitchSetOf("Cmaj7")

        assertTrue(evaluator.evaluate(requirement, attempt(60, 64, 67, 71)).isCorrect)
        assertTrue(evaluator.evaluate(requirement, attempt(48, 52, 55, 59)).isCorrect, "An octave lower")
        assertTrue(evaluator.evaluate(requirement, attempt(71, 76, 79, 84)).isCorrect, "First inversion, high")
    }

    @Test
    fun `a missing tone is named by its degree`() {
        val result = evaluator.evaluate(pitchSetOf("Cmaj7"), attempt(60, 64, 67))

        assertEquals(Verdict.PARTIAL, result.verdict)
        val missing = assertIs<PerformanceError.MissingDegree>(result.explanation.primaryError)
        assertEquals(ChordDegree.SEVENTH, missing.tone.degree)
        assertEquals("B", missing.tone.pitchClass.toString())
    }

    @Test
    fun `a wrong quality is diagnosed rather than reported as a missing note`() {
        // A minor third where a major one was wanted.
        val result = evaluator.evaluate(pitchSetOf("Cmaj7"), attempt(60, 63, 67, 71))

        assertEquals(Verdict.PARTIAL, result.verdict)
        assertTrue(result.semanticErrors.any { it is PerformanceError.MissingDegree })
        assertTrue(result.semanticErrors.any { it is PerformanceError.ExtraTone })
    }

    @Test
    fun `an extra note fails unless the exercise permits it`() {
        val strict = evaluator.evaluate(pitchSetOf("Cmaj7"), attempt(60, 64, 66, 67, 71))
        assertEquals(Verdict.PARTIAL, strict.verdict)
        assertEquals(setOf(MidiNote(66)), strict.extra)

        val lenient = evaluator.evaluate(pitchSetOf("Cmaj7", allowExtraNotes = true), attempt(60, 64, 66, 67, 71))
        assertTrue(lenient.isCorrect, "The policy decides, not the evaluator")
    }

    @Test
    fun `a required bass is enforced`() {
        val chord = JazzChordParser.parseOrThrow("C/E")
        val requirement = ExerciseRequirement.PitchSet(
            pitchClasses = realizer.chordTones(chord).toSet(),
            requiredBass = chord.explicitBass,
            chord = chord,
        )

        assertTrue(evaluator.evaluate(requirement, attempt(52, 60, 67)).isCorrect, "E in the bass")

        val wrong = evaluator.evaluate(requirement, attempt(48, 52, 67))
        assertTrue(wrong.semanticErrors.any { it is PerformanceError.WrongBass })
    }

    // --- Exact voicing (§4) -----------------------------------------------------------------

    @Test
    fun `an exact voicing requires the exact notes`() {
        val voicing = realizer.analyze(JazzChordParser.parseOrThrow("Cmaj7"), listOf(60, 64, 67, 71).map(::MidiNote))
        val requirement = ExerciseRequirement.ExactVoicing(voicing)

        assertTrue(evaluator.evaluate(requirement, attempt(60, 64, 67, 71)).isCorrect)

        val wrongOctave = evaluator.evaluate(requirement, attempt(48, 52, 55, 59))
        assertTrue(!wrongOctave.isCorrect, "The same chord an octave down is not this voicing")
    }

    @Test
    fun `a doubled voice is part of an exact answer`() {
        // §4: "A set loses duplicate information. Use a multiset/count map."
        val doubled = realizer.analyze(
            JazzChordParser.parseOrThrow("Cmaj7"),
            listOf(48, 60, 64, 67, 71).map(::MidiNote),
        )
        val requirement = ExerciseRequirement.ExactVoicing(doubled)

        assertTrue(evaluator.evaluate(requirement, attempt(48, 60, 64, 67, 71)).isCorrect)

        val single = evaluator.evaluate(requirement, attempt(60, 64, 67, 71))
        assertTrue(!single.isCorrect, "The lower root is missing and that is a real difference")
        assertTrue(single.missing.any { it.exactNote == MidiNote(48) })
    }

    @Test
    fun `an octave-equivalent voicing may be played in another register`() {
        val voicing = realizer.analyze(JazzChordParser.parseOrThrow("Cmaj7"), listOf(60, 64, 67, 71).map(::MidiNote))
        val requirement = ExerciseRequirement.ExactVoicing(voicing, octaveEquivalent = true)

        val result = evaluator.evaluate(requirement, attempt(48, 52, 55, 59))
        assertTrue(result.isCorrect)
        assertEquals(Verdict.CORRECT_WITH_ACCEPTED_VARIATION, result.verdict)

        // The shape still has to hold: moving one voice is not an octave transposition.
        assertTrue(!evaluator.evaluate(requirement, attempt(48, 64, 67, 71)).isCorrect)
    }

    @Test
    fun `bass and top are called out separately`() {
        val voicing = realizer.analyze(JazzChordParser.parseOrThrow("Cmaj7"), listOf(60, 64, 67, 71).map(::MidiNote))
        val requirement = ExerciseRequirement.ExactVoicing(voicing)

        val result = evaluator.evaluate(requirement, attempt(64, 67, 71, 72))
        assertTrue(result.semanticErrors.any { it is PerformanceError.WrongBass })
        assertTrue(result.semanticErrors.any { it is PerformanceError.WrongTopNote })
    }

    // --- Degree-aware policy matching (§5) --------------------------------------------------

    @Test
    fun `a rootless G13 is correct without its root`() {
        // The example in §5, and the rule 03_JAZZ_CURRICULUM.md §9 insists on: "Do not evaluate
        // a rootless voicing using a generic root missing = incorrect rule."
        val g13 = JazzChordParser.parseOrThrow("G13")
        val requirement = ExerciseRequirement.ChordPolicyMatch(
            chord = g13,
            policy = VoicingPolicy(
                requiredDegrees = setOf(
                    ChordDegree.THIRD,
                    ChordDegree.FLAT_SEVENTH,
                    ChordDegree.NINTH,
                    ChordDegree.THIRTEENTH,
                ),
                optionalDegrees = setOf(ChordDegree.FIFTH),
                requireRoot = false,
                pitchRange = 48..84,
            ),
        )

        // B, E, F, A: third, thirteenth, seventh, ninth — no G anywhere.
        val result = evaluator.evaluate(requirement, attempt(59, 64, 65, 69))
        assertTrue(result.isCorrect, "Got ${result.semanticErrors}")
    }

    @Test
    fun `the same voicing fails a policy that requires the root`() {
        val g13 = JazzChordParser.parseOrThrow("G13")
        val requirement = ExerciseRequirement.ChordPolicyMatch(
            chord = g13,
            policy = VoicingPolicy(
                requiredDegrees = setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.FLAT_SEVENTH),
                requireRoot = true,
                pitchRange = 48..84,
            ),
        )

        val result = evaluator.evaluate(requirement, attempt(59, 64, 65, 69))
        assertTrue(result.semanticErrors.any { it is PerformanceError.WrongRoot })
    }

    @Test
    fun `a register constraint is enforced`() {
        val requirement = ExerciseRequirement.ChordPolicyMatch(
            chord = JazzChordParser.parseOrThrow("Cmaj7"),
            policy = VoicingPolicy(
                requiredDegrees = setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.SEVENTH),
                optionalDegrees = setOf(ChordDegree.FIFTH),
                pitchRange = 48..72,
            ),
        )

        assertTrue(evaluator.evaluate(requirement, attempt(48, 52, 59)).isCorrect)

        val tooHigh = evaluator.evaluate(requirement, attempt(72, 76, 83))
        assertTrue(tooHigh.semanticErrors.any { it is PerformanceError.RegisterViolation })
    }

    @Test
    fun `bass and top requirements are enforced by degree`() {
        val chord = JazzChordParser.parseOrThrow("Cmaj7")
        val requirement = ExerciseRequirement.ChordPolicyMatch(
            chord = chord,
            policy = VoicingPolicy(
                requiredDegrees = chord.requiredDegrees,
                bassRequirement = BassRequirement.DegreeInBass(ChordDegree.THIRD),
                topNoteRequirement = TopNoteRequirement.DegreeOnTop(ChordDegree.ROOT),
                pitchRange = 48..84,
            ),
        )

        assertTrue(evaluator.evaluate(requirement, attempt(52, 55, 59, 60)).isCorrect, "First inversion")

        val rootPosition = evaluator.evaluate(requirement, attempt(48, 52, 55, 59))
        assertTrue(rootPosition.semanticErrors.any { it is PerformanceError.WrongBass })
    }

    // --- Timing (§6) ------------------------------------------------------------------------

    @Test
    fun `a rolled chord passes a rolled policy and fails a simultaneous one`() {
        val requirement = pitchSetOf("Cmaj7")
        val rolled = attempt(60, 64, 67, 71, spreadMillis = 180)

        assertTrue(evaluator.evaluate(requirement, rolled, OnsetPolicy.NormalRoll).isCorrect)

        val strict = evaluator.evaluate(requirement, rolled, OnsetPolicy.Together)
        assertTrue(!strict.isCorrect)
        val violation = assertIs<PerformanceError.OnsetSpreadViolation>(
            strict.semanticErrors.first { it is PerformanceError.OnsetSpreadViolation },
        )
        assertEquals(180L, violation.spreadMillis)
        assertEquals(60, violation.allowedMillis)
    }

    @Test
    fun `timing reports latency and spread separately`() {
        val requirement = pitchSetOf("Cmaj7")
        val result = evaluator.evaluate(requirement, attempt(60, 64, 67, 71, spreadMillis = 90))

        val timing = result.timing!!
        assertEquals(90L, timing.onsetSpreadMillis)
        assertEquals(0L, timing.responseLatencyMillis, "Thinking time is a different measurement")
        assertTrue(timing.spreadWithinPolicy)
    }

    // --- Inconclusive attempts (§2) ---------------------------------------------------------

    @Test
    fun `losing the keyboard is never a wrong answer`() {
        val result = evaluator.evaluate(
            pitchSetOf("Cmaj7"),
            attempt(60, 64, completion = CaptureCompletion.DeviceLost),
        )

        assertEquals(Verdict.ABORTED_DEVICE_LOSS, result.verdict)
        assertTrue(result.verdict.isInconclusive)
        assertTrue(result.semanticErrors.isEmpty(), "Nothing to correct; the cable came out")
    }

    @Test
    fun `playing nothing is not a wrong answer either`() {
        val result = evaluator.evaluate(
            pitchSetOf("Cmaj7"),
            PerformanceAttempt.empty(startedAtNanos = 0, completedAtNanos = 1_000),
        )

        assertEquals(Verdict.NO_ATTEMPT, result.verdict)
        assertTrue(result.verdict.isInconclusive)
        assertEquals(PerformanceError.NoNotesPlayed, result.explanation.primaryError)
    }

    // --- Error ordering (§8) ----------------------------------------------------------------

    @Test
    fun `errors are ordered by educational importance, not collection order`() {
        val requirement = ExerciseRequirement.ChordPolicyMatch(
            chord = JazzChordParser.parseOrThrow("Cmaj7"),
            policy = VoicingPolicy(
                requiredDegrees = setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.SEVENTH),
                bassRequirement = BassRequirement.RootInBass,
                pitchRange = 48..84,
            ),
        )
        // Wrong chord entirely, spread wide, in the wrong inversion.
        val result = evaluator.evaluate(
            requirement,
            attempt(62, 66, 69, spreadMillis = 400),
            OnsetPolicy.Together,
        )

        val ranks = result.semanticErrors.map { it.rank }
        assertEquals(ranks.sorted(), ranks, "Ordered by importance")
        assertTrue(
            result.explanation.primaryError is PerformanceError.WrongRoot,
            "Being on the wrong chord matters more than how it was spread: ${result.semanticErrors}",
        )
    }

    @Test
    fun `metrics describe how much of the chord arrived`() {
        val result = evaluator.evaluate(pitchSetOf("Cmaj7"), attempt(60, 64, 67))

        assertEquals(4, result.metrics.requiredToneCount)
        assertEquals(3, result.metrics.matchedToneCount)
        assertEquals(0.75, result.metrics.completeness)
        assertEquals(3, result.metrics.notesPlayed)
    }

    @Test
    fun `a correct answer carries no errors and a correct headline`() {
        val result = evaluator.evaluate(pitchSetOf("Cmaj7"), attempt(60, 64, 67, 71))

        assertEquals(Verdict.CORRECT, result.verdict)
        assertTrue(result.semanticErrors.isEmpty())
        assertEquals(4, result.matched.size)
        assertTrue(result.missing.isEmpty())
    }
}
