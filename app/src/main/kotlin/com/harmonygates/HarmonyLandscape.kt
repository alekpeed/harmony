package com.harmonygates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

/**
 * Keeps the app horizontal, whatever the window turns out to be.
 *
 * `android:screenOrientation` in the manifest is the first line of defence and, on the tablets
 * this app is for, no longer a reliable one: from Android 16 the platform ignores a fixed
 * orientation on large screens, which is exactly what lint's `DiscouragedApi` says when it
 * objects to the attribute. The request is still made — it works on Android 15 and below, and on
 * any device that still honours it — but it cannot be depended on.
 *
 * So the shape is enforced from inside the window as well, where nothing can overrule it. Given
 * a portrait window, the content is laid out at the window's own dimensions swapped, and turned
 * a quarter turn. The result is that the interface is always horizontal: hold the tablet
 * sideways and it is upright, hold it upright and the interface asks to be turned.
 *
 * That is a deliberate demand on the player rather than a failure to adapt. Every approved frame
 * is composed at 1536 x 1024; the piano keyboard, the progression track's perspective and the
 * home artwork are all landscape designs, and a portrait version of them is not a narrower
 * layout but a different screen that nobody has drawn. Reflowing into one would be inventing
 * design, which is the thing this project does not do.
 */
@Composable
fun HarmonyLandscape(content: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth >= maxHeight) {
            content()
            return@BoxWithConstraints
        }

        // `requiredSize` rather than `size`: the child is deliberately laid out larger than the
        // constraints allow in one axis, because after the rotation that axis is the other one.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(width = maxHeight, height = maxWidth)
                .rotate(ONE_QUARTER_TURN),
        ) {
            content()
        }
    }
}

private const val ONE_QUARTER_TURN = 90f
