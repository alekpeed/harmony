package com.harmonygates.core.data.relativepitch

import com.harmonygates.core.data.db.HarmonyDatabase
import com.harmonygates.core.data.db.RelativePitchLevelStatEntity
import com.harmonygates.core.data.progress.ProfileId
import com.harmonygates.core.music.relativepitch.LevelStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Accuracy evidence for the relative-pitch ladder, stored separately from `ProgressRepository`.
 *
 * A multiple-choice "which interval was that" answer has no chord, no MIDI note event, and no
 * onset to record — `AttemptEntity` and the evaluator behind it are built entirely around those,
 * so recording an answer here would mean inventing fake chord and note data to satisfy a shape
 * that does not fit. This repository is a plain accuracy counter per level instead: exactly what
 * `RelativePitchEvaluator` needs and nothing the attempt pipeline would have to pretend about.
 */
public interface RelativePitchProgressRepository {
    public fun observeStats(profile: ProfileId): Flow<Map<String, LevelStat>>

    public suspend fun recordAnswer(profile: ProfileId, levelId: String, correct: Boolean, at: Instant)
}

public class RoomRelativePitchProgressRepository(
    private val database: HarmonyDatabase,
) : RelativePitchProgressRepository {

    private val dao get() = database.relativePitchLevelStats()

    override fun observeStats(profile: ProfileId): Flow<Map<String, LevelStat>> =
        dao.observeAll(profile.value).map { rows ->
            rows.associate { it.levelId to LevelStat(attempts = it.attempts, correct = it.correct) }
        }

    override suspend fun recordAnswer(profile: ProfileId, levelId: String, correct: Boolean, at: Instant) {
        val existing = dao.find(profile.value, levelId)
        dao.upsert(
            RelativePitchLevelStatEntity(
                profileId = profile.value,
                levelId = levelId,
                attempts = (existing?.attempts ?: 0) + 1,
                correct = (existing?.correct ?: 0) + if (correct) 1 else 0,
                lastAnsweredAtEpochMillis = at.toEpochMilli(),
            ),
        )
    }
}
