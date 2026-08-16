package com.harmonygates.core.music

import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.mastery.Evidence
import com.harmonygates.core.music.mastery.MasteryEvent
import com.harmonygates.core.music.mastery.MasteryUpdater
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.pitch.SpelledPitchClass
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The mastery model.
 *
 * 06_PERFORMANCE_EVALUATION_AND_SCORING.md §9 asks for "a transparent deterministic update
 * algorithm", and transparent means a person can predict it. So these tests are written as
 * predictions: play this sequence, and the number should be that.
 */
class MasteryTest {

    private val skill = SkillId("skill.dom7.build")
    private val updater = MasteryUpdater()
    private val start: Instant = Instant.parse("2026-01-01T10:00:00Z")

    private fun event(
        evidence: Evidence,
        at: Instant = start,
        errors: List<ErrorClass> = emptyList(),
        root: String? = null,
        responseMillis: Long? = null,
    ) = MasteryEvent(
        skillId = skill,
        evidence = evidence,
        at = at,
        responseMillis = responseMillis,
        errors = errors,
        root = root?.let { SpelledPitchClass.parseOrNull(it) },
    )

    private fun runOf(vararg evidence: Evidence): SkillMastery =
        updater.replay(skill, evidence.map { event(it) })

    // --- Evidence weights --------------------------------------------------------------------

    @Test
    fun `the four evidence weights are the ones the specification names`() {
        assertEquals(1.0, Evidence.INDEPENDENT_CORRECT.weight)
        assertEquals(0.8, Evidence.CORRECT_WITH_REDUCED_ASSISTANCE.weight)
        assertEquals(0.5, Evidence.CORRECT_AFTER_HINT.weight)
        assertEquals(0.0, Evidence.INCORRECT.weight)
    }

    @Test
    fun `a single independent correct answer is full evidence, once`() {
        val mastery = runOf(Evidence.INDEPENDENT_CORRECT)

        assertEquals(1.0, mastery.estimate)
        assertEquals(1, mastery.attempts)
        assertEquals(1, mastery.successfulAttempts)
    }

    @Test
    fun `a hint costs half the evidence, not the answer`() {
        val hinted = runOf(Evidence.CORRECT_AFTER_HINT)

        assertEquals(0.5, hinted.estimate)
        assertEquals(1, hinted.successfulAttempts, "It was still a correct answer")
    }

    @Test
    fun `recent work outweighs old work`() {
        // Ten guided answers followed by four independent ones. An unweighted mean would still
        // be under 0.9; the point of recency weighting is that the recent four count for more.
        val events = List(10) { Evidence.CORRECT_AFTER_HINT } + List(4) { Evidence.INDEPENDENT_CORRECT }
        val mastery = updater.replay(skill, events.map { event(it) })
        val unweighted = events.takeLast(MasteryUpdater.DEFAULT_WINDOW).map { it.weight }.average()

        assertTrue(
            mastery.estimate > unweighted,
            "Recent independent work should pull the estimate above the flat mean " +
                "(${mastery.estimate} vs $unweighted)",
        )
    }

    @Test
    fun `old failures stop counting once they are out of the window`() {
        val failures = List(MasteryUpdater.DEFAULT_WINDOW) { Evidence.INCORRECT }
        val recoveries = List(MasteryUpdater.DEFAULT_WINDOW) { Evidence.INDEPENDENT_CORRECT }
        val mastery = updater.replay(skill, (failures + recoveries).map { event(it) })

        assertEquals(1.0, mastery.estimate, "A window's worth of clean answers clears the record")
        assertEquals(MasteryUpdater.DEFAULT_WINDOW * 2, mastery.attempts, "But the history remains")
    }

    @Test
    fun `the estimate never leaves zero to one`() {
        val everything = Evidence.entries.flatMap { evidence -> List(5) { evidence } }
        val mastery = updater.replay(skill, everything.map { event(it) })

        assertTrue(mastery.estimate in 0.0..1.0, "Got ${mastery.estimate}")
    }

    @Test
    fun `replaying the same events always gives the same answer`() {
        val events = listOf(
            Evidence.INDEPENDENT_CORRECT,
            Evidence.INCORRECT,
            Evidence.CORRECT_AFTER_HINT,
            Evidence.INDEPENDENT_CORRECT,
        ).map { event(it) }

        assertEquals(updater.replay(skill, events), updater.replay(skill, events))
    }

    // --- What went wrong ---------------------------------------------------------------------

    @Test
    fun `every performance error maps to an error class`() {
        val errors = listOf(
            PerformanceError.WrongRoot(SpelledPitchClass.parseOrNull("C")!!, null),
            PerformanceError.NoNotesPlayed,
            PerformanceError.OnsetSpreadViolation(300, 120),
        )
        assertEquals(
            listOf(ErrorClass.WRONG_ROOT, ErrorClass.NOTHING_PLAYED, ErrorClass.ONSET_TIMING),
            errors.map { ErrorClass.of(it) },
        )
    }

    @Test
    fun `the histogram counts what keeps going wrong`() {
        val mastery = updater.replay(
            skill,
            listOf(
                event(Evidence.INCORRECT, errors = listOf(ErrorClass.MISSING_CHORD_TONE)),
                event(Evidence.INCORRECT, errors = listOf(ErrorClass.MISSING_CHORD_TONE, ErrorClass.EXTRA_NOTE)),
                event(Evidence.INCORRECT, errors = listOf(ErrorClass.MISSING_CHORD_TONE)),
            ),
        )

        assertEquals(
            ErrorClass.MISSING_CHORD_TONE to 3,
            mastery.recurringErrors.first(),
            "The commonest mistake is what a review should target",
        )
    }

    @Test
    fun `one attempt cannot log the same mistake twice`() {
        // Four notes missing from one chord is one missing-tone problem, not four.
        val mastery = updater.replay(
            skill,
            listOf(event(Evidence.INCORRECT, errors = List(4) { ErrorClass.MISSING_CHORD_TONE })),
        )

        assertEquals(1, mastery.errorHistogram[ErrorClass.MISSING_CHORD_TONE])
    }

    @Test
    fun `a rushed onset is not a theory error`() {
        assertTrue(ErrorClass.WRONG_QUALITY.isCriticalTheoryError)
        assertTrue(ErrorClass.MISSING_CHORD_TONE.isCriticalTheoryError)
        assertFalse(ErrorClass.ONSET_TIMING.isCriticalTheoryError)
        assertFalse(ErrorClass.REGISTER.isCriticalTheoryError)
    }

    // --- Coverage and review -----------------------------------------------------------------

    @Test
    fun `roots are remembered so a gate can require breadth`() {
        val mastery = updater.replay(
            skill,
            listOf("C", "F", "Bb", "C").map { event(Evidence.INDEPENDENT_CORRECT, root = it) },
        )

        assertEquals(3, mastery.rootsCovered.size, "C twice is still one root")
    }

    @Test
    fun `review moves further out with each success and snaps back on a miss`() {
        val after2 = updater.replay(skill, List(2) { event(Evidence.INDEPENDENT_CORRECT) })
        val after5 = updater.replay(skill, List(5) { event(Evidence.INDEPENDENT_CORRECT) })
        val lapsed = updater.replay(
            skill,
            List(5) { event(Evidence.INDEPENDENT_CORRECT) } + event(Evidence.INCORRECT),
        )

        val gap2 = Duration.between(start, assertNotNull(after2.nextReviewAt))
        val gap5 = Duration.between(start, assertNotNull(after5.nextReviewAt))
        val gapLapsed = Duration.between(start, assertNotNull(lapsed.nextReviewAt))

        assertTrue(gap5 > gap2, "A longer streak earns a longer gap ($gap5 vs $gap2)")
        assertEquals(
            MasteryUpdater.DEFAULT_REVIEW_INTERVALS.first(),
            gapLapsed,
            "One miss puts the skill back to the shortest interval",
        )
    }

    @Test
    fun `a skill is due when its review time arrives`() {
        val mastery = updater.replay(skill, listOf(event(Evidence.INDEPENDENT_CORRECT)))
        val due = assertNotNull(mastery.nextReviewAt)

        assertFalse(mastery.isDueForReview(due.minusSeconds(1)))
        assertTrue(mastery.isDueForReview(due))
        assertTrue(mastery.isDueForReview(due.plusSeconds(1)))
    }

    @Test
    fun `an untouched skill is never due`() {
        assertFalse(SkillMastery(skill).isDueForReview(start))
    }

    @Test
    fun `the median response time follows the player rather than one slow answer`() {
        val steady = updater.replay(
            skill,
            List(8) { event(Evidence.INDEPENDENT_CORRECT, responseMillis = 2_000) },
        )
        val withOutlier = updater.replay(
            skill,
            List(8) { event(Evidence.INDEPENDENT_CORRECT, responseMillis = 2_000) } +
                event(Evidence.INDEPENDENT_CORRECT, responseMillis = 60_000),
        )

        assertEquals(2_000L, steady.medianResponseMillis)
        val median = assertNotNull(withOutlier.medianResponseMillis)
        assertTrue(median < 3_000, "One 60-second answer should barely move the median: $median")
    }

    @Test
    fun `an attempt with no timing does not erase the timing already known`() {
        val mastery = updater.replay(
            skill,
            listOf(
                event(Evidence.INDEPENDENT_CORRECT, responseMillis = 1_500),
                event(Evidence.INDEPENDENT_CORRECT, responseMillis = null),
            ),
        )

        assertEquals(1_500L, mastery.medianResponseMillis)
    }
}
