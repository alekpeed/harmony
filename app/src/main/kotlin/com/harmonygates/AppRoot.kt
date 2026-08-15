package com.harmonygates

import androidx.compose.material3.SnackbarHost
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
import com.harmonygates.harness.HarmonyLabRoute
import com.harmonygates.home.HomeDestination
import com.harmonygates.home.HomeScreen

/** The two places the app can currently be. */
enum class AppScreen {
    Home,
    TheoryLab,
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { insets ->
        when (screen) {
            AppScreen.Home -> HomeScreen(
                modifier = Modifier.padding(insets),
                onAction = { action ->
                    when {
                        action.destination == HomeDestination.TheoryLab ->
                            screen = AppScreen.TheoryLab
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
        }
    }
}
