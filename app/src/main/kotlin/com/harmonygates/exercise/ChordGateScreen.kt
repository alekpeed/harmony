package com.harmonygates.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyChordSymbol
import com.harmonygates.core.designsystem.component.HarmonyLabelledValue
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.component.PianoKeyboard
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.core.music.session.ExercisePresentationModel
import com.harmonygates.core.music.session.ExerciseSessionState
import com.harmonygates.core.music.session.PauseReason

/**
 * The Phase 4 vertical slice: show a chord, play it, see the verdict, move on.
 *
 * 15_IMPLEMENTATION_PHASES.md calls for "one intentionally plain exercise screen", and this is
 * deliberately that. It exists to prove that theory, MIDI, capture and evaluation hold together
 * under real fingers — not to look like the finished game, which arrives with the design system.
 */
@Composable
fun ChordGateRoute(
    modifier: Modifier = Modifier,
    viewModel: ChordGateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChordGateScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun ChordGateScreen(
    state: ChordGateUiState,
    onIntent: (ChordGateIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session

    // Arming is automatic once a target is on screen: asking a player to press "ready" before
    // every chord would put a tap between them and the keyboard twenty times a session.
    LaunchedEffect(session) {
        if (session is ExerciseSessionState.Presenting && state.midiConnected) {
            onIntent(ChordGateIntent.Arm)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        SessionHeader(state = state)

        when (session) {
            is ExerciseSessionState.Completed -> SessionResult(session, onIntent)
            is ExerciseSessionState.Paused -> PausedNotice(session.reason)
            else -> ExerciseBody(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun SessionHeader(state: ChordGateUiState) {
    val exercise = state.session.visibleExercise
    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chord gates",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            HarmonyStatusChip(
                label = state.midiStatus,
                tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
            )
        }
        if (exercise != null) {
            Text(
                text = "Exercise ${exercise.exerciseNumber} of ${exercise.totalExercises}",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            LinearProgressIndicator(
                progress = { exercise.exerciseNumber.toFloat() / exercise.totalExercises },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExerciseBody(
    state: ChordGateUiState,
    onIntent: (ChordGateIntent) -> Unit,
) {
    val session = state.session
    val exercise = session.visibleExercise

    if (exercise == null) {
        Text(
            text = "Preparing the session.",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.body,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium)) {
        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Text(
                    text = exercise.instruction,
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                HarmonyChordSymbol(symbol = exercise.chordSymbol.orEmpty())
                exercise.inversionLabel?.let {
                    Text(
                        text = it,
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.body,
                    )
                }
                if (exercise.spelledNoteNames.isNotEmpty()) {
                    HarmonyLabelledValue("Notes", exercise.spelledNoteNames.joinToString("  "))
                }
            }
        }

        PianoKeyboard(
            lowNote = KEYBOARD_LOW,
            highNote = KEYBOARD_HIGH,
            held = state.soundingNotes.toSet(),
            sustained = exercise.keyboardTargets.toSet(),
        )

        when (session) {
            is ExerciseSessionState.Feedback -> FeedbackPanel(session, onIntent)
            else -> WaitingPanel(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun WaitingPanel(
    state: ChordGateUiState,
    onIntent: (ChordGateIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
        HarmonyStatusChip(
            label = if (state.soundingNotes.isEmpty()) "Waiting for your chord" else "Listening",
            tone = FeedbackTone.NEUTRAL,
        )
        if (!state.midiConnected) {
            Text(
                text = "Connect a MIDI keyboard to answer. Your place in the session is kept.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            OutlinedButton(onClick = { onIntent(ChordGateIntent.Skip) }) { Text("Skip") }
        }
    }
}

@Composable
private fun FeedbackPanel(
    feedback: ExerciseSessionState.Feedback,
    onIntent: (ChordGateIntent) -> Unit,
) {
    val result = feedback.result
    val tone = when (result.explanation.headline) {
        FeedbackModel.Headline.CORRECT, FeedbackModel.Headline.CORRECT_VARIATION -> FeedbackTone.CORRECT
        FeedbackModel.Headline.ALMOST -> FeedbackTone.PARTIAL
        FeedbackModel.Headline.NOT_YET -> FeedbackTone.INCORRECT
        else -> FeedbackTone.NEUTRAL
    }

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            // Correctness is never colour alone (18_ACCEPTANCE_CRITERIA.md): the chip carries a
            // glyph and a word, and the diagnosis below says what actually happened.
            HarmonyStatusChip(label = headlineText(result.explanation.headline), tone = tone)

            result.explanation.errors.take(MAX_REASONS).forEach { error ->
                Text(
                    text = describe(error),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
            }

            result.timing?.let { timing ->
                timing.onsetSpreadMillis?.let { spread ->
                    HarmonyLabelledValue("Chord spread", "$spread ms")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Button(onClick = { onIntent(ChordGateIntent.Next) }) { Text("Next") }
            }
        }
    }
}

@Composable
private fun SessionResult(
    completed: ExerciseSessionState.Completed,
    onIntent: (ChordGateIntent) -> Unit,
) {
    val summary = completed.summary
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "Session complete",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            HarmonyLabelledValue("Correct", "${summary.correct} of ${summary.attempted}")
            HarmonyLabelledValue("Almost", summary.partial.toString())
            HarmonyLabelledValue("Skipped", summary.skipped.toString())
            summary.medianResponseMillis?.let {
                HarmonyLabelledValue("Median response", "$it ms")
            }
            Button(onClick = { onIntent(ChordGateIntent.Restart) }) { Text("Play again") }
        }
    }
}

@Composable
private fun PausedNotice(reason: PauseReason) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (reason) {
                PauseReason.DeviceDisconnected ->
                    "The keyboard was disconnected. Plug it back in and the session carries on " +
                        "from here — nothing has been lost."

                PauseReason.AppBackgrounded -> "Paused. Return to the app to carry on."
                PauseReason.PlayerRequested -> "Paused."
            },
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

@Preview(showBackground = true, widthDp = 1000, heightDp = 760)
@Composable
private fun ChordGatePreview() {
    HarmonyTheme {
        ChordGateScreen(
            state = ChordGateUiState(
                session = ExerciseSessionState.Presenting(
                    ExercisePresentationModel(
                        exerciseNumber = 3,
                        totalExercises = 20,
                        chordSymbol = "Ebm7",
                        spelledNoteNames = emptyList(),
                        keyboardTargets = emptyList(),
                        inversionLabel = null,
                        instruction = "Play this chord in root position",
                    ),
                ),
                midiConnected = true,
                midiStatus = "Simulated Keyboard",
            ),
            onIntent = {},
        )
    }
}
