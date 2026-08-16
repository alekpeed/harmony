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
import com.harmonygates.core.designsystem.state.FeedbackPresentation
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * The base components every screen is built from.
 *
 * 12_UI_UX_AND_FIGMA_HANDOFF.md §5 lists the components Figma and Compose should both define;
 * these and the ones in `Controls.kt`, `Shell.kt`, `Campaign.kt` and `Exercise.kt` are that
 * list. They read tokens rather than literals, so the Figma pass changes `Tokens.kt` and not
 * these files.
 */

/** How an answer was judged. The old name for [FeedbackPresentation], kept for call sites. */
public typealias FeedbackTone = FeedbackPresentation

/** A raised panel. The app's basic content container. */
@Composable
public fun HarmonyPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)
    Column(
        modifier = modifier
            .background(HarmonyTheme.colors.surfaceRaised, shape)
            .border(HarmonyTheme.spacing.hairline, HarmonyTheme.colors.outline, shape)
            .padding(HarmonyTheme.spacing.medium),
    ) {
        content()
    }
}

/** The colour a feedback tone is drawn in. One place, so a verdict never disagrees with itself. */
@Composable
public fun FeedbackPresentation.color(): Color = when (this) {
    FeedbackPresentation.CORRECT -> HarmonyTheme.colors.feedbackSuccess
    FeedbackPresentation.PARTIAL -> HarmonyTheme.colors.feedbackWarning
    FeedbackPresentation.INCORRECT -> HarmonyTheme.colors.feedbackError
    FeedbackPresentation.NEUTRAL -> HarmonyTheme.colors.feedbackNeutral
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
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusPill)
    val accent = tone.color()

    Row(
        modifier = modifier
            .background(HarmonyTheme.colors.surface, shape)
            .border(HarmonyTheme.spacing.hairline, accent, shape)
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.small)
            .semantics { contentDescription = "$label, ${tone.shortLabel.lowercase()}" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = tone.glyph, color = accent, fontWeight = FontWeight.Bold)
        Text(text = label, color = HarmonyTheme.colors.textPrimary)
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
        color = HarmonyTheme.colors.textPrimary,
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
            color = HarmonyTheme.colors.textSecondary,
            fontSize = HarmonyTheme.typography.caption,
        )
        Text(
            text = value,
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.body,
        )
    }
}
