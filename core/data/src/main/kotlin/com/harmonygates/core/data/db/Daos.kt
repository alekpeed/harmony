package com.harmonygates.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface ProfileDao {
    @Upsert
    public suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    public suspend fun find(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAtEpochMillis LIMIT 1")
    public suspend fun first(): ProfileEntity?
}

@Dao
public interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(session: SessionEntity)

    @Query("UPDATE sessions SET endedAtEpochMillis = :endedAt WHERE id = :id")
    public suspend fun markEnded(id: String, endedAt: Long)

    @Query(
        """
        SELECT * FROM sessions
        WHERE profileId = :profileId
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    public fun observeRecent(profileId: String, limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions WHERE profileId = :profileId AND gateId = :gateId")
    public suspend fun countForGate(profileId: String, gateId: String): Int

    /** Every session, oldest first. Used by the progress export. */
    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startedAtEpochMillis")
    public suspend fun allForProfile(profileId: String): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(sessions: List<SessionEntity>)
}

@Dao
public interface AttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(attempt: AttemptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(attempts: List<AttemptEntity>)

    /**
     * The history screen's query.
     *
     * Newest first, because "what did I just get wrong" is the question a player actually asks;
     * scrolling back through a session is the rarer case.
     */
    @Query(
        """
        SELECT a.* FROM attempts a
        JOIN sessions s ON s.id = a.sessionId
        WHERE s.profileId = :profileId
        ORDER BY a.completedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    public fun observeRecent(profileId: String, limit: Int): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY completedAtEpochMillis")
    public suspend fun forSession(sessionId: String): List<AttemptEntity>

    /**
     * Every attempt that touched one skill, oldest first.
     *
     * This is what makes a stored mastery estimate rebuildable: change the weighting and the
     * whole history can be replayed rather than migrated by guesswork.
     */
    @Query(
        """
        SELECT a.* FROM attempts a
        JOIN sessions s ON s.id = a.sessionId
        WHERE s.profileId = :profileId AND (',' || a.skillIds || ',') LIKE '%,' || :skillId || ',%'
        ORDER BY a.completedAtEpochMillis
        """,
    )
    public suspend fun forSkill(profileId: String, skillId: String): List<AttemptEntity>

    /** Every attempt, oldest first. The input to a mastery rebuild. */
    @Query(
        """
        SELECT a.* FROM attempts a
        JOIN sessions s ON s.id = a.sessionId
        WHERE s.profileId = :profileId
        ORDER BY a.completedAtEpochMillis
        """,
    )
    public suspend fun allOldestFirst(profileId: String): List<AttemptEntity>

    @Query("SELECT COUNT(*) FROM attempts a JOIN sessions s ON s.id = a.sessionId WHERE s.profileId = :profileId")
    public suspend fun count(profileId: String): Int
}

@Dao
public interface SkillMasteryDao {
    @Upsert
    public suspend fun upsert(mastery: SkillMasteryEntity)

    @Upsert
    public suspend fun upsertAll(mastery: List<SkillMasteryEntity>)

    @Query("SELECT * FROM skill_mastery WHERE profileId = :profileId AND skillId = :skillId")
    public suspend fun find(profileId: String, skillId: String): SkillMasteryEntity?

    @Query("SELECT * FROM skill_mastery WHERE profileId = :profileId AND skillId = :skillId")
    public fun observe(profileId: String, skillId: String): Flow<SkillMasteryEntity?>

    @Query("SELECT * FROM skill_mastery WHERE profileId = :profileId")
    public fun observeAll(profileId: String): Flow<List<SkillMasteryEntity>>

    @Query("SELECT * FROM skill_mastery WHERE profileId = :profileId")
    public suspend fun all(profileId: String): List<SkillMasteryEntity>

    @Query(
        """
        SELECT * FROM skill_mastery
        WHERE profileId = :profileId AND nextReviewAtEpochMillis IS NOT NULL
          AND nextReviewAtEpochMillis <= :now
        ORDER BY nextReviewAtEpochMillis
        """,
    )
    public suspend fun dueForReview(profileId: String, now: Long): List<SkillMasteryEntity>
}

@Dao
public interface GateProgressDao {
    @Upsert
    public suspend fun upsert(progress: GateProgressEntity)

    @Query("SELECT * FROM gate_progress WHERE profileId = :profileId")
    public fun observeAll(profileId: String): Flow<List<GateProgressEntity>>

    @Query("SELECT * FROM gate_progress WHERE profileId = :profileId")
    public suspend fun all(profileId: String): List<GateProgressEntity>

    @Query("SELECT * FROM gate_progress WHERE profileId = :profileId AND gateId = :gateId")
    public suspend fun find(profileId: String, gateId: String): GateProgressEntity?
}

@Dao
public interface RelativePitchLevelStatDao {
    @Upsert
    public suspend fun upsert(stat: RelativePitchLevelStatEntity)

    @Query("SELECT * FROM relative_pitch_level_stats WHERE profileId = :profileId")
    public fun observeAll(profileId: String): Flow<List<RelativePitchLevelStatEntity>>

    @Query("SELECT * FROM relative_pitch_level_stats WHERE profileId = :profileId AND levelId = :levelId")
    public suspend fun find(profileId: String, levelId: String): RelativePitchLevelStatEntity?
}

@Dao
public interface UnlockDao {
    @Upsert
    public suspend fun upsertAll(unlocks: List<UnlockEntity>)

    @Query("SELECT * FROM unlocks WHERE profileId = :profileId")
    public fun observeAll(profileId: String): Flow<List<UnlockEntity>>
}

@Dao
public interface ReviewItemDao {
    @Upsert
    public suspend fun upsert(item: ReviewItemEntity)

    @Query("DELETE FROM review_items WHERE profileId = :profileId AND skillId = :skillId")
    public suspend fun clear(profileId: String, skillId: String)

    @Query(
        """
        SELECT * FROM review_items
        WHERE profileId = :profileId AND dueAtEpochMillis <= :now
        ORDER BY dueAtEpochMillis
        LIMIT :limit
        """,
    )
    public suspend fun due(profileId: String, now: Long, limit: Int): List<ReviewItemEntity>

    @Query("SELECT COUNT(*) FROM review_items WHERE profileId = :profileId AND dueAtEpochMillis <= :now")
    public fun observeDueCount(profileId: String, now: Long): Flow<Int>
}
