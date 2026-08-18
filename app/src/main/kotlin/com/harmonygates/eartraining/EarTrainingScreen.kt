package com.harmonygates.eartraining

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Text
import com.harmonygates.R
import com.harmonygates.artwork.DesignHit
import com.harmonygates.artwork.DesignImage
import com.harmonygates.artwork.DesignRect
import com.harmonygates.artwork.DesignScope
import com.harmonygates.artwork.DesignSurface
import com.harmonygates.artwork.LabelOverflow
import com.harmonygates.artwork.Plate
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.eartraining.EarTaskFamily
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.exercise.describe

/**
 * Ear Training, as the approved layered console.
 *
 * The artwork is the interface. Every plate, control and label is placed through one shared
 * design transform (see [DesignSurface]), so the console, the moving parts, the live text and the
 * touch targets stay registered to one another at any tablet size.
 *
 * Nothing here holds state. Everything comes from [EarTrainingViewModel], which is also what MIDI
 * drives, so a control cannot show one thing while the exercise engine believes another.
 *
 * Controls with no engine state behind them are deliberately left as artwork with no touch
 * target: the interval, mode, direction and option panels are part of the approved plate and are
 * where those features belong when they exist, but drawing a live-looking switch over a setting
 * the engine cannot read would be a lie the player only discovers by pressing it.
 */
@Composable
fun EarTrainingRoute(
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EarTrainingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EarTrainingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onExit = onExit,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@Composable
fun EarTrainingScreen(
    state: EarTrainingUiState,
    onIntent: (EarTrainingIntent) -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        DesignSurface {
            // The room is common to both modes and never moves; only what stands in front of it
            // changes, which is what makes the console read as an object in the space.
            Plate(R.drawable.et_ear_training_background_cinque_terre)

            AnimatedVisibility(
                visible = state.mode == EarMode.SETUP,
                enter = fadeIn(tween(CONSOLE_FADE_MS)) +
                    slideInVertically(tween(CONSOLE_FADE_MS)) { -it / CONSOLE_SLIDE_FRACTION },
                exit = fadeOut(tween(CONSOLE_FADE_MS)) +
                    slideOutVertically(tween(CONSOLE_FADE_MS)) { -it / CONSOLE_SLIDE_FRACTION },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Plate(R.drawable.et_ear_training_setup_shell)
                    Plate(R.drawable.et_ear_training_section_layout)
                }
            }

            AnimatedVisibility(
                visible = state.mode == EarMode.TRAINING,
                enter = fadeIn(tween(CONSOLE_FADE_MS)),
                exit = fadeOut(tween(CONSOLE_FADE_MS)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DesignImage(R.drawable.et_ear_training_training_bar_shell, EarLayout.TrainingBar)
                }
            }

            if (state.mode == EarMode.SETUP) {
                SetupControls(state, onIntent)
            } else {
                TrainingControls(state, onIntent)
            }

            TransportBar(state, onIntent, onExit, onOpenSettings)
        }
    }
}

// --- Setup ---------------------------------------------------------------------------------------

@Composable
private fun DesignScope.SetupControls(
    state: EarTrainingUiState,
    onIntent: (EarTrainingIntent) -> Unit,
) {
    state.families.forEachIndexed { index, family ->
        val selected = family == state.family
        DesignImage(
            resourceId = if (selected) R.drawable.et_music_note_active else R.drawable.et_music_note_idle,
            rect = EarLayout.familyRowIcon(index),
        )
        DesignLabel(
            text = family.label,
            rect = EarLayout.familyRowLabel(index),
            size = 21f,
            colour = if (selected) HarmonyTheme.colors.textPrimary else HarmonyTheme.colors.textSecondary,
            weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            align = TextAlign.Start,
        )
        DesignHit(
            rect = EarLayout.familyRow(index),
            contentDescription = "${family.label} exercises" + if (selected) ", selected" else "",
            onClick = { onIntent(EarTrainingIntent.ChooseFamily(family)) },
        )
    }

    // The generator places every session through all twelve keys; the panel states that rather
    // than offering a choice the engine does not read.
    DesignLabel(
        text = "All 12 keys",
        rect = EarLayout.RootKeyPanel,
        size = 22f,
        colour = HarmonyTheme.colors.textSecondary,
    )

    DesignLabel(
        text = if (state.sessionLength > 0) "${state.sessionLength} questions" else "",
        rect = EarLayout.SessionRowLabel,
        size = 19f,
        colour = HarmonyTheme.colors.textSecondary,
        align = TextAlign.Start,
    )

    DesignLabel(
        text = "START",
        rect = EarLayout.StartPill,
        size = 30f,
        colour = HarmonyTheme.colors.textPrimary,
        weight = FontWeight.SemiBold,
    )
    DesignHit(
        rect = EarLayout.StartPill,
        contentDescription = "Start ear training",
        onClick = { onIntent(EarTrainingIntent.StartTraining) },
        enabled = state.canStart,
    )

    if (state.phase == EarPhase.UNAVAILABLE && state.message != null) {
        DesignLabel(
            text = state.message,
            rect = EarLayout.IntervalSettingsPanel,
            size = 17f,
            colour = HarmonyTheme.colors.onSurfaceMuted,
            lines = 3,
        )
    }
}

// --- Training ------------------------------------------------------------------------------------

@Composable
private fun DesignScope.TrainingControls(
    state: EarTrainingUiState,
    onIntent: (EarTrainingIntent) -> Unit,
) {
    when (state.phase) {
        EarPhase.COMPLETED -> {
            DesignLabel(
                text = "Session finished — ${state.correctCount} of ${state.sessionLength} correct",
                rect = EarLayout.TrainingInstruction,
                size = 30f,
                colour = HarmonyTheme.colors.textPrimary,
                weight = FontWeight.SemiBold,
            )
            DesignLabel(
                text = "Recorded. This session counts towards your mastery.",
                rect = EarLayout.TrainingVerdict,
                size = 17f,
                colour = HarmonyTheme.colors.onSurfaceMuted,
                lines = 2,
            )
        }

        EarPhase.UNAVAILABLE -> DesignLabel(
            text = state.message.orEmpty(),
            rect = EarLayout.TrainingMessage,
            size = 19f,
            colour = HarmonyTheme.colors.onSurfaceMuted,
            lines = 4,
        )

        else -> {
            // The key is shown only where it is part of the question. For the other families the
            // key is an implementation detail of generation and telling the player would be noise.
            if (state.family == EarTaskFamily.FUNCTION_HEARING) {
                state.keySpelling?.let { spelling ->
                    EarLayout.noteButton(spelling, active = true)?.let { resource ->
                        DesignImage(
                            resourceId = resource,
                            rect = EarLayout.TrainingKeyButton,
                            contentDescription = "Key of $spelling",
                        )
                    }
                }
            }

            DesignLabel(
                text = state.instruction,
                rect = EarLayout.TrainingInstruction,
                size = 27f,
                colour = HarmonyTheme.colors.textPrimary,
                weight = FontWeight.SemiBold,
            )

            if (state.phase == EarPhase.FEEDBACK) {
                DesignLabel(
                    text = feedbackLine(state),
                    rect = EarLayout.TrainingVerdict,
                    size = 20f,
                    colour = HarmonyTheme.colors.textSecondary,
                    lines = 2,
                )
            } else if (state.plays > 0) {
                DesignLabel(
                    text = if (state.phase == EarPhase.PLAYING) "Listen" else "Heard ${state.plays}×",
                    rect = EarLayout.TrainingVerdict,
                    size = 20f,
                    colour = HarmonyTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

/** Verdict, revealed answer and diagnosis, in the order a player can use them. */
private fun feedbackLine(state: EarTrainingUiState): String {
    val result = state.result ?: return ""
    val headline = when (result.explanation.headline) {
        FeedbackModel.Headline.CORRECT, FeedbackModel.Headline.CORRECT_VARIATION -> "Correct"
        FeedbackModel.Headline.ALMOST -> "Almost"
        FeedbackModel.Headline.NOT_YET -> "Not yet"
        FeedbackModel.Headline.NOTHING_PLAYED -> "Nothing played"
        FeedbackModel.Headline.DEVICE_LOST -> "Keyboard disconnected"
    }
    val answer = state.answerSymbol?.let { " · that was $it" }.orEmpty()
    val why = result.explanation.primaryError?.let { " · ${describe(it)}" }.orEmpty()
    val difference = state.differenceDescription?.let { " · $it" }.orEmpty()
    return headline + answer + why + difference
}

// --- The bar, in both modes ----------------------------------------------------------------------

@Composable
private fun DesignScope.TransportBar(
    state: EarTrainingUiState,
    onIntent: (EarTrainingIntent) -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val training = state.mode == EarMode.TRAINING

    // MIDI: green when notes are arriving, amber when a keyboard is there but not connected, dark
    // when there is none. 18_ACCEPTANCE_CRITERIA.md forbids colour alone, so the wording beside it
    // says the same thing.
    DesignImage(
        resourceId = when {
            state.midiConnected -> R.drawable.et_status_green_on
            state.midiStatus == "Keyboard available" -> R.drawable.et_status_amber_on
            else -> R.drawable.et_status_off
        },
        rect = EarLayout.BarMidiLamp,
    )
    DesignLabel(
        text = state.midiStatus,
        rect = EarLayout.BarMidiLabel,
        size = 18f,
        colour = HarmonyTheme.colors.textSecondary,
        align = TextAlign.Start,
    )

    DesignImage(
        resourceId = if (state.phase == EarPhase.PLAYING) {
            R.drawable.et_play_active_large
        } else {
            R.drawable.et_play_idle_large
        },
        rect = EarLayout.BarPlay,
    )
    DesignHit(
        rect = EarLayout.BarPlay,
        contentDescription = if (training) {
            if (state.plays == 0) "Play the chord" else "Play the chord again"
        } else {
            "Start ear training"
        },
        onClick = {
            onIntent(if (training) EarTrainingIntent.Play else EarTrainingIntent.StartTraining)
        },
        enabled = if (training) state.canReplay else state.canStart,
    )

    if (training) {
        DesignImage(
            resourceId = if (state.canReplay) R.drawable.et_repeat_active else R.drawable.et_repeat_idle,
            rect = EarLayout.BarReplay,
        )
        DesignHit(
            rect = EarLayout.BarReplay,
            contentDescription = "Hear it again. Heard ${state.plays} times so far.",
            onClick = { onIntent(EarTrainingIntent.Play) },
            enabled = state.canReplay,
        )

        DesignImage(
            resourceId = if (state.phase == EarPhase.FEEDBACK) {
                R.drawable.et_shuffle_active
            } else {
                R.drawable.et_shuffle_idle
            },
            rect = EarLayout.BarNext,
        )
        DesignHit(
            rect = EarLayout.BarNext,
            contentDescription = if (state.phase == EarPhase.FEEDBACK) "Next exercise" else "Skip this exercise",
            onClick = { onIntent(EarTrainingIntent.Next) },
            enabled = state.phase != EarPhase.COMPLETED,
        )

        DesignLabel(
            text = state.progressLabel,
            rect = EarLayout.BarRightPanel,
            size = 22f,
            colour = HarmonyTheme.colors.textPrimary,
        )
    } else {
        DesignLabel(
            text = state.family?.label.orEmpty(),
            rect = EarLayout.BarRightPanel,
            size = 20f,
            colour = HarmonyTheme.colors.textSecondary,
        )
    }

    // Left circle leaves whatever is on screen: the session in training, the screen in setup.
    DesignImage(
        resourceId = R.drawable.et_previous_idle,
        rect = EarLayout.BarLeftCircle,
    )
    DesignHit(
        rect = EarLayout.BarLeftCircle,
        contentDescription = if (training) "End the session and return to setup" else "Leave ear training",
        onClick = { if (training) onIntent(EarTrainingIntent.ExitToSetup) else onExit() },
    )

    DesignImage(resourceId = R.drawable.et_level_meter, rect = EarLayout.BarSettings)
    DesignHit(
        rect = EarLayout.BarSettings,
        contentDescription = "Settings",
        onClick = onOpenSettings,
    )

    DesignImage(resourceId = R.drawable.et_eye, rect = EarLayout.BarRightCircle)
    DesignHit(
        rect = EarLayout.BarRightCircle,
        contentDescription = "Leave ear training",
        onClick = onExit,
    )
}

// --- Text ----------------------------------------------------------------------------------------

/**
 * A live value, placed in its slot on the plate.
 *
 * Everything that can change is drawn here rather than baked into artwork, which is the whole
 * reason the approved plates ship with empty fields.
 */
@Composable
private fun DesignScope.DesignLabel(
    text: String,
    rect: DesignRect,
    size: Float,
    colour: Color,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Center,
    lines: Int = 1,
) {
    if (text.isEmpty()) return
    Box(modifier = Modifier.designRect(rect), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = colour,
            fontSize = textSize(size),
            fontWeight = weight,
            textAlign = align,
            maxLines = lines,
            overflow = LabelOverflow,
        )
    }
}

private const val CONSOLE_FADE_MS = 260
private const val CONSOLE_SLIDE_FRACTION = 6

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun EarTrainingSetupPreview() {
    HarmonyTheme {
        EarTrainingScreen(
            state = EarTrainingUiState(
                mode = EarMode.SETUP,
                families = EarTaskFamily.entries.take(4),
                family = EarTaskFamily.REPRODUCE,
                sessionLength = 16,
                midiStatus = "Studio keyboard",
                midiConnected = true,
            ),
            onIntent = {},
            onExit = {},
            onOpenSettings = {},
        )
    }
}
