package com.harmonygates.progression

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.progression.OrbState
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.core.music.performance.FeedbackModel
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.exercise.describe
import com.harmonygates.home.HomeDestination

/**
 * Progression Run, plain.
 *
 * This used to draw the room plate with the moving orb track over it and hide every control in
 * two transient drawers. The plate and `interface/maps/progression-run.json` are untouched — the
 * map is still what [ProgressionRunViewModel] reads its slot window from — but the visual design
 * is being reworked, so the run is shown as a lane of chord chips with its controls on the page.
 *
 * The run engine, the MIDI capture and the judging are unchanged: none of that ever lived in the
 * artwork.
 */
@Composable
fun ProgressionRunRoute(
    modifier: Modifier = Modifier,
    onNavigate: (HomeDestination) -> Unit = {},
    viewModel: ProgressionRunViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressionRunScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigate = onNavigate,
        modifier = modifier,
    )
}

@Composable
fun ProgressionRunScreen(
    state: ProgressionRunUiState,
    onIntent: (ProgressionRunIntent) -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSetup by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Header(
            state = state,
            showSetup = showSetup,
            onToggleSetup = { showSetup = !showSetup },
            onNavigate = onNavigate,
        )

        Lane(state)

        NowPlaying(state)

        Transport(state, onIntent)

        state.lastResult?.let { Feedback(it.explanation) }

        if (showSetup) Setup(state, onIntent)
    }
}

@Composable
private fun Header(
    state: ProgressionRunUiState,
    showSetup: Boolean,
    onToggleSetup: () -> Unit,
    onNavigate: (HomeDestination) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Progression Run",
            color = HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.heading,
            fontWeight = FontWeight.SemiBold,
        )
        HarmonyStatusChip(
            label = state.midiStatus,
            tone = if (state.midiConnected) FeedbackTone.CORRECT else FeedbackTone.INCORRECT,
        )
        Box(Modifier.weight(1f))
        SecondaryButton(
            label = if (showSetup) "Hide setup" else "Setup",
            onClick = onToggleSetup,
        )
        SecondaryButton(label = "Settings", onClick = { onNavigate(HomeDestination.Settings) })
        SecondaryButton(label = "Leave", onClick = { onNavigate(HomeDestination.Home) })
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large),
    ) {
        HarmonyLabelledValue(
            label = "Progress",
            value = state.progressLabel.ifBlank { "Ready" },
            modifier = Modifier.weight(1f),
        )
        HarmonyLabelledValue(
            label = "Accuracy",
            value = "${state.accuracyPercent}%",
            modifier = Modifier.weight(1f),
        )
        HarmonyLabelledValue(
            label = "Elapsed",
            value = state.elapsedLabel,
            modifier = Modifier.weight(1f),
        )
        HarmonyLabelledValue(
            label = "Clean / runs",
            value = "${state.run.clean} / ${state.runsCompleted}",
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The run, left to right.
 *
 * The engine hands over a window of chords around the active one, each carrying the slot it sits
 * in — negative behind, zero active, positive ahead. Sorting by that slot is all the ordering the
 * lane needs, and it is the same number the orb track used to position a circle with.
 */
@Composable
private fun Lane(state: ProgressionRunUiState) {
    if (state.orbs.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.orbs.sortedBy { it.slot }, key = { it.eventId }) { orb ->
            ChordChip(
                symbol = orb.chordSymbol,
                functionLabel = orb.functionLabel,
                state = orb.state,
            )
        }
    }
}

@Composable
private fun ChordChip(symbol: String, functionLabel: String?, state: OrbState) {
    val colors = HarmonyTheme.colors
    val accent = when (state) {
        OrbState.ACTIVE -> colors.accentPrimary
        OrbState.CORRECT -> colors.feedbackSuccess
        OrbState.INCORRECT -> colors.feedbackError
        OrbState.PREVIOUS -> colors.outline
        OrbState.UPCOMING -> colors.outline
    }
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)

    Column(
        modifier = Modifier
            .background(
                if (state == OrbState.ACTIVE) colors.surfaceOverlay else colors.surface,
                shape,
            )
            .border(HarmonyTheme.spacing.hairline, accent, shape)
            .padding(
                horizontal = HarmonyTheme.spacing.medium,
                vertical = HarmonyTheme.spacing.small,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        Text(
            text = symbol,
            color = if (state == OrbState.UPCOMING) colors.textSecondary else colors.textPrimary,
            fontSize = HarmonyTheme.typography.title,
            fontWeight = FontWeight.SemiBold,
        )
        functionLabel?.let {
            Text(text = it, color = colors.textSecondary, fontSize = HarmonyTheme.typography.caption)
        }
    }
}

@Composable
private fun NowPlaying(state: ProgressionRunUiState) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = if (state.countingIn) "Counting in" else "Play",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.caption,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HarmonyChordSymbol(symbol = state.activeSymbol ?: "—")
                state.activeFunction?.let {
                    Text(
                        text = it,
                        color = HarmonyTheme.colors.onSurfaceMuted,
                        fontSize = HarmonyTheme.typography.body,
                    )
                }
            }
            Text(
                text = state.instruction
                    ?: "${state.setup.template.title} · ${keyLabel(state)} · ${state.setup.tempoBpm} BPM",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
        }
    }
}

@Composable
private fun Transport(state: ProgressionRunUiState, onIntent: (ProgressionRunIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton(
            label = if (state.run.status == ProgressionRunStatus.RUNNING) "Pause" else "Play",
            onClick = { onIntent(ProgressionRunIntent.TogglePlaying) },
        )
        SecondaryButton(label = "Back", onClick = { onIntent(ProgressionRunIntent.Previous) })
        SecondaryButton(label = "Skip", onClick = { onIntent(ProgressionRunIntent.Next) })
        SecondaryButton(label = "Restart", onClick = { onIntent(ProgressionRunIntent.Restart) })
    }
}

@Composable
private fun Feedback(explanation: FeedbackModel) {
    val tone = when (explanation.headline) {
        FeedbackModel.Headline.CORRECT, FeedbackModel.Headline.CORRECT_VARIATION -> FeedbackTone.CORRECT
        FeedbackModel.Headline.ALMOST -> FeedbackTone.PARTIAL
        FeedbackModel.Headline.NOT_YET -> FeedbackTone.INCORRECT
        else -> FeedbackTone.NEUTRAL
    }
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            HarmonyStatusChip(label = headlineText(explanation.headline), tone = tone)
            explanation.errors.take(MAX_REASONS).forEach { error ->
                Text(
                    text = describe(error),
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun Setup(state: ProgressionRunUiState, onIntent: (ProgressionRunIntent) -> Unit) {
    HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            SectionLabel("Progression")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            ) {
                ProgressionTemplates.all.forEach { template ->
                    FilterChip(
                        label = template.title,
                        selected = template == state.setup.template,
                        onToggle = { onIntent(ProgressionRunIntent.ChooseTemplate(template)) },
                    )
                }
            }

            SectionLabel("Voicing")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            ) {
                VoicingStyle.entries.forEach { style ->
                    FilterChip(
                        label = style.label,
                        selected = style == state.setup.style,
                        onToggle = { onIntent(ProgressionRunIntent.ChooseStyle(style)) },
                    )
                }
            }

            SectionLabel("Key")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            ) {
                FilterChip(
                    label = "All 12 keys",
                    selected = state.setup.allKeys,
                    onToggle = { onIntent(ProgressionRunIntent.ToggleAllKeys) },
                )
                state.availableKeys.forEach { key ->
                    FilterChip(
                        label = key,
                        selected = !state.setup.allKeys && state.setup.key.toString() == key,
                        onToggle = { onIntent(ProgressionRunIntent.ChooseKey(key)) },
                    )
                }
            }

            SectionLabel("Tempo")
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.setup.tempoBpm} BPM",
                    color = HarmonyTheme.colors.onSurface,
                    fontSize = HarmonyTheme.typography.body,
                    fontWeight = FontWeight.SemiBold,
                )
                // The engine steps by five and wraps at the top rather than clamping, so one
                // button covers the whole range without a second one to walk back down.
                SecondaryButton(
                    label = "+5 BPM",
                    onClick = { onIntent(ProgressionRunIntent.IncreaseTempo) },
                )
            }

            SectionLabel("Run")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            ) {
                FilterChip(
                    label = "Loop",
                    selected = state.setup.loop,
                    onToggle = { onIntent(ProgressionRunIntent.ToggleLoop) },
                )
                FilterChip(
                    label = "Roman numerals",
                    selected = state.setup.showRomanNumerals,
                    onToggle = { onIntent(ProgressionRunIntent.ToggleRomanNumerals) },
                )
                FilterChip(
                    label = "Count in",
                    selected = state.countIn,
                    onToggle = { onIntent(ProgressionRunIntent.ToggleCountIn) },
                )
                FilterChip(
                    label = "Auto-start next run",
                    selected = state.autoNextGate,
                    onToggle = { onIntent(ProgressionRunIntent.ToggleAutoNextGate) },
                )
            }

            if (state.countIn) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        label = "−",
                        onClick = { onIntent(ProgressionRunIntent.DecreaseCountBars) },
                    )
                    Text(
                        text = "${state.countBars} bars",
                        color = HarmonyTheme.colors.onSurface,
                        fontSize = HarmonyTheme.typography.body,
                    )
                    SecondaryButton(
                        label = "+",
                        onClick = { onIntent(ProgressionRunIntent.IncreaseCountBars) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        color = HarmonyTheme.colors.onSurfaceMuted,
        fontSize = HarmonyTheme.typography.caption,
    )
}

private fun headlineText(headline: FeedbackModel.Headline): String = when (headline) {
    FeedbackModel.Headline.CORRECT -> "Correct"
    FeedbackModel.Headline.CORRECT_VARIATION -> "Correct"
    FeedbackModel.Headline.ALMOST -> "Almost"
    FeedbackModel.Headline.NOT_YET -> "Not yet"
    FeedbackModel.Headline.NOTHING_PLAYED -> "Nothing played"
    FeedbackModel.Headline.DEVICE_LOST -> "Keyboard disconnected"
}

private fun keyLabel(state: ProgressionRunUiState): String =
    if (state.setup.allKeys) "12 keys" else state.setup.key.toString()

private const val MAX_REASONS = 3

@Preview(showBackground = true, widthDp = 1024, heightDp = 700)
@Composable
private fun ProgressionRunScreenPreview() {
    HarmonyTheme {
        ProgressionRunScreen(
            state = ProgressionRunUiState(),
            onIntent = {},
            onNavigate = {},
        )
    }
}
