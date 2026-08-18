package com.harmonygates.relativepitch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.relativepitch.LevelStat
import com.harmonygates.core.music.relativepitch.LevelStatus
import com.harmonygates.core.music.relativepitch.RelativePitchLevel
import com.harmonygates.core.music.relativepitch.RelativePitchTier

/**
 * Relative pitch, from the ground up: a graded ladder of multiple-choice levels, ending in the
 * two levels that hand off to `eartraining`'s existing keyboard screen.
 *
 * [onOpenFullTrainer] is only ever called for the ladder's last two rungs — [RelativePitchTier
 * .FUNCTION_HEARING] and [RelativePitchTier.REPRODUCE] generate no multiple-choice question of
 * their own; they exist in the ladder to say "this is unlocked now" and to route into the
 * console that already does that job.
 */
@Composable
fun RelativePitchRoute(
    onExit: () -> Unit,
    onOpenFullTrainer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RelativePitchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RelativePitchScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onExit = onExit,
        onOpenFullTrainer = onOpenFullTrainer,
        modifier = modifier,
    )
}

@Composable
fun RelativePitchScreen(
    state: RelativePitchUiState,
    onIntent: (RelativePitchIntent) -> Unit,
    onExit: () -> Unit,
    onOpenFullTrainer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        when (state.mode) {
            RelativePitchMode.LADDER -> Ladder(
                state = state,
                onExit = onExit,
                onSelect = { level ->
                    if (level.tier == RelativePitchTier.FUNCTION_HEARING || level.tier == RelativePitchTier.REPRODUCE) {
                        onOpenFullTrainer()
                    } else {
                        onIntent(RelativePitchIntent.SelectLevel(level.id))
                    }
                },
            )
            RelativePitchMode.PRACTICE -> Practice(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun Ladder(state: RelativePitchUiState, onExit: () -> Unit, onSelect: (RelativePitchLevel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Text(
                text = "Relative Pitch",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Start from the top of your ladder. Each level unlocks the next once you've " +
                    "answered it accurately enough.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
        }
        SecondaryButton(label = "Leave", onClick = onExit)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
        items(state.levels, key = { it.id }) { level ->
            LevelRow(
                level = level,
                status = state.statuses[level.id] ?: LevelStatus.LOCKED,
                stat = state.stats[level.id],
                onSelect = { onSelect(level) },
            )
        }
    }
}

@Composable
private fun LevelRow(level: RelativePitchLevel, status: LevelStatus, stat: LevelStat?, onSelect: () -> Unit) {
    val locked = status == LevelStatus.LOCKED
    HarmonyPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
                Text(
                    text = tierLabel(level.tier),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
                Text(
                    text = level.title,
                    color = if (locked) HarmonyTheme.colors.textTertiary else HarmonyTheme.colors.onSurface,
                    fontSize = HarmonyTheme.typography.body,
                    fontWeight = FontWeight.SemiBold,
                )
                if (stat != null && stat.attempts > 0) {
                    Text(
                        text = "${(stat.accuracy * PERCENT).toInt()}% over ${stat.attempts}",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
            StatusBadge(status)
        }
    }
}

@Composable
private fun StatusBadge(status: LevelStatus) {
    val (label, tone) = when (status) {
        LevelStatus.LOCKED -> "Locked" to FeedbackTone.NEUTRAL
        LevelStatus.AVAILABLE -> "Open" to FeedbackTone.PARTIAL
        LevelStatus.MASTERED -> "Mastered" to FeedbackTone.CORRECT
    }
    HarmonyStatusChip(label = label, tone = tone)
}

private fun tierLabel(tier: RelativePitchTier): String = when (tier) {
    RelativePitchTier.INTERVALS -> "Intervals"
    RelativePitchTier.SCALE_DEGREES -> "Scale degrees"
    RelativePitchTier.CHORD_QUALITY -> "Chord quality"
    RelativePitchTier.FUNCTION_HEARING -> "Function hearing"
    RelativePitchTier.REPRODUCE -> "Reproduction"
}

@Composable
private fun Practice(state: RelativePitchUiState, onIntent: (RelativePitchIntent) -> Unit) {
    val level = state.activeLevel ?: return
    val exercise = state.exercise

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = level.title,
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.progressLabel,
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
        }
        SecondaryButton(label = "Ladder", onClick = { onIntent(RelativePitchIntent.BackToLadder) })
    }

    if (exercise == null) {
        Text(
            text = "Couldn't build a question for this level. Try again from the ladder.",
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.body,
        )
        return
    }

    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium)) {
            Text(
                text = level.prompt,
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
            )
            PrimaryButton(
                label = when (state.phase) {
                    RelativePitchPhase.PLAYING -> "Playing…"
                    else -> if (state.canReplay) "Play again" else "Play"
                },
                onClick = { onIntent(RelativePitchIntent.Play) },
                enabled = state.phase != RelativePitchPhase.PLAYING,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
    ) {
        exercise.choiceIds.forEach { choiceId ->
            ChoiceButton(
                label = choiceLabel(level, choiceId),
                state = choiceState(state, exercise.correctChoiceId, choiceId),
                enabled = state.phase == RelativePitchPhase.ANSWERING,
                onClick = { onIntent(RelativePitchIntent.Answer(choiceId)) },
            )
        }
    }

    if (state.phase == RelativePitchPhase.FEEDBACK) {
        HarmonyStatusChip(
            label = if (state.wasCorrect == true) "Correct" else "Not yet — the right answer is highlighted",
            tone = if (state.wasCorrect == true) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
        )
        PrimaryButton(
            label = "Next",
            onClick = { onIntent(RelativePitchIntent.Next) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class ChoiceVisualState { NEUTRAL, SELECTED, CORRECT, WRONG }

private fun choiceState(state: RelativePitchUiState, correctId: String, choiceId: String): ChoiceVisualState =
    when {
        state.phase != RelativePitchPhase.FEEDBACK -> ChoiceVisualState.NEUTRAL
        choiceId == correctId -> ChoiceVisualState.CORRECT
        choiceId == state.selectedChoiceId -> ChoiceVisualState.WRONG
        else -> ChoiceVisualState.NEUTRAL
    }

@Composable
private fun ChoiceButton(label: String, state: ChoiceVisualState, enabled: Boolean, onClick: () -> Unit) {
    val colors = HarmonyTheme.colors
    val accent = when (state) {
        ChoiceVisualState.CORRECT -> colors.feedbackSuccess
        ChoiceVisualState.WRONG -> colors.feedbackError
        ChoiceVisualState.SELECTED, ChoiceVisualState.NEUTRAL -> colors.outline
    }
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)

    Box(
        modifier = Modifier
            .background(colors.surface, shape)
            .border(HarmonyTheme.spacing.hairline * (if (state == ChoiceVisualState.NEUTRAL) 1 else 2), accent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.small),
    ) {
        Text(
            text = label,
            color = if (enabled) colors.textPrimary else colors.textSecondary,
            fontSize = HarmonyTheme.typography.body,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun choiceLabel(level: RelativePitchLevel, choiceId: String): String = when (level.tier) {
    RelativePitchTier.INTERVALS -> level.intervalChoices.first { it.name == choiceId }.label
    RelativePitchTier.SCALE_DEGREES -> level.degreeChoices.first { it.name == choiceId }.label
    RelativePitchTier.CHORD_QUALITY -> level.qualityChoices.first { it.name == choiceId }.label
    RelativePitchTier.FUNCTION_HEARING, RelativePitchTier.REPRODUCE -> choiceId
}

private const val PERCENT = 100

@Preview(showBackground = true, widthDp = 1024, heightDp = 700)
@Composable
private fun RelativePitchLadderPreview() {
    HarmonyTheme {
        RelativePitchScreen(
            state = RelativePitchUiState(),
            onIntent = {},
            onExit = {},
            onOpenFullTrainer = {},
        )
    }
}
