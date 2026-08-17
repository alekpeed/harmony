package com.harmonygates.core.data.backup

import com.harmonygates.core.data.db.AttemptEntity
import com.harmonygates.core.data.db.GateProgressEntity
import com.harmonygates.core.data.db.ProfileEntity
import com.harmonygates.core.data.db.SessionEntity
import com.harmonygates.core.data.progress.ProfileId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A player's progress, as a file.
 *
 * 11_DATA_MODEL_AND_PERSISTENCE.md §7: "V1 should include local JSON export/import of learning
 * progress if practical. It is useful for a personal app and avoids requiring an account
 * backend." This is that file.
 *
 * **What is in it, and what is deliberately not.** Sessions, attempts and gate completions are
 * exported. Skill mastery is not — it is derived from the attempts, and re-derived on import.
 * That is the same rule the campaign follows for gate status: store the evidence, never the
 * conclusion. A backup that carried a mastery estimate could be restored into a build whose
 * weighting had changed, and would then disagree with its own attempt history forever.
 */
@Serializable
public data class ProgressBackup(
    val schemaVersion: Int,
    /** The content version the progress was earned against. Kept for diagnosis, not enforced. */
    val contentVersion: String,
    val exportedAtEpochMillis: Long,
    val profile: BackupProfile,
    val sessions: List<BackupSession>,
    val attempts: List<BackupAttempt>,
    val gateCompletions: List<BackupGateCompletion>,
) {
    public companion object {
        /** Bumped when the shape below changes incompatibly. */
        public const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
public data class BackupProfile(
    val id: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val contentVersion: String,
)

@Serializable
public data class BackupSession(
    val id: String,
    val gateId: String? = null,
    val exercisePolicyId: String,
    val seed: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val exercisesPlanned: Int,
)

@Serializable
public data class BackupAttempt(
    val id: String,
    val sessionId: String,
    val exerciseDefinitionId: String,
    val exerciseSeed: Long,
    val skillIds: String,
    val chordSymbol: String,
    val rootPitchClass: String,
    val startedAtEpochMillis: Long,
    val firstInputAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long,
    val verdict: String,
    val hintsUsed: Int,
    val skipped: Boolean,
    val responseMillis: Long? = null,
    val onsetSpreadMillis: Long? = null,
    val evidence: String,
    val errorClasses: String,
    val expectedSnapshot: String,
    val performedSnapshot: String,
    val semanticErrors: String,
)

@Serializable
public data class BackupGateCompletion(
    val gateId: String,
    val firstCompletedAtEpochMillis: Long,
    val sessionsPlayed: Int = 0,
)

/** What an import did. */
public data class ImportResult(
    val profile: ProfileId,
    val sessionsRestored: Int,
    val attemptsRestored: Int,
    val gateCompletionsRestored: Int,
    /** Skills whose mastery was recomputed from the restored attempts. */
    val skillsRebuilt: Int,
    /** Attempts skipped because they referenced a session the backup did not contain. */
    val orphanedAttempts: Int,
)

/** Why an import was refused. */
public class BackupFormatException(message: String) : IllegalArgumentException(message)

/**
 * Re-targets a parsed backup at a different local profile before [ProgressBackupService.import].
 *
 * Every install creates its own profile with a random id and its own `createdAtEpochMillis`
 * (`RoomProgressRepository.currentProfile`). Importing a file unchanged would write a *second*
 * profile row under the id it was exported with, rather than restore the one this install
 * already has — and because the repository reads back whichever profile row is oldest, that
 * second row could sit there unread: the import would appear to succeed and the player's screen
 * would never change. This app keeps exactly one profile per install, so a caller restoring a
 * backup should always retarget it at the local `currentProfile()` id first.
 */
public fun ProgressBackup.retargetedTo(profile: ProfileId): ProgressBackup =
    copy(profile = this.profile.copy(id = profile.value))

/**
 * Storage an export reads and an import writes.
 *
 * A narrow interface rather than the database itself, so the rules above — what is exported,
 * what is refused, what is rebuilt — can be tested on the JVM. Room's own round trip needs an
 * emulator; the decisions in this file do not, and they are the part that can be wrong.
 */
public interface ProgressBackupStore {
    public suspend fun profile(id: ProfileId): ProfileEntity?

    public suspend fun sessions(id: ProfileId): List<SessionEntity>

    public suspend fun attempts(id: ProfileId): List<AttemptEntity>

    public suspend fun gateProgress(id: ProfileId): List<GateProgressEntity>

    public suspend fun writeProfile(profile: ProfileEntity)

    public suspend fun writeSessions(sessions: List<SessionEntity>)

    public suspend fun writeAttempts(attempts: List<AttemptEntity>)

    public suspend fun writeGateProgress(progress: List<GateProgressEntity>)

    /** Recomputes mastery from the stored attempts. Returns how many skills were touched. */
    public suspend fun rebuildMastery(id: ProfileId): Int
}

/**
 * Reads and writes [ProgressBackup] files.
 *
 * @param clockMillis the export timestamp, injected so an exported file is reproducible in a
 *   test. Nothing here reads a clock of its own.
 */
public class ProgressBackupService(
    private val store: ProgressBackupStore,
    private val json: Json = DEFAULT_JSON,
) {

    public suspend fun export(profile: ProfileId, clockMillis: Long): ProgressBackup {
        val stored = store.profile(profile)
            ?: throw BackupFormatException("No profile '$profile' to export")

        return ProgressBackup(
            schemaVersion = ProgressBackup.SCHEMA_VERSION,
            contentVersion = stored.contentVersion,
            exportedAtEpochMillis = clockMillis,
            profile = BackupProfile(
                id = stored.id,
                displayName = stored.displayName,
                createdAtEpochMillis = stored.createdAtEpochMillis,
                contentVersion = stored.contentVersion,
            ),
            sessions = store.sessions(profile).map { it.toBackup() },
            attempts = store.attempts(profile).map { it.toBackup() },
            gateCompletions = store.gateProgress(profile).map { it.toBackup() },
        )
    }

    public suspend fun exportToJson(profile: ProfileId, clockMillis: Long): String =
        json.encodeToString(ProgressBackup.serializer(), export(profile, clockMillis))

    /** Reads a file, refusing anything this build cannot make sense of. */
    public fun parse(text: String): ProgressBackup {
        val backup = runCatching { json.decodeFromString(ProgressBackup.serializer(), text) }
            .getOrElse { throw BackupFormatException("This is not a Harmony Gates backup: ${it.message}") }

        if (backup.schemaVersion > ProgressBackup.SCHEMA_VERSION) {
            throw BackupFormatException(
                "That backup was written by a newer version of the app " +
                    "(format ${backup.schemaVersion}; this build reads ${ProgressBackup.SCHEMA_VERSION})",
            )
        }
        return backup
    }

    /**
     * Restores a backup.
     *
     * Attempts belonging to a session the file does not contain are dropped rather than
     * imported, because the schema makes an attempt's session its parent and a half-restored
     * history would be worse than a shorter one. How many were dropped is reported rather than
     * hidden — a truncated import that says nothing is how a player loses six months quietly.
     */
    public suspend fun import(backup: ProgressBackup): ImportResult {
        if (backup.schemaVersion > ProgressBackup.SCHEMA_VERSION) {
            throw BackupFormatException(
                "That backup was written by a newer version of the app " +
                    "(format ${backup.schemaVersion})",
            )
        }
        val profileId = ProfileId(backup.profile.id)

        store.writeProfile(
            ProfileEntity(
                id = backup.profile.id,
                displayName = backup.profile.displayName,
                createdAtEpochMillis = backup.profile.createdAtEpochMillis,
                contentVersion = backup.profile.contentVersion,
            ),
        )

        val sessions = backup.sessions.map { it.toEntity(backup.profile.id) }
        store.writeSessions(sessions)

        val knownSessions = sessions.map { it.id }.toSet()
        val (keep, orphans) = backup.attempts.partition { it.sessionId in knownSessions }
        store.writeAttempts(keep.map { it.toEntity() })

        store.writeGateProgress(backup.gateCompletions.map { it.toEntity(backup.profile.id) })

        return ImportResult(
            profile = profileId,
            sessionsRestored = sessions.size,
            attemptsRestored = keep.size,
            gateCompletionsRestored = backup.gateCompletions.size,
            // Mastery is not in the file. It is recomputed from the attempts that were, which is
            // what keeps a restored profile consistent with this build's weighting rather than
            // with whichever build wrote the file.
            skillsRebuilt = store.rebuildMastery(profileId),
            orphanedAttempts = orphans.size,
        )
    }

    public suspend fun importFromJson(text: String): ImportResult = import(parse(text))

    public companion object {
        internal val DEFAULT_JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

// --- Mapping ------------------------------------------------------------------------------

private fun SessionEntity.toBackup() = BackupSession(
    id = id,
    gateId = gateId,
    exercisePolicyId = exercisePolicyId,
    seed = seed,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    exercisesPlanned = exercisesPlanned,
)

private fun BackupSession.toEntity(profileId: String) = SessionEntity(
    id = id,
    profileId = profileId,
    gateId = gateId,
    exercisePolicyId = exercisePolicyId,
    seed = seed,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    exercisesPlanned = exercisesPlanned,
)

private fun AttemptEntity.toBackup() = BackupAttempt(
    id = id,
    sessionId = sessionId,
    exerciseDefinitionId = exerciseDefinitionId,
    exerciseSeed = exerciseSeed,
    skillIds = skillIds,
    chordSymbol = chordSymbol,
    rootPitchClass = rootPitchClass,
    startedAtEpochMillis = startedAtEpochMillis,
    firstInputAtEpochMillis = firstInputAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    verdict = verdict,
    hintsUsed = hintsUsed,
    skipped = skipped,
    responseMillis = responseMillis,
    onsetSpreadMillis = onsetSpreadMillis,
    evidence = evidence,
    errorClasses = errorClasses,
    expectedSnapshot = expectedSnapshot,
    performedSnapshot = performedSnapshot,
    semanticErrors = semanticErrors,
)

private fun BackupAttempt.toEntity() = AttemptEntity(
    id = id,
    sessionId = sessionId,
    exerciseDefinitionId = exerciseDefinitionId,
    exerciseSeed = exerciseSeed,
    skillIds = skillIds,
    chordSymbol = chordSymbol,
    rootPitchClass = rootPitchClass,
    startedAtEpochMillis = startedAtEpochMillis,
    firstInputAtEpochMillis = firstInputAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    verdict = verdict,
    hintsUsed = hintsUsed,
    skipped = skipped,
    responseMillis = responseMillis,
    onsetSpreadMillis = onsetSpreadMillis,
    evidence = evidence,
    errorClasses = errorClasses,
    expectedSnapshot = expectedSnapshot,
    performedSnapshot = performedSnapshot,
    semanticErrors = semanticErrors,
)

private fun GateProgressEntity.toBackup() = BackupGateCompletion(
    gateId = gateId,
    firstCompletedAtEpochMillis = firstCompletedAtEpochMillis,
    sessionsPlayed = sessionsPlayed,
)

private fun BackupGateCompletion.toEntity(profileId: String) = GateProgressEntity(
    profileId = profileId,
    gateId = gateId,
    firstCompletedAtEpochMillis = firstCompletedAtEpochMillis,
    sessionsPlayed = sessionsPlayed,
)
