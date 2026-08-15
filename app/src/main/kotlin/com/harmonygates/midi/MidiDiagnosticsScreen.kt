package com.harmonygates.midi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyLabelledValue
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.component.PianoKeyboard
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.midi.MidiConnectionState

/**
 * MIDI setup and diagnostics.
 *
 * The Phase 2 deliverable, and the screen a player will open when the keyboard is not behaving.
 * It shows the connection, what is sounding, and the raw event stream, so a problem can be
 * localised to the cable, the device or the app without a debugger.
 */
@Composable
fun MidiDiagnosticsRoute(
    modifier: Modifier = Modifier,
    viewModel: MidiDiagnosticsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MidiDiagnosticsScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun MidiDiagnosticsScreen(
    state: MidiDiagnosticsState,
    onIntent: (MidiDiagnosticsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Text(
                text = "MIDI setup",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            Text(
                text = "Phase 2. Full settings arrive with the campaign in phase 6.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
        }

        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HarmonyStatusChip(
                        label = state.statusLabel,
                        tone = if (state.isReceiving) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
                    )
                    if (state.activeNotes.sustainPedalDown) {
                        HarmonyStatusChip(label = "Sustain down", tone = FeedbackTone.PARTIAL)
                    }
                    if (state.isSimulated) {
                        HarmonyStatusChip(label = "Simulated", tone = FeedbackTone.NEUTRAL)
                    }
                }
                Text(
                    text = state.guidance,
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }

        if (state.endpoints.isNotEmpty() && !state.isReceiving) {
            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    Text(
                        text = "Available keyboards",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    state.endpoints.forEach { endpoint ->
                        Button(
                            onClick = { onIntent(MidiDiagnosticsIntent.Connect(endpoint)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(endpoint.displayName)
                        }
                    }
                }
            }
        }

        PianoKeyboard(
            lowNote = state.keyboardRange.first,
            highNote = state.keyboardRange.last,
            held = state.activeNotes.physicallyHeld.map { it.value }.toSet(),
            sustained = state.activeNotes.pedalSustained.map { it.value }.toSet(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large)) {
            HarmonyLabelledValue(
                label = "Sounding",
                value = state.activeNotes.soundingAscending
                    .joinToString(" ") { it.value.toString() }
                    .ifBlank { "—" },
                modifier = Modifier.weight(1f),
            )
            HarmonyLabelledValue(
                label = "Events",
                value = state.eventCount.toString(),
                modifier = Modifier.weight(1f),
            )
            HarmonyLabelledValue(
                label = "Observed range",
                value = if (state.observedLowNote == null) {
                    "—"
                } else {
                    "${state.observedLowNote} – ${state.observedHighNote}"
                },
                modifier = Modifier.weight(1f),
            )
        }

        SimulatorControls(state = state, onIntent = onIntent)

        Text(
            text = "Event stream",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.caption,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
        ) {
            items(state.recentEvents) { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    Text(
                        text = line.label,
                        color = when (line.kind) {
                            MidiEventLine.Kind.NOTE_ON -> HarmonyTheme.colors.correct
                            MidiEventLine.Kind.NOTE_OFF -> HarmonyTheme.colors.onSurfaceMuted
                            MidiEventLine.Kind.PEDAL -> HarmonyTheme.colors.partial
                            MidiEventLine.Kind.OTHER -> HarmonyTheme.colors.onSurfaceMuted
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    Text(
                        text = line.detail,
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
        }
    }
}

/**
 * On-device simulation.
 *
 * Debug affordance in the spirit of 17_BUILD_CI_INSTALL_RELEASE.md §1: it lets the whole screen,
 * including the disconnect and reconnect path, be exercised on a tablet with nothing plugged in.
 */
@Composable
private fun SimulatorControls(
    state: MidiDiagnosticsState,
    onIntent: (MidiDiagnosticsIntent) -> Unit,
) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.isSimulated,
                    onClick = { onIntent(MidiDiagnosticsIntent.UseSimulator(!state.isSimulated)) },
                    label = { Text("Simulate a keyboard") },
                )
                OutlinedButton(onClick = { onIntent(MidiDiagnosticsIntent.ClearNotes) }) {
                    Text("Clear notes")
                }
                OutlinedButton(onClick = { onIntent(MidiDiagnosticsIntent.ClearLog) }) {
                    Text("Clear log")
                }
            }

            if (state.isSimulated) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                ) {
                    // A C major seventh, the chord every other part of this project is tested with.
                    listOf(60, 64, 67, 71).forEach { note ->
                        val held = note in state.activeNotes.physicallyHeld.map { it.value }
                        FilterChip(
                            selected = held,
                            onClick = { onIntent(MidiDiagnosticsIntent.SimulateNote(note, !held)) },
                            label = { Text(note.toString()) },
                        )
                    }
                    FilterChip(
                        selected = state.activeNotes.sustainPedalDown,
                        onClick = {
                            onIntent(
                                MidiDiagnosticsIntent.SimulateSustain(!state.activeNotes.sustainPedalDown),
                            )
                        },
                        label = { Text("Sustain") },
                    )
                    OutlinedButton(onClick = { onIntent(MidiDiagnosticsIntent.SimulateDisconnect) }) {
                        Text("Pull the cable")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun MidiDiagnosticsPreview() {
    HarmonyTheme {
        MidiDiagnosticsScreen(
            state = MidiDiagnosticsState(
                connection = MidiConnectionState.Connected(
                    com.harmonygates.core.midi.FakeMidiInputSource.DEFAULT_ENDPOINT,
                ),
                eventCount = 12,
                observedLowNote = 48,
                observedHighNote = 84,
            ),
            onIntent = {},
        )
    }
}
