package com.harmonygates.core.music.mastery

import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.session.AttemptRecord
import java.time.Instant

/**
 * Reads an attempt as evidence about the skills it exercised.
 *
 * One attempt produces one event per skill, because a gate that tests "build a dominant seventh"
 * and "put the third in the bass" together has learned something about both.
 *
 * Inconclusive attempts produce nothing at all. 06_PERFORMANCE_EVALUATION_AND_SCORING.md §2 is
 * firm that a keyboard unplugged mid-chord and a session left running are not wrong answers, and
 * scoring them as incorrect would punish a player for a loose cable.
 */
public object MasteryEvidence {

    public fun eventsFor(record: AttemptRecord, at: Instant): List<MasteryEvent> {
        if (record.result.verdict.isInconclusive) return emptyList()
        // A skipped exercise says nothing about skill either: the player declined to answer.
        if (record.skipped) return emptyList()

        val evidence = evidenceFor(record)
        val errors = record.result.semanticErrors.map { ErrorClass.of(it) }
        return record.instance.skillIds.map { skillId ->
            MasteryEvent(
                skillId = skillId,
                evidence = evidence,
                at = at,
                responseMillis = record.result.timing?.responseLatencyMillis,
                errors = errors,
                root = record.instance.chord.root,
            )
        }
    }

    /**
     * How much this attempt is worth.
     *
     * The three correct grades come straight from §9. What counts as "reduced assistance" is
     * read from the exercise's own presentation: if the player was shown the notes or the keys,
     * they were helped, whatever the gate called the difficulty. Phase 7's assistance profiles
     * set those switches rather than replacing them, so this keeps working when they arrive.
     */
    public fun evidenceFor(record: AttemptRecord): Evidence = when {
        !record.result.verdict.isCorrect -> Evidence.INCORRECT
        record.hintsUsed > 0 -> Evidence.CORRECT_AFTER_HINT
        record.instance.presentation.revealsTheAnswer -> Evidence.CORRECT_WITH_REDUCED_ASSISTANCE
        else -> Evidence.INDEPENDENT_CORRECT
    }

    /**
     * True when the screen showed enough that the player did not have to know the chord.
     *
     * The chord symbol on its own is the question, not a hint. Note names, highlighted keys, a
     * named bass note or a staff are the answer in another form.
     */
    private val PresentationSpec.revealsTheAnswer: Boolean
        get() = showSpelledNoteNames || showKeyboardTargets || showStaffNotation || showTargetBassNote
}
