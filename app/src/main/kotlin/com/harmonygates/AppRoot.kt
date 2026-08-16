package com.harmonygates

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.harmonygates.exercise.ChordGateRoute
import com.harmonygates.progression.ProgressionRunRoute
import com.harmonygates.harness.HarmonyLabRoute
import com.harmonygates.midi.MidiDiagnosticsRoute
import com.harmonygates.home.HomeDestination
import com.harmonygates.home.HomeScreen

/** The two places the app can currently be. */
enum class AppScreen {
    Home,
    TheoryLab,
    MidiSetup,
    ChordGate,
    ProgressionRun,
}

/**
 * Top-level screen switch.
 *
 * Two destinations do not need a router. Navigation 3 and typed routes arrive in Phase 6 with
 * the campaign (10_ANDROID_ARCHITECTURE.md §7), when there is a back stack worth modelling;
 * introducing one now would be scaffolding with no load on it. The current screen is saved so
 * it survives rotation and process death, which is the only behaviour a router would add here.
 */
@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            pendingMessage = null
        }
    }

    // The artwork has no drawn back control, so system back is the way home. Once Navigation 3
    // arrives in Phase 6 this becomes an ordinary back stack.
    BackHandler(enabled = screen != AppScreen.Home) { screen = AppScreen.Home }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { insets ->
        when (screen) {
            AppScreen.Home -> HomeScreen(
                modifier = Modifier.padding(insets),
                onAction = { action ->
                    when {
                        action.destination == HomeDestination.TheoryLab ->
                            screen = AppScreen.TheoryLab

                        action.destination == HomeDestination.Settings ->
                            screen = AppScreen.MidiSetup

                        action.destination == HomeDestination.ChordGate ||
                            action.destination == HomeDestination.QuickPractice ->
                            screen = AppScreen.ChordGate

                        action.destination == HomeDestination.ProgressionLab ->
                            screen = AppScreen.ProgressionRun
                        // Saying "not yet" is better than navigating into an empty room. The
                        // phase number turns a dead end into a schedule.
                        !action.destination.isImplemented ->
                            pendingMessage =
                                "${action.label} arrives in phase ${action.destination.arrivesInPhase}"
                        else -> screen = AppScreen.Home
                    }
                },
            )

            AppScreen.TheoryLab -> HarmonyLabRoute(modifier = Modifier.padding(insets))

            AppScreen.MidiSetup -> MidiDiagnosticsRoute(modifier = Modifier.padding(insets))

            AppScreen.ChordGate -> ChordGateRoute(modifier = Modifier.padding(insets))

            // The track draws to the edges, so it is handed the window rather than the insets;
            // its own HUD applies `safeDrawingPadding` to the parts that carry text.
            AppScreen.ProgressionRun -> ProgressionRunRoute()
        }
    }
}
