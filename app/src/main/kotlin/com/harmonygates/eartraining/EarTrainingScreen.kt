package com.harmonygates.eartraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.FilterChip
import com.harmonygates.core.designsystem.component.HarmonyChordSymbol
import com.harmonygates.core.designsystem.component.HarmonyLabelledValue
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.component.PianoKeyboard
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.exercise.describe

/**
 * Ear training, in the plain idiom.
 *
 * No approved artwork exists for this screen yet — it is being drawn now — so this is deliberately
 * the same functional Compose the MIDI and settings screens use: it exists to be played rather
 * than to be looked at, and the plate drops in later without the loop changing.
 */
@Composable
fun EarTrainingRoute(
    modifier: Modifier = Modifier,
    viewModel: EarTrainingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EarTrainingScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun EarTrainingScreen(
    state: EarTrainingUiState,
    onIntent: (EarTrainingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Header(state)
        FamilyPicker(state, onIntent)

        when (state.phase) {
            EarPhase.COMPLETED -> SessionResult(state, onIntent)
            EarPhase.UNAVAILABLE -> UnavailableNotice(state)
            else -> ExerciseBody(state, onIntent)
        }
    }
}

@Composable
private fun Header(state: EarTrainingUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ear training",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            HarmonyStatusChip(
                label = state.midiStatus,
                tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
            )
        }
        if (state.sessionLength > 0 && state.phase != EarPhase.COMPLETED) {
            Text(
                text = state.progressLabel,
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            LinearProgressIndicator(
                progress = { state.exerciseNumber.toFloat() / state.sessionLength },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = state.progressLabel },
            )
        }
    }
}

@Composable
private fun FamilyPicker(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
    if (state.families.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
    ) {
        state.families.forEach { family ->
            FilterChip(
                label = family.label,
                selected = family == state.family,
                onToggle = { onIntent(EarTrainingIntent.ChooseFamily(family)) },
            )
        }
    }
}

@Composable
private fun ExerciseBody(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = state.instruction.ifBlank { "Getting an exercise ready…" },
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onIntent(EarTrainingIntent.Play) },
                    enabled = state.canReplay,
                    modifier = Modifier.semantics {
                        contentDescription = if (state.plays == 0) {
                            "Play the chord"
                        } else {
                            "Play the chord again. Heard ${state.plays} times so far."
                        }
                    },
                ) {
                    Text(if (state.plays == 0) "Play" else "Play again")
                }
                HarmonyStatusChip(
                    label = when (state.phase) {
                        EarPhase.PLAYING -> "Listen"
                        EarPhase.LISTENING -> "Play it back"
                        EarPhase.FEEDBACK -> "Answered"
                        else -> "Getting ready"
                    },
                    tone = FeedbackTone.NEUTRAL,
                )
                // 07 §5: replays are assistance evidence, so the count is shown rather than
                // quietly tracked — a player should know a fifth hearing is not a first one.
                if (state.plays > 0) {
                    Text(
                        text = "Heard ${state.plays}×",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
        }
    }

    PianoKeyboard(
        lowNote = KEYBOARD_LOW,
        highNote = KEYBOARD_HIGH,
        held = state.soundingNotes.toSet(),
    )

    if (!state.midiConnected) {
        Text(
            text = "Connect a MIDI keyboard to answer. The chord still plays without one.",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.body,
        )
    }

    when (state.phase) {
        EarPhase.FEEDBACK -> FeedbackPanel(state, onIntent)
        else -> Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            OutlinedButton(
                onClick = { onIntent(EarTrainingIntent.Skip) },
                modifier = Modifier.semantics { contentDescription = "Skip this exercise and move on" },
            ) { Text("Skip") }
        }
    }
}

@Composable
private fun FeedbackPanel(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
    val result = state.result ?: return
    val tone = when (result.explanation.headline) {
        FeedbackModel.Headline.CORRECT, FeedbackModel.Headline.CORRECT_VARIATION -> FeedbackTone.CORRECT
        FeedbackModel.Headline.ALMOST -> FeedbackTone.PARTIAL
        FeedbackModel.Headline.NOT_YET -> FeedbackTone.INCORRECT
        else -> FeedbackTone.NEUTRAL
    }

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            HarmonyStatusChip(label = headlineText(result.explanation.headline), tone = tone)

            // The answer is revealed once it has been given, never before: naming it is half of
            // what identify-then-play is testing.
            state.answerSymbol?.let { symbol ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "That was",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.body,
                    )
                    HarmonyChordSymbol(symbol = symbol)
                }
            }

            state.differenceDescription?.let { difference ->
                Text(
                    text = difference,
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
            }

            result.explanation.errors.take(MAX_REASONS).forEach { error ->
                Text(
                    text = describe(error),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Button(onClick = { onIntent(EarTrainingIntent.Next) }) { Text("Next") }
                OutlinedButton(onClick = { onIntent(EarTrainingIntent.Play) }) { Text("Hear it again") }
            }
        }
    }
}

@Composable
private fun SessionResult(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "Session finished",
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.heading,
            )
            HarmonyLabelledValue(
                label = "Correct",
                value = "${state.correctCount} of ${state.sessionLength}",
            )
            // Phase 8's engines are reachable now; the attempt store is not wired to them yet.
            Text(
                text = "Ear sessions are not recorded yet — nothing here changes your mastery or " +
                    "opens a gate.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            Button(onClick = { onIntent(EarTrainingIntent.Restart) }) { Text("Go again") }
        }
    }
}

@Composable
private fun UnavailableNotice(state: EarTrainingUiState) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.message ?: "This exercise family is not available.",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.body,
        )
    }
}

private fun headlineText(headline: FeedbackModel.Headline): String = when (headline) {
    FeedbackModel.Headline.CORRECT -> "Correct"
    FeedbackModel.Headline.CORRECT_VARIATION -> "Correct"
    FeedbackModel.Headline.ALMOST -> "Almost"
    FeedbackModel.Headline.NOT_YET -> "Not yet"
    FeedbackModel.Headline.NOTHING_PLAYED -> "Nothing played"
    FeedbackModel.Headline.DEVICE_LOST -> "Keyboard disconnected"
}

private const val KEYBOARD_LOW = 48
private const val KEYBOARD_HIGH = 84
private const val MAX_REASONS = 3

@Preview(showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun EarTrainingPreview() {
    HarmonyTheme {
        EarTrainingScreen(
            state = EarTrainingUiState(
                phase = EarPhase.LISTENING,
                instruction = "Play what you hear.",
                exerciseNumber = 3,
                sessionLength = 16,
                plays = 1,
                canReplay = true,
                midiConnected = true,
                midiStatus = "Studio keyboard",
            ),
            onIntent = {},
        )
    }
}
