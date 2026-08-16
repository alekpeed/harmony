package com.harmonygates.core.designsystem.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Renders an approved artwork frame and places invisible controls over its named regions.
 *
 * This is the integration seam described in `interface/README.md`: the artwork is the visual,
 * the app keeps its own behaviour, and the two meet through named regions rather than through
 * a rewrite. Dropping in a new export changes a drawable and a table of fractions; it does not
 * touch navigation, state handling, MIDI or game logic.
 *
 * The artwork is fitted rather than cropped so no control can be pushed off-screen on an
 * unusual aspect ratio — a cropped background would silently move a hit region out of reach.
 *
 * The fitting is done by the layout rather than by arithmetic. An inner box is given the
 * artwork's own aspect ratio and told to fill what it can, so it *becomes* the largest correctly
 * shaped rectangle that fits, and is centred by its parent. The image then simply fills that box
 * and every region is a fraction of it.
 *
 * That is deliberate, and it replaces an earlier version that measured the container and
 * computed the rectangle itself. Measuring is the fragile way round: a container that reports a
 * width larger than what is actually visible — a window being resized, a parent handing down a
 * looser constraint than the one it will draw within — produces an image scaled to the wrong
 * number and clipped at the edge, which looks exactly like a cropped background. Sizing by
 * aspect ratio cannot do that, because the box is never larger than the space it was given.
 *
 * The frame is inset by the system bars and the display cutout. A landscape tablet puts its
 * navigation bar down one side, and a frame drawn edge to edge slides that side of the design
 * underneath an opaque strip — which is how a quarter of the home screen goes missing.
 *
 * The IME is deliberately *not* in that set. A screen with no text field should never see a
 * keyboard, and if one appears anyway — a hardware keyboard attaching, a stray focus — the
 * frame should not resize itself around something that is not there.
 *
 * @param onRegionClick receives the region's Figma layer name, e.g. `HIT / Ear Trainer`.
 */
@Composable
public fun ArtworkScreen(
    artwork: Painter,
    spec: ArtworkSpec,
    onRegionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    background: Color = Color.Black,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // The whole of the fitting, in one line: the largest box of the artwork's shape
                // that the parent can hold, centred by the parent's alignment.
                .aspectRatio(spec.nativeWidth.toFloat() / spec.nativeHeight.toFloat()),
        ) {
            val frameWidth = maxWidth
            val frameHeight = maxHeight

            Image(
                painter = artwork,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // The box is already the artwork's shape, so filling it preserves the aspect
                // ratio and leaves no letterbox inside the frame for a region to be offset by.
                contentScale = ContentScale.FillBounds,
            )

            // Largest first, so a region nested inside another still receives its taps.
            for (region in spec.regionsInHitTestOrder) {
                val interactionSource = remember(region.id) { MutableInteractionSource() }

                // Fractions of the frame, which is the artwork: no letterbox to offset by,
                // because the frame has the artwork's own shape.
                Box(
                    modifier = Modifier
                        .offset(
                            x = frameWidth * region.bounds.left,
                            y = frameHeight * region.bounds.top,
                        )
                        .size(
                            width = frameWidth * region.bounds.width,
                            height = frameHeight * region.bounds.height,
                        )
                        .clip(RoundedCornerShape(REGION_CORNER_RADIUS))
                        .clickable(
                            interactionSource = interactionSource,
                            // The artwork already shows the control; the ripple is the only
                            // feedback this layer adds, so touch still feels answered.
                            indication = ripple(),
                            onClick = { onRegionClick(region.id) },
                        )
                        .semantics {
                            this.contentDescription = region.contentDescription
                            role = Role.Button
                        },
                )
            }
        }
    }
}

private val REGION_CORNER_RADIUS = 8.dp
