package com.harmonygates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * What a portrait window gets instead of the interface.
 *
 * Every approved frame is composed at 1536 x 1024. Fitting one into a portrait window leaves
 * most of the screen empty above and below it — a band of dead colour taller than the picture,
 * which looks like a rendering fault rather than a design. Rotating the interface instead, which
 * is what this app used to do, is worse: it assumes a portrait window means a portrait tablet,
 * and a small window launched from another app is portrait too.
 *
 * So a portrait window is told what it is. The interface is landscape, deliberately — the piano
 * keyboard, the progression track's perspective and the home artwork are all wide designs, and a
 * portrait version of them is not a narrower layout but a different screen nobody has drawn.
 * Asking for the tablet to be turned is honest about that; a giant letterbox pretends it is a
 * layout problem.
 */
@Composable
fun RequireLandscape(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= maxHeight) {
            content()
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(HarmonyTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(HarmonyTheme.spacing.medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Turn the tablet",
                color = HarmonyTheme.colors.textPrimary,
                fontSize = HarmonyTheme.typography.heading,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Harmony Gates is played sideways, with the tablet propped in front of a " +
                    "keyboard. Every screen is drawn for that shape.",
                color = HarmonyTheme.colors.textSecondary,
                fontSize = HarmonyTheme.typography.body,
                textAlign = TextAlign.Center,
            )
        }
    }
}
