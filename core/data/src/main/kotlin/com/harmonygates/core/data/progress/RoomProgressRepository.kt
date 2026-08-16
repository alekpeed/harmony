package com.harmonygates.core.data.progress

import com.harmonygates.core.data.db.GateProgressEntity
import com.harmonygates.core.data.db.HarmonyDatabase
import com.harmonygates.core.data.db.ProfileEntity
import com.harmonygates.core.data.db.ReviewItemEntity
import com.harmonygates.core.data.db.SessionEntity
import androidx.room.withTransaction
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.MasteryEvidence
import com.harmonygates.core.music.mastery.MasteryUpdater
import com.harmonygates.core.music.mastery.SkillMastery
import com.harmonygates.core.music.session.AttemptRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * Progress, stored in Room.
 *
 * The whole of the mastery arithmetic lives in `core:music`; this class reads a row, hands it to
 * [MasteryUpdater], and writes the answer back. Keeping it that way is what makes the
 * calculation testable without a database and the storage testable without the calculation.
 */
public class RoomProgressRepository(
    private val database: HarmonyDatabase,
    private val updater: MasteryUpdater = MasteryUpdater(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ProgressRepository {

    override suspend fun currentProfile(contentVersion: String): ProfileId {
        val existing = database.profiles().first()
        if (existing != null) {
            // The content version is refreshed rather than compared: 11 §4 keeps old attempts
            // whatever pack they were recorded against, and each attempt already carries enough
            // to be read on its own.
            if (existing.contentVersion != contentVersion) {
                database.profiles().upsert(existing.copy(contentVersion = contentVersion))
            }
            return ProfileId(existing.id)
        }
        val created = ProfileEntity(
            id = idFactory(),
            displayName = DEFAULT_PROFILE_NAME,
            createdAtEpochMillis = System.currentTimeMillis(),
            contentVersion = contentVersion,
        )
        database.profiles().upsert(created)
        return ProfileId(created.id)
    }

    override fun observeSkillMastery(profile: ProfileId, skillId: SkillId): Flow<SkillMastery?> =
        database.skillMastery().observe(profile.value, skillId.value)
            .map { entity -> entity?.let(Mappers::toDomain) }

    override fun observeAllMastery(profile: ProfileId): Flow<Map<SkillId, SkillMastery>> =
        database.skillMastery().observeAll(profile.value)
            .map { rows -> rows.associate { SkillId(it.skillId) to Mappers.toDomain(it) } }

    override suspend fun allMastery(profile: ProfileId): Map<SkillId, SkillMastery> =
        database.skillMastery().all(profile.value)
            .associate { SkillId(it.skillId) to Mappers.toDomain(it) }

    override fun observeGateCompletions(profile: ProfileId): Flow<Map<GateId, Instant>> =
        database.gateProgress().observeAll(profile.value).map { rows ->
            rows.associate { GateId(it.gateId) to Instant.ofEpochMilli(it.firstCompletedAtEpochMillis) }
        }

    override fun observeRecentAttempts(profile: ProfileId, limit: Int): Flow<List<StoredAttempt>> =
        database.attempts().observeRecent(profile.value, limit)
            .map { rows -> rows.map(Mappers::toStored) }

    override suspend fun attemptsForSkill(profile: ProfileId, skillId: SkillId): List<StoredAttempt> =
        database.attempts().forSkill(profile.value, skillId.value).map(Mappers::toStored)

    override suspend fun startSession(profile: ProfileId, session: SessionRecord) {
        database.sessions().insert(
            SessionEntity(
                id = session.id,
                profileId = profile.value,
                gateId = session.gateId?.value,
                exercisePolicyId = session.exercisePolicyId,
                seed = session.seed,
                startedAtEpochMillis = session.startedAt.toEpochMilli(),
                endedAtEpochMillis = session.endedAt?.toEpochMilli(),
                exercisesPlanned = session.exercisesPlanned,
            ),
        )
    }

    override suspend fun endSession(sessionId: String, endedAt: Instant) {
        database.sessions().markEnded(sessionId, endedAt.toEpochMilli())
    }

    /**
     * Stores the attempt and updates every skill it touched, in one transaction.
     *
     * A crash between the two writes would leave a player's history and their mastery
     * disagreeing, and the disagreement would only surface later as a gate that refuses to open
     * for no visible reason.
     */
    override suspend fun recordAttempt(
        profile: ProfileId,
        sessionId: String,
        record: AttemptRecord,
        at: Instant,
    ): Unit = database.withTransaction {
        val evidence = MasteryEvidence.evidenceFor(record)
        val events = MasteryEvidence.eventsFor(record, at)

        database.attempts().insert(Mappers.toEntity(idFactory(), sessionId, record, evidence, at))

        events.forEach { event ->
            val current = database.skillMastery().find(profile.value, event.skillId.value)
                ?.let(Mappers::toDomain)
                ?: SkillMastery(event.skillId)
            val updated = updater.apply(current, event)
            database.skillMastery().upsert(Mappers.toEntity(profile.value, updated))
            updated.nextReviewAt?.let { due ->
                database.reviewItems().upsert(
                    ReviewItemEntity(
                        profileId = profile.value,
                        skillId = event.skillId.value,
                        dueAtEpochMillis = due.toEpochMilli(),
                        consecutiveSuccesses = updated.recentEvidence
                            .reversed()
                            .takeWhile { it.isCorrect }
                            .size,
                    ),
                )
            }
        }
    }

    override suspend fun recordGateCompletion(profile: ProfileId, gateId: GateId, at: Instant) {
        val existing = database.gateProgress().find(profile.value, gateId.value)
        database.gateProgress().upsert(
            GateProgressEntity(
                profileId = profile.value,
                gateId = gateId.value,
                // The first pass is the one that counts as the achievement; replaying a gate
                // later should not move the date the player earned it.
                firstCompletedAtEpochMillis = existing?.firstCompletedAtEpochMillis ?: at.toEpochMilli(),
                sessionsPlayed = (existing?.sessionsPlayed ?: 0) + 1,
            ),
        )
    }

    override suspend fun dueForReview(profile: ProfileId, now: Instant): List<SkillId> =
        database.skillMastery().dueForReview(profile.value, now.toEpochMilli())
            .map { SkillId(it.skillId) }

    override suspend fun rebuildMastery(profile: ProfileId): Int {
        val bySkill = mutableMapOf<SkillId, SkillMastery>()
        // Oldest first, so the replay sees the same order the player played in. Recency
        // weighting would otherwise produce a different answer from the same evidence.
        database.attempts().allOldestFirst(profile.value).forEach { entity ->
            Mappers.toMasteryEvents(entity).forEach { event ->
                val current = bySkill[event.skillId] ?: SkillMastery(event.skillId)
                bySkill[event.skillId] = updater.apply(current, event)
            }
        }
        database.skillMastery().upsertAll(bySkill.values.map { Mappers.toEntity(profile.value, it) })
        return bySkill.size
    }

    private companion object {
        const val DEFAULT_PROFILE_NAME = "Player"
    }
}
