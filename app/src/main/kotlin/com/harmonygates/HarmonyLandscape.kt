package com.harmonygates

import androidx.compose.runtime.Composable

/**
 * Kept as a pass-through, deliberately, with its history in the comment.
 *
 * This used to enforce a landscape shape from inside the window: given a portrait window it laid
 * the content out at the window's dimensions swapped and turned it a quarter turn, so the
 * interface was always horizontal whichever way the tablet was held.
 *
 * That was wrong in a way that only showed up on a real device. It assumes a portrait window
 * means a portrait *tablet*, and it does not: a small window is also portrait. Launching the app
 * from another app — a file transfer tool, a share sheet — hands it a tall narrow window, and
 * the rotation would then lay the whole interface out larger than the window it was given and
 * turn it on its side. On screen that reads as an app that opens in the wrong place with its
 * edges cut off, which took most of an afternoon to attribute to the right cause because the
 * same build launched from the home screen was perfect.
 *
 * It is no longer needed. Every frame is now sized by its own aspect ratio, so a window of any
 * shape gets the whole design, upright, as large as it will go — a narrow window simply gets a
 * smaller picture rather than a rotated one. The manifest still asks for landscape, which is a
 * request the platform may decline; declining it is now harmless.
 *
 * Left in place rather than deleted so this note has somewhere to live, and so the wrapper is
 * still there if a future screen genuinely needs to fight the window.
 */
@Composable
fun HarmonyLandscape(content: @Composable () -> Unit) {
    content()
}
