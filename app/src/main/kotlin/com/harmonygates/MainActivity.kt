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
 * Screen switching lives in [AppRoot]. The activity deliberately uses Android's normal
 * recreation behavior when its window configuration changes. Compose saveable state restores
 * the current destination, while every artwork screen receives fresh window constraints and
 * insets instead of carrying geometry from an earlier task/window configuration.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this, System.currentTimeMillis())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullScreen()
        setContent {
            HarmonyTheme(reducedMotion = animationsAreOff()) {
                RequireLandscape {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        goFullScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()
    }

    /**
     * Requests immersive system bars for a true full-screen window.
     *
     * Android may refuse or constrain this in multi-window/freeform modes. ArtworkScreen does
     * not depend on this request succeeding: if bars remain visible, its live safeDrawing insets
     * keep painted controls out from under them while the root background remains edge-to-edge.
     */
    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun animationsAreOff(): Boolean = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}
