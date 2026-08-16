package com.harmonygates.progression

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.R
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.progression.ChordOrbUiModel
import com.harmonygates.core.designsystem.progression.OrbState
import com.harmonygates.core.designsystem.progression.ProgressionTrack
import com.harmonygates.core.designsystem.theme.HarmonyTheme

@Composable
fun ProgressionRunRoute(
    modifier: Modifier = Modifier,
    viewModel: ProgressionRunViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressionRunScreen(
        state = state,
        track = viewModel.track,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * Full-screen Progression Run.
 *
 * The approved room remains the visual interface. The previous implementation covered nearly
 * half of it with an opaque generic setup panel; on the target tablet that made the approved
 * screen look vertically chopped in two. Runtime track/orb state stays layered over the room,
 * while the only Compose chrome retained is a compact live strip for information that genuinely
 * changes at runtime plus the manual Next control used for testing without MIDI.
 */
@Composable
fun ProgressionRunScreen(
    state: ProgressionRunUiState,
    track: TrackSpec,
    onIntent: (ProgressionRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        )

        CompactRunOverlay(
            state = state,
            onNext = { onIntent(ProgressionRunIntent.Next) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(HarmonyTheme.spacing.medium),
        )
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
private fun CompactRunOverlay(
    state: ProgressionRunUiState,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HarmonyPanel(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
                Text(
                    text = state.run.progression?.title ?: "Progression run",
                    color = HarmonyTheme.colors.onSurface,
                    fontSize = HarmonyTheme.typography.body,
                )
                Text(
                    text = listOfNotNull(
                        state.progressLabel.takeIf { it.isNotBlank() },
                        state.activeSymbol?.let { "Play $it" },
                    ).joinToString(" · "),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }

            HarmonyStatusChip(
                label = state.midiStatus,
                tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
            )

            OutlinedButton(onClick = onNext) { Text("Next") }
        }
    }
}

@Preview(showBackground = true, widthDp = 1200, heightDp = 800)
@Composable
private fun ProgressionRunPreview() {
    val track = TrackMapReader.build(TrackMap())
    HarmonyTheme {
        Box(Modifier.fillMaxSize().background(HarmonyTheme.colors.background)) {
            ProgressionTrack(
                geometry = track.geometry,
                designAspectRatio = track.aspectRatio,
                orbs = listOf(
                    ChordOrbUiModel("0", "Cmaj7", "Imaj7", OrbState.PREVIOUS, -1f),
                    ChordOrbUiModel("1", "Dm7", "ii7", OrbState.ACTIVE, 0f),
                    ChordOrbUiModel("2", "G7", "V7", OrbState.UPCOMING, 1f),
                    ChordOrbUiModel("3", "Cmaj7", "Imaj7", OrbState.UPCOMING, 2f),
                    ChordOrbUiModel("4", "Fm7", "ii7", OrbState.UPCOMING, 3f),
                    ChordOrbUiModel("5", "Bb7", "V7", OrbState.UPCOMING, 4f),
                    ChordOrbUiModel("6", "Ebmaj7", "Imaj7", OrbState.UPCOMING, 5f),
                    ChordOrbUiModel("7", "Gm7", "ii7", OrbState.UPCOMING, 6f),
                ),
            )
        }
    }
}
