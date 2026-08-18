package com.harmonygates

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.campaign.CampaignRoute
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.eartraining.EarTrainingRoute
import com.harmonygates.exercise.ChordGateRoute
import com.harmonygates.exercise.ChordGateViewModel
import com.harmonygates.exercise.SessionRequest
import com.harmonygates.harness.HarmonyLabRoute
import com.harmonygates.home.HomeAction
import com.harmonygates.home.HomeDestination
import com.harmonygates.home.HomeScreen
import com.harmonygates.midi.MidiDiagnosticsRoute
import com.harmonygates.placeholder.PlaceholderScreen
import com.harmonygates.progress.ProgressRoute
import com.harmonygates.progression.ProgressionRunRoute
import com.harmonygates.relativepitch.RelativePitchRoute
import com.harmonygates.settings.SettingsRoute
import com.harmonygates.library.LibraryRoute
import com.harmonygates.menu.MenuScreen
import com.harmonygates.sightreading.SightReadingRoute
import com.harmonygates.voiceleading.VoiceLeadingRoute

enum class AppScreen {
    Home,
    TheoryLab,
    MidiSetup,
    Settings,
    ChordGate,
    RelativePitch,
    EarTraining,
    SightReading,
    VoiceLeading,
    Library,
    Menu,
    ProgressionRun,
    Campaign,
    Progress,
    Placeholder,
}

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
    var gate by rememberSaveable { mutableStateOf<String?>(null) }
    var placeholder by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            pendingMessage = null
        }
    }

    BackHandler(enabled = screen != AppScreen.Home) {
        screen = AppScreen.Home
        gate = null
        placeholder = null
    }

    fun navigate(destination: HomeDestination) {
        when (destination) {
            HomeDestination.TheoryLab -> screen = AppScreen.TheoryLab
            HomeDestination.Settings -> screen = AppScreen.Settings
            // The ladder is the entry point now; it hands off to the keyboard console
            // (AppScreen.EarTraining) itself once its last two levels are reached.
            HomeDestination.EarTraining -> screen = AppScreen.RelativePitch
            HomeDestination.VoicingLab -> screen = AppScreen.VoiceLeading
            HomeDestination.SightReading -> screen = AppScreen.SightReading
            HomeDestination.Library -> screen = AppScreen.Library
            HomeDestination.Menu -> screen = AppScreen.Menu
            // One exercise drawn from what you are weakest at. The planner already orders a
            // session that way, so the daily challenge is quick practice with no gate around it.
            HomeDestination.DailyChallenge -> {
                gate = null
                screen = AppScreen.ChordGate
            }
            HomeDestination.ProgressionLab -> screen = AppScreen.ProgressionRun
            HomeDestination.Campaign,
            HomeDestination.NextGate,
            HomeDestination.Resume,
            -> screen = AppScreen.Campaign
            HomeDestination.Progress,
            HomeDestination.Profile,
            -> screen = AppScreen.Progress
            HomeDestination.ChordGate,
            HomeDestination.QuickPractice,
            -> {
                gate = null
                screen = AppScreen.ChordGate
            }
            HomeDestination.Home -> {
                screen = AppScreen.Home
                gate = null
                placeholder = null
            }
            else -> {
                placeholder = destination.name
                screen = AppScreen.Placeholder
            }
        }
    }

    val context = LocalContext.current
    var lastCrash by remember { mutableStateOf(CrashLog.lastCrash(context)) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { insets ->
        BuildStamp()

        lastCrash?.let { trace ->
            CrashReport(
                trace = trace,
                onDismiss = {
                    CrashLog.clear(context)
                    lastCrash = null
                },
                modifier = Modifier.padding(insets),
            )
            return@Scaffold
        }

        when (screen) {
            AppScreen.Home -> HomeScreen(
                onAction = { action ->
                    if (action.destination == HomeDestination.Home) {
                        pendingMessage = "Already home"
                    } else {
                        navigate(action.destination)
                    }
                },
            )

            AppScreen.TheoryLab -> HarmonyLabRoute(modifier = Modifier.padding(insets))
            AppScreen.MidiSetup -> MidiDiagnosticsRoute(modifier = Modifier.padding(insets))
            AppScreen.Settings -> SettingsRoute(
                onOpenMidiSetup = { screen = AppScreen.MidiSetup },
                modifier = Modifier.padding(insets),
            )
            AppScreen.ChordGate -> ChordGateSession(gateId = gate, modifier = Modifier.padding(insets))
            AppScreen.RelativePitch -> RelativePitchRoute(
                onExit = { screen = AppScreen.Home },
                onOpenFullTrainer = { screen = AppScreen.EarTraining },
                modifier = Modifier.padding(insets),
            )
            AppScreen.EarTraining -> EarTrainingRoute(
                onExit = { screen = AppScreen.RelativePitch },
                onOpenSettings = { screen = AppScreen.Settings },
            )
            AppScreen.VoiceLeading -> VoiceLeadingRoute(onExit = { screen = AppScreen.Home })
            AppScreen.SightReading -> SightReadingRoute(
                onExit = { screen = AppScreen.Home },
                modifier = Modifier.padding(insets),
            )
            AppScreen.Library -> LibraryRoute(
                onExit = { screen = AppScreen.Home },
                modifier = Modifier.padding(insets),
            )
            AppScreen.Menu -> MenuScreen(
                onNavigate = ::navigate,
                onExit = { screen = AppScreen.Home },
                modifier = Modifier.padding(insets),
            )
            AppScreen.ProgressionRun -> ProgressionRunRoute(onNavigate = ::navigate)
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
                    title = HomeAction.entries.firstOrNull { it.destination == destination }?.label ?: destination.name,
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

@Composable
private fun CrashReport(trace: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Text(
            text = "The last session ended in a crash",
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.title,
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = trace,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                color = HarmonyTheme.colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = HarmonyTheme.typography.caption,
            )
        }
        SecondaryButton(label = "Carry on", onClick = onDismiss)
    }
}

@Composable
private fun BuildStamp() {
    if (!BuildConfig.DEBUG) return
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            text = "build ${BuildConfig.VERSION_NAME} · content ${BuildConfig.CONTENT_VERSION}",
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = STAMP_ALPHA),
            fontSize = STAMP_SIZE,
        )
    }
}

private const val STAMP_ALPHA = 0.55f
private val STAMP_SIZE = 10.sp

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
