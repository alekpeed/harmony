package com.harmonygates.core.data

import com.harmonygates.core.data.progress.Mappers
import com.harmonygates.core.music.exercise.ExerciseDefinitionId
import com.harmonygates.core.music.exercise.ExerciseInstance
import com.harmonygates.core.music.exercise.ExerciseInstanceId
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.mastery.Evidence
import com.harmonygates.core.music.mastery.MasteryEvent
import com.harmonygates.core.music.mastery.MasteryUpdater
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.core.music.performance.NormalizedNoteEvent
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.performance.PerformanceMetrics
import com.harmonygates.core.music.performance.Verdict
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.session.AttemptRecord
import com.harmonygates.core.music.voicing.Inversion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The encoding progress is stored in.
 *
 * This is where "app restart preserves progress" is actually decided. A perfect database still
 * loses a player's history if the mastery written into a row cannot be read back out of it, and
 * that failure is silent — the app starts, the screen is simply emptier than it was.
 */
class MappersTest {

    private val skill = SkillId("skill.dom7.build")
    private val updater = MasteryUpdater()
    private val now: Instant = Instant.parse("2026-04-01T09:30:00Z")
    private val realizer = DefaultChordRealizer()

    private fun root(name: String) = requireNotNull(SpelledPitchClass.parseOrNull(name))

    private fun mastery(): SkillMastery = updater.replay(
        skill,
        listOf(
            MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now, 1_800, emptyList(), root("C")),
            MasteryEvent(skill, Evidence.INCORRECT, now, 4_200, listOf(ErrorClass.MISSING_CHORD_TONE), root("Eb")),
            MasteryEvent(skill, Evidence.CORRECT_AFTER_HINT, now, 3_000, emptyList(), root("F#")),
            MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now, 1_500, emptyList(), root("Bb")),
        ),
    )

    @Test
    fun `a mastery record survives a round trip through storage`() {
        val original = mastery()
        val restored = Mappers.toDomain(Mappers.toEntity("profile-1", original))

        assertEquals(original, restored, "Everything known about a skill must come back unchanged")
    }

    @Test
    fun `the recent window keeps its order`() {
        val original = mastery()
        val restored = Mappers.toDomain(Mappers.toEntity("profile-1", original))

        assertEquals(
            original.recentEvidence,
            restored.recentEvidence,
            "Recency weighting reads the window in order, so the order is part of the data",
        )
    }

    @Test
    fun `an enharmonic root is stored as written, not as a pitch number`() {
        val sharpRoot = updater.apply(
            SkillMastery(skill),
            MasteryEvent(skill, Evidence.INDEPENDENT_CORRECT, now, root = root("F#")),
        )
        val restored = Mappers.toDomain(Mappers.toEntity("profile-1", sharpRoot))

        assertEquals(setOf(root("F#")), restored.rootsCovered, "F# and Gb are different keys to practise")
    }

    @Test
    fun `an empty mastery record round-trips without inventing anything`() {
        val blank = SkillMastery(skill)
        val restored = Mappers.toDomain(Mappers.toEntity("profile-1", blank))

        assertEquals(blank, restored)
        assertTrue(restored.rootsCovered.isEmpty())
        assertTrue(restored.errorHistogram.isEmpty())
    }

    // --- Attempts ------------------------------------------------------------------------------

    private fun attemptRecord(
        symbol: String = "G7",
        played: List<Int> = listOf(55, 59, 62, 65),
        verdict: Verdict = Verdict.CORRECT,
        hintsUsed: Int = 0,
        errors: List<PerformanceError> = emptyList(),
    ): AttemptRecord {
        val chord = JazzChordParser.parseOrThrow(symbol)
        val attempt = PerformanceAttempt(
            startedAtNanos = 0,
            completedAtNanos = 1_000,
            noteEvents = played.map { NormalizedNoteEvent(MidiNote(it), 80, 0) },
            finalEffectiveNotes = played.map { MidiNote(it) }.sorted(),
            onsetSpreadNanos = 0,
            sustainUsed = false,
        )
        return AttemptRecord(
            instance = ExerciseInstance(
                id = ExerciseInstanceId("ex-1"),
                definitionId = ExerciseDefinitionId("policy.seventh.build"),
                seed = 4242,
                skillIds = setOf(skill, SkillId("skill.seventh.inversion")),
                chord = chord,
                requirement = ExerciseRequirement.PitchSet(realizer.chordTones(chord).toSet(), chord = chord),
                presentation = PresentationSpec.Independent,
                inversion = Inversion.ROOT,
                spelledTones = realizer.chordTones(chord),
                targetNotes = played,
            ),
            attempt = attempt,
            result = EvaluationResult(
                verdict = verdict,
                matched = emptySet(),
                missing = emptySet(),
                extra = emptySet(),
                semanticErrors = errors,
                timing = null,
                metrics = PerformanceMetrics(
                    notesPlayed = played.size,
                    requiredToneCount = 4,
                    matchedToneCount = if (verdict.isCorrect) 4 else 2,
                    extraNoteCount = 0,
                    sustainUsed = false,
                    completeness = if (verdict.isCorrect) 1.0 else 0.5,
                ),
                explanation = FeedbackModel(FeedbackModel.Headline.CORRECT, errors),
            ),
            hintsUsed = hintsUsed,
        )
    }

    @Test
    fun `an attempt keeps the seed and both snapshots`() {
        val stored = Mappers.toStored(
            Mappers.toEntity("id", "session", attemptRecord(), Evidence.INDEPENDENT_CORRECT, now),
        )

        assertEquals(4242, stored.exerciseSeed, "The seed is what makes a bug reproducible")
        assertTrue(stored.expected.contains("G7"), "Expected snapshot: ${stored.expected}")
        assertTrue(stored.performed.contains("55"), "Performed snapshot: ${stored.performed}")
        assertEquals(2, stored.skillIds.size, "An attempt can be evidence about more than one skill")
    }

    @Test
    fun `a stored attempt reads back as evidence about every skill it touched`() {
        val entity = Mappers.toEntity("id", "session", attemptRecord(), Evidence.INDEPENDENT_CORRECT, now)
        val events = Mappers.toMasteryEvents(entity)

        assertEquals(2, events.size)
        assertTrue(events.all { it.evidence == Evidence.INDEPENDENT_CORRECT })
        assertEquals(root("G"), events.first().root, "The root is what root coverage is counted from")
    }

    @Test
    fun `a rebuild from stored attempts matches the running total`() {
        // The guarantee behind `rebuildMastery`: replaying history must land where the running
        // update did, or a weighting change would quietly move every player's progress.
        val records = listOf(
            attemptRecord(symbol = "G7") to Evidence.INDEPENDENT_CORRECT,
            attemptRecord(symbol = "Cmaj7", verdict = Verdict.INCORRECT) to Evidence.INCORRECT,
            attemptRecord(symbol = "Dm7", hintsUsed = 1) to Evidence.CORRECT_AFTER_HINT,
        )

        var running = SkillMastery(skill)
        var replayed = SkillMastery(skill)

        records.forEach { (record, evidence) ->
            val entity = Mappers.toEntity("id", "session", record, evidence, now)
            // The running update, as the app does it while playing.
            val live = MasteryEvent(
                skillId = skill,
                evidence = evidence,
                at = now,
                responseMillis = null,
                errors = emptyList(),
                root = record.instance.chord.root,
            )
            running = updater.apply(running, live)
            // The rebuild, from what was written down.
            Mappers.toMasteryEvents(entity).filter { it.skillId == skill }.forEach {
                replayed = updater.apply(replayed, it)
            }
        }

        assertEquals(running.estimate, replayed.estimate, "A rebuild must not move the estimate")
        assertEquals(running.rootsCovered, replayed.rootsCovered)
        assertEquals(running.attempts, replayed.attempts)
    }

    @Test
    fun `a skipped attempt is not evidence about anything`() {
        val skipped = attemptRecord(verdict = Verdict.NO_ATTEMPT).copy(skipped = true)
        val entity = Mappers.toEntity("id", "session", skipped, Evidence.INCORRECT, now)

        assertEquals(
            emptyList(),
            Mappers.toMasteryEvents(entity),
            "Declining to answer is not the same as answering wrongly",
        )
    }

    @Test
    fun `the diagnosis is stored as data, not as a sentence`() {
        val stored = Mappers.toStored(
            Mappers.toEntity(
                "id",
                "session",
                attemptRecord(
                    verdict = Verdict.PARTIAL,
                    errors = listOf(PerformanceError.WrongBass(root("G"), MidiNote(59))),
                ),
                Evidence.INCORRECT,
                now,
            ),
        )

        assertEquals(1, stored.errors.size)
        assertTrue(
            stored.errors.first().startsWith("WRONG_BASS"),
            "A stored diagnosis should be parseable later, not a rendered sentence: ${stored.errors}",
        )
    }
}
