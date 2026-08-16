package com.harmonygates.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.state.MidiPresentation
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * The frame every screen sits in.
 *
 * 12 §6 describes the exercise screen as "a shell with slots" rather than a layout, so the shell
 * takes its regions as content lambdas. A screen that wants no rail or no footer passes nothing
 * and gets the space back, which is how one shell serves the campaign map and a chord gate alike.
 */
@Composable
public fun AppShell(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    rail: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(HarmonyTheme.colors.backgroundBase)) {
        topBar()
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            rail()
            Box(modifier = Modifier.fillMaxHeight().weight(1f), content = content)
        }
        footer()
    }
}

/**
 * Gate and session information on the left, device and menu on the right.
 *
 * The MIDI chip lives here rather than on each screen because 12 §8 requires connection state to
 * be discoverable at all times, and a status that each screen had to remember to draw would
 * eventually be missing from one.
 */
@Composable
public fun TopStatusBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HarmonyTheme.colors.surface)
            .padding(horizontal = HarmonyTheme.spacing.large, vertical = HarmonyTheme.spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.title,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = HarmonyTheme.colors.textSecondary,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

/** The MIDI connection state, always visible. 12 §8. */
@Composable
public fun MidiStatusChip(
    state: MidiPresentation,
    modifier: Modifier = Modifier,
    deviceName: String? = null,
) {
    val colors = HarmonyTheme.colors
    val shape = RoundedCornerShape(HarmonyTheme.shapes.radiusPill)
    val accent = when (state) {
        MidiPresentation.CONNECTED -> colors.feedbackSuccess
        MidiPresentation.CONNECTING -> colors.feedbackWarning
        MidiPresentation.DISCONNECTED -> colors.feedbackNeutral
        MidiPresentation.ERROR -> colors.feedbackError
    }

    Row(
        modifier = modifier
            .background(colors.surfaceRaised, shape)
            .border(HarmonyTheme.spacing.hairline, accent, shape)
            .padding(horizontal = HarmonyTheme.spacing.medium, vertical = HarmonyTheme.spacing.small)
            .semantics { contentDescription = state.description(deviceName) },
        horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = state.glyph, color = accent)
        Text(
            text = deviceName?.takeIf { state == MidiPresentation.CONNECTED } ?: state.shortLabel,
            color = colors.textPrimary,
            fontSize = HarmonyTheme.typography.label,
        )
    }
}

/** One destination on the navigation rail. */
public data class RailDestination(
    val glyph: String,
    val label: String,
    val onSelect: () -> Unit,
)

/** The vertical navigation rail. Landscape tablet: destinations go down the side, not the bottom. */
@Composable
public fun NavigationRail(
    destinations: List<RailDestination>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            .background(HarmonyTheme.colors.surface)
            .padding(vertical = HarmonyTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
    ) {
        destinations.forEachIndexed { index, destination ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = HarmonyTheme.spacing.minimumTouchTarget)
                    .background(
                        if (selected) HarmonyTheme.colors.surfaceRaised else HarmonyTheme.colors.surface,
                        RoundedCornerShape(HarmonyTheme.shapes.radiusMedium),
                    )
                    .clickable(onClick = destination.onSelect)
                    .padding(HarmonyTheme.spacing.small)
                    .semantics {
                        contentDescription =
                            "${destination.label}${if (selected) ", current" else ""}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight),
            ) {
                Text(
                    text = destination.glyph,
                    color = if (selected) {
                        HarmonyTheme.colors.accentPrimary
                    } else {
                        HarmonyTheme.colors.textSecondary
                    },
                )
                Text(
                    text = destination.label,
                    color = if (selected) {
                        HarmonyTheme.colors.textPrimary
                    } else {
                        HarmonyTheme.colors.textSecondary
                    },
                    fontSize = HarmonyTheme.typography.caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The count before a timed task starts.
 *
 * Drawn over the screen rather than replacing it: 12 §9 says input must never be ignored because
 * a decorative animation is running, and a player who can still see the keyboard during the
 * count-in is a player who is ready when it ends.
 */
@Composable
public fun CountdownOverlay(
    count: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonyTheme.colors.scrim)
            .semantics { contentDescription = caption?.let { "$it, $count" } ?: count },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count,
                color = HarmonyTheme.colors.accentSecondary,
                fontSize = HarmonyTheme.typography.chordSymbol,
                fontWeight = FontWeight.Bold,
            )
            caption?.let {
                Text(
                    text = it,
                    color = HarmonyTheme.colors.textSecondary,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }
    }
}

/** A sheet rising from the bottom of the screen. Settings and pickers, not decisions. */
@Composable
public fun BottomSheet(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = HarmonyTheme.shapes.radiusLarge,
        topEnd = HarmonyTheme.shapes.radiusLarge,
        bottomStart = HarmonyTheme.shapes.radiusNone,
        bottomEnd = HarmonyTheme.shapes.radiusNone,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HarmonyTheme.colors.surfaceOverlay, shape)
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Text(
            text = title,
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.heading,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

/**
 * A decision that has to be made before anything else happens.
 *
 * Rare on purpose. 12 §2 asks for a game rather than a settings-heavy utility, and a dialog
 * stops the game.
 */
@Composable
public fun HarmonyDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().background(HarmonyTheme.colors.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(
                    HarmonyTheme.colors.surfaceOverlay,
                    RoundedCornerShape(HarmonyTheme.shapes.radiusLarge),
                )
                .padding(HarmonyTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
        ) {
            Text(
                text = title,
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = HarmonyTheme.colors.textSecondary,
                fontSize = HarmonyTheme.typography.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                if (dismissLabel != null && onDismiss != null) {
                    SecondaryButton(label = dismissLabel, onClick = onDismiss)
                }
                PrimaryButton(label = confirmLabel, onClick = onConfirm)
            }
        }
    }
}

private val RAIL_WIDTH = 96.dp
