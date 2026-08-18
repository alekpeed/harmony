package com.harmonygates.voiceleading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.harmonygates.core.designsystem.component.color
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.exercise.describe

/**
 * Voice leading, plain.
 *
 * This replaces the asset-skinned surface and the illustrated menu. The primitive library is
 * still in `interface/assets/voice_leading/` and the map still records the regions, so the design
 * can be reinstated over the same view model — the exercise, the evaluator and the motion
 * measurement are unchanged.
 */
@Composable
fun VoiceLeadingRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceLeadingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VoiceLeadingScreen(state, viewModel::onIntent, onExit, modifier)
}

@Composable
fun VoiceLeadingScreen(
    state: VoiceLeadingUiState,
    onIntent: (VoiceLeadingIntent) -> Unit,
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
                text = "Voice Leading",
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

        when (state.mode) {
            VoiceLeadingMode.SETUP -> Setup(state, onIntent)
            VoiceLeadingMode.PRACTICE -> Practice(state, onIntent)
        }
    }
}

@Composable
private fun Setup(state: VoiceLeadingUiState, onIntent: (VoiceLeadingIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "The app plays where your hand starts. Play the next chord, moving as " +
                    "little as you can.",
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
            )

            Text(
                text = "Progression",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            ) {
                state.availableTemplates.forEach { template ->
                    FilterChip(
                        label = template.title,
                        selected = template.id == state.template.id,
                        onToggle = { onIntent(VoiceLeadingIntent.ChooseProgression(template)) },
                    )
                }
            }

            Text(
                text = "Voicing",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            ) {
                state.availableStyles.forEach { style ->
                    FilterChip(
                        label = style.label,
                        selected = style == state.style,
                        onToggle = { onIntent(VoiceLeadingIntent.ChooseStyle(style)) },
                    )
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = HarmonyTheme.colors.feedbackWarning,
                    fontSize = HarmonyTheme.typography.body,
                )
            }

            PrimaryButton(label = "Start", onClick = { onIntent(VoiceLeadingIntent.StartExercise) })
        }
    }
}

@Composable
private fun Practice(state: VoiceLeadingUiState, onIntent: (VoiceLeadingIntent) -> Unit) {
    if (state.phase == VoiceLeadingPhase.COMPLETE) {
        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Text(
                    text = "Run finished",
                    color = HarmonyTheme.colors.textPrimary,
                    fontSize = HarmonyTheme.typography.heading,
                )
                HarmonyLabelledValue(
                    label = "Correct",
                    value = "${state.correct} of ${state.attempted}",
                )
                Text(
                    text = "Recorded towards your mastery.",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                    PrimaryButton(label = "Go again", onClick = { onIntent(VoiceLeadingIntent.StartExercise) })
                    SecondaryButton(label = "Setup", onClick = { onIntent(VoiceLeadingIntent.ExitToSetup) })
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
        Text(
            text = "Move ${state.stepNumber} of ${state.stepCount}",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.caption,
        )
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Move ${state.stepNumber} of ${state.stepCount}" },
        )
    }

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "From",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    state.sourceSymbol?.let { HarmonyChordSymbol(symbol = it) }
                }
                Text(
                    text = "→",
                    color = HarmonyTheme.colors.accentPrimary,
                    fontSize = HarmonyTheme.typography.heading,
                )
                Column {
                    Text(
                        text = "Play",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                    state.targetSymbol?.let { HarmonyChordSymbol(symbol = it) }
                }
                state.targetFunction?.let { function ->
                    HarmonyStatusChip(label = function, tone = FeedbackTone.NEUTRAL)
                }
            }
            state.instruction?.let { instruction ->
                Text(
                    text = instruction,
                    color = HarmonyTheme.colors.textSecondary,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
            SecondaryButton(
                label = "Hear the chord you are moving from",
                onClick = { onIntent(VoiceLeadingIntent.PlaySource) },
            )
        }
    }

    // The source voicing is drawn as sustained so where the hand started stays visible under
    // what it is playing now.
    PianoKeyboard(
        lowNote = 48,
        highNote = 84,
        held = state.soundingNotes.toSet(),
        sustained = state.sourceNotes.toSet(),
    )

    if (state.result != null) {
        MotionFeedback(state, onIntent)
    }
}

@Composable
private fun MotionFeedback(state: VoiceLeadingUiState, onIntent: (VoiceLeadingIntent) -> Unit) {
    val result = state.result ?: return
    val tone = when (result.explanation.headline) {
        FeedbackModel.Headline.CORRECT, FeedbackModel.Headline.CORRECT_VARIATION -> FeedbackTone.CORRECT
        FeedbackModel.Headline.ALMOST -> FeedbackTone.PARTIAL
        FeedbackModel.Headline.NOT_YET -> FeedbackTone.INCORRECT
        else -> FeedbackTone.NEUTRAL
    }

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HarmonyStatusChip(label = headlineText(result.explanation.headline), tone = tone)
                // Right chord reached clumsily is a different result from right chord reached
                // well, which is the whole subject of this screen.
                if (result.verdict.isCorrect && state.totalMotionSemitones != null) {
                    HarmonyStatusChip(
                        label = if (state.isSmoothest) "Smoothest route" else "A smoother route exists",
                        tone = if (state.isSmoothest) FeedbackTone.CORRECT else FeedbackTone.PARTIAL,
                    )
                }
            }

            result.explanation.errors.take(MAX_REASONS).forEach { error ->
                Text(
                    text = describe(error),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
            }

            if (state.totalMotionSemitones != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large)) {
                    HarmonyLabelledValue(
                        label = "Total motion",
                        value = buildString {
                            append("${state.totalMotionSemitones} semitones")
                            state.bestTotalMotionSemitones?.let { append(" · best $it") }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    HarmonyLabelledValue(
                        label = "Largest leap",
                        value = "${state.maxLeapSemitones ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                    HarmonyLabelledValue(
                        label = "Common tones",
                        value = "${state.commonToneCount ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.motions.isNotEmpty()) {
                Text(
                    text = "Each voice",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                ) {
                    state.motions.forEach { motion -> VoiceMotionToken(motion) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                PrimaryButton(label = "Next", onClick = { onIntent(VoiceLeadingIntent.Next) })
                SecondaryButton(label = "Setup", onClick = { onIntent(VoiceLeadingIntent.ExitToSetup) })
            }
        }
    }
}

/** One voice's move. The glyph carries it as well as the colour, never colour alone. */
@Composable
private fun VoiceMotionToken(motion: VoiceMotionUi) {
    val tone = when {
        motion.isCommonTone -> FeedbackTone.CORRECT
        motion.isLeap -> FeedbackTone.PARTIAL
        else -> FeedbackTone.NEUTRAL
    }
    val distance = kotlin.math.abs(motion.semitones)
    val label = when {
        motion.isCommonTone -> "held"
        motion.isUp -> "↑ $distance"
        else -> "↓ $distance"
    }

    Box(
        modifier = Modifier
            .background(HarmonyTheme.colors.surface, RoundedCornerShape(HarmonyTheme.shapes.radiusPill))
            .border(
                HarmonyTheme.spacing.hairline,
                tone.color(),
                RoundedCornerShape(HarmonyTheme.shapes.radiusPill),
            )
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.small)
            .semantics {
                contentDescription = if (motion.isCommonTone) {
                    "Voice held on ${motion.fromNote}"
                } else {
                    "Voice moved from ${motion.fromNote} to ${motion.toNote}, $distance semitones"
                }
            },
    ) {
        Text(
            text = label,
            color = HarmonyTheme.colors.textPrimary,
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

private const val MAX_REASONS = 3

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun VoiceLeadingPreview() {
    HarmonyTheme { VoiceLeadingScreen(VoiceLeadingUiState(), {}, {}) }
}
