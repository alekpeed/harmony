package com.harmonygates

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            HarmonyTheme {
                HarmonyLandscape {
                    AppRoot()
                }
            }
        }
    }
}
