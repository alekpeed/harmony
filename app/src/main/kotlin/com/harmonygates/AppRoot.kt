package com.harmonygates

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.campaign.CampaignRoute
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.exercise.ChordGateRoute
import com.harmonygates.exercise.ChordGateViewModel
import com.harmonygates.exercise.SessionRequest
import com.harmonygates.harness.HarmonyLabRoute
import com.harmonygates.home.HomeAction
import com.harmonygates.home.HomeDestination
import com.harmonygates.home.HomeScreen
import com.harmonygates.placeholder.PlaceholderScreen
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

    /** A destination whose screen is not built. Carries which one, so it can say so. */
    Placeholder,
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
    // Which unbuilt destination the placeholder is standing in for. Saved with the screen so a
    // rotation does not land the player on a placeholder for nothing in particular.
    var placeholder by rememberSaveable { mutableStateOf<String?>(null) }
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
        placeholder = null
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { insets ->
        BuildStamp()

        when (screen) {
            // Handed the whole window, not the insets: the home frame insets itself, so that
            // the artwork is fitted inside the safe area rather than fitted to the window and
            // then slid under a system bar.
            AppScreen.Home -> HomeScreen(
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

                        // Home is where we already are, and tapping it should not feel broken.
                        HomeDestination.Home -> pendingMessage = "Already home"

                        // Everything else opens a screen that says what it will be. A control
                        // that goes somewhere can be tested; one that raises a message and
                        // leaves you where you were cannot be told apart from a dead one.
                        else -> {
                            placeholder = action.destination.name
                            screen = AppScreen.Placeholder
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

            AppScreen.Placeholder -> {
                val destination = placeholder
                    ?.let { name -> HomeDestination.entries.firstOrNull { it.name == name } }
                    ?: HomeDestination.Menu
                PlaceholderScreen(
                    title = HomeAction.entries
                        .firstOrNull { it.destination == destination }
                        ?.label
                        ?: destination.name,
                    summary = destination.summary,
                    engineStatus = destination.engineStatus,
                    arrivesInPhase = destination.arrivesInPhase,
                    onBack = {
                        screen = AppScreen.Home
                        placeholder = null
                    },
                    modifier = Modifier.padding(insets),
                )
            }
        }
    }
}

/**
 * Which build this is, in the corner, on every screen.
 *
 * Debug only, and small. It exists because three different debug builds reached a tablet under
 * one version number and one filename, and there was no way to tell from the device which of
 * them was running — so "is my fix in this build" could not be answered, and every question
 * after it was a guess. A build that cannot identify itself cannot be tested.
 *
 * The version name carries the commit count and the short SHA, and `+dirty` when the build came
 * from a working tree with uncommitted changes in it.
 */
@Composable
private fun BuildStamp() {
    if (!BuildConfig.DEBUG) return
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            text = "build ${BuildConfig.VERSION_NAME} · content ${BuildConfig.CONTENT_VERSION}",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = STAMP_ALPHA),
            fontSize = STAMP_SIZE,
        )
    }
}

private const val STAMP_ALPHA = 0.55f
private val STAMP_SIZE = 10.sp

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
