package com.harmonygates.data

import com.harmonygates.core.data.progress.ProfileId
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.data.progress.SessionRecord
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExerciseDefinitionId
import com.harmonygates.core.music.exercise.ExerciseInstance
import com.harmonygates.core.music.exercise.ExerciseInstanceId
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.session.AttemptRecord
import com.harmonygates.core.music.voicing.Inversion
import java.time.Instant
import java.util.UUID

/**
 * Lets a module that is not a chord gate write what a player did.
 *
 * The attempt store is built around [ExerciseInstance], because that is what the chord gate had
 * when the schema was designed. Ear training, voice leading and every other loop since produce a
 * real [PerformanceAttempt] and a real [EvaluationResult] — the same evaluator judges them all —
 * but they have no `ExerciseInstance`, and for a while that meant their sessions were simply
 * forgotten: no mastery, no gate completion, nothing in the history.
 *
 * The missing piece is small and entirely derivable. An instance is an id, the chord asked for,
 * the requirement, the skills it exercises, and how it was presented; every module already knows
 * all five. So rather than leave three modules unrecorded, or widen the schema and migrate the
 * database for a field none of them would use, they build the instance here.
 *
 * What is deliberately *not* faked: nothing invents an attempt or a verdict. Both come from the
 * capture and the evaluator exactly as the chord gate's do, so a mastery estimate built from
 * these is built from the same evidence as any other.
 */
object RecordedAttempts {

    private val realizer = DefaultChordRealizer()

    /**
     * Opens a session row so attempts have a parent.
     *
     * The schema makes an attempt's session its parent, and a stored attempt whose session was
     * never opened is the orphan `ProgressBackupService` has to drop on import.
     */
    suspend fun startSession(
        progress: ProgressRepository,
        profile: ProfileId,
        sessionId: String,
        policyId: String,
        seed: Long,
        planned: Int,
        at: Instant = Instant.now(),
    ) {
        progress.startSession(
            profile = profile,
            session = SessionRecord(
                id = sessionId,
                gateId = null,
                exercisePolicyId = policyId,
                seed = seed,
                startedAt = at,
                endedAt = null,
                exercisesPlanned = planned,
            ),
        )
    }

    /** Writes one attempt, deriving the instance from what the module already has. */
    suspend fun record(
        progress: ProgressRepository,
        profile: ProfileId,
        sessionId: String,
        definitionId: String,
        skillIds: Set<SkillId>,
        chord: ChordSpec,
        requirement: ExerciseRequirement,
        attempt: PerformanceAttempt,
        result: EvaluationResult,
        seed: Long,
        presentation: PresentationSpec = PresentationSpec.Independent,
        targetNotes: List<Int> = emptyList(),
        inversion: Inversion = Inversion.ROOT,
        at: Instant = Instant.now(),
    ) {
        progress.recordAttempt(
            profile = profile,
            sessionId = sessionId,
            record = AttemptRecord(
                instance = ExerciseInstance(
                    id = ExerciseInstanceId(UUID.randomUUID().toString()),
                    definitionId = ExerciseDefinitionId(definitionId),
                    seed = seed,
                    skillIds = skillIds,
                    chord = chord,
                    requirement = requirement,
                    presentation = presentation,
                    inversion = inversion,
                    // An unspellable chord is not worth failing a write over: the tones are for
                    // the note-names channel, and the attempt itself is the thing being kept.
                    spelledTones = runCatching { realizer.chordTones(chord) }.getOrDefault(emptyList()),
                    targetNotes = targetNotes,
                ),
                attempt = attempt,
                result = result,
            ),
            at = at,
        )
    }

    /** Closes the session row, so a finished run is distinguishable from an abandoned one. */
    suspend fun endSession(
        progress: ProgressRepository,
        sessionId: String,
        at: Instant = Instant.now(),
    ) {
        progress.endSession(sessionId, at)
    }

    /** The skills a module records against when its policy does not name any. */
    fun fallbackSkills(id: String): Set<SkillId> = setOf(SkillId(id))

    fun policySkills(policy: ExercisePolicy?, fallback: String): Set<SkillId> =
        policy?.skillIds?.takeIf { it.isNotEmpty() } ?: fallbackSkills(fallback)
}
