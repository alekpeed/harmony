package com.harmonygates.core.designsystem.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * @param onRegionClick receives the region's Figma layer name, e.g. `HIT / Ear Trainer`.
 */
@Composable
public fun ArtworkScreen(
    artwork: Painter,
    spec: ArtworkSpec,
    onRegionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        Image(
            painter = artwork,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        for (region in spec.regions) {
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
