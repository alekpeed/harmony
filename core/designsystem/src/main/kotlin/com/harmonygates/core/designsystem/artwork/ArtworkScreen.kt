package com.harmonygates.core.designsystem.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
 * Both the image and the regions are placed from [ArtworkGeometry.fittedBounds] rather than the
 * image being left to `ContentScale.Fit` and the regions computed separately. Two independent
 * implementations of "fit" is one too many: if they ever disagreed — because of a rounding
 * difference, an inset applied to one and not the other, or a window shape nobody tried — the
 * buttons would drift off the things they are drawn over, and it would look like a design
 * problem rather than an arithmetic one.
 *
 * The artwork is also inset by [WindowInsets.safeDrawing]. A landscape tablet puts its
 * navigation bar down one side, and a frame drawn edge to edge slides that side of the design
 * underneath an opaque system bar — which is how a quarter of the home screen goes missing.
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            // The design is landscape and complete; nothing of it may sit under a system bar.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val fitted = ArtworkGeometry.fittedBounds(spec, containerWidthPx, containerHeightPx)

        Image(
            painter = artwork,
            contentDescription = contentDescription,
            modifier = Modifier
                .offset(
                    x = with(density) { fitted.left.toDp() },
                    y = with(density) { fitted.top.toDp() },
                )
                .size(
                    width = with(density) { fitted.width.toDp() },
                    height = with(density) { fitted.height.toDp() },
                ),
            // The rectangle is already the right shape, so this only fills it. The aspect ratio
            // is preserved by the arithmetic above, not by the scale mode.
            contentScale = ContentScale.FillBounds,
        )

        // Largest first, so a region nested inside another still receives its taps.
        for (region in spec.regionsInHitTestOrder) {
            val rect = ArtworkGeometry.resolve(region, spec, containerWidthPx, containerHeightPx)
            val interactionSource = remember(region.id) { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { rect.left.toDp() },
                        y = with(density) { rect.top.toDp() },
                    )
                    .size(
                        width = with(density) { rect.width.toDp() },
                        height = with(density) { rect.height.toDp() },
                    )
                    .clip(RoundedCornerShape(8.dp))
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
