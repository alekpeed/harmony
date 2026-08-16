package com.harmonygates.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.campaign.GateProgress
import com.harmonygates.core.music.campaign.GateStatus
import com.harmonygates.core.music.campaign.Unmet

/**
 * The campaign map.
 *
 * 02_GAME_LOOP_AND_PROGRESSION.md §10 leaves the visual metaphor to Figma and requires that "the
 * progression API must not depend on the final art metaphor". So this is a plain list of regions
 * and gates: honest about state, and easy to replace with a map, a constellation or a transit
 * diagram without any of the rules moving.
 */
@Composable
fun CampaignRoute(
    onPlayGate: (com.harmonygates.core.music.campaign.GateId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampaignViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CampaignScreen(state = state, onPlayGate = onPlayGate, modifier = modifier)
}

@Composable
fun CampaignScreen(
    state: CampaignUiState,
    onPlayGate: (com.harmonygates.core.music.campaign.GateId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Text(
            text = "Campaign",
            color = HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.heading,
        )

        state.error?.let { message ->
            HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    color = HarmonyTheme.colors.incorrect,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }

        if (state.loading) {
            Text(
                text = "Loading the curriculum.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
            return@Column
        }

        state.nextGate?.let { next ->
            ContinuePanel(next = next, onPlayGate = onPlayGate)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium)) {
            state.regions.filter { it.isVisible }.forEach { region ->
                item(key = region.id.value) { RegionHeader(region) }
                items(region.gates, key = { it.id.value }) { gate ->
                    GateRow(gate = gate, onPlayGate = onPlayGate)
                }
            }
            // Regions that exist but are not yet visible are counted rather than listed: a
            // player should know there is more without being shown a wall of locked doors.
            val hidden = state.regions.count { !it.isVisible }
            if (hidden > 0) {
                item(key = "hidden") {
                    Text(
                        text = if (hidden == 1) "One more region to unlock." else "$hidden more regions to unlock.",
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinuePanel(
    next: GateProgress,
    onPlayGate: (com.harmonygates.core.music.campaign.GateId) -> Unit,
) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = "Up next",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            Text(
                text = next.gate.title,
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
            )
            Text(
                text = next.gate.objective,
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
            Button(onClick = { onPlayGate(next.id) }) { Text("Play") }
        }
    }
}

@Composable
private fun RegionHeader(region: RegionProgress) {
    Column(
        modifier = Modifier.padding(top = HarmonyTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        Text(
            text = region.region.title,
            color = HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.body,
            fontWeight = FontWeight.SemiBold,
        )
        region.region.summary?.let {
            Text(
                text = it,
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun GateRow(
    gate: GateProgress,
    onPlayGate: (com.harmonygates.core.music.campaign.GateId) -> Unit,
) {
    val playable = gate.status.isPlayable
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonyTheme.colors.surface, RoundedCornerShape(12.dp))
            .then(if (playable) Modifier.clickable { onPlayGate(gate.id) } else Modifier)
            .padding(HarmonyTheme.spacing.medium)
            .semantics { contentDescription = "${gate.gate.title}, ${statusLabel(gate.status)}" },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = gate.gate.title,
                    color = if (playable) HarmonyTheme.colors.onSurface else HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
                HarmonyStatusChip(label = statusLabel(gate.status), tone = toneFor(gate.status))
            }

            if (gate.status == GateStatus.IN_PROGRESS) {
                LinearProgressIndicator(
                    progress = { gate.progress.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // What is left, in the player's terms. A locked gate says what to finish first;
            // an unfinished one says what the completion rule is still waiting for.
            val remaining = gate.remaining.take(MAX_REASONS).map { describe(it) }
            remaining.forEach { reason ->
                Text(
                    text = reason,
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
    }
}

private fun statusLabel(status: GateStatus): String = when (status) {
    GateStatus.LOCKED -> "Locked"
    GateStatus.AVAILABLE -> "Open"
    GateStatus.IN_PROGRESS -> "In progress"
    GateStatus.COMPLETE -> "Complete"
}

private fun toneFor(status: GateStatus): FeedbackTone = when (status) {
    GateStatus.COMPLETE -> FeedbackTone.CORRECT
    GateStatus.IN_PROGRESS -> FeedbackTone.PARTIAL
    GateStatus.AVAILABLE -> FeedbackTone.NEUTRAL
    GateStatus.LOCKED -> FeedbackTone.INCORRECT
}

/**
 * Says what is still missing, in words a player can act on.
 *
 * The domain reports the shortfall as numbers; turning them into sentences belongs next to the
 * screen, the same split `describe(PerformanceError)` already uses for musical diagnoses.
 */
private fun describe(unmet: Unmet): String = when (unmet) {
    Unmet.NoEvidence -> "Not started yet."
    is Unmet.NotEnoughAttempts -> "${unmet.have} of ${unmet.need} attempts so far."
    is Unmet.AccuracyBelowBar ->
        "Accuracy ${percent(unmet.have)}; this gate asks for ${percent(unmet.need)}."

    is Unmet.TooManyCriticalErrors ->
        "${unmet.have} chord-level mistakes recently; ${unmet.allowed} is the limit."

    is Unmet.NotEnoughRoots -> "Played on ${unmet.have} of ${unmet.need} required roots."
    is Unmet.TooSlow -> "Median response ${unmet.medianMillis} ms; the target is ${unmet.limitMillis} ms."
}

private fun percent(fraction: Double): String = "${(fraction * PERCENT).toInt()}%"

private const val PERCENT = 100
private const val MAX_REASONS = 2
