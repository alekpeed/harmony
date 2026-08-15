package com.harmonygates.harness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyChordSymbol
import com.harmonygates.core.designsystem.component.HarmonyLabelledValue
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * Route: owns the view model, hands the screen a state and an intent sink.
 *
 * 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §5: one state and one intent channel, and a screen that
 * previews with fake state and no real device attached.
 */
@Composable
fun HarmonyLabRoute(
    modifier: Modifier = Modifier,
    viewModel: HarmonyLabViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HarmonyLabScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun HarmonyLabScreen(
    state: HarmonyLabState,
    onIntent: (HarmonyLabIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Text(
                text = "Harmony domain harness",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            Text(
                text = "Phase 1: the music engine, before MIDI. Type a chord symbol.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = { onIntent(HarmonyLabIntent.SymbolChanged(it)) },
            label = { Text("Chord symbol") },
            singleLine = true,
            isError = state.errorMessage != null,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            HarmonyStatusChip(
                label = if (state.isValid) "Parsed" else "Not read",
                tone = if (state.isValid) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
            )
            state.bassNote?.let { HarmonyStatusChip(label = "Bass $it", tone = FeedbackTone.NEUTRAL) }
        }

        state.errorMessage?.let { message ->
            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    color = HarmonyTheme.colors.incorrect,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }

        if (state.isValid) {
            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    HarmonyChordSymbol(symbol = state.chordSymbol.orEmpty())
                    HarmonyLabelledValue("Degrees", state.degrees.joinToString("  "))
                    HarmonyLabelledValue("Spelled tones", state.tones.joinToString("  "))
                    HarmonyLabelledValue("Close voicing", state.voicing.joinToString("  "))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun HarmonyLabScreenPreview() {
    HarmonyTheme {
        HarmonyLabScreen(
            state = HarmonyLabState(
                input = "Dbmaj9/F",
                chordSymbol = "Dbmaj9/F",
                degrees = listOf("1", "3", "5", "7", "9"),
                tones = listOf("Db", "F", "Ab", "C", "Eb"),
                voicing = listOf("F3", "Ab3", "C4", "Db4", "Eb4"),
                bassNote = "F",
            ),
            onIntent = {},
        )
    }
}
