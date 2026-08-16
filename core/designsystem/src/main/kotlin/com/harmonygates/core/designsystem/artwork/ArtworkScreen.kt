package com.harmonygates.core.designsystem.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
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
 * The black/root surface always fills the complete activity window. The artwork itself is fitted
 * inside the currently safe drawing area. In immersive full-screen that area normally equals the
 * whole window; if Android keeps a status/navigation/task bar visible (for example in multi-window),
 * the artwork contracts rather than allowing system UI to cover a painted control.
 *
 * Frame dimensions are derived directly from the live constraints reaching this composition.
 * No display metrics, remembered launch size, or chained fillMaxSize/aspectRatio modifier is used.
 * A window resize therefore produces a new fitted frame on the next measure, and the normalized
 * hit regions use that exact same frame.
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
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth.value <= 0f || maxHeight.value <= 0f) return@BoxWithConstraints

                val artworkRatio = spec.aspectRatio
                val containerRatio = maxWidth.value / maxHeight.value
                val frameWidth = if (containerRatio > artworkRatio) {
                    maxHeight * artworkRatio
                } else {
                    maxWidth
                }
                val frameHeight = if (containerRatio > artworkRatio) {
                    maxHeight
                } else {
                    maxWidth / artworkRatio
                }

                Box(
                    modifier = Modifier
                        .size(width = frameWidth, height = frameHeight)
                        .align(Alignment.Center),
                ) {
                    Image(
                        painter = artwork,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )

                    for (region in spec.regionsInHitTestOrder) {
                        val interactionSource = remember(region.id) { MutableInteractionSource() }

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
    }
}

private val REGION_CORNER_RADIUS = 8.dp
