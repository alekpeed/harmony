package com.harmonygates.core.data.backup

import com.harmonygates.core.data.db.AttemptEntity
import com.harmonygates.core.data.db.GateProgressEntity
import com.harmonygates.core.data.db.HarmonyDatabase
import com.harmonygates.core.data.db.ProfileEntity
import com.harmonygates.core.data.db.SessionEntity
import com.harmonygates.core.data.progress.ProfileId
import com.harmonygates.core.data.progress.ProgressRepository

/**
 * The database behind an export.
 *
 * Thin on purpose: everything that decides *what* is exported and *what* an import does lives in
 * [ProgressBackupService], where it can be tested without a device. This part is only the
 * queries, and it is the part an emulator would be needed to test.
 *
 * The rebuild is delegated to the repository rather than reimplemented, so a restored profile is
 * scored by exactly the code that scores a played one.
 */
public class RoomProgressBackupStore(
    private val database: HarmonyDatabase,
    private val repository: ProgressRepository,
) : ProgressBackupStore {

    override suspend fun profile(id: ProfileId): ProfileEntity? = database.profiles().find(id.value)

    override suspend fun sessions(id: ProfileId): List<SessionEntity> =
        database.sessions().allForProfile(id.value)

    override suspend fun attempts(id: ProfileId): List<AttemptEntity> =
        database.attempts().allOldestFirst(id.value)

    override suspend fun gateProgress(id: ProfileId): List<GateProgressEntity> =
        database.gateProgress().all(id.value)

    override suspend fun writeProfile(profile: ProfileEntity) {
        database.profiles().upsert(profile)
    }

    override suspend fun writeSessions(sessions: List<SessionEntity>) {
        database.sessions().insertAll(sessions)
    }

    override suspend fun writeAttempts(attempts: List<AttemptEntity>) {
        database.attempts().insertAll(attempts)
    }

    override suspend fun writeGateProgress(progress: List<GateProgressEntity>) {
        progress.forEach { database.gateProgress().upsert(it) }
    }

    override suspend fun rebuildMastery(id: ProfileId): Int = repository.rebuildMastery(id)
}
