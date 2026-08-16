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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
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
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.exercise.describe

/**
 * Progression Run.
 *
 * The screen is built as the handoff describes it: a clean plate, an independent track drawn
 * over it, and a HUD that stays out of the way. Nothing about harmony is decided here — the
 * chord at the play point advances when `core:music` says the player played it, and this draws
 * whatever the run reports.
 */
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

        // The live HUD sits in its own opaque strip along the bottom rather than floating over
        // the whole plate.
        //
        // The supplied plate is a full-screen mockup: it has play controls, a sound picker, a
        // tips button and a milestone bar painted into it, which `interface/README.md` says a
        // plate must not contain. Drawing the real controls transparently over the painted ones
        // produced two of everything, overlapping — a working button on top of a picture of a
        // button. Until a clean plate arrives, covering that strip is the honest fix: one set of
        // controls, and they are the ones that do something.
        RunHud(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(HarmonyTheme.colors.backgroundBase)
                .safeDrawingPadding()
                .padding(HarmonyTheme.spacing.large),
        )
    }
}

/**
 * The clean room the track runs through.
 *
 * `interface/README.md` forbids a background carrying a baked track, orbs, pedestal rings or
 * chord labels, so this draws the supplied plate and nothing else. Until one is supplied the
 * generated placeholder is skipped entirely and the theme background shows through, which keeps
 * the layering identical either way.
 *
 * Fitted, not cropped, and that is not a detail. The track places its orbs inside the same
 * fitted 1536 x 1024 rectangle; a cropped plate would be scaled and offset differently, so on
 * any tablet that is not exactly 3:2 the orbs would slide off the room they are supposed to be
 * standing in. The handoff's validation list asks for "orb centers and perspective" and
 * "pedestal alignment" to be checked against the reference — this is what makes them line up.
 */
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

/**
 * Puts the orbs where they are mid-advance.
 *
 * One animated number moves the whole track: every orb is drawn one slot further out than it
 * has settled into, closing to zero over the advance. That is what the handoff's "all visible
 * orbs slide smoothly to the next fixed perspective slot" means in practice, and it makes
 * overlapping advances impossible by construction — a second advance restarts the same
 * animation rather than starting a competing one, which is the safe equivalent §6 allows.
 */
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
private fun RunHud(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Wrap-height, not fill-height: the strip is only as tall as its controls, so the room and
    // the track keep everything above it.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        RunHeader(state)
        RunFooter(state = state, onIntent = onIntent)
    }
}

@Composable
private fun RunHeader(state: ProgressionRunUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Text(
                text = state.run.progression?.title ?: "Progression run",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            Text(
                text = state.progressLabel,
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
        }
        HarmonyStatusChip(
            label = state.midiStatus,
            tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
        )
    }
}

@Composable
private fun RunFooter(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
        when {
            state.run.status == ProgressionRunStatus.COMPLETED -> CompletedPanel(state, onIntent)
            state.run.status == ProgressionRunStatus.PAUSED -> PausedPanel()
            else -> PlayingPanel(state)
        }
        SetupControls(state = state, onIntent = onIntent)
    }
}

@Composable
private fun PlayingPanel(state: ProgressionRunUiState) {
    HarmonyPanel {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Text(
                text = state.activeSymbol?.let { "Play $it" } ?: "Preparing the run",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.body,
            )
            state.instruction?.let {
                Text(
                    text = it,
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
            // Only the last judgement, and only when it was not accepted: a correct chord has
            // already said so by moving the track.
            state.lastResult?.takeIf { it.verdict.isCorrect.not() }?.let { result ->
                result.explanation.errors.take(MAX_REASONS).forEach { error ->
                    Text(
                        text = describe(error),
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
            if (!state.midiConnected) {
                Text(
                    text = "Connect a MIDI keyboard to play the run. Next steps through it meanwhile.",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
private fun PausedPanel() {
    HarmonyPanel {
        Text(
            text = "The keyboard was disconnected. Plug it back in and the run carries on from " +
                "the chord it stopped at.",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.body,
        )
    }
}

@Composable
private fun CompletedPanel(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
) {
    HarmonyPanel {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "Run complete",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            Text(
                text = "${state.run.clean} of ${state.run.progression?.size ?: 0} chords first time, " +
                    "${state.run.attempts} attempts in all.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
            Button(onClick = { onIntent(ProgressionRunIntent.Restart) }) { Text("Run it again") }
        }
    }
}

/**
 * The run's own controls.
 *
 * Laid out in Compose rather than placed against the map: the Progression Run map marks its
 * non-track hit regions as pending remapping from the approved `77:2` frame, and inventing
 * coordinates for them is exactly what that note asks a coder not to do.
 */
@Composable
private fun SetupControls(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        ) {
            ProgressionTemplates.all.forEach { template ->
                Choice(
                    label = template.title,
                    selected = state.setup.template.id == template.id,
                    onClick = { onIntent(ProgressionRunIntent.ChooseTemplate(template)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        ) {
            VoicingStyle.entries.forEach { style ->
                Choice(
                    label = style.label,
                    selected = state.setup.style == style,
                    onClick = { onIntent(ProgressionRunIntent.ChooseStyle(style)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Choice(
                label = "All twelve keys",
                selected = state.setup.allKeys,
                onClick = { onIntent(ProgressionRunIntent.ToggleAllKeys) },
            )
            Choice(
                label = "Roman numerals",
                selected = state.setup.showRomanNumerals,
                onClick = { onIntent(ProgressionRunIntent.ToggleRomanNumerals) },
            )
            OutlinedButton(onClick = { onIntent(ProgressionRunIntent.Next) }) { Text("Next") }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private const val MAX_REASONS = 2

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
