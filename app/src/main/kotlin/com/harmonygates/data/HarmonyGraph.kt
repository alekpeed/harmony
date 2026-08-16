package com.harmonygates.data

import android.content.Context
import com.harmonygates.core.data.content.AssetContentSource
import com.harmonygates.core.data.content.ContentRepository
import com.harmonygates.core.data.content.DefaultContentRepository
import com.harmonygates.core.data.db.HarmonyDatabase
import com.harmonygates.core.data.prefs.HarmonyPreferences
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.data.progress.RoomProgressRepository

/**
 * The app's singletons, in one place.
 *
 * 10_ANDROID_ARCHITECTURE.md leaves dependency injection open, and a personal app with four
 * long-lived objects does not need a framework to hand them out. What it does need is one place
 * that decides when the database is opened, so that two view models cannot each open their own.
 *
 * When a DI framework does arrive, this file is what it replaces.
 */
object HarmonyGraph {

    @Volatile
    private var database: HarmonyDatabase? = null

    @Volatile
    private var progress: ProgressRepository? = null

    @Volatile
    private var content: ContentRepository? = null

    @Volatile
    private var preferences: HarmonyPreferences? = null

    fun database(context: Context): HarmonyDatabase =
        database ?: synchronized(this) {
            database ?: HarmonyDatabase.open(context).also { database = it }
        }

    fun progress(context: Context): ProgressRepository =
        progress ?: synchronized(this) {
            progress ?: RoomProgressRepository(database(context)).also { progress = it }
        }

    fun content(context: Context): ContentRepository =
        content ?: synchronized(this) {
            content ?: DefaultContentRepository(AssetContentSource(context)).also { content = it }
        }

    fun preferences(context: Context): HarmonyPreferences =
        preferences ?: synchronized(this) {
            preferences ?: HarmonyPreferences(context).also { preferences = it }
        }

    /** The content pack the stored history was recorded against (11 §4). */
    const val CONTENT_VERSION: String = "0.1.0"
}
