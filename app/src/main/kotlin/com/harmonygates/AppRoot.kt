package com.harmonygates

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.campaign.CampaignRoute
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.exercise.ChordGateRoute
import com.harmonygates.exercise.ChordGateViewModel
import com.harmonygates.exercise.SessionRequest
import com.harmonygates.harness.HarmonyLabRoute
import com.harmonygates.home.HomeDestination
import com.harmonygates.home.HomeScreen
import com.harmonygates.midi.MidiDiagnosticsRoute
import com.harmonygates.progress.ProgressRoute
import com.harmonygates.progression.ProgressionRunRoute

/** The places the app can currently be. */
enum class AppScreen {
    Home,
    TheoryLab,
    MidiSetup,
    ChordGate,
    ProgressionRun,
    Campaign,
    Progress,
}

/**
 * Top-level screen switch.
 *
 * Navigation 3 and typed routes arrive with the full campaign UI (10_ANDROID_ARCHITECTURE.md §7).
 * Seven destinations all reached from one screen still do not need a back stack beyond "return
 * home", and introducing a router now would be scaffolding with no load on it. The current
 * screen is saved so it survives rotation and process death, which is the only behaviour a
 * router would add here.
 */
@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
    // The gate a campaign row asked for, so the exercise screen knows whether this session
    // counts towards anything. Saved with the screen, so a rotation mid-gate stays in the gate.
    var gate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            pendingMessage = null
        }
    }

    // The artwork has no drawn back control, so system back is the way home.
    BackHandler(enabled = screen != AppScreen.Home) {
        screen = AppScreen.Home
        gate = null
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { insets ->
        when (screen) {
            AppScreen.Home -> HomeScreen(
                modifier = Modifier.padding(insets),
                onAction = { action ->
                    when (action.destination) {
                        HomeDestination.TheoryLab -> screen = AppScreen.TheoryLab
                        HomeDestination.Settings -> screen = AppScreen.MidiSetup
                        HomeDestination.ProgressionLab -> screen = AppScreen.ProgressionRun

                        HomeDestination.Campaign,
                        HomeDestination.NextGate,
                        HomeDestination.Resume,
                        -> screen = AppScreen.Campaign

                        HomeDestination.Progress,
                        HomeDestination.Profile,
                        -> screen = AppScreen.Progress

                        HomeDestination.ChordGate, HomeDestination.QuickPractice -> {
                            gate = null
                            screen = AppScreen.ChordGate
                        }
                        // Saying "not yet" is better than navigating into an empty room. The
                        // phase number turns a dead end into a schedule.
                        else -> if (!action.destination.isImplemented) {
                            pendingMessage =
                                "${action.label} arrives in phase ${action.destination.arrivesInPhase}"
                        }
                    }
                },
            )

            AppScreen.TheoryLab -> HarmonyLabRoute(modifier = Modifier.padding(insets))

            AppScreen.MidiSetup -> MidiDiagnosticsRoute(modifier = Modifier.padding(insets))

            AppScreen.ChordGate -> ChordGateSession(
                gateId = gate,
                modifier = Modifier.padding(insets),
            )

            // The track draws to the edges, so it is handed the window rather than the insets;
            // its own HUD applies `safeDrawingPadding` to the parts that carry text.
            AppScreen.ProgressionRun -> ProgressionRunRoute()

            AppScreen.Campaign -> CampaignRoute(
                modifier = Modifier.padding(insets),
                onPlayGate = { gateId ->
                    gate = gateId.value
                    screen = AppScreen.ChordGate
                },
            )

            AppScreen.Progress -> ProgressRoute(modifier = Modifier.padding(insets))
        }
    }
}

/**
 * A chord-gate session, for a gate or for free practice.
 *
 * Keyed on the gate so that moving between gates starts a fresh session rather than continuing
 * the previous one under a new title. Which policy a gate runs is the curriculum's business, so
 * only the id travels here.
 */
@Composable
private fun ChordGateSession(gateId: String?, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as Application
    val request = remember(gateId) {
        gateId?.let { SessionRequest.Gate(GateId(it)) } ?: SessionRequest.QuickPractice
    }

    ChordGateRoute(
        modifier = modifier,
        viewModel = viewModel(
            key = gateId ?: "practice",
            factory = ChordGateViewModel.factory(application, request),
        ),
    )
}
