package com.harmonygates.progression

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.R
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle

@Composable
fun ProgressionRunRoute(
    modifier: Modifier = Modifier,
    viewModel: ProgressionRunViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressionRunScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * Progression Run intentionally shows only controls that are backed by runtime state.
 *
 * The supplied artwork contains an old prototype track, placeholder blobs, fake statistics and
 * fake controls. Those pixels are not allowed to masquerade as UI. The lower part of the plate
 * is therefore faded completely into the app surface and rebuilt with real Compose controls.
 * No separate progression-track renderer is drawn here; the user explicitly removed that visual
 * treatment, and doing so also prevents the old baked track from doubling with runtime graphics.
 */
@Composable
fun ProgressionRunScreen(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonyTheme.colors.background),
    ) {
        BackgroundPlate()

        // The prototype artwork becomes visibly contaminated from roughly the lower third down.
        // Fade it out before any fake controls, path, blobs or static statistics can be mistaken
        // for live UI.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.27f to Color.Transparent,
                            0.38f to HarmonyTheme.colors.backgroundBase.copy(alpha = 0.88f),
                            0.48f to HarmonyTheme.colors.backgroundBase,
                            1.00f to HarmonyTheme.colors.backgroundBase,
                        ),
                    ),
                ),
        )

        RunControlSurface(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(HarmonyTheme.spacing.large),
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
private fun RunControlSurface(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    HarmonyPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
                    Text(
                        text = state.run.progression?.title ?: "Progression run",
                        color = HarmonyTheme.colors.onSurface,
                        fontSize = HarmonyTheme.typography.heading,
                    )
                    Text(
                        text = when (state.run.status) {
                            ProgressionRunStatus.COMPLETED -> "Complete · ${state.progressLabel}"
                            else -> listOfNotNull(
                                state.progressLabel.takeIf { it.isNotBlank() },
                                state.activeSymbol?.let { "Play $it" },
                            ).joinToString(" · ")
                        },
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HarmonyStatusChip(
                        label = state.midiStatus,
                        tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
                    )
                    Text(
                        text = "Attempts ${state.run.attempts} · Clean ${state.run.clean}",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CycleButton(
                    label = "Progression: ${state.setup.template.title}",
                    onClick = {
                        val all = ProgressionTemplates.all
                        val index = all.indexOf(state.setup.template).coerceAtLeast(0)
                        onIntent(ProgressionRunIntent.ChooseTemplate(all[(index + 1) % all.size]))
                    },
                )
                CycleButton(
                    label = "Voicing: ${state.setup.style.label}",
                    onClick = {
                        val all = VoicingStyle.entries
                        val index = all.indexOf(state.setup.style).coerceAtLeast(0)
                        onIntent(ProgressionRunIntent.ChooseStyle(all[(index + 1) % all.size]))
                    },
                )
                ToggleButton(
                    label = "12 keys",
                    selected = state.setup.allKeys,
                    onClick = { onIntent(ProgressionRunIntent.ToggleAllKeys) },
                )
                ToggleButton(
                    label = "Loop",
                    selected = state.setup.loop,
                    onClick = { onIntent(ProgressionRunIntent.ToggleLoop) },
                )
                ToggleButton(
                    label = "Roman",
                    selected = state.setup.showRomanNumerals,
                    onClick = { onIntent(ProgressionRunIntent.ToggleRomanNumerals) },
                )

                if (state.run.status == ProgressionRunStatus.COMPLETED) {
                    Button(onClick = { onIntent(ProgressionRunIntent.Restart) }) { Text("Restart") }
                } else {
                    Button(onClick = { onIntent(ProgressionRunIntent.Next) }) { Text("Next chord") }
                }
            }
        }
    }
}

@Composable
private fun CycleButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 260.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
