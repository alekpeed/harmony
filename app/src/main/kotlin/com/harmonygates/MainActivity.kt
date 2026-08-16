package com.harmonygates

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * The single entry point.
 *
 * Screen switching lives in [AppRoot]. Navigation 3 and typed routes arrive in Phase 6 with the
 * campaign, when there is a back stack worth modelling.
 *
 * The activity handles configuration changes itself so that a running exercise survives a
 * window resize, which 10_ANDROID_ARCHITECTURE.md §9 requires from the start.
 *
 * Everything is wrapped in [HarmonyLandscape]: the manifest asks for landscape and Android 16
 * may decline, so the shape is also enforced from inside the window.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        goFullScreen()
        setContent {
            HarmonyTheme(reducedMotion = animationsAreOff()) {
                HarmonyLandscape {
                    AppRoot()
                }
            }
        }
    }

    /**
     * The system bars come back after some interactions. This puts them away again.
     *
     * Android re-shows the bars on its own after certain events — a permission dialog, an app
     * switch, a transient swipe that times out. Without this the clock reappears over the
     * artwork some minutes into a session and stays there, which is worse than never having
     * hidden it, because it looks like a bug that comes and goes.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()
    }

    /**
     * Hides the status and navigation bars.
     *
     * Every approved frame is composed at 1536 x 1024 and is the whole screen: the sidebar, the
     * title, the eight tiles and the room they sit in are one picture with no margin designed
     * into it for a clock. Drawing edge to edge and leaving the bars visible puts the time and
     * the battery on top of the artwork, and insetting the frame away from them shrinks the
     * design and leaves a strip of dead colour instead. Neither is what was drawn.
     *
     * So the bars go away entirely, and come back on a swipe from the edge as a transient
     * overlay — [WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE]. Transient
     * bars do not change the window's insets, so revealing them does not move the artwork
     * underneath: it stays exactly where it was and the bars float over it until they time out.
     *
     * This is the one screen decision in the app that is not waiting on Figma, because it is not
     * a question of design. The design is a full-bleed frame; a full-bleed frame needs the
     * screen.
     */
    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Whether the player has turned animations off in system settings.
     *
     * 12_UI_UX_AND_FIGMA_HANDOFF.md §10 asks for a reduced-motion mode. The system already has
     * one, and honouring it is better than adding a second switch inside the app for the same
     * preference. Read here rather than in `core:designsystem`, which has no Android context on
     * purpose.
     */
    private fun animationsAreOff(): Boolean = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}
