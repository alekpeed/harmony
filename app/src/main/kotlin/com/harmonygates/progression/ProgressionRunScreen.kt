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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.R
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.progression.ChordOrbUiModel
import com.harmonygates.core.designsystem.progression.ProgressionTrack
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.home.HomeDestination

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

private enum class Selector { Progression, Voicing, Key, Tempo, Sound }

private data class DesignFrame(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
)

@Composable
fun ProgressionRunScreen(
    state: ProgressionRunUiState,
    track: TrackSpec,
    onIntent: (ProgressionRunIntent) -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selector by remember { mutableStateOf<Selector?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonyTheme.colors.background),
    ) {
        val frame = fittedFrame(maxWidth, maxHeight)

        BackgroundPlate()
        CleanStaticMasks(frame)

        ProgressionTrack(
            geometry = track.geometry,
            orbs = animatedOrbs(state, track),
            designAspectRatio = track.aspectRatio,
            showPath = false,
        )

        Sidebar(
            frame = frame,
            state = state,
            onNavigate = onNavigate,
        )
        TopControls(
            frame = frame,
            state = state,
            onOpenSelector = { selector = it },
        )
        GoalAndStats(frame = frame, state = state)
        ControlDeck(
            frame = frame,
            state = state,
            onIntent = onIntent,
            onOpenSelector = { selector = it },
        )
        Footer(frame = frame, state = state)

        selector?.let { selected ->
            SelectorPanel(
                selector = selected,
                state = state,
                onIntent = onIntent,
                onDismiss = { selector = null },
                modifier = Modifier.align(Alignment.Center),
            )
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
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun BoxScope.CleanStaticMasks(frame: DesignFrame) {
    // The source artwork contains prototype values and old progression graphics. These regions
    // are intentionally covered completely so no baked value can ever collide with live state.
    DesignArea(frame, 0, 0, 286, 1024) {
        Box(Modifier.fillMaxSize().background(HarmonyTheme.colors.surface.copy(alpha = 0.985f)))
    }
    DesignArea(frame, 888, 14, 360, 90) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(HarmonyTheme.colors.surface.copy(alpha = 0.985f)),
        )
    }
    DesignArea(frame, 1190, 130, 346, 300) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(HarmonyTheme.colors.surface.copy(alpha = 0.985f)),
        )
    }
    DesignArea(frame, 286, 332, 1250, 270) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(HarmonyTheme.colors.surface.copy(alpha = 0.90f)),
        )
    }
    DesignArea(frame, 286, 575, 1250, 365) {
        Box(
            Modifier
                .fillMaxSize()
                .background(HarmonyTheme.colors.surface.copy(alpha = 0.99f)),
        )
    }
    DesignArea(frame, 286, 938, 1250, 86) {
        Box(
            Modifier
                .fillMaxSize()
                .background(HarmonyTheme.colors.surface.copy(alpha = 0.99f)),
        )
    }
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
private fun BoxScope.Sidebar(
    frame: DesignFrame,
    state: ProgressionRunUiState,
    onNavigate: (HomeDestination) -> Unit,
) {
    DesignArea(frame, 0, 0, 286, 1024) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "HARMONY\nGATES",
                color = HarmonyTheme.colors.accent,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            SessionProfile(state)
            Spacer(Modifier.height(6.dp))

            NavRow("HOME", false) { onNavigate(HomeDestination.Home) }
            NavRow("PROGRESSION RUN", true) { }
            NavRow("CHORD GATES", false) { onNavigate(HomeDestination.ChordGate) }
            NavRow("EAR TRAINER", false) { onNavigate(HomeDestination.EarTraining) }
            NavRow("SIGHT READING", false) { onNavigate(HomeDestination.SightReading) }
            NavRow("VOICE LEADING", false) { onNavigate(HomeDestination.VoicingLab) }
            NavRow("THEORY LAB", false) { onNavigate(HomeDestination.TheoryLab) }
            NavRow("DAILY CHALLENGE", false) { onNavigate(HomeDestination.DailyChallenge) }
            NavRow("MY JOURNEY", false) { onNavigate(HomeDestination.Campaign) }
            NavRow("MAP", false) { onNavigate(HomeDestination.Campaign) }
            NavRow("PRACTICE", false) { onNavigate(HomeDestination.QuickPractice) }
            NavRow("STATS", false) { onNavigate(HomeDestination.Progress) }
            NavRow("LIBRARY", false) { onNavigate(HomeDestination.Library) }

            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                UtilityButton("⚙") { onNavigate(HomeDestination.Settings) }
                UtilityButton("P") { onNavigate(HomeDestination.Profile) }
                UtilityButton("L") { onNavigate(HomeDestination.Library) }
            }
        }
    }
}

@Composable
private fun SessionProfile(state: ProgressionRunUiState) {
    val sessionXp = ((state.totalClean + state.run.clean) * 100) + (state.runsCompleted * 250)
    val level = 1 + (sessionXp / XP_PER_LEVEL)
    val inLevel = sessionXp % XP_PER_LEVEL
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HarmonyTheme.colors.background.copy(alpha = 0.7f))
            .border(1.dp, HarmonyTheme.colors.outline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Jazz Explorer", color = HarmonyTheme.colors.onSurface, fontWeight = FontWeight.SemiBold)
        Text("Level $level", color = HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
        LinearProgressIndicator(
            progress = { inLevel.toFloat() / XP_PER_LEVEL.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$inLevel / $XP_PER_LEVEL session XP",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.caption,
        )
    }
}

@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) HarmonyTheme.colors.accent.copy(alpha = 0.18f) else HarmonyTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) HarmonyTheme.colors.accent else HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.caption,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun UtilityButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(1.dp, HarmonyTheme.colors.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = HarmonyTheme.colors.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxScope.TopControls(
    frame: DesignFrame,
    state: ProgressionRunUiState,
    onOpenSelector: (Selector) -> Unit,
) {
    DesignArea(frame, 900, 22, 168, 70) {
        SelectorCapsule(
            label = "KEY",
            value = if (state.setup.allKeys) "12 keys" else state.setup.key.toString(),
            onClick = { onOpenSelector(Selector.Key) },
        )
    }
    DesignArea(frame, 1074, 22, 156, 70) {
        SelectorCapsule(
            label = "BPM",
            value = state.setup.tempoBpm.toString(),
            onClick = { onOpenSelector(Selector.Tempo) },
        )
    }
}

@Composable
private fun SelectorCapsule(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, HarmonyTheme.colors.outline, RoundedCornerShape(20.dp))
            .background(HarmonyTheme.colors.background.copy(alpha = 0.82f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
        Text(value, color = HarmonyTheme.colors.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxScope.GoalAndStats(frame: DesignFrame, state: ProgressionRunUiState) {
    DesignArea(frame, 1210, 150, 300, 215) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, HarmonyTheme.colors.outline, RoundedCornerShape(18.dp))
                .background(HarmonyTheme.colors.background.copy(alpha = 0.82f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("CURRENT GOAL", color = HarmonyTheme.colors.accent, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
            Text(
                state.activeSymbol?.let { "Play $it cleanly" } ?: "Reach the next gate",
                color = HarmonyTheme.colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(state.progressLabel, color = HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
            LinearProgressIndicator(
                progress = { state.goalProgress / 5f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${state.goalProgress} / 5", color = HarmonyTheme.colors.onSurface, fontWeight = FontWeight.Bold)
        }
    }

    DesignArea(frame, 1210, 375, 300, 184) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, HarmonyTheme.colors.outline, RoundedCornerShape(18.dp))
                .background(HarmonyTheme.colors.background.copy(alpha = 0.82f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("RUN STATS", color = HarmonyTheme.colors.accent, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
            StatRow("Runs", state.runsCompleted.toString())
            StatRow("Best streak", maxOf(state.bestStreak, state.run.clean).toString())
            StatRow("Accuracy", "${state.accuracyPercent}%")
            StatRow("Time", state.elapsedLabel)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
        Text(value, color = HarmonyTheme.colors.onSurface, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxScope.ControlDeck(
    frame: DesignFrame,
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    onOpenSelector: (Selector) -> Unit,
) {
    DesignArea(frame, 305, 600, 430, 320) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("PROGRESSION", color = HarmonyTheme.colors.accent, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { onOpenSelector(Selector.Progression) }, modifier = Modifier.fillMaxWidth()) {
                Text(state.setup.template.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = { onOpenSelector(Selector.Voicing) }, modifier = Modifier.fillMaxWidth()) {
                Text("Voicing: ${state.setup.style.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("Straight", state.timeFeel == TimeFeel.Straight) { onIntent(ProgressionRunIntent.SetTimeFeel(TimeFeel.Straight)) }
                ChoicePill("Swing", state.timeFeel == TimeFeel.Swing) { onIntent(ProgressionRunIntent.SetTimeFeel(TimeFeel.Swing)) }
                ChoicePill("Shuffle", state.timeFeel == TimeFeel.Shuffle) { onIntent(ProgressionRunIntent.SetTimeFeel(TimeFeel.Shuffle)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("12 keys", state.setup.allKeys) { onIntent(ProgressionRunIntent.ToggleAllKeys) }
                ChoicePill("Roman", state.setup.showRomanNumerals) { onIntent(ProgressionRunIntent.ToggleRomanNumerals) }
            }
        }
    }

    DesignArea(frame, 748, 600, 250, 320) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PLAY CONTROLS", color = HarmonyTheme.colors.accent, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundControl("◀", 56.dp) { onIntent(ProgressionRunIntent.Previous) }
                RoundControl(if (state.run.status == ProgressionRunStatus.RUNNING) "Ⅱ" else "▶", 78.dp) {
                    onIntent(ProgressionRunIntent.TogglePlaying)
                }
                RoundControl("▶", 56.dp) { onIntent(ProgressionRunIntent.Next) }
            }
            ToggleRow("Count in", state.countIn) { onIntent(ProgressionRunIntent.ToggleCountIn) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundControl("−", 42.dp) { onIntent(ProgressionRunIntent.DecreaseCountBars) }
                Text(
                    if (state.countingIn) "COUNTING" else "${state.countBars} BARS",
                    color = HarmonyTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                RoundControl("+", 42.dp) { onIntent(ProgressionRunIntent.IncreaseCountBars) }
            }
            Text(state.midiStatus, color = if (state.midiConnected) HarmonyTheme.colors.accent else HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
        }
    }

    DesignArea(frame, 1010, 600, 250, 320) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("SOUND", color = HarmonyTheme.colors.accent, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { onOpenSelector(Selector.Sound) }, modifier = Modifier.fillMaxWidth()) {
                Text(state.selectedSound, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("OCTAVE", color = HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundControl("−", 42.dp) { onIntent(ProgressionRunIntent.DecreaseOctave) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(state.octave.toString(), color = HarmonyTheme.colors.onSurface, fontWeight = FontWeight.Bold)
                }
                RoundControl("+", 42.dp) { onIntent(ProgressionRunIntent.IncreaseOctave) }
            }
            Text(
                state.activeSymbol?.let { "Now: $it" } ?: "Ready",
                color = HarmonyTheme.colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            state.activeFunction?.let {
                Text(it, color = HarmonyTheme.colors.onSurfaceMuted, fontSize = HarmonyTheme.typography.caption)
            }
        }
    }

    DesignArea(frame, 1270, 600, 246, 320) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("OPTIONS", color = HarmonyTheme.colors.accent, fontSize = HarmonyTheme.typography.caption, fontWeight = FontWeight.Bold)
            ToggleRow("Loop", state.setup.loop) { onIntent(ProgressionRunIntent.ToggleLoop) }
            ToggleRow("Highlight root", state.highlightRoot) { onIntent(ProgressionRunIntent.ToggleHighlightRoot) }
            ToggleRow("Guide tones", state.showGuideTones) { onIntent(ProgressionRunIntent.ToggleGuideTones) }
            ToggleRow("Auto next", state.autoNextGate) { onIntent(ProgressionRunIntent.ToggleAutoNextGate) }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) HarmonyTheme.colors.accent.copy(alpha = 0.22f) else HarmonyTheme.colors.background.copy(alpha = 0.5f))
            .border(1.dp, if (selected) HarmonyTheme.colors.accent else HarmonyTheme.colors.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = HarmonyTheme.colors.onSurface, fontSize = HarmonyTheme.typography.caption)
    }
}

@Composable
private fun RoundControl(label: String, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(HarmonyTheme.colors.background.copy(alpha = 0.72f))
            .border(1.dp, HarmonyTheme.colors.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = HarmonyTheme.colors.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HarmonyTheme.colors.onSurface, fontSize = HarmonyTheme.typography.caption)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun BoxScope.Footer(frame: DesignFrame, state: ProgressionRunUiState) {
    DesignArea(frame, 305, 948, 1210, 62) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                state.instruction ?: "Listen, play the active chord, then move with the progression.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Attempts ${state.totalAttempts + state.run.attempts} · Clean ${state.totalClean + state.run.clean}",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.caption,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BoxScope.DesignArea(
    frame: DesignFrame,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(
                x = frame.left + frame.width * (x / DESIGN_WIDTH),
                y = frame.top + frame.height * (y / DESIGN_HEIGHT),
            )
            .size(
                width = frame.width * (width / DESIGN_WIDTH),
                height = frame.height * (height / DESIGN_HEIGHT),
            ),
        content = content,
    )
}

private fun fittedFrame(maxWidth: Dp, maxHeight: Dp): DesignFrame {
    val width = minOf(maxWidth, maxHeight * DESIGN_ASPECT)
    val height = width / DESIGN_ASPECT
    return DesignFrame(
        left = (maxWidth - width) / 2,
        top = (maxHeight - height) / 2,
        width = width,
        height = height,
    )
}

@Composable
private fun SelectorPanel(
    selector: Selector,
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HarmonyPanel(
        modifier = modifier
            .widthIn(min = 360.dp, max = 680.dp)
            .heightIn(max = 640.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(HarmonyTheme.spacing.large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        ) {
            Text(
                text = when (selector) {
                    Selector.Progression -> "Choose progression"
                    Selector.Voicing -> "Choose voicing"
                    Selector.Key -> "Choose key"
                    Selector.Tempo -> "Choose tempo"
                    Selector.Sound -> "Choose sound"
                },
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )

            when (selector) {
                Selector.Progression -> ProgressionTemplates.all.forEach { template ->
                    ChoiceButton(template.title) {
                        onIntent(ProgressionRunIntent.ChooseTemplate(template))
                        onDismiss()
                    }
                }
                Selector.Voicing -> VoicingStyle.entries.forEach { style ->
                    ChoiceButton(style.label) {
                        onIntent(ProgressionRunIntent.ChooseStyle(style))
                        onDismiss()
                    }
                }
                Selector.Key -> {
                    ChoiceButton("All 12 keys") {
                        if (!state.setup.allKeys) onIntent(ProgressionRunIntent.ToggleAllKeys)
                        onDismiss()
                    }
                    state.availableKeys.forEach { key ->
                        ChoiceButton(key) {
                            onIntent(ProgressionRunIntent.ChooseKey(key))
                            onDismiss()
                        }
                    }
                }
                Selector.Tempo -> TEMPOS.forEach { bpm ->
                    ChoiceButton("$bpm BPM") {
                        repeat(((bpm - state.setup.tempoBpm + TEMPO_WRAP) % TEMPO_WRAP) / 5) {
                            onIntent(ProgressionRunIntent.IncreaseTempo)
                        }
                        onDismiss()
                    }
                }
                Selector.Sound -> state.availableSounds.forEach { sound ->
                    ChoiceButton(sound) {
                        onIntent(ProgressionRunIntent.ChooseSound(sound))
                        onDismiss()
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                OutlinedButton(onClick = onDismiss) { Text("Close") }
                if (state.run.status == ProgressionRunStatus.COMPLETED) {
                    Button(onClick = {
                        onIntent(ProgressionRunIntent.Restart)
                        onDismiss()
                    }) { Text("Restart run") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

private val TEMPOS = listOf(60, 80, 100, 120, 140, 160, 180, 200)
private const val XP_PER_LEVEL = 1000
private const val TEMPO_WRAP = 205
private const val DESIGN_WIDTH = 1536f
private const val DESIGN_HEIGHT = 1024f
private const val DESIGN_ASPECT = DESIGN_WIDTH / DESIGN_HEIGHT
