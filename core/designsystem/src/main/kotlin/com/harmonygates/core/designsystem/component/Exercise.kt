package com.harmonygates.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.state.FeedbackPresentation
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * The header of the exercise shell: which gate, how far through, and what is being asked.
 *
 * 12 §6 puts gate and session information top-left and device state top-right; this is the
 * left-hand half, and [TopStatusBar] carries the right.
 */
@Composable
public fun ExerciseHeader(
    gateTitle: String,
    instruction: String,
    modifier: Modifier = Modifier,
    position: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HarmonyTheme.spacing.large, vertical = HarmonyTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = gateTitle,
                color = HarmonyTheme.colors.textSecondary,
                fontSize = HarmonyTheme.typography.caption,
            )
            position?.let {
                Text(
                    text = it,
                    color = HarmonyTheme.colors.textSecondary,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
        Text(
            text = instruction,
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.title,
        )
    }
}

/**
 * The chord symbol and, optionally, the shape being asked for.
 *
 * The voicing name sits under the symbol rather than in it: `Cmaj7` is what the chord is called
 * and "Rootless A" is what you are being asked to do with it, and running the two together would
 * teach a player that the shape is part of the name.
 */
@Composable
public fun ChordSymbolDisplay(
    symbol: String,
    modifier: Modifier = Modifier,
    voicingName: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
    ) {
        HarmonyChordSymbol(symbol = symbol)
        voicingName?.let {
            Text(
                text = it,
                color = HarmonyTheme.colors.textSecondary,
                fontSize = HarmonyTheme.typography.body,
            )
        }
    }
}

/**
 * A roman numeral with its key.
 *
 * Takes both as text. Working out that `Dm7` is `ii` in C is a music decision and belongs in
 * `core:music`; this draws what it is handed.
 */
@Composable
public fun RomanNumeralDisplay(
    numeral: String,
    keyName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "$numeral in $keyName" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = numeral,
            color = HarmonyTheme.colors.accentSecondary,
            fontSize = HarmonyTheme.typography.heading,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "in $keyName",
            color = HarmonyTheme.colors.textSecondary,
            fontSize = HarmonyTheme.typography.body,
        )
    }
}

/** The spelled note names of the current chord. An assistance layer, shown only when enabled. */
@Composable
public fun NoteNameStrip(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "Notes: ${names.joinToString(", ")}" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
    ) {
        names.forEach { name ->
            Text(
                text = name,
                modifier = Modifier
                    .background(
                        HarmonyTheme.colors.surfaceRaised,
                        RoundedCornerShape(HarmonyTheme.shapes.radiusSmall),
                    )
                    .padding(
                        horizontal = HarmonyTheme.spacing.small,
                        vertical = HarmonyTheme.spacing.tight,
                    ),
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.body,
            )
        }
    }
}

/**
 * The verdict and what to do about it.
 *
 * Sits below the keyboard rather than over it — 12 §2 asks for feedback that does not cover the
 * keyboard or staff, because a player reading why they were wrong needs to see the keys they
 * were wrong on.
 */
@Composable
public fun FeedbackPanel(
    tone: FeedbackPresentation,
    headline: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val accent = tone.color()
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusMedium)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HarmonyTheme.colors.surface, shape)
            .border(HarmonyTheme.spacing.hairline, accent, shape)
            .padding(HarmonyTheme.spacing.medium)
            .semantics {
                contentDescription = listOfNotNull(tone.shortLabel, headline, detail).joinToString(". ")
            },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tone.glyph,
            color = accent,
            fontSize = HarmonyTheme.typography.heading,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
        ) {
            Text(
                text = headline,
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.title,
            )
            detail?.let {
                Text(
                    text = it,
                    color = HarmonyTheme.colors.textSecondary,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }
        actions()
    }
}

/**
 * The metronome, as a beat position rather than an animation.
 *
 * [beat] is one-based and [beatsPerBar] is the meter, so 6/8 shows six pips. The caller drives
 * it from the same clock the exercise is timed by; nothing here keeps time, because a widget
 * with its own clock would drift away from the one that decides whether a note was late.
 */
@Composable
public fun MetronomeIndicator(
    beat: Int,
    beatsPerBar: Int,
    modifier: Modifier = Modifier,
) {
    require(beatsPerBar > 0) { "A bar needs at least one beat" }
    Row(
        modifier = modifier.semantics { contentDescription = "Beat $beat of $beatsPerBar" },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..beatsPerBar).forEach { position ->
            val current = position == beat
            Box(
                modifier = Modifier
                    .size(if (position == 1) DOWNBEAT_DIAMETER else BEAT_DIAMETER)
                    .background(
                        if (current) {
                            HarmonyTheme.colors.accentSecondary
                        } else {
                            HarmonyTheme.colors.outline
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * What the exercise is currently giving away.
 *
 * 07's assistance ladder is a rung, not a switch, so this shows the rung by name and by how many
 * of the pips are filled. A player who cannot tell how much help they are getting cannot tell
 * what they have actually learned.
 */
@Composable
public fun AssistanceIndicator(
    levelName: String,
    level: Int,
    levelCount: Int,
    modifier: Modifier = Modifier,
) {
    require(levelCount > 0) { "An assistance ladder needs at least one rung" }
    val clamped = level.coerceIn(0, levelCount)
    Row(
        modifier = modifier
            .background(
                HarmonyTheme.colors.surface,
                RoundedCornerShape(HarmonyTheme.shapes.radiusPill),
            )
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.tight)
            .semantics {
                contentDescription = "Assistance: $levelName, level $clamped of $levelCount"
            },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = levelName,
            color = HarmonyTheme.colors.textSecondary,
            fontSize = HarmonyTheme.typography.caption,
        )
        Text(
            text = "▮".repeat(clamped) + "▯".repeat(levelCount - clamped),
            color = HarmonyTheme.colors.accentPrimary,
            fontSize = HarmonyTheme.typography.caption,
        )
    }
}

private val BEAT_DIAMETER = 10.dp
private val DOWNBEAT_DIAMETER = 14.dp
