package com.harmonygates.core.data.progress

import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.session.AttemptRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A player. One for now; the storage does not assume it. */
@JvmInline
public value class ProfileId(public val value: String) {
    init {
        require(value.isNotBlank()) { "A profile id must not be blank" }
    }

    override fun toString(): String = value
}

/** One sitting, as the repository sees it. */
public data class SessionRecord(
    val id: String,
    val gateId: GateId?,
    val exercisePolicyId: String,
    val seed: Long,
    val startedAt: Instant,
    val endedAt: Instant?,
    val exercisesPlanned: Int,
)

/**
 * One stored attempt, readable without the content that produced it.
 *
 * 11_DATA_MODEL_AND_PERSISTENCE.md §3 asks for snapshots "sufficiently complete to reproduce
 * bugs even if content definitions later change", which is why this carries the chord and the
 * diagnosis as text rather than an id to look up. An attempt from three content versions ago is
 * still legible.
 */
public data class StoredAttempt(
    val id: String,
    val sessionId: String,
    val exerciseDefinitionId: String,
    val exerciseSeed: Long,
    val skillIds: List<SkillId>,
    val chordSymbol: String,
    val verdict: String,
    val hintsUsed: Int,
    val skipped: Boolean,
    val completedAt: Instant,
    val responseMillis: Long?,
    val onsetSpreadMillis: Long?,
    /** What the exercise asked for. */
    val expected: String,
    /** What was played. */
    val performed: String,
    /** The semantic diagnosis, one entry per finding. */
    val errors: List<String>,
)

/**
 * Everything a player has done.
 *
 * The interface is 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §5's, grown by the two things Phase 5's
 * acceptance criteria need: history that can be read back ("historical attempts are
 * inspectable") and a mastery figure that survives a restart.
 */
public interface ProgressRepository {

    /** Ensures a profile exists and returns it. Called once at startup. */
    public suspend fun currentProfile(contentVersion: String): ProfileId

    public fun observeSkillMastery(profile: ProfileId, skillId: SkillId): Flow<SkillMastery?>

    public fun observeAllMastery(profile: ProfileId): Flow<Map<SkillId, SkillMastery>>

    public suspend fun allMastery(profile: ProfileId): Map<SkillId, SkillMastery>

    public fun observeGateCompletions(profile: ProfileId): Flow<Map<GateId, Instant>>

    public fun observeRecentAttempts(profile: ProfileId, limit: Int = RECENT_LIMIT): Flow<List<StoredAttempt>>

    public suspend fun attemptsForSkill(profile: ProfileId, skillId: SkillId): List<StoredAttempt>

    public suspend fun startSession(profile: ProfileId, session: SessionRecord)

    public suspend fun endSession(sessionId: String, endedAt: Instant)

    /**
     * Stores an attempt and folds it into the mastery of every skill it exercised.
     *
     * One call rather than two because the two must not diverge: an attempt recorded without its
     * mastery update would leave a player's history and their progress disagreeing, and the
     * disagreement would be invisible until a gate refused to open.
     */
    public suspend fun recordAttempt(
        profile: ProfileId,
        sessionId: String,
        record: AttemptRecord,
        at: Instant,
    )

    /** Notes that a gate has been passed. Idempotent: the first completion is the one kept. */
    public suspend fun recordGateCompletion(profile: ProfileId, gateId: GateId, at: Instant)

    /** Skills whose review time has come. */
    public suspend fun dueForReview(profile: ProfileId, now: Instant): List<SkillId>

    /**
     * Rebuilds every mastery row from the stored attempts.
     *
     * The escape hatch for 11 §4: when the weighting changes, history is replayed rather than
     * migrated, so old evidence is never silently reinterpreted by a formula it predates.
     */
    public suspend fun rebuildMastery(profile: ProfileId): Int

    public companion object {
        public const val RECENT_LIMIT: Int = 50
    }
}
