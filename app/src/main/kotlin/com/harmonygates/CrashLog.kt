package com.harmonygates

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The last crash, kept so it can be read on the device.
 *
 * A tester with a tablet in their hands and no cable can see that an app died and nothing about
 * why. "It crashes when I tap that" is not a bug report anybody can act on, and the round trip
 * to find out more costs an hour each time.
 *
 * So the stack trace is written down as it happens and shown on the next launch. It is not a
 * crash reporting service and nothing leaves the device — 17_BUILD_CI_INSTALL_RELEASE.md §7 is
 * explicit that v1 has no analytics backend and that a practice history is private. This is a
 * note the app leaves for whoever opens it next.
 */
object CrashLog {

    private const val PREFERENCES = "harmony.crash"
    private const val KEY_TRACE = "trace"
    private const val KEY_WHEN = "when"

    /**
     * Records crashes from every thread, then lets the platform do what it would have done.
     *
     * The default handler is called afterwards rather than swallowed: an app that catches its
     * own fatal errors and carries on is in an unknown state, and the second failure is always
     * harder to read than the first.
     */
    fun install(context: Context, nowMillis: Long) {
        val preferences = preferences(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // `commit` rather than `apply`: the process is about to die, and an
                // asynchronous write would lose the one thing this exists to keep.
                preferences.edit(commit = true) {
                    putString(KEY_TRACE, describe(thread, error))
                    putLong(KEY_WHEN, nowMillis)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last recorded crash, or null. */
    fun lastCrash(context: Context): String? =
        preferences(context).getString(KEY_TRACE, null)?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        preferences(context).edit {
            remove(KEY_TRACE)
            remove(KEY_WHEN)
        }
    }

    private fun describe(thread: Thread, error: Throwable): String {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("${error::class.qualifiedName}: ${error.message}")
            appendLine("on thread ${thread.name}")
            appendLine()
            // The whole trace is kept. The line that matters is rarely the first one, and a
            // truncated trace is how a bug report ends up naming the framework instead of us.
            append(stack)
        }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
