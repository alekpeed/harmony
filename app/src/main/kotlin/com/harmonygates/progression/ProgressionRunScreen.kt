package com.harmonygates.progression

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.R
import com.harmonygates.core.designsystem.progression.ChordOrbUiModel
import com.harmonygates.core.designsystem.progression.ProgressionTrack
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.home.HomeDestination

/**
 * Progression Run is intentionally three layers:
 * 1. a clean full-screen room plate;
 * 2. the live chord track plus a minimal practice HUD;
 * 3. transient navigation/setup drawers that exist only when the player asks for them.
 *
 * No variable value is baked into the background, and no permanent control slab is allowed to
 * cover the practice corridor.
 */
@Composable
fun ProgressionRunRoute(
    modifier: Modifier = Modifier,
    onNavigate: (HomeDestination) -> Unit = {},
    viewModel: ProgressionRunViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressionRunScreen(
        state = state,
        track = viewModel.track,
        onIntent = viewModel::onIntent,
        onNavigate = onNavigate,
        modifier = modifier,
    )
}

private enum class Drawer { Navigation, Setup }

@Composable
fun ProgressionRunScreen(
    state: ProgressionRunUiState,
    track: TrackSpec,
    onIntent: (ProgressionRunIntent) -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drawer by remember { mutableStateOf<Drawer?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonyTheme.colors.background),
    ) {
        BackgroundPlate()

        ProgressionTrack(
            geometry = track.geometry,
            orbs = animatedOrbs(state, track),
            designAspectRatio = track.aspectRatio,
            showPath = false,
        )

        PracticeHud(
            state = state,
            onMenu = { drawer = Drawer.Navigation },
            onSetup = {
                if (state.run.status == ProgressionRunStatus.RUNNING) {
                    onIntent(ProgressionRunIntent.TogglePlaying)
                }
                drawer = Drawer.Setup
            },
            onPlayPause = { onIntent(ProgressionRunIntent.TogglePlaying) },
        )

        drawer?.let { openDrawer ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HarmonyTheme.colors.background.copy(alpha = 0.42f))
                    .clickable { drawer = null },
            )
            when (openDrawer) {
                Drawer.Navigation -> NavigationDrawer(
                    onNavigate = { destination ->
                        drawer = null
                        onNavigate(destination)
                    },
                    onDismiss = { drawer = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Drawer.Setup -> SetupDrawer(
                    state = state,
                    onIntent = onIntent,
                    onDone = {
                        drawer = null
                        if (state.run.status == ProgressionRunStatus.PAUSED) {
                            onIntent(ProgressionRunIntent.TogglePlaying)
                        }
                    },
                    onDismiss = { drawer = null },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun BackgroundPlate() {
    if (!booleanResource(R.bool.progression_run_background_available)) return
    Image(
        painter = painterResource(R.drawable.progression_run_background),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun animatedOrbs(state: ProgressionRunUiState, track: TrackSpec): List<ChordOrbUiModel> {
    val progress = remember { Animatable(1f) }
    val easing = remember(track.easing) {
        CubicBezierEasing(track.easing[0], track.easing[1], track.easing[2], track.easing[3])
    }
    LaunchedEffect(state.run.advanceCount) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = track.advanceDurationMs, easing = easing))
    }
    val offset = 1f - progress.value
    return state.orbs.map { orb -> orb.copy(slot = orb.slot + offset) }
}

@Composable
private fun PracticeHud(
    state: ProgressionRunUiState,
    onMenu: () -> Unit,
    onSetup: () -> Unit,
    onPlayPause: () -> Unit,
) {
    // Top-left and top-right are deliberately used for status; the track's central perspective
    // corridor remains untouched from the near play point through the far upcoming chords.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        HudPanel {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onMenu) { Text("MENU") }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "PROGRESSION RUN",
                        color = HarmonyTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.setup.template.title,
                        color = HarmonyTheme.colors.onSurface,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    Text(
                        "${keyLabel(state)}  ·  ${state.setup.tempoBpm} BPM",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
        }

        HudPanel {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        state.progressLabel.ifBlank { "Ready" },
                        color = HarmonyTheme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${state.midiStatus}  ·  ${state.accuracyPercent}%  ·  ${state.elapsedLabel}",
                        color = if (state.midiConnected) HarmonyTheme.colors.accent else HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    Text(
                        "Clean ${state.run.clean}  ·  Runs ${state.runsCompleted}",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
                OutlinedButton(onClick = onPlayPause) {
                    Text(if (state.run.status == ProgressionRunStatus.RUNNING) "PAUSE" else "PLAY")
                }
                Button(onClick = onSetup) { Text("SETUP") }
            }
        }
    }

    state.activeSymbol?.let { symbol ->
        HudPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "PLAY",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                Text(
                    symbol,
                    color = HarmonyTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = HarmonyTheme.typography.heading,
                )
                state.activeFunction?.let { function ->
                    Text(
                        function,
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun HudPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(HarmonyTheme.colors.background.copy(alpha = 0.78f))
            .border(1.dp, HarmonyTheme.colors.outline.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        content()
    }
}

@Composable
private fun NavigationDrawer(
    onNavigate: (HomeDestination) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(330.dp)
            .background(HarmonyTheme.colors.surface.copy(alpha = 0.98f))
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "HARMONY GATES",
                color = HarmonyTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = HarmonyTheme.typography.heading,
            )
            OutlinedButton(onClick = onDismiss) { Text("CLOSE") }
        }
        Spacer(Modifier.height(8.dp))
        NavButton("Home") { onNavigate(HomeDestination.Home) }
        NavButton("Chord Gates") { onNavigate(HomeDestination.ChordGate) }
        NavButton("Theory Lab") { onNavigate(HomeDestination.TheoryLab) }
        NavButton("My Journey / Map") { onNavigate(HomeDestination.Campaign) }
        NavButton("Quick Practice") { onNavigate(HomeDestination.QuickPractice) }
        NavButton("Progress / Stats") { onNavigate(HomeDestination.Progress) }
        NavButton("Profile") { onNavigate(HomeDestination.Profile) }
        NavButton("Settings / MIDI") { onNavigate(HomeDestination.Settings) }
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Suppress("LongMethod")
@Composable
private fun SetupDrawer(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(430.dp)
            .background(HarmonyTheme.colors.surface.copy(alpha = 0.985f))
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "PRACTICE SETUP",
                    color = HarmonyTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = HarmonyTheme.typography.heading,
                )
                Text(
                    "Configure the run, then collapse this drawer.",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
            OutlinedButton(onClick = onDismiss) { Text("CLOSE") }
        }

        SectionLabel("Progression")
        ProgressionTemplates.all.forEach { template ->
            ChoiceButton(
                label = template.title,
                selected = template == state.setup.template,
            ) { onIntent(ProgressionRunIntent.ChooseTemplate(template)) }
        }

        SectionLabel("Voicing")
        VoicingStyle.entries.forEach { style ->
            ChoiceButton(
                label = style.label,
                selected = style == state.setup.style,
            ) { onIntent(ProgressionRunIntent.ChooseStyle(style)) }
        }

        SectionLabel("Key")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All 12 keys", color = HarmonyTheme.colors.onSurface, modifier = Modifier.weight(1f))
            Switch(
                checked = state.setup.allKeys,
                onCheckedChange = { onIntent(ProgressionRunIntent.ToggleAllKeys) },
            )
        }
        if (!state.setup.allKeys) {
            state.availableKeys.forEach { key ->
                ChoiceButton(
                    label = key,
                    selected = state.setup.key.toString() == key,
                ) { onIntent(ProgressionRunIntent.ChooseKey(key)) }
            }
        }

        SectionLabel("Tempo")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.setup.tempoBpm} BPM",
                color = HarmonyTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = { onIntent(ProgressionRunIntent.IncreaseTempo) }) { Text("+5 BPM") }
        }
        Text(
            "Tempo wraps to 40 BPM after 240 BPM.",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.caption,
        )

        SectionLabel("Run behavior")
        ToggleRow("Loop progression", state.setup.loop) { onIntent(ProgressionRunIntent.ToggleLoop) }
        ToggleRow("Show Roman numerals", state.setup.showRomanNumerals) {
            onIntent(ProgressionRunIntent.ToggleRomanNumerals)
        }
        ToggleRow("Count in", state.countIn) { onIntent(ProgressionRunIntent.ToggleCountIn) }
        if (state.countIn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { onIntent(ProgressionRunIntent.DecreaseCountBars) }) { Text("−") }
                Text("${state.countBars} bars", color = HarmonyTheme.colors.onSurface)
                OutlinedButton(onClick = { onIntent(ProgressionRunIntent.IncreaseCountBars) }) { Text("+") }
            }
        }
        ToggleRow("Auto-start next run", state.autoNextGate) {
            onIntent(ProgressionRunIntent.ToggleAutoNextGate)
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.run.status == ProgressionRunStatus.PAUSED) "DONE · RESUME PRACTICE" else "DONE")
        }
        OutlinedButton(
            onClick = { onIntent(ProgressionRunIntent.Restart) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("RESTART RUN") }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        color = HarmonyTheme.colors.accent,
        fontWeight = FontWeight.Bold,
        fontSize = HarmonyTheme.typography.caption,
    )
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HarmonyTheme.colors.onSurface)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private fun keyLabel(state: ProgressionRunUiState): String =
    if (state.setup.allKeys) "12 keys" else state.setup.key.toString()
