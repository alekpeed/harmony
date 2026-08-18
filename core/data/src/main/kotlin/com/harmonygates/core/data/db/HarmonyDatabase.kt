package com.harmonygates.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The learning database.
 *
 * Version 2. When it becomes version 3, a [Migration] goes into [HarmonyMigrations] and the
 * exported schema in `core/data/schemas` gains a file — `RoomSchemaTest` fails the build if
 * either half is missing, which is the mechanism that keeps 11_DATA_MODEL_AND_PERSISTENCE.md
 * §4's promise to "retain old attempt history" from depending on anyone remembering.
 *
 * `fallbackToDestructiveMigration` is deliberately never called. Losing a player's history on
 * upgrade is precisely the failure this module exists to prevent, and it is a one-line mistake
 * to make, so its absence is worth stating.
 */
@Database(
    entities = [
        ProfileEntity::class,
        SessionEntity::class,
        AttemptEntity::class,
        SkillMasteryEntity::class,
        GateProgressEntity::class,
        UnlockEntity::class,
        ReviewItemEntity::class,
        RelativePitchLevelStatEntity::class,
    ],
    version = HarmonyDatabase.VERSION,
    exportSchema = true,
)
public abstract class HarmonyDatabase : RoomDatabase() {

    public abstract fun profiles(): ProfileDao

    public abstract fun sessions(): SessionDao

    public abstract fun attempts(): AttemptDao

    public abstract fun skillMastery(): SkillMasteryDao

    public abstract fun gateProgress(): GateProgressDao

    public abstract fun unlocks(): UnlockDao

    public abstract fun reviewItems(): ReviewItemDao

    public abstract fun relativePitchLevelStats(): RelativePitchLevelStatDao

    public companion object {
        public const val VERSION: Int = 2

        public const val FILE_NAME: String = "harmony-gates.db"

        public fun open(context: Context, name: String = FILE_NAME): HarmonyDatabase =
            Room.databaseBuilder(context.applicationContext, HarmonyDatabase::class.java, name)
                .addMigrations(*HarmonyMigrations.ALL)
                .build()

        /** An unsaved database, for previews and for tests that do not care about durability. */
        public fun inMemory(context: Context): HarmonyDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, HarmonyDatabase::class.java)
                .build()
    }
}

/**
 * Every migration this database has ever needed.
 *
 * `RoomSchemaTest` asserts that the list forms an unbroken chain from 1 to
 * [HarmonyDatabase.VERSION], so raising the version without adding the step fails the build
 * rather than the upgrade.
 */
public object HarmonyMigrations {
    /** Version 2: the relative-pitch ladder's accuracy table. Nothing existing changes shape. */
    private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `relative_pitch_level_stats` (
                    `profileId` TEXT NOT NULL,
                    `levelId` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `correct` INTEGER NOT NULL,
                    `lastAnsweredAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`profileId`, `levelId`),
                    FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_relative_pitch_level_stats_profileId` " +
                    "ON `relative_pitch_level_stats` (`profileId`)",
            )
        }
    }

    public val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /** The versions each migration steps between, oldest first. */
    public val steps: List<Pair<Int, Int>> get() = ALL.map { it.startVersion to it.endVersion }
}
