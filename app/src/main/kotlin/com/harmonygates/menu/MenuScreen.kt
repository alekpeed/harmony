package com.harmonygates.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.PrimaryButton
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import com.harmonygates.home.HomeDestination

/** One way in, listed. */
data class MenuEntry(
    val label: String,
    val summary: String,
    val destination: HomeDestination,
)

/**
 * Everything the app can do, on one screen.
 *
 * The home artwork's own tiles are the primary way in, but they are painted into a picture and a
 * new destination cannot appear there without a new export. This list is generated from
 * [HomeDestination] instead, so a module that becomes reachable becomes reachable here on the
 * same commit.
 */
val MenuEntries: List<MenuEntry> = listOf(
    MenuEntry("Campaign", "The gates, in curriculum order", HomeDestination.Campaign),
    MenuEntry("Chord gates", "A symbol appears; play it", HomeDestination.ChordGate),
    MenuEntry("Quick practice", "The same loop, no gate around it", HomeDestination.QuickPractice),
    MenuEntry("Ear training", "Hear a chord and play it back", HomeDestination.EarTraining),
    MenuEntry("Sight reading", "Read a line and play it in time", HomeDestination.SightReading),
    MenuEntry("Progression run", "A progression comes towards you", HomeDestination.ProgressionLab),
    MenuEntry("Voice leading", "Move with the least motion that works", HomeDestination.VoicingLab),
    MenuEntry("Daily challenge", "One exercise, drawn from your weakest skill", HomeDestination.DailyChallenge),
    MenuEntry("Library", "The chord and progression vocabulary", HomeDestination.Library),
    MenuEntry("Progress", "Mastery and attempt history", HomeDestination.Progress),
    MenuEntry("Theory lab", "Type a chord, see how it is spelled", HomeDestination.TheoryLab),
    MenuEntry("Settings", "MIDI, and progress export/import", HomeDestination.Settings),
)

@Composable
fun MenuScreen(
    onNavigate: (HomeDestination) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Everything",
                color = HarmonyTheme.colors.onSurface,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
            )
            Box(Modifier.weight(1f))
            SecondaryButton(label = "Home", onClick = onExit)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TILE_MINIMUM),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small),
        ) {
            items(MenuEntries) { entry ->
                HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.tight)) {
                        Text(
                            text = entry.summary,
                            color = HarmonyTheme.colors.onSurfaceMuted,
                            fontSize = HarmonyTheme.typography.caption,
                        )
                        PrimaryButton(
                            label = entry.label,
                            onClick = { onNavigate(entry.destination) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private val TILE_MINIMUM = 260.dp

@Preview(showBackground = true, widthDp = 1024, heightDp = 683)
@Composable
private fun MenuPreview() {
    HarmonyTheme { MenuScreen({}, {}) }
}
