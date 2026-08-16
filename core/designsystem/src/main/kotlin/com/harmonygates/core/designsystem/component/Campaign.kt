package com.harmonygates.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.state.GatePresentation
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/** The colour a gate state is drawn in. */
@Composable
public fun GatePresentation.color(): Color = when (this) {
    GatePresentation.LOCKED -> HarmonyTheme.colors.gateLocked
    GatePresentation.AVAILABLE -> HarmonyTheme.colors.gateAvailable
    GatePresentation.IN_PROGRESS -> HarmonyTheme.colors.gateInProgress
    GatePresentation.MASTERED -> HarmonyTheme.colors.gateMastered
}

/**
 * A gate in a list: title, objective and where the player has got to.
 *
 * The status appears as a glyph and a word as well as a colour. A locked gate is shown rather
 * than hidden — 02 §2 makes the campaign a map, and a map with the unvisited parts cut out is
 * not one.
 */
@Composable
public fun GateCard(
    title: String,
    objective: String,
    status: GatePresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)
    val accent = status.color()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shape)
            .border(HarmonyTheme.spacing.hairline, accent, shape)
            .clickable(enabled = status.isPlayable, onClick = onClick)
            .padding(HarmonyTheme.spacing.medium)
            .semantics { contentDescription = "$title. $objective. ${status.shortLabel}." },
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = HarmonyTheme.typography.title,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = status.glyph, color = accent)
                Text(
                    text = status.shortLabel,
                    color = colors.textSecondary,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
        Text(
            text = objective,
            color = colors.textSecondary,
            fontSize = HarmonyTheme.typography.body,
        )
        progress?.let { ProgressMeter(fraction = it, label = "Mastery") }
    }
}

/**
 * A gate as a node on the campaign map.
 *
 * The same four states as [GateCard], drawn small enough to sit on a painted background. The
 * label is beside the node rather than inside it, because a node big enough to hold readable
 * text would cover the artwork it is standing on.
 */
@Composable
public fun GateNode(
    label: String,
    status: GatePresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = status.color()
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = HarmonyTheme.spacing.minimumTouchTarget)
            .clickable(enabled = status.isPlayable, onClick = onClick)
            .semantics { contentDescription = "$label, ${status.shortLabel}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        Box(
            modifier = Modifier
                .size(NODE_DIAMETER)
                .background(
                    if (status == GatePresentation.LOCKED) {
                        HarmonyTheme.colors.surface
                    } else {
                        accent
                    },
                    CircleShape,
                )
                .border(HarmonyTheme.spacing.tight / 2, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = status.glyph,
                color = if (status == GatePresentation.LOCKED) {
                    accent
                } else {
                    HarmonyTheme.colors.onAccent
                },
                fontSize = HarmonyTheme.typography.caption,
            )
        }
        Text(
            text = label,
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.caption,
        )
    }
}

/**
 * A horizontal meter.
 *
 * [fraction] is clamped rather than checked: a mastery value slightly over one because of
 * rounding should draw a full bar, not crash a campaign screen.
 */
@Composable
public fun ProgressMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusPill)
    val percent = (clamped * PERCENT).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${label ?: "Progress"}: $percent percent" },
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        label?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = it, color = colors.textSecondary, fontSize = HarmonyTheme.typography.caption)
                Text(
                    text = "$percent%",
                    color = colors.textSecondary,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(METER_HEIGHT)
                .background(colors.surface, shape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(METER_HEIGHT)
                    .background(colors.accentPrimary, shape),
            )
        }
    }
}

/** A skill and how well it is held. Small enough to sit several to a row under a gate. */
@Composable
public fun SkillBadge(
    name: String,
    mastery: Float,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonyTheme.colors
    val clamped = mastery.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusSmall)
    // Filled pips rather than a bar: at this size a bar is a smear, and four pips can be counted.
    val filled = (clamped * PIPS).toInt().coerceIn(0, PIPS)

    Row(
        modifier = modifier
            .background(colors.surface, shape)
            .border(HarmonyTheme.spacing.hairline, colors.outline, shape)
            .padding(horizontal = HarmonyTheme.spacing.small, vertical = HarmonyTheme.spacing.tight)
            .semantics { contentDescription = "$name, $filled of $PIPS" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, color = colors.textPrimary, fontSize = HarmonyTheme.typography.caption)
        Text(
            text = "●".repeat(filled) + "○".repeat(PIPS - filled),
            color = colors.accentPrimary,
            fontSize = HarmonyTheme.typography.caption,
        )
    }
}

/** What a session came to. Shown after scoring, which is when 12 §9 allows a richer moment. */
@Composable
public fun ResultCard(
    title: String,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    HarmonyPanel(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
            Text(
                text = title,
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            lines.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        color = HarmonyTheme.colors.textSecondary,
                        fontSize = HarmonyTheme.typography.body,
                    )
                    Text(
                        text = value,
                        color = HarmonyTheme.colors.textPrimary,
                        fontSize = HarmonyTheme.typography.body,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            footer()
        }
    }
}

private val NODE_DIAMETER = 40.dp
private val METER_HEIGHT = 10.dp
private const val PERCENT = 100
private const val PIPS = 4
