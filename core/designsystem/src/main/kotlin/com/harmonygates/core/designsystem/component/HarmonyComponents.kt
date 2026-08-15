package com.harmonygates.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * Component contracts for the pre-Figma phases.
 *
 * These exist so features can be built with stable state contracts before the visual system is
 * designed (20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §7). Phase 12 refines the implementations and
 * leaves the signatures alone.
 */

/** A raised panel. The app's basic content container. */
@Composable
public fun HarmonyPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(HarmonyTheme.colors.surfaceRaised, RoundedCornerShape(12.dp))
            .border(
                HarmonyTheme.spacing.hairline,
                HarmonyTheme.colors.outline,
                RoundedCornerShape(12.dp),
            )
            .padding(HarmonyTheme.spacing.medium),
    ) {
        content()
    }
}

/** How an answer was judged. Mirrors the verdicts in 06_PERFORMANCE_EVALUATION_AND_SCORING.md §2. */
public enum class FeedbackTone {
    CORRECT,
    INCORRECT,
    PARTIAL,
    NEUTRAL,
}

/**
 * A labelled status chip.
 *
 * Carries a glyph and a word as well as a colour, because 18_ACCEPTANCE_CRITERIA.md forbids
 * representing correctness by colour alone. A colour-blind player and a screen-reader user get
 * the same information as everyone else.
 */
@Composable
public fun HarmonyStatusChip(
    label: String,
    tone: FeedbackTone,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonyTheme.colors
    val (accent: Color, glyph: String) = when (tone) {
        FeedbackTone.CORRECT -> colors.correct to "✓"
        FeedbackTone.INCORRECT -> colors.incorrect to "✕"
        FeedbackTone.PARTIAL -> colors.partial to "~"
        FeedbackTone.NEUTRAL -> colors.awaitingInput to "•"
    }

    Row(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(999.dp))
            .border(HarmonyTheme.spacing.hairline, accent, RoundedCornerShape(999.dp))
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.small)
            .semantics { contentDescription = "$label, ${tone.name.lowercase()}" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = glyph, color = accent, fontWeight = FontWeight.Bold)
        Text(text = label, color = colors.onSurface)
    }
}

/**
 * A chord symbol, sized to be read from a keyboard bench.
 *
 * Takes the symbol as text rather than a domain type on purpose: the design system must not
 * depend on `core:music`, so it never gets the chance to make a theory decision.
 */
@Composable
public fun HarmonyChordSymbol(
    symbol: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = symbol,
        modifier = modifier.semantics { contentDescription = "Chord symbol $symbol" },
        color = HarmonyTheme.colors.onSurface,
        fontSize = HarmonyTheme.typography.chordSymbol,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A labelled row of read-only values, e.g. the tones of a chord. */
@Composable
public fun HarmonyLabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = HarmonyTheme.spacing.minimumTouchTarget)
            .padding(vertical = HarmonyTheme.spacing.tight),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        Text(
            text = label,
            color = HarmonyTheme.colors.onSurfaceMuted,
            fontSize = HarmonyTheme.typography.caption,
        )
        Text(
            text = value,
            color = HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.body,
        )
    }
}
