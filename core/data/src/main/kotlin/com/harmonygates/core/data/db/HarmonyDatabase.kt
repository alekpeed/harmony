package com.harmonygates.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The learning database.
 *
 * Version 1. When it becomes version 2, a [Migration] goes into [HarmonyMigrations] and the
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

    public companion object {
        public const val VERSION: Int = 1

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
 * Empty at version 1, and not a placeholder: `HarmonyMigrationTest` asserts that the list forms
 * an unbroken chain from 1 to [HarmonyDatabase.VERSION], so raising the version without adding
 * the step fails the build rather than the upgrade.
 */
public object HarmonyMigrations {
    public val ALL: Array<Migration> = emptyArray()

    /** The versions each migration steps between, oldest first. */
    public val steps: List<Pair<Int, Int>> get() = ALL.map { it.startVersion to it.endVersion }
}
