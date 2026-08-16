package com.harmonygates.core.data

import com.harmonygates.core.data.db.HarmonyDatabase
import com.harmonygates.core.data.db.HarmonyMigrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The exported schema, checked as a source file.
 *
 * Room's own `MigrationTestHelper` needs a device, and there is not one here. What it needs a
 * device *for* is running SQL — and the schema Room exports is ordinary DDL, so the same ground
 * can be covered by running it against a plain JVM SQLite.
 *
 * That makes three things testable without hardware: that the schema was exported at all, that
 * the tables it describes really can be created, and that the migration chain has no gap in it.
 * 11_DATA_MODEL_AND_PERSISTENCE.md §4's promise to "retain old attempt history" depends on all
 * three, and none of them should wait for an emulator.
 */
class RoomSchemaTest {

    private val schemaDirectory = File("schemas/${HarmonyDatabase::class.qualifiedName}")

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDirectory, "$version.json")
        assertTrue(
            file.isFile,
            "No exported schema for version $version at ${file.absolutePath}. " +
                "Room writes it during compilation; the file belongs in version control.",
        )
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun entities(version: Int) = schema(version)["entities"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun `the current version is exported`() {
        val exported = schema(HarmonyDatabase.VERSION)["version"]!!.jsonPrimitive.int

        assertEquals(HarmonyDatabase.VERSION, exported)
    }

    @Test
    fun `every table the app relies on is in the schema`() {
        val tables = entities(HarmonyDatabase.VERSION).map { it["tableName"]!!.jsonPrimitive.content }

        // 11 §2's list. A table quietly disappearing from an entity list is a silent data loss
        // bug, so the names are written out here rather than derived from the same source.
        assertEquals(
            setOf(
                "profiles",
                "sessions",
                "attempts",
                "skill_mastery",
                "gate_progress",
                "unlocks",
                "review_items",
            ),
            tables.toSet(),
        )
    }

    @Test
    fun `an attempt keeps everything needed to reproduce it`() {
        val attempts = assertNotNull(
            entities(HarmonyDatabase.VERSION).firstOrNull { it["tableName"]!!.jsonPrimitive.content == "attempts" },
        )
        val columns = attempts["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }

        // 11 §3's record. The seed and the two snapshots are the difference between a bug report
        // that can be replayed and one that cannot.
        listOf(
            "exerciseSeed",
            "expectedSnapshotJson",
            "performedSnapshotJson",
            "semanticErrorsJson",
            "evidence",
        ).forEach { required ->
            assertTrue(required in columns, "attempts is missing '$required'; columns were $columns")
        }
    }

    @Test
    fun `the migration chain reaches the current version without a gap`() {
        val steps = HarmonyMigrations.steps.sortedBy { it.first }

        if (HarmonyDatabase.VERSION == 1) {
            assertTrue(steps.isEmpty(), "Version 1 needs no migrations, but found $steps")
            return
        }

        var at = 1
        steps.forEach { (from, to) ->
            assertEquals(at, from, "Migration chain jumps: expected a step from $at, got $from")
            at = to
        }
        assertEquals(
            HarmonyDatabase.VERSION,
            at,
            "The chain stops at $at but the database is version ${HarmonyDatabase.VERSION}. " +
                "Raising the version without adding the migration loses a player's history.",
        )
    }

    @Test
    fun `every exported schema has a migration leading to it`() {
        val exported = schemaDirectory.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            .orEmpty()

        assertTrue(exported.isNotEmpty(), "No schemas exported at all")
        exported.filter { it > 1 }.forEach { version ->
            assertTrue(
                HarmonyMigrations.steps.any { it.second == version },
                "Schema $version was exported but nothing migrates to it",
            )
        }
    }

    // --- The DDL actually works ----------------------------------------------------------------

    private fun createSchema(connection: Connection, version: Int) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            entities(version).forEach { entity ->
                statement.execute(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", entity["tableName"]!!.jsonPrimitive.content))
                entity["indices"]?.jsonArray?.forEach { index ->
                    statement.execute(
                        index.jsonObject["createSql"]!!.jsonPrimitive.content
                            .replace("\${TABLE_NAME}", entity["tableName"]!!.jsonPrimitive.content),
                    )
                }
            }
        }
    }

    private fun withDatabase(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection, HarmonyDatabase.VERSION)
            block(connection)
        }
    }

    @Test
    fun `the exported schema creates a working database`() {
        withDatabase { connection ->
            val tables = mutableListOf<String>()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rows ->
                    while (rows.next()) tables += rows.getString(1)
                }
            }
            assertTrue("attempts" in tables, "Tables created: $tables")
            assertTrue("skill_mastery" in tables, "Tables created: $tables")
        }
    }

    @Test
    fun `deleting a profile takes its history with it`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute(
                    "INSERT INTO profiles VALUES ('p1', 'Player', 0, '0.1.0')",
                )
                statement.execute(
                    "INSERT INTO sessions VALUES ('s1', 'p1', NULL, 'policy.x', 1, 0, NULL, 10)",
                )
                statement.execute(
                    """
                    INSERT INTO attempts VALUES (
                      'a1','s1','def',1,'skill.x','Cmaj7','C',0,NULL,0,'CORRECT',0,0,NULL,NULL,
                      'INDEPENDENT_CORRECT','','expected','performed',''
                    )
                    """.trimIndent(),
                )
                statement.execute("DELETE FROM profiles WHERE id = 'p1'")

                statement.executeQuery("SELECT COUNT(*) FROM attempts").use { rows ->
                    rows.next()
                    assertEquals(
                        0,
                        rows.getInt(1),
                        "An attempt whose session and profile are gone must not survive them",
                    )
                }
            }
        }
    }

    @Test
    fun `a skill cannot have two mastery rows for one profile`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles VALUES ('p1', 'Player', 0, '0.1.0')")
                val row = "'p1','skill.x',0.5,1,1,0.5,NULL,NULL,NULL,'','',''"
                statement.execute("INSERT INTO skill_mastery VALUES ($row)")
                val duplicate = runCatching {
                    statement.execute("INSERT INTO skill_mastery VALUES ($row)")
                }
                if (duplicate.isSuccess) {
                    fail("The composite primary key should stop a second row for the same skill")
                }
            }
        }
    }
}
