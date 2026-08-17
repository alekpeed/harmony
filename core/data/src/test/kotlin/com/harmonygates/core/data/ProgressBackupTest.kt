package com.harmonygates.core.data

import com.harmonygates.core.data.backup.BackupFormatException
import com.harmonygates.core.data.backup.ProgressBackup
import com.harmonygates.core.data.backup.ProgressBackupService
import com.harmonygates.core.data.backup.ProgressBackupStore
import com.harmonygates.core.data.backup.retargetedTo
import com.harmonygates.core.data.db.AttemptEntity
import com.harmonygates.core.data.db.GateProgressEntity
import com.harmonygates.core.data.db.ProfileEntity
import com.harmonygates.core.data.db.SessionEntity
import com.harmonygates.core.data.progress.ProfileId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exporting and restoring a player's progress.
 *
 * 11_DATA_MODEL_AND_PERSISTENCE.md §7 asks for local JSON export/import, and the reason it is
 * worth testing carefully is that this is the one feature whose failure mode is losing
 * somebody's practice history. The decisions live in [ProgressBackupService] rather than in the
 * Room layer precisely so they can be checked here, without a device.
 */
class ProgressBackupTest {

    private val store = FakeBackupStore()
    private val service = ProgressBackupService(store)

    @Test
    fun `an export carries the profile, its sessions and its attempts`() = runTest {
        store.seed()

        val backup = service.export(PROFILE, clockMillis = EXPORTED_AT)

        assertEquals(ProgressBackup.SCHEMA_VERSION, backup.schemaVersion)
        assertEquals(EXPORTED_AT, backup.exportedAtEpochMillis)
        assertEquals("player-1", backup.profile.id)
        assertEquals(2, backup.sessions.size)
        assertEquals(3, backup.attempts.size)
        assertEquals(1, backup.gateCompletions.size)
    }

    @Test
    fun `mastery is not in the file, because it is not evidence`() = runTest {
        store.seed()

        val text = service.exportToJson(PROFILE, clockMillis = EXPORTED_AT)

        // Storing a mastery estimate would let a restored profile disagree with its own attempt
        // history forever, if the weighting changed between the two builds.
        assertTrue("skillMastery" !in text, "A backup carries the evidence, not the conclusion")
        assertTrue("estimate" !in text)
        assertTrue("attempts" in text, "The evidence itself is there")
    }

    @Test
    fun `a file written now reads back as the same progress`() = runTest {
        store.seed()
        val text = service.exportToJson(PROFILE, clockMillis = EXPORTED_AT)

        val restored = FakeBackupStore()
        val result = ProgressBackupService(restored).importFromJson(text)

        assertEquals(2, result.sessionsRestored)
        assertEquals(3, result.attemptsRestored)
        assertEquals(1, result.gateCompletionsRestored)
        assertEquals(0, result.orphanedAttempts)
        assertEquals(store.attempts.map { it.id }.toSet(), restored.attempts.map { it.id }.toSet())
        assertEquals(store.sessions.map { it.id }.toSet(), restored.sessions.map { it.id }.toSet())
    }

    @Test
    fun `restoring rebuilds mastery from the attempts rather than trusting the file`() = runTest {
        store.seed()
        val text = service.exportToJson(PROFILE, clockMillis = EXPORTED_AT)

        val restored = FakeBackupStore()
        val result = ProgressBackupService(restored).importFromJson(text)

        assertEquals(1, restored.rebuildCount, "The rebuild should happen exactly once")
        assertTrue(result.skillsRebuilt > 0, "A restored profile should have mastery again")
    }

    @Test
    fun `an attempt whose session is missing is dropped, and the drop is reported`() = runTest {
        store.seed()
        store.attempts += attempt("a-orphan", sessionId = "session-that-was-never-exported")
        val text = service.exportToJson(PROFILE, clockMillis = EXPORTED_AT)

        val restored = FakeBackupStore()
        val result = ProgressBackupService(restored).importFromJson(text)

        assertEquals(1, result.orphanedAttempts, "The orphan should be counted")
        assertEquals(3, result.attemptsRestored)
        assertTrue(
            restored.attempts.none { it.id == "a-orphan" },
            "An attempt with no session would violate the schema's own parent rule",
        )
    }

    @Test
    fun `a backup from a newer app is refused rather than half-read`() {
        val text = """
            {
              "schemaVersion": ${ProgressBackup.SCHEMA_VERSION + 1},
              "contentVersion": "9.9.9",
              "exportedAtEpochMillis": 0,
              "profile": {
                "id": "p", "displayName": "P", "createdAtEpochMillis": 0, "contentVersion": "9.9.9"
              },
              "sessions": [], "attempts": [], "gateCompletions": []
            }
        """.trimIndent()

        val failure = assertFailsWith<BackupFormatException> { service.parse(text) }
        assertTrue(
            "newer version" in failure.message.orEmpty(),
            "The message should say why, not just that it failed: ${failure.message}",
        )
    }

    @Test
    fun `something that is not a backup at all is refused`() {
        assertFailsWith<BackupFormatException> { service.parse("{}") }
        assertFailsWith<BackupFormatException> { service.parse("not json") }
        assertFailsWith<BackupFormatException> { service.parse("") }
    }

    @Test
    fun `exporting a profile that does not exist says so`() = runTest {
        assertFailsWith<BackupFormatException> { service.export(ProfileId("nobody"), 0) }
    }

    @Test
    fun `retargeting a backup changes only its profile id`() = runTest {
        store.seed()
        val original = service.export(PROFILE, clockMillis = EXPORTED_AT)

        val retargeted = original.retargetedTo(ProfileId("this-install"))

        assertEquals("this-install", retargeted.profile.id)
        assertEquals(original.profile.displayName, retargeted.profile.displayName)
        assertEquals(original.sessions, retargeted.sessions)
        assertEquals(original.attempts, retargeted.attempts)
        assertEquals(original.gateCompletions, retargeted.gateCompletions)
    }

    @Test
    fun `importing a retargeted backup restores under the local profile, not the exported one`() = runTest {
        store.seed()
        val text = service.exportToJson(PROFILE, clockMillis = EXPORTED_AT)
        val local = ProfileId("this-install")

        val restored = FakeBackupStore()
        val backup = ProgressBackupService(restored).parse(text).retargetedTo(local)
        val result = ProgressBackupService(restored).import(backup)

        assertEquals(local, result.profile)
        assertEquals("this-install", restored.profile?.id)
    }

    @Test
    fun `an unknown field in a future file does not stop an import`() = runTest {
        store.seed()
        val text = service.exportToJson(PROFILE, clockMillis = EXPORTED_AT)
            .replaceFirst("\"contentVersion\"", "\"somethingAddedLater\": 1,\n  \"contentVersion\"")

        val restored = FakeBackupStore()
        val result = ProgressBackupService(restored).importFromJson(text)

        // Forward compatibility within a schema version: a field this build does not know about
        // is not a reason to refuse somebody their practice history.
        assertEquals(3, result.attemptsRestored)
    }

    // --- Fixtures ------------------------------------------------------------------------------

    private fun attempt(id: String, sessionId: String) = AttemptEntity(
        id = id,
        sessionId = sessionId,
        exerciseDefinitionId = "policy.seventh.build",
        exerciseSeed = 42,
        skillIds = "skill.seventh.build",
        chordSymbol = "Cmaj7",
        rootPitchClass = "C",
        startedAtEpochMillis = 1_000,
        firstInputAtEpochMillis = 1_200,
        completedAtEpochMillis = 1_800,
        verdict = "CORRECT",
        hintsUsed = 0,
        skipped = false,
        responseMillis = 800,
        onsetSpreadMillis = 40,
        evidence = "INDEPENDENT_CORRECT",
        errorClasses = "",
        expectedSnapshot = """{"chord":"Cmaj7"}""",
        performedSnapshot = """{"notes":[60,64,67,71]}""",
        semanticErrors = "[]",
    )

    private inner class FakeBackupStore : ProgressBackupStore {
        val sessions = mutableListOf<SessionEntity>()
        val attempts = mutableListOf<AttemptEntity>()
        val gates = mutableListOf<GateProgressEntity>()
        var profile: ProfileEntity? = null
        var rebuildCount = 0

        fun seed() {
            profile = ProfileEntity(
                id = "player-1",
                displayName = "Player",
                createdAtEpochMillis = 500,
                contentVersion = "0.2.0",
            )
            sessions += session("s1")
            sessions += session("s2")
            attempts += attempt("a1", "s1")
            attempts += attempt("a2", "s1")
            attempts += attempt("a3", "s2")
            gates += GateProgressEntity(
                profileId = "player-1",
                gateId = "gate.sevenths.build",
                firstCompletedAtEpochMillis = 2_000,
                sessionsPlayed = 2,
            )
        }

        private fun session(id: String) = SessionEntity(
            id = id,
            profileId = "player-1",
            gateId = "gate.sevenths.build",
            exercisePolicyId = "policy.seventh.build",
            seed = 7,
            startedAtEpochMillis = 900,
            endedAtEpochMillis = 2_000,
            exercisesPlanned = 20,
        )

        override suspend fun profile(id: ProfileId): ProfileEntity? =
            profile?.takeIf { it.id == id.value }

        override suspend fun sessions(id: ProfileId): List<SessionEntity> = sessions.toList()

        override suspend fun attempts(id: ProfileId): List<AttemptEntity> = attempts.toList()

        override suspend fun gateProgress(id: ProfileId): List<GateProgressEntity> = gates.toList()

        override suspend fun writeProfile(profile: ProfileEntity) {
            this.profile = profile
        }

        override suspend fun writeSessions(sessions: List<SessionEntity>) {
            this.sessions += sessions
        }

        override suspend fun writeAttempts(attempts: List<AttemptEntity>) {
            this.attempts += attempts
        }

        override suspend fun writeGateProgress(progress: List<GateProgressEntity>) {
            gates += progress
        }

        override suspend fun rebuildMastery(id: ProfileId): Int {
            rebuildCount++
            return attempts.flatMap { it.skillIds.split(",") }.filter { it.isNotBlank() }.toSet().size
        }
    }

    private companion object {
        val PROFILE = ProfileId("player-1")
        const val EXPORTED_AT = 1_700_000_000_000L
    }
}
