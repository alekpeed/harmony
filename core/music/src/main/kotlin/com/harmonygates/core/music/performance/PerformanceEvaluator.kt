package com.harmonygates.core.music.performance

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.PitchClass
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.TopNoteRequirement
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingPolicy

/** Decides whether a performance satisfied a requirement. */
public interface PerformanceEvaluator {
    public fun evaluate(
        requirement: ExerciseRequirement,
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy = OnsetPolicy.NormalRoll,
    ): EvaluationResult
}

/**
 * The production evaluator.
 *
 * 06_PERFORMANCE_EVALUATION_AND_SCORING.md §1: validity first, score second. This class only
 * answers "did that satisfy the requirement, and if not, why" — mastery weighting arrives in
 * Phase 5 and reads these results rather than replacing them.
 *
 * Three things it deliberately does not do:
 *
 * - **Compare raw MIDI lists** (§3). "Play any Cmaj7" is a question about pitch classes; the
 *   octave a player chose is not part of the answer.
 * - **Lose duplicate voices** (§4). An exact voicing is compared as a multiset, because a
 *   doubled root is musically different from a single one and a `Set` would silently agree.
 * - **Assume a chord needs its root** (§5). Region 7 is built on voicings that omit it, so
 *   whether the root is required comes from the policy, never from a rule in here.
 */
public class DefaultPerformanceEvaluator(
    private val realizer: ChordRealizer = DefaultChordRealizer(),
) : PerformanceEvaluator {

    override fun evaluate(
        requirement: ExerciseRequirement,
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy,
    ): EvaluationResult {
        // A device that vanished mid-chord is not a wrong answer, and an empty attempt is not
        // evidence of anything. Both are settled before any music is looked at.
        inconclusiveResult(attempt, onsetPolicy)?.let { return it }

        return when (requirement) {
            is ExerciseRequirement.PitchSet -> evaluatePitchSet(requirement, attempt, onsetPolicy)
            is ExerciseRequirement.ExactVoicing -> evaluateExactVoicing(requirement, attempt, onsetPolicy)
            is ExerciseRequirement.ChordPolicyMatch -> evaluatePolicy(requirement, attempt, onsetPolicy)
            is ExerciseRequirement.VoiceLeadingTarget ->
                evaluatePolicy(
                    ExerciseRequirement.ChordPolicyMatch(requirement.destination, requirement.policy),
                    attempt,
                    onsetPolicy,
                )
            // Timed sequences and sight-reading phrases are graded step by step by the session
            // engine in Phases 9 and 10; a single attempt is not the unit they are judged in.
            is ExerciseRequirement.TimedSequence,
            is ExerciseRequirement.SightReadingPhrase,
            -> unsupported(attempt, onsetPolicy)
        }
    }

    // --- Pitch-class matching (§3) ----------------------------------------------------------

    private fun evaluatePitchSet(
        requirement: ExerciseRequirement.PitchSet,
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy,
    ): EvaluationResult {
        val played = attempt.finalEffectiveNotes
        val playedClasses = played.map { it.pitchClass }.toSet()
        val expected = requirement.pitchClasses

        val matchedClasses = expected.filter { it.pitchClass in playedClasses }.toSet()
        val missingTones = (expected - matchedClasses).map { pitchClass ->
            ExpectedTone(pitchClass, degreeOf(requirement.chord, pitchClass))
        }.toSet()

        val expectedClasses = expected.map { it.pitchClass }.toSet()
        val extraNotes = played.filter { it.pitchClass !in expectedClasses }.toSet()

        val errors = mutableListOf<PerformanceError>()
        missingTones.forEach { errors += PerformanceError.MissingDegree(it) }
        if (!requirement.allowExtraNotes) {
            extraNotes.forEach { errors += PerformanceError.ExtraTone(it, degreeOf(requirement.chord, it)) }
        }
        if (!requirement.allowOctaveDoubling) {
            duplicatedClasses(played).forEach { note -> errors += PerformanceError.ExtraTone(note) }
        }
        requirement.requiredBass?.let { bass ->
            if (played.minOrNull()?.pitchClass != bass.pitchClass) {
                errors += PerformanceError.WrongBass(bass, played.minOrNull())
            }
        }
        errors += onsetErrors(attempt, onsetPolicy)

        val matchedNotes = played.filter { it.pitchClass in expectedClasses }.toSet()
        return result(
            attempt = attempt,
            onsetPolicy = onsetPolicy,
            matched = matchedNotes,
            missing = missingTones,
            extra = if (requirement.allowExtraNotes) emptySet() else extraNotes,
            errors = errors,
            requiredCount = expected.size,
            matchedCount = matchedClasses.size,
        )
    }

    // --- Exact voicing (§4) -----------------------------------------------------------------

    private fun evaluateExactVoicing(
        requirement: ExerciseRequirement.ExactVoicing,
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy,
    ): EvaluationResult {
        val target = requirement.voicing
        val played = attempt.finalEffectiveNotes
        val expectedNotes = if (requirement.octaveEquivalent) {
            transposeToMatchRegister(target, played)
        } else {
            target.pitches
        }

        // Multisets, not sets: a doubled voice is part of the answer.
        val expectedCounts = expectedNotes.groupingBy { it }.eachCount()
        val playedCounts = played.groupingBy { it }.eachCount()

        val missingNotes = expectedCounts.flatMap { (note, count) ->
            List((count - (playedCounts[note] ?: 0)).coerceAtLeast(0)) { note }
        }
        val extraNotes = playedCounts.flatMap { (note, count) ->
            List((count - (expectedCounts[note] ?: 0)).coerceAtLeast(0)) { note }
        }

        val errors = mutableListOf<PerformanceError>()
        missingNotes.distinct().forEach { note ->
            errors += PerformanceError.MissingDegree(
                ExpectedTone(
                    pitchClass = spellingOf(target, note),
                    degree = degreeOf(target.chord, note),
                    exactNote = note,
                ),
            )
        }
        extraNotes.distinct().forEach { errors += PerformanceError.ExtraTone(it, degreeOf(target.chord, it)) }

        // Bass and top are called out separately: they are what a listener hears as the shape.
        val expectedBass = expectedNotes.minOrNull()
        val expectedTop = expectedNotes.maxOrNull()
        if (played.isNotEmpty() && expectedBass != null && played.min() != expectedBass) {
            errors += PerformanceError.WrongBass(spellingOf(target, expectedBass), played.min())
        }
        if (played.isNotEmpty() && expectedTop != null && played.max() != expectedTop) {
            errors += PerformanceError.WrongTopNote(expectedTop, played.max())
        }
        errors += onsetErrors(attempt, onsetPolicy)

        val matchedCount = expectedCounts.entries.sumOf { (note, count) ->
            minOf(count, playedCounts[note] ?: 0)
        }
        return result(
            attempt = attempt,
            onsetPolicy = onsetPolicy,
            matched = played.filter { it in expectedCounts }.toSet(),
            missing = missingNotes.distinct().map {
                ExpectedTone(spellingOf(target, it), degreeOf(target.chord, it), it)
            }.toSet(),
            extra = extraNotes.toSet(),
            errors = errors,
            requiredCount = expectedNotes.size,
            matchedCount = matchedCount,
            acceptedVariation = requirement.octaveEquivalent && expectedNotes != target.pitches,
        )
    }

    // --- Degree-aware policy matching (§5) --------------------------------------------------

    private fun evaluatePolicy(
        requirement: ExerciseRequirement.ChordPolicyMatch,
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy,
    ): EvaluationResult {
        val chord = requirement.chord
        val policy = requirement.policy
        val played = attempt.finalEffectiveNotes
        val analysis = if (played.isEmpty()) null else realizer.analyze(chord, played)

        val degreesPlayed = analysis?.metadata?.includedDegrees.orEmpty()
        val spelled = spelledDegrees(chord)

        val required = policy.requiredDegrees.ifEmpty { chord.requiredDegrees }
        val missingDegrees = required - degreesPlayed
        val permitted = required + policy.optionalDegrees.ifEmpty { chord.optionalDegrees }

        val errors = mutableListOf<PerformanceError>()

        // A wrong root is the most useful thing to say, so it is diagnosed before anything else.
        if (played.isNotEmpty() && ChordDegree.ROOT in required && ChordDegree.ROOT !in degreesPlayed) {
            errors += PerformanceError.WrongRoot(chord.root, analysis?.spelledPitches?.first()?.pitchClass)
        }
        missingDegrees.forEach { degree ->
            spelled[degree]?.let { errors += PerformanceError.MissingDegree(ExpectedTone(it, degree)) }
        }

        val extraNotes = mutableSetOf<MidiNote>()
        analysis?.tones?.forEach { tone ->
            val degree = tone.degree
            val note = tone.pitch.midiNote
            when {
                degree == null -> {
                    extraNotes += note
                    errors += PerformanceError.ExtraTone(note)
                }

                degree in policy.disallowedDegrees || degree in chord.forbiddenDegrees -> {
                    extraNotes += note
                    errors += PerformanceError.ExtraTone(note, degree)
                }

                degree !in permitted -> {
                    extraNotes += note
                    errors += PerformanceError.ExtraTone(note, degree)
                }
            }
        }

        if (policy.requireRoot && analysis?.metadata?.isRootless == true) {
            errors += PerformanceError.WrongRoot(chord.root, null)
        }
        if (!policy.allowDoubling) {
            analysis?.metadata?.doubledDegrees?.forEach { degree ->
                spelled[degree]?.let { errors += PerformanceError.ExtraTone(played.first(), degree) }
            }
        }
        policy.pitchRange?.let { range ->
            played.filter { it.value !in range }
                .forEach { errors += PerformanceError.RegisterViolation(it, range) }
        }
        bassError(policy.bassRequirement, chord, analysis)?.let { errors += it }
        topError(policy.topNoteRequirement, analysis)?.let { errors += it }
        errors += onsetErrors(attempt, onsetPolicy)

        val matchedDegrees = required intersect degreesPlayed
        return result(
            attempt = attempt,
            onsetPolicy = onsetPolicy,
            matched = analysis?.tones?.filter { it.degree != null }?.map { it.pitch.midiNote }?.toSet().orEmpty(),
            missing = missingDegrees.mapNotNull { degree ->
                spelled[degree]?.let { ExpectedTone(it, degree) }
            }.toSet(),
            extra = extraNotes,
            errors = errors,
            requiredCount = required.size,
            matchedCount = matchedDegrees.size,
            // Anything beyond the required tones that the policy still permits is a variation
            // the exercise accepted rather than the canonical answer.
            acceptedVariation = degreesPlayed.any { it !in required && it in permitted },
        )
    }

    private fun bassError(
        requirement: BassRequirement,
        chord: ChordSpec,
        analysis: Voicing?,
    ): PerformanceError? {
        val bassDegree = analysis?.metadata?.bassDegree
        val bassNote = analysis?.bass
        return when (requirement) {
            BassRequirement.Unconstrained -> null

            BassRequirement.RootInBass ->
                if (bassDegree == ChordDegree.ROOT) null
                else PerformanceError.WrongBass(chord.root, bassNote)

            is BassRequirement.DegreeInBass ->
                if (bassDegree == requirement.degree) null
                else spelledDegrees(chord)[requirement.degree]
                    ?.let { PerformanceError.WrongBass(it, bassNote) }

            is BassRequirement.PitchClassInBass ->
                if (bassNote?.pitchClass == requirement.pitchClass.pitchClass) null
                else PerformanceError.WrongBass(requirement.pitchClass, bassNote)

            is BassRequirement.DegreeNotInBass ->
                if (bassDegree != requirement.degree) null
                else PerformanceError.WrongBass(chord.root, bassNote)
        }
    }

    private fun topError(requirement: TopNoteRequirement?, analysis: Voicing?): PerformanceError? =
        when (requirement) {
            null -> null
            is TopNoteRequirement.DegreeOnTop ->
                if (analysis?.metadata?.topDegree == requirement.degree) null
                else PerformanceError.WrongTopNote(null, analysis?.top)

            is TopNoteRequirement.ExactNoteOnTop ->
                if (analysis?.top == requirement.note) null
                else PerformanceError.WrongTopNote(requirement.note, analysis?.top)

            is TopNoteRequirement.PitchClassOnTop ->
                if (analysis?.top?.pitchClass == requirement.pitchClass) null
                else PerformanceError.WrongTopNote(null, analysis?.top)
        }

    // --- Shared -----------------------------------------------------------------------------

    private fun inconclusiveResult(
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy,
    ): EvaluationResult? {
        val verdict = when {
            attempt.completion == CaptureCompletion.DeviceLost -> Verdict.ABORTED_DEVICE_LOSS
            attempt.isEmpty -> Verdict.NO_ATTEMPT
            else -> return null
        }
        val headline = if (verdict == Verdict.ABORTED_DEVICE_LOSS) {
            FeedbackModel.Headline.DEVICE_LOST
        } else {
            FeedbackModel.Headline.NOTHING_PLAYED
        }
        val errors = if (verdict == Verdict.NO_ATTEMPT) listOf(PerformanceError.NoNotesPlayed) else emptyList()
        return EvaluationResult(
            verdict = verdict,
            matched = emptySet(),
            missing = emptySet(),
            extra = emptySet(),
            semanticErrors = errors,
            timing = timingOf(attempt, onsetPolicy),
            metrics = PerformanceMetrics.of(attempt, required = 0, matched = 0, extra = 0),
            explanation = FeedbackModel(headline, errors),
        )
    }

    private fun unsupported(attempt: PerformanceAttempt, onsetPolicy: OnsetPolicy): EvaluationResult =
        EvaluationResult(
            verdict = Verdict.NO_ATTEMPT,
            matched = emptySet(),
            missing = emptySet(),
            extra = emptySet(),
            semanticErrors = emptyList(),
            timing = timingOf(attempt, onsetPolicy),
            metrics = PerformanceMetrics.of(attempt, required = 0, matched = 0, extra = 0),
            explanation = FeedbackModel(FeedbackModel.Headline.NOTHING_PLAYED, emptyList()),
        )

    private fun onsetErrors(attempt: PerformanceAttempt, policy: OnsetPolicy): List<PerformanceError> {
        if (policy.permits(attempt.onsetSpreadNanos)) return emptyList()
        val allowed = when (policy) {
            is OnsetPolicy.Simultaneous -> policy.maxSpreadMillis
            is OnsetPolicy.RolledAllowed -> policy.maxSpreadMillis
            OnsetPolicy.Unrestricted -> return emptyList()
        }
        return listOf(
            PerformanceError.OnsetSpreadViolation(attempt.onsetSpreadMillis ?: 0L, allowed),
        )
    }

    private fun timingOf(attempt: PerformanceAttempt, policy: OnsetPolicy) = TimingEvaluation(
        responseLatencyMillis = attempt.responseLatencyNanos?.let { it / PerformanceAttempt.NANOS_PER_MILLI },
        onsetSpreadMillis = attempt.onsetSpreadMillis,
        onsetPolicy = policy,
        spreadWithinPolicy = policy.permits(attempt.onsetSpreadNanos),
    )

    @Suppress("LongParameterList")
    private fun result(
        attempt: PerformanceAttempt,
        onsetPolicy: OnsetPolicy,
        matched: Set<MidiNote>,
        missing: Set<ExpectedTone>,
        extra: Set<MidiNote>,
        errors: List<PerformanceError>,
        requiredCount: Int,
        matchedCount: Int,
        acceptedVariation: Boolean = false,
    ): EvaluationResult {
        val ordered = errors.byEducationalImportance()
        val verdict = when {
            ordered.isEmpty() && acceptedVariation -> Verdict.CORRECT_WITH_ACCEPTED_VARIATION
            ordered.isEmpty() -> Verdict.CORRECT
            // Something of the chord is there — all of it plus an extra note, or most of it.
            // Worth saying "almost" and naming the gap rather than a flat no.
            matchedCount > 0 -> Verdict.PARTIAL
            else -> Verdict.INCORRECT
        }
        val headline = when (verdict) {
            Verdict.CORRECT -> FeedbackModel.Headline.CORRECT
            Verdict.CORRECT_WITH_ACCEPTED_VARIATION -> FeedbackModel.Headline.CORRECT_VARIATION
            Verdict.PARTIAL -> FeedbackModel.Headline.ALMOST
            else -> FeedbackModel.Headline.NOT_YET
        }
        return EvaluationResult(
            verdict = verdict,
            matched = matched,
            missing = missing,
            extra = extra,
            semanticErrors = ordered,
            timing = timingOf(attempt, onsetPolicy),
            metrics = PerformanceMetrics.of(attempt, requiredCount, matchedCount, extra.size),
            explanation = FeedbackModel(headline, ordered),
        )
    }

    private fun spelledDegrees(chord: ChordSpec): Map<ChordDegree, SpelledPitchClass> =
        realizer.spelledDegrees(chord)

    private fun degreeOf(chord: ChordSpec?, note: MidiNote): ChordDegree? =
        chord?.let { realizer.degreeOf(it, note.pitchClass) }

    private fun degreeOf(chord: ChordSpec?, pitchClass: SpelledPitchClass): ChordDegree? =
        chord?.let { realizer.degreeOf(it, pitchClass.pitchClass) }

    private fun spellingFor(chord: ChordSpec, note: MidiNote): SpelledPitchClass =
        spelledDegrees(chord).values.firstOrNull { it.pitchClass == note.pitchClass } ?: chord.root

    private fun spellingOf(voicing: Voicing, note: MidiNote): SpelledPitchClass =
        voicing.spelledPitches.firstOrNull { it.midiNote == note }?.pitchClass
            ?: spellingFor(voicing.chord, note)

    /** Notes whose pitch class appears more than once. */
    private fun duplicatedClasses(played: List<MidiNote>): List<MidiNote> {
        val seen = mutableSetOf<PitchClass>()
        return played.sorted().filter { !seen.add(it.pitchClass) }
    }

    /**
     * Shifts the target by whole octaves to sit where the player actually played.
     *
     * Used only when the exercise declared octave equivalence: the shape must be preserved, so
     * the whole voicing moves together rather than each note finding its own octave.
     */
    private fun transposeToMatchRegister(target: Voicing, played: List<MidiNote>): List<MidiNote> {
        val playedBass = played.minOrNull() ?: return target.pitches
        val difference = playedBass.value - target.bass.value
        if (difference.mod(12) != 0) return target.pitches
        val shift = difference
        return target.pitches.mapNotNull { MidiNote.orNull(it.value + shift) }
            .takeIf { it.size == target.pitches.size }
            ?: target.pitches
    }
}
