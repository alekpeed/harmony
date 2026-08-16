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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * Buttons, chips and sliders.
 *
 * All of them are at least [HarmonySpacingTokens.minimumTouchTarget] tall: 12 §2 asks for touch
 * controls that are "large and sparse", because the player is at arm's length from a tablet
 * propped behind a keyboard rather than holding a phone.
 */

/** The one action a screen most wants you to take. Filled, and there is rarely more than one. */
@Composable
public fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = HarmonyTheme.spacing.minimumTouchTarget)
            .background(if (enabled) colors.accentPrimary else colors.surfaceRaised, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HarmonyTheme.spacing.large, vertical = HarmonyTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.onAccent else colors.textTertiary,
            fontSize = HarmonyTheme.typography.title,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Everything else. Outlined rather than filled, so the primary action stays the loudest thing. */
@Composable
public fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = HarmonyTheme.spacing.minimumTouchTarget)
            .background(colors.surface, shape)
            .border(
                HarmonyTheme.spacing.hairline,
                if (enabled) colors.accentPrimary else colors.outline,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HarmonyTheme.spacing.large, vertical = HarmonyTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            fontSize = HarmonyTheme.typography.title,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A square control carrying a glyph.
 *
 * [description] is required rather than optional: an icon with no label is invisible to a
 * screen reader, and 12 §10 asks for TalkBack labels on controls.
 */
@Composable
public fun HarmonyIconButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HarmonyTheme.colors
    Box(
        modifier = modifier
            .size(HarmonyTheme.spacing.minimumTouchTarget)
            .background(colors.surface, RoundedCornerShape(HarmonyTheme.shapes.radiusMedium))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            fontSize = HarmonyTheme.typography.title,
        )
    }
}

/** One of several exclusive choices, all visible at once. */
@Composable
public fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty()) { "A segmented control needs at least one option" }
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusPill)

    Row(
        modifier = modifier
            .background(colors.surface, shape)
            .border(HarmonyTheme.spacing.hairline, colors.outline, shape)
            .padding(HarmonyTheme.spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = HarmonyTheme.spacing.minimumTouchTarget)
                    .background(if (selected) colors.accentPrimary else colors.surface, shape)
                    .clickable { onSelect(index) }
                    .padding(
                        horizontal = HarmonyTheme.spacing.medium,
                        vertical = HarmonyTheme.spacing.small,
                    )
                    .semantics {
                        contentDescription = "$option, ${if (selected) "selected" else "not selected"}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                    fontSize = HarmonyTheme.typography.label,
                )
            }
        }
    }
}

/** A toggle that narrows a list. Reads as pressed or not without relying on its fill colour. */
@Composable
public fun FilterChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusPill)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = HarmonyTheme.spacing.minimumTouchTarget)
            .background(if (selected) colors.surfaceOverlay else colors.surface, shape)
            .border(
                if (selected) HarmonyTheme.spacing.tight / 2 else HarmonyTheme.spacing.hairline,
                if (selected) colors.accentPrimary else colors.outline,
                shape,
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.small)
            .semantics { contentDescription = "$label filter, ${if (selected) "on" else "off"}" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = if (selected) "✓" else "＋", color = colors.accentPrimary)
        Text(text = label, color = colors.textPrimary, fontSize = HarmonyTheme.typography.label)
    }
}

/**
 * The difficulty control.
 *
 * 07 and 12 both treat difficulty as one dial over the assistance ladder rather than as named
 * presets, so the caller supplies the level names and this shows which rung is selected. The
 * name is displayed because "4 of 8" tells a player nothing about what changed.
 */
@Composable
public fun DifficultySlider(
    levels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(levels.isNotEmpty()) { "A difficulty slider needs at least one level" }
    val colors = HarmonyTheme.colors
    val clamped = selectedIndex.coerceIn(0, levels.lastIndex)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        Text(
            text = levels[clamped],
            color = colors.textPrimary,
            fontSize = HarmonyTheme.typography.title,
        )
        Slider(
            value = clamped.toFloat(),
            onValueChange = { onSelect(it.toInt().coerceIn(0, levels.lastIndex)) },
            valueRange = 0f..levels.lastIndex.toFloat(),
            // One step per rung: assistance levels are discrete, and a slider that stopped
            // between two of them would be asking for a level that does not exist.
            steps = (levels.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = colors.accentPrimary,
                activeTrackColor = colors.accentPrimary,
                inactiveTrackColor = colors.outline,
            ),
            modifier = Modifier.semantics {
                contentDescription = "Difficulty: ${levels[clamped]}, ${clamped + 1} of ${levels.size}"
            },
        )
    }
}
