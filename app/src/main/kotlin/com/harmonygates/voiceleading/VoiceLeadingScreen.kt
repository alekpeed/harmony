package com.harmonygates.voiceleading

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.R
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
 * Voice Leading: play the next chord, moving as little as possible.
 *
 * The asset pack is explicit that this screen is built differently from Ear Training. Its
 * `ASSET_USAGE_RULES.md` says the background is "static environment only" and its
 * `voice-leading.json` requires that "visible interactive elements must be real Compose
 * components, not invisible hitboxes over screenshot controls" — so the approved plate is the
 * room and nothing else, and every control here is a genuine component reading design tokens
 * rather than a touch target over a picture of a control. The pack's SVG shells are the design
 * reference for those components, which its own rules permit: "SVG shells may be used as Figma
 * references or translated into Compose shapes."
 *
 * That is also why this screen does not use the design-pixel placement machinery Ear Training
 * needs: there is no illustrated console to stay registered against.
 */
@Composable
fun VoiceLeadingRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceLeadingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VoiceLeadingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onExit = onExit,
        modifier = modifier,
    )
}

@Composable
fun VoiceLeadingScreen(
    state: VoiceLeadingUiState,
    onIntent: (VoiceLeadingIntent) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Environment only. Nothing about the exercise is in this image.
        Image(
            painter = painterResource(R.drawable.voice_leading_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // The room is a lit night scene; the copy over it needs its own contrast rather than
        // relying on wherever the skyline happens to be bright.
        Box(modifier = Modifier.fillMaxSize().background(HarmonyTheme.colors.scrim))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(HarmonyTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        ) {
            Header(state, onExit)

            when (state.mode) {
                VoiceLeadingMode.SETUP -> SetupPanel(state, onIntent)
                VoiceLeadingMode.PRACTICE -> PracticePanel(state, onIntent)
            }
        }
    }
}

@Composable
private fun Header(state: VoiceLeadingUiState, onExit: () -> Unit) {
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
        Box(modifier = Modifier.weight(1f))
        SecondaryButton(label = "Leave", onClick = onExit)
    }
}

// --- Setup ---------------------------------------------------------------------------------------

@Composable
private fun SetupPanel(state: VoiceLeadingUiState, onIntent: (VoiceLeadingIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "Move between chords with the least motion that works.",
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

            PrimaryButton(
                label = "Start",
                onClick = { onIntent(VoiceLeadingIntent.StartExercise) },
            )
        }
    }
}

// --- Practice ------------------------------------------------------------------------------------

@Composable
private fun PracticePanel(state: VoiceLeadingUiState, onIntent: (VoiceLeadingIntent) -> Unit) {
    if (state.phase == VoiceLeadingPhase.COMPLETE) {
        RunResult(state, onIntent)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
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
                Column(horizontalAlignment = Alignment.Start) {
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
                Column(horizontalAlignment = Alignment.Start) {
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

    // Where the hand is now against where it started: the source voicing is drawn as sustained so
    // the two are distinguishable without colour alone.
    PianoKeyboard(
        lowNote = KEYBOARD_LOW,
        highNote = KEYBOARD_HIGH,
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
                // Right chord, badly reached, is a different result from right chord well reached.
                // That distinction is the entire subject of this screen, so it gets its own chip.
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
                            state.bestTotalMotionSemitones?.let { append(" · best ${it}") }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    HarmonyLabelledValue(
                        label = "Largest leap",
                        value = "${state.maxLeapSemitones ?: 0} semitones",
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
                SecondaryButton(
                    label = "Back to setup",
                    onClick = { onIntent(VoiceLeadingIntent.ExitToSetup) },
                )
            }
        }
    }
}

/**
 * One voice's move, as a token.
 *
 * The pack ships separate line primitives for step, leap, common tone and direction; this is the
 * same distinction in Compose. The glyph carries it as well as the colour, because a held voice
 * and a leaping one must be tellable apart without seeing hue.
 */
@Composable
private fun VoiceMotionToken(motion: VoiceMotionUi) {
    val tone = when {
        motion.isCommonTone -> FeedbackTone.CORRECT
        motion.isLeap -> FeedbackTone.PARTIAL
        else -> FeedbackTone.NEUTRAL
    }
    val glyph = when {
        motion.isCommonTone -> "="
        motion.isUp -> "↑"
        else -> "↓"
    }
    val distance = kotlin.math.abs(motion.semitones)
    val description = when {
        motion.isCommonTone -> "held"
        else -> "$glyph $distance"
    }

    Box(
        modifier = Modifier
            .background(
                HarmonyTheme.colors.surface,
                RoundedCornerShape(HarmonyTheme.shapes.radiusPill),
            )
            .border(
                HarmonyTheme.spacing.hairline,
                tone.color(),
                RoundedCornerShape(HarmonyTheme.shapes.radiusPill),
            )
            .padding(
                horizontal = HarmonyTheme.spacing.medium,
                vertical = HarmonyTheme.spacing.small,
            )
            .semantics {
                contentDescription = if (motion.isCommonTone) {
                    "Voice held on ${motion.fromNote}"
                } else {
                    "Voice moved from ${motion.fromNote} to ${motion.toNote}, $distance semitones"
                }
            },
    ) {
        Text(
            text = description,
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.body,
        )
    }
}

@Composable
private fun RunResult(state: VoiceLeadingUiState, onIntent: (VoiceLeadingIntent) -> Unit) {
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
                text = "Voice leading runs are not recorded yet — nothing here changes your " +
                    "mastery or opens a gate.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                PrimaryButton(
                    label = "Go again",
                    onClick = { onIntent(VoiceLeadingIntent.StartExercise) },
                )
                SecondaryButton(
                    label = "Back to setup",
                    onClick = { onIntent(VoiceLeadingIntent.ExitToSetup) },
                )
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

private const val KEYBOARD_LOW = 48
private const val KEYBOARD_HIGH = 84
private const val MAX_REASONS = 3

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun VoiceLeadingPreview() {
    HarmonyTheme {
        VoiceLeadingScreen(
            state = VoiceLeadingUiState(
                mode = VoiceLeadingMode.SETUP,
                midiStatus = "Studio keyboard",
                midiConnected = true,
            ),
            onIntent = {},
            onExit = {},
        )
    }
}
