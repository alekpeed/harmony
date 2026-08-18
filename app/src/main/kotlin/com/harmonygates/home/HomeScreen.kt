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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * Home, plain.
 *
 * This used to draw the approved 1536 x 1024 plate with invisible hit regions over its painted
 * tiles. The artwork is still in `interface/` and the map still describes where every control
 * sits, but the visual design is being reworked, so the screen is a list of what the app does
 * rather than a picture of it.
 *
 * The entries come from [HomeAction], the same enum the artwork regions were keyed to, so
 * navigation behaviour is unchanged and the artwork can be reinstated by rendering that enum
 * through `ArtworkScreen` again — nothing about routing has to be rebuilt.
 */
@Composable
fun HomeScreen(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Several actions lead to the same place — three of them open the campaign — so the home
    // list shows each destination once rather than repeating a door because the artwork had two
    // handles on it.
    val entries = remember {
        HomeAction.entries
            .filter { it.destination != HomeDestination.Home }
            .distinctBy { it.destination }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
            Text(
                text = "Harmony Gates",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Plug in a MIDI keyboard and pick something to practise.",
                color = HarmonyTheme.colors.onSurfaceMuted,
                fontSize = HarmonyTheme.typography.body,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TILE_MINIMUM),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        ) {
            items(entries, key = { it.name }) { action ->
                HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        label = action.label,
                        onClick = { onAction(action) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private val TILE_MINIMUM = 240.dp

@Preview(showBackground = true, widthDp = 1024, heightDp = 700)
@Composable
private fun HomeScreenPreview() {
    HarmonyTheme { HomeScreen(onAction = {}) }
}
