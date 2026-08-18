package com.harmonygates.sightreading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.FilterChip
import com.harmonygates.core.designsystem.component.HarmonyLabelledValue
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.component.PianoKeyboard
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.notation.NotationStaff
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * Sight reading, plain.
 *
 * No artwork: this is the functional screen for a module whose engine was finished in Phase 9 and
 * never had one. The staff itself is the design system's real renderer, because a reading screen
 * without notation is not a reading screen.
 */
@Composable
fun SightReadingRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SightReadingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SightReadingScreen(state, viewModel::onIntent, onExit, modifier)
}

@Composable
fun SightReadingScreen(
    state: SightReadingUiState,
    onIntent: (SightReadingIntent) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sight Reading",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            HarmonyStatusChip(
                label = state.midiStatus,
                tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
            )
            Box(Modifier.weight(1f))
            SecondaryButton(label = "Leave", onClick = onExit)
        }

        when (state.phase) {
            SightReadingPhase.SETUP -> Setup(state, onIntent)
            else -> Reading(state, onIntent)
        }
    }
}

@Composable
private fun Setup(state: SightReadingUiState, onIntent: (SightReadingIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "Read the line and play it in time. Pitch and rhythm are scored separately.",
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
            )

            Caption("Material")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                state.materials.forEach { material ->
                    FilterChip(
                        label = material.label,
                        selected = material == state.material,
                        onToggle = { onIntent(SightReadingIntent.ChooseMaterial(material)) },
                    )
                }
            }

            Caption("Key")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                state.keys.forEach { key ->
                    FilterChip(
                        label = key,
                        selected = key == state.key,
                        onToggle = { onIntent(SightReadingIntent.ChooseKey(key)) },
                    )
                }
            }

            Caption("Tempo — ${state.tempoBpm} BPM")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                listOf(52, 60, 72, 88, 104, 120).forEach { bpm ->
                    FilterChip(
                        label = "$bpm",
                        selected = bpm == state.tempoBpm,
                        onToggle = { onIntent(SightReadingIntent.ChooseTempo(bpm)) },
                    )
                }
            }

            Caption("Bars")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                listOf(1, 2, 4, 8).forEach { bars ->
                    FilterChip(
                        label = "$bars",
                        selected = bars == state.measures,
                        onToggle = { onIntent(SightReadingIntent.ChooseMeasures(bars)) },
                    )
                }
            }

            PrimaryButton(label = "Start", onClick = { onIntent(SightReadingIntent.Start) })
        }
    }
}

@Composable
private fun Reading(state: SightReadingUiState, onIntent: (SightReadingIntent) -> Unit) {
    val phrase = state.phrase ?: return

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            when (state.phase) {
                SightReadingPhase.COUNT_IN -> Text(
                    text = "Count in — ${state.countInBeat}",
                    color = HarmonyTheme.colors.accentPrimary,
                    fontSize = HarmonyTheme.typography.heading,
                    fontWeight = FontWeight.SemiBold,
                )
                SightReadingPhase.PLAYING -> Text(
                    text = "Play",
                    color = HarmonyTheme.colors.textPrimary,
                    fontSize = HarmonyTheme.typography.heading,
                    fontWeight = FontWeight.SemiBold,
                )
                else -> Unit
            }

            NotationStaff(system = phrase.toStaffSystem(state.progress))

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    PianoKeyboard(lowNote = 48, highNote = 84, held = state.soundingNotes.toSet())

    state.result?.let { result ->
        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large)) {
                    HarmonyLabelledValue(
                        label = "Pitch",
                        value = "${result.pitchCorrectCount} of ${result.expectedCount}",
                        modifier = Modifier.weight(1f),
                    )
                    HarmonyLabelledValue(
                        label = "Timing",
                        value = "${result.timingCorrectCount} of ${result.expectedCount}",
                        modifier = Modifier.weight(1f),
                    )
                    HarmonyLabelledValue(
                        label = "Missed",
                        value = "${result.missedCount}",
                        modifier = Modifier.weight(1f),
                    )
                }
                // 08 §5's whole point: right notes late is a timing problem, wrong notes on the
                // beat is a pitch problem, and one number would hide both.
                Text(
                    text = "Right notes late is a timing problem. Wrong notes exactly on the " +
                        "beat is not.",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    PrimaryButton(
                        label = "Another",
                        onClick = { onIntent(SightReadingIntent.NewPhrase) },
                    )
                    SecondaryButton(
                        label = "Settings",
                        onClick = { onIntent(SightReadingIntent.BackToSetup) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        color = HarmonyTheme.colors.onSurfaceMuted,
        fontSize = HarmonyTheme.typography.caption,
    )
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun SightReadingPreview() {
    HarmonyTheme {
        SightReadingScreen(SightReadingUiState(), {}, {})
    }
}
