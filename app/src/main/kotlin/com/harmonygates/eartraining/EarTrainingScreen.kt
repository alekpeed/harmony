package com.harmonygates.eartraining

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
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.eartraining.EarTaskFamily
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.exercise.describe

/**
 * Ear training, plain.
 *
 * This replaces the layered illustrated console. The plates are still in `interface/` and
 * `interface/maps/ear_training.json` still records where every control sat, so the design can be
 * reinstated; the exercise loop below is unchanged either way, because it never lived in the
 * artwork.
 */
@Composable
fun EarTrainingRoute(
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EarTrainingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EarTrainingScreen(state, viewModel::onIntent, onExit, onOpenSettings, modifier)
}

@Composable
fun EarTrainingScreen(
    state: EarTrainingUiState,
    onIntent: (EarTrainingIntent) -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
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
                text = "Ear Training",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            HarmonyStatusChip(
                label = state.midiStatus,
                tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
            )
            Box(Modifier.weight(1f))
            SecondaryButton(label = "Settings", onClick = onOpenSettings)
            SecondaryButton(label = "Leave", onClick = onExit)
        }

        when (state.mode) {
            EarMode.SETUP -> Setup(state, onIntent)
            EarMode.TRAINING -> Training(state, onIntent)
        }
    }
}

@Composable
private fun Setup(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "The app plays a chord. Play it back on the keyboard.",
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
            )

            Text(
                text = "Exercise",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
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

            if (state.sessionLength > 0) {
                HarmonyLabelledValue(
                    label = "Session",
                    value = "${state.sessionLength} questions, cycling all 12 keys",
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = HarmonyTheme.colors.feedbackWarning,
                    fontSize = HarmonyTheme.typography.body,
                )
            }

            PrimaryButton(
                label = "Start",
                onClick = { onIntent(EarTrainingIntent.StartTraining) },
                enabled = state.canStart,
            )
        }
    }
}

@Composable
private fun Training(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
    if (state.phase == EarPhase.COMPLETED) {
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
                Text(
                    text = "Recorded towards your mastery.",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    PrimaryButton(label = "Go again", onClick = { onIntent(EarTrainingIntent.Restart) })
                    SecondaryButton(label = "Setup", onClick = { onIntent(EarTrainingIntent.ExitToSetup) })
                }
            }
        }
        return
    }

    if (state.phase == EarPhase.UNAVAILABLE) {
        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Text(
                    text = state.message.orEmpty(),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
                SecondaryButton(label = "Setup", onClick = { onIntent(EarTrainingIntent.ExitToSetup) })
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
        Text(
            text = state.progressLabel,
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.caption,
        )
        if (state.sessionLength > 0) {
            LinearProgressIndicator(
                progress = { state.exerciseNumber.toFloat() / state.sessionLength },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = state.progressLabel },
            )
        }
    }

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = state.instruction.ifBlank { "Getting an exercise ready…" },
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
                fontWeight = FontWeight.SemiBold,
            )
            state.keySpelling?.takeIf { state.family == EarTaskFamily.FUNCTION_HEARING }?.let { key ->
                HarmonyLabelledValue(label = "Key", value = key)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    label = if (state.plays == 0) "Play" else "Play again",
                    onClick = { onIntent(EarTrainingIntent.Play) },
                    enabled = state.canReplay,
                )
                HarmonyStatusChip(
                    label = when (state.phase) {
                        EarPhase.PLAYING -> "Listen"
                        EarPhase.LISTENING -> "Play it back"
                        EarPhase.FEEDBACK -> "Answered"
                        else -> "Getting ready"
                    },
                    tone = FeedbackTone.NEUTRAL,
                )
                // 07 §5 counts replays as assistance evidence, so the count is shown rather than
                // quietly tracked.
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

    PianoKeyboard(lowNote = 48, highNote = 84, held = state.soundingNotes.toSet())

    if (state.phase == EarPhase.FEEDBACK) {
        Feedback(state, onIntent)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            SecondaryButton(label = "Skip", onClick = { onIntent(EarTrainingIntent.Skip) })
        }
    }
}

@Composable
private fun Feedback(state: EarTrainingUiState, onIntent: (EarTrainingIntent) -> Unit) {
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

            // Revealed only once the answer is in: naming it is half of what identify-then-play
            // is testing.
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
                PrimaryButton(label = "Next", onClick = { onIntent(EarTrainingIntent.Next) })
                SecondaryButton(label = "Hear it again", onClick = { onIntent(EarTrainingIntent.Play) })
            }
        }
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

private const val MAX_REASONS = 3

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun EarTrainingPreview() {
    HarmonyTheme {
        EarTrainingScreen(
            state = EarTrainingUiState(
                families = EarTaskFamily.entries.take(4),
                family = EarTaskFamily.REPRODUCE,
                sessionLength = 16,
            ),
            onIntent = {},
            onExit = {},
            onOpenSettings = {},
        )
    }
}
