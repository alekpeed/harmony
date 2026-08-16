package com.harmonygates.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.harmonygates.core.designsystem.component.FeedbackTone
import com.harmonygates.core.designsystem.component.HarmonyPanel
import com.harmonygates.core.designsystem.component.HarmonyStatusChip
import com.harmonygates.core.designsystem.component.SecondaryButton
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * A room that has been built but not furnished.
 *
 * Every control on the home screen leads somewhere. Before this existed, thirteen of the twenty
 * regions raised a snackbar saying "arrives in phase 8" and left the player where they were,
 * which is indistinguishable from a broken button — you cannot tell a control that is waiting
 * from one that is dead by tapping it.
 *
 * So each of them now opens a real screen that says what it will be, what is already built
 * behind it, and how to get back. That is worth more than the snackbar for testing, too: a
 * person can walk the whole interface and confirm that every region is wired, which is not
 * something a message that vanishes after four seconds lets you do.
 *
 * This makes no attempt to look like the screen it stands in for. 16_AGENT_EXECUTION_PROTOCOL.md
 * §8 forbids approximating a design that has a Figma source of truth, and a convincing mock is
 * worse than an honest gap: it gets screenshotted, and then it gets built.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    summary: String,
    engineStatus: String,
    arrivesInPhase: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(HarmonyTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = HarmonyTheme.colors.textPrimary,
            fontSize = HarmonyTheme.typography.display,
            fontWeight = FontWeight.SemiBold,
        )

        HarmonyStatusChip(label = "Screen arrives in phase $arrivesInPhase", tone = FeedbackTone.NEUTRAL)

        HarmonyPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.small)) {
                Text(
                    text = summary,
                    color = HarmonyTheme.colors.textPrimary,
                    fontSize = HarmonyTheme.typography.title,
                )
                Text(
                    text = engineStatus,
                    color = HarmonyTheme.colors.textSecondary,
                    fontSize = HarmonyTheme.typography.body,
                )
            }
        }

        SecondaryButton(label = "Back to home", onClick = onBack)
    }
}
