package com.harmonygates.eartraining

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * The one transform, and everything drawn through it.
 *
 * Every approved Ear Training layer is authored on a 1536 x 1024 canvas, and the whole design
 * depends on those layers staying registered to one another: a knob a few pixels out of its
 * housing, or a hit region that has drifted off the button under it, is immediately visible and
 * cannot be fixed by nudging one layer. So no layer is ever scaled on its own. One scale factor
 * is derived from the window, a frame of exactly that shape is centred in it, and every plate,
 * control, label and touch target inside is placed as a fraction of that same frame.
 *
 * This is the same fitting rule `ArtworkScreen` uses for Home and Progression Run, expressed in
 * design pixels rather than normalized fractions because the Ear Training map is authored in
 * pixels and reading the two against each other should not require arithmetic.
 */
public const val DESIGN_WIDTH: Float = 1536f
public const val DESIGN_HEIGHT: Float = 1024f

/** A rectangle in design pixels, as written in `interface/maps/ear_training.json`. */
@Immutable
data class DesignRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val centreX: Float get() = x + width / 2f
    val centreY: Float get() = y + height / 2f

    companion object {
        fun centred(centreX: Float, centreY: Float, width: Float, height: Float): DesignRect =
            DesignRect(centreX - width / 2f, centreY - height / 2f, width, height)

        /** A round control, given the centre and radius read off the plate. */
        fun circle(centreX: Float, centreY: Float, radius: Float): DesignRect =
            centred(centreX, centreY, radius * 2f, radius * 2f)
    }

    /** Grown about its own centre, for a touch target larger than the drawn control. */
    fun inflated(by: Float): DesignRect =
        DesignRect(x - by, y - by, width + by * 2f, height + by * 2f)
}

/**
 * Placement inside the fitted design frame.
 *
 * [scale] is design pixels to dp. It is the only number any of this multiplies by, which is what
 * makes "do not scale layers independently" a property of the code rather than a rule to remember.
 */
@Immutable
class DesignScope(val scale: Float) {

    fun Modifier.designRect(rect: DesignRect): Modifier = this
        .offset(x = (rect.x * scale).dp, y = (rect.y * scale).dp)
        .size(width = (rect.width * scale).dp, height = (rect.height * scale).dp)

    fun dp(designPixels: Float): Dp = (designPixels * scale).dp

    /** Type scales with the frame, so a label authored to fit its slot still fits it. */
    fun textSize(designPixels: Float): TextUnit = (designPixels * scale).sp
}

/**
 * Fits the design frame into the window and centres it.
 *
 * The frame is never larger than the space it was given, so a plate cannot be clipped at an edge;
 * whatever is left over is the room showing through around the console, which is the intent.
 */
@Composable
fun DesignSurface(
    modifier: Modifier = Modifier,
    content: @Composable DesignScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT)
        if (scale <= 0f) return@BoxWithConstraints

        val scope = remember(scale) { DesignScope(scale) }
        Box(
            modifier = Modifier.size(
                width = (DESIGN_WIDTH * scale).dp,
                height = (DESIGN_HEIGHT * scale).dp,
            ),
        ) {
            scope.content()
        }
    }
}

/** A full-screen plate: background, shell, section layout. Always the whole frame, never inset. */
@Composable
fun DesignScope.Plate(resourceId: Int, contentDescription: String? = null) {
    Image(
        painter = painterResource(resourceId),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )
}

/** One control asset, placed at its design rectangle. */
@Composable
fun DesignScope.DesignImage(
    resourceId: Int,
    rect: DesignRect,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(resourceId),
        contentDescription = contentDescription,
        modifier = modifier.designRect(rect),
        contentScale = ContentScale.Fit,
    )
}

/**
 * An invisible touch target over a drawn control.
 *
 * The artwork carries no logic, so the hit region is declared here and documented in the screen
 * map rather than inferred from opaque pixels. Small controls are given at least a 48dp target
 * around their own centre — a 30 pixel indicator is legible and is not something to ask a finger
 * to hit exactly.
 */
@Composable
fun DesignScope.DesignHit(
    rect: DesignRect,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val minimum = MINIMUM_TOUCH_DP / scale
    val grow = maxOf(0f, (minimum - min(rect.width, rect.height)) / 2f)
    val target = if (grow > 0f) rect.inflated(grow) else rect
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .designRect(target)
            .clickable(
                interactionSource = interaction,
                // The artwork already shows press state by swapping its own active asset, so no
                // ripple is drawn over illustrated hardware.
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .background(Color.Transparent),
    )
}

private const val MINIMUM_TOUCH_DP = 48f

/** Text overflow used by every dynamic label, so a long value clips rather than reflows a panel. */
internal val LabelOverflow = TextOverflow.Ellipsis
