package com.harmonygates.progression

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
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

private enum class Selector { Progression, Voicing, Key, Sound }

@Composable
fun ProgressionRunScreen(
    state: ProgressionRunUiState,
    track: TrackSpec,
    onIntent: (ProgressionRunIntent) -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selector by remember { mutableStateOf<Selector?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonyTheme.colors.background),
    ) {
        BackgroundPlate()

        // Progression remains visible and animated, but the old glowing line is gone.
        ProgressionTrack(
            geometry = track.geometry,
            orbs = animatedOrbs(state, track),
            designAspectRatio = track.aspectRatio,
            showPath = false,
        )

        RuntimeReadouts(state)

        InteractionLayer(
            state = state,
            onIntent = onIntent,
            onNavigate = onNavigate,
            onOpenSelector = { selector = it },
        )

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
private fun RuntimeReadouts(state: ProgressionRunUiState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Readout("${state.goalProgress} / 5", Modifier.offset(w * 0.943f, h * 0.289f))
        Readout(state.runsCompleted.toString(), Modifier.offset(w * 0.943f, h * 0.526f))
        Readout(state.bestStreak.toString(), Modifier.offset(w * 0.943f, h * 0.559f))
        Readout("${state.accuracyPercent}%", Modifier.offset(w * 0.935f, h * 0.592f))
        Readout(state.elapsedLabel, Modifier.offset(w * 0.922f, h * 0.625f))
    }
}

@Composable
private fun Readout(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.background(HarmonyTheme.colors.surface.copy(alpha = 0.96f)),
        color = HarmonyTheme.colors.accent,
        fontSize = HarmonyTheme.typography.caption,
    )
}

@Suppress("LongMethod")
@Composable
private fun InteractionLayer(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    onOpenSelector: (Selector) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Full sidebar navigation.
        Hit(10, 286, 255, 338) { onNavigate(HomeDestination.Home) }
        Hit(10, 384, 255, 432) { onNavigate(HomeDestination.ChordGate) }
        Hit(10, 432, 255, 480) { onNavigate(HomeDestination.EarTraining) }
        Hit(10, 480, 255, 528) { onNavigate(HomeDestination.SightReading) }
        Hit(10, 528, 255, 576) { onNavigate(HomeDestination.VoicingLab) }
        Hit(10, 576, 255, 624) { onNavigate(HomeDestination.TheoryLab) }
        Hit(10, 624, 255, 672) { onNavigate(HomeDestination.DailyChallenge) }
        Hit(10, 672, 255, 720) { onNavigate(HomeDestination.Campaign) }
        Hit(10, 720, 255, 768) { onNavigate(HomeDestination.Campaign) }
        Hit(10, 768, 255, 816) { onNavigate(HomeDestination.QuickPractice) }
        Hit(10, 816, 255, 864) { onNavigate(HomeDestination.Progress) }
        Hit(10, 864, 255, 910) { onNavigate(HomeDestination.Library) }

        // Top selectors.
        Hit(906, 18, 1062, 84) { onOpenSelector(Selector.Key) }
        Hit(1062, 18, 1218, 84) { onIntent(ProgressionRunIntent.IncreaseTempo) }

        // Progression console.
        Hit(292, 700, 725, 790) { onOpenSelector(Selector.Progression) }
        Hit(295, 840, 422, 889) { onIntent(ProgressionRunIntent.SetTimeFeel(TimeFeel.Straight)) }
        Hit(422, 840, 545, 889) { onIntent(ProgressionRunIntent.SetTimeFeel(TimeFeel.Swing)) }
        Hit(545, 840, 670, 889) { onIntent(ProgressionRunIntent.SetTimeFeel(TimeFeel.Shuffle)) }

        // Transport.
        Hit(775, 704, 832, 770) { onIntent(ProgressionRunIntent.Previous) }
        Hit(832, 700, 916, 785) { onIntent(ProgressionRunIntent.TogglePlaying) }
        Hit(916, 704, 975, 770) { onIntent(ProgressionRunIntent.Next) }
        Hit(910, 797, 970, 840) { onIntent(ProgressionRunIntent.ToggleCountIn) }
        Hit(776, 839, 827, 890) { onIntent(ProgressionRunIntent.DecreaseCountBars) }
        Hit(925, 839, 975, 890) { onIntent(ProgressionRunIntent.IncreaseCountBars) }

        // Sound and octave.
        Hit(1012, 698, 1230, 792) { onOpenSelector(Selector.Sound) }
        Hit(1012, 838, 1078, 890) { onIntent(ProgressionRunIntent.DecreaseOctave) }
        Hit(1155, 838, 1230, 890) { onIntent(ProgressionRunIntent.IncreaseOctave) }

        // Real switches cover the baked prototype switches so state changes are visible.
        PositionedSwitch(1435, 704, state.setup.loop) { onIntent(ProgressionRunIntent.ToggleLoop) }
        PositionedSwitch(1435, 746, state.highlightRoot) { onIntent(ProgressionRunIntent.ToggleHighlightRoot) }
        PositionedSwitch(1435, 789, state.showGuideTones) { onIntent(ProgressionRunIntent.ToggleGuideTones) }
        PositionedSwitch(1435, 831, state.autoNextGate) { onIntent(ProgressionRunIntent.ToggleAutoNextGate) }

        Hit(295, 655, 520, 700) { onOpenSelector(Selector.Voicing) }
    }
}

@Composable
private fun BoxWithConstraintsScope.Hit(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(maxWidth * (left / 1536f), maxHeight * (top / 1024f))
            .size(
                maxWidth * ((right - left) / 1536f),
                maxHeight * ((bottom - top) / 1024f),
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun BoxWithConstraintsScope.PositionedSwitch(
    x: Int,
    y: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(maxWidth * (x / 1536f), maxHeight * (y / 1024f))
            .background(HarmonyTheme.colors.surface.copy(alpha = 0.96f)),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SelectorPanel(
    selector: Selector,
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HarmonyPanel(modifier = modifier.widthIn(min = 360.dp, max = 680.dp)) {
        Column(
            modifier = Modifier.padding(HarmonyTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        ) {
            Text(
                text = when (selector) {
                    Selector.Progression -> "Choose progression"
                    Selector.Voicing -> "Choose voicing"
                    Selector.Key -> "Choose key"
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
                Selector.Key -> state.availableKeys.forEach { key ->
                    ChoiceButton(key) {
                        onIntent(ProgressionRunIntent.ChooseKey(key))
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
    OutlinedButton(onClick = onClick) { Text(label) }
}
