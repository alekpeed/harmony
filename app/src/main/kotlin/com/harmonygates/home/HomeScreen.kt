package com.harmonygates.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.harmonygates.core.designsystem.artwork.ArtworkScreen
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * The home screen.
 *
 * Two presentations of the same contract. When the approved artwork from `interface/` is
 * present, the screen *is* the artwork with invisible controls over its named regions. Until
 * then it lists the same actions plainly.
 *
 * The fallback is not a guess at the approved design. 16_AGENT_EXECUTION_PROTOCOL.md §8 is
 * explicit that prose must not be approximated once a Figma source of truth exists, so this
 * makes no attempt to look like the real home screen — it just works, and says what it is
 * waiting for. Swapping in the artwork changes no behaviour, only how it is drawn.
 */
@Composable
fun HomeScreen(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkResId = HomeArtwork.drawableResId
    if (artworkResId != null && HomeArtwork.regionBounds.isNotEmpty()) {
        ArtworkScreen(
            artwork = painterResource(artworkResId),
            spec = HomeArtwork.spec,
            onRegionClick = { regionId -> HomeAction.forRegion(regionId)?.let(onAction) },
            modifier = modifier,
            contentDescription = "Harmony Gates home",
        )
    } else {
        HomeActionList(onAction = onAction, modifier = modifier)
    }
}

@Composable
private fun HomeActionList(
    onAction: (HomeAction) -> Unit,
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
            text = "Harmony Gates",
            color = HarmonyTheme.colors.onSurface,
            fontSize = HarmonyTheme.typography.heading,
        )

        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                HarmonyStatusChip(label = "Approved artwork pending", tone = FeedbackTone.NEUTRAL)
                Text(
                    text = "Every control below is already wired to its Figma hit region. " +
                        "Drop the approved 1536x1024 export into res/drawable-nodpi and fill in " +
                        "HomeArtwork.regionBounds, and this screen becomes the artwork with no " +
                        "other change.",
                    color = HarmonyTheme.colors.onSurfaceMuted,
                    fontSize = HarmonyTheme.typography.caption,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = MINIMUM_TILE_WIDTH_DP),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        ) {
            items(HomeAction.entries.toList(), key = { it.name }) { action ->
                if (action.destination.isImplemented) {
                    Button(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth()) {
                        Text(action.label)
                    }
                } else {
                    OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${action.label} · phase ${action.destination.arrivesInPhase}")
                    }
                }
            }
        }
    }
}

private val MINIMUM_TILE_WIDTH_DP = androidx.compose.ui.unit.Dp(220f)

@Preview(showBackground = true, widthDp = 1024, heightDp = 700)
@Composable
private fun HomeScreenPreview() {
    HarmonyTheme {
        HomeScreen(onAction = {})
    }
}
