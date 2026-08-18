package com.harmonygates.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The stored shape of learning data.
 *
 * 11_DATA_MODEL_AND_PERSISTENCE.md §2 names the entities; this is them. Two rules shaped the
 * columns:
 *
 * - **§2: "Avoid storing every derived metric if it can be reliably recomputed."** Mastery is
 *   stored because recomputing it means replaying every attempt ever made, but nothing derived
 *   from mastery is — a gate's status is worked out on the way to the screen.
 * - **§3: "Keep snapshots sufficiently complete to reproduce bugs even if content definitions
 *   later change."** An attempt therefore stores what was asked and what was played as JSON,
 *   not a reference to a policy that may be edited next release.
 *
 * Everything is denormalised onto simple column types on purpose. A `TypeConverter` that turned
 * a `ChordSpec` into a blob would tie the database to the shape of a domain class, and 11 §4
 * requires old rows to stay readable after those change.
 */

/** One player. The app is single-profile today; the table is not, so it need not change later. */
@Entity(tableName = "profiles")
public data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    /** The content pack this profile's history was recorded against (11 §4). */
    val contentVersion: String,
)

/**
 * One sitting.
 *
 * Sessions exist so that an attempt can be read in context: "the fourth chord of a run that had
 * already gone badly" is a different fact from "one wrong chord".
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("startedAtEpochMillis")],
)
public data class SessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    /** Null for free practice that was not launched from a gate. */
    val gateId: String?,
    val exercisePolicyId: String,
    val seed: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val exercisesPlanned: Int,
)

/**
 * One answer.
 *
 * The two snapshot columns are the reason this table is worth having: they hold what the
 * exercise asked for and what the player actually played, in full, so an attempt from an old
 * content pack can still be read back and understood.
 */
@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("completedAtEpochMillis"), Index("exerciseDefinitionId")],
)
public data class AttemptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseDefinitionId: String,
    val exerciseSeed: Long,
    /** Comma-separated skill ids. Queried by `LIKE`, which is enough for a personal app. */
    val skillIds: String,
    val chordSymbol: String,
    val rootPitchClass: String,
    val startedAtEpochMillis: Long,
    val firstInputAtEpochMillis: Long?,
    val completedAtEpochMillis: Long,
    val verdict: String,
    val hintsUsed: Int,
    val skipped: Boolean,
    val responseMillis: Long?,
    val onsetSpreadMillis: Long?,
    /**
     * How much this attempt was worth as evidence, graded when it happened.
     *
     * The grade is a fact about the attempt — the player was or was not helped — while the
     * weight attached to that grade is a teaching policy that may change. Storing the grade and
     * not the weight is what lets `rebuildMastery` re-score old history under new weights
     * without 11 §4's "silently reinterpret old attempts" ever happening.
     */
    val evidence: String,
    /** The semantic error classes, comma separated, for replay without re-parsing the JSON. */
    val errorClasses: String,
    /** What was asked for, as JSON. */
    @ColumnInfo(name = "expectedSnapshotJson") val expectedSnapshot: String,
    /** What was played, as JSON. */
    @ColumnInfo(name = "performedSnapshotJson") val performedSnapshot: String,
    /** The semantic diagnosis, as JSON. Never a rendered sentence. */
    @ColumnInfo(name = "semanticErrorsJson") val semanticErrors: String,
)

/**
 * What is known about one skill.
 *
 * Stored rather than recomputed because replaying every attempt on every launch would grow
 * without bound, and the update algorithm is deterministic enough that the stored value and a
 * replay always agree. `MasteryUpdater.replay` exists so that a changed weighting can rebuild
 * this table from `attempts` rather than requiring a migration that guesses.
 */
@Entity(
    tableName = "skill_mastery",
    primaryKeys = ["profileId", "skillId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("nextReviewAtEpochMillis")],
)
public data class SkillMasteryEntity(
    val profileId: String,
    val skillId: String,
    val estimate: Double,
    val attempts: Int,
    val successfulAttempts: Int,
    val recentWeightedAccuracy: Double,
    val medianResponseMillis: Long?,
    val lastPracticedAtEpochMillis: Long?,
    val nextReviewAtEpochMillis: Long?,
    /** `CLASS:count` pairs, comma separated. */
    val errorHistogram: String,
    /** Comma-separated spelled pitch classes. */
    val rootsCovered: String,
    /** The recent evidence window, comma separated, oldest first. */
    val recentEvidence: String,
)

/**
 * When a gate was first passed.
 *
 * Deliberately *only* the timestamp. Whether a gate is passed is decided by the evidence every
 * time the map is drawn, so a stored "complete" flag could disagree with the attempts behind it;
 * this records when it happened, for display, and nothing that could contradict the evidence.
 */
@Entity(
    tableName = "gate_progress",
    primaryKeys = ["profileId", "gateId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
public data class GateProgressEntity(
    val profileId: String,
    val gateId: String,
    val firstCompletedAtEpochMillis: Long,
    val sessionsPlayed: Int,
)

/**
 * Accuracy evidence for one rung of the relative-pitch ladder (`core.music.relativepitch`).
 *
 * Whether a level counts as mastered, and therefore whether the one after it is unlocked, is
 * decided from these two counters every time the ladder is drawn — the same "evidence, not a
 * flag" rule [GateProgressEntity] follows, so a stored status can never disagree with what it was
 * computed from.
 */
@Entity(
    tableName = "relative_pitch_level_stats",
    primaryKeys = ["profileId", "levelId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
public data class RelativePitchLevelStatEntity(
    val profileId: String,
    val levelId: String,
    val attempts: Int,
    val correct: Int,
    val lastAnsweredAtEpochMillis: Long,
)

/** A reward that has been earned. Kept so an unlock survives a content edit that removes it. */
@Entity(
    tableName = "unlocks",
    primaryKeys = ["profileId", "unlockKey"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
public data class UnlockEntity(
    val profileId: String,
    /** `kind:value`, e.g. `voicing_family:ROOTLESS_A`. */
    val unlockKey: String,
    val grantedByGateId: String,
    val grantedAtEpochMillis: Long,
)

/**
 * A skill waiting to come round again.
 *
 * The due time also lives on the mastery row, which is where the updater writes it. This table
 * is the queue a review session reads, so that "what is due" is one indexed query rather than a
 * scan of every skill the player has ever touched.
 */
@Entity(
    tableName = "review_items",
    primaryKeys = ["profileId", "skillId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("dueAtEpochMillis")],
)
public data class ReviewItemEntity(
    val profileId: String,
    val skillId: String,
    val dueAtEpochMillis: Long,
    val consecutiveSuccesses: Int,
)
