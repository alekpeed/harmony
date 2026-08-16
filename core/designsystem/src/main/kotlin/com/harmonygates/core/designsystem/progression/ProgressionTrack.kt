package com.harmonygates.core.designsystem.progression

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.theme.HarmonyColorTokens
import com.harmonygates.core.designsystem.theme.HarmonyTheme
import kotlin.math.abs

/** How an orb is being treated right now. */
public enum class OrbState {
    /** Already played. Behind the play point, visually subordinate. */
    PREVIOUS,

    /** The chord the player is being asked for. */
    ACTIVE,

    /** Still to come. */
    UPCOMING,

    /** Accepted, in the moment before the track moves. */
    CORRECT,

    /** Wrong or incomplete. Shown, but the track does not move. */
    INCORRECT,
}

/**
 * One chord as the track draws it.
 *
 * Deliberately strings and numbers. `core:designsystem` must not depend on `core:music`, so an
 * orb cannot form an opinion about harmony even by accident — it is handed a symbol and a slot
 * and draws them.
 *
 * @param slot where along the track this orb currently is. Fractional during an advance: the
 *   screen animates one number and every orb follows from it.
 */
public data class ChordOrbUiModel(
    val eventId: String,
    val chordSymbol: String,
    val functionLabel: String?,
    val state: OrbState,
    val slot: Float,
)

/**
 * The progression track: a path, and chords travelling along it.
 *
 * `interface/PROGRESSION_RUN_HANDOFF.md` §1 and §4 ask for exactly this shape — one reusable
 * renderer driven by runtime data, with orbs that are generic containers rather than one
 * component per chord. Nothing here knows how long the progression is: it draws the orbs it is
 * given, wherever they say they are, so a three-chord ii-V-I and a thirty-six chord drill are
 * the same call.
 *
 * The background is not drawn here either. §1 requires the room to be a clean plate underneath,
 * with no baked track, orbs or labels, so this composable is transparent by construction and
 * whatever is behind it shows through.
 */
@Composable
public fun ProgressionTrack(
    geometry: TrackGeometry,
    orbs: List<ChordOrbUiModel>,
    modifier: Modifier = Modifier,
    designAspectRatio: Float = DEFAULT_DESIGN_ASPECT,
) {
    val colors = HarmonyTheme.colors
    val measurer = rememberTextMeasurer()
    val description = orbs.firstOrNull { it.state == OrbState.ACTIVE }
        ?.let { "Play ${it.chordSymbol}" }
        ?: "Progression track"

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
    ) {
        val frame = fittedFrame(size, designAspectRatio)
        drawPath(geometry, frame, colors.accent, colors.outline)

        // Farthest first, so a nearer orb overlaps the one behind it rather than the reverse.
        orbs.sortedByDescending { it.slot }.forEach { orb ->
            drawOrb(orb, geometry, frame, colors, measurer)
        }
    }
}

/**
 * The area the design frame occupies, centred and fitted.
 *
 * Same arithmetic as the home artwork: the composition keeps its proportions and the spare room
 * goes evenly to the two edges that have it. Without this a wider tablet would stretch the
 * perspective and the track would stop reading as a receding path.
 */
private fun fittedFrame(size: Size, aspectRatio: Float): Rect {
    val width = minOf(size.width, size.height * aspectRatio)
    val height = width / aspectRatio
    val left = (size.width - width) / 2f
    val top = (size.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun Rect.pointFor(point: TrackPoint): Offset =
    Offset(left + point.x * width, top + point.y * height)

/** Orb diameters are a fraction of the frame width, which keeps them circular. */
private fun Rect.sizeFor(point: TrackPoint): Float = point.diameter * width

private fun DrawScope.drawPath(
    geometry: TrackGeometry,
    frame: Rect,
    accent: Color,
    outline: Color,
) {
    val path = Path()
    var position = geometry.nearestIndex.toFloat() - PATH_OVERRUN
    val end = geometry.farthestIndex.toFloat() + PATH_OVERRUN
    var first = true
    while (position <= end) {
        val point = frame.pointFor(geometry.sample(position))
        if (first) {
            path.moveTo(point.x, point.y)
            first = false
        } else {
            path.lineTo(point.x, point.y)
        }
        position += PATH_STEP
    }

    // Brightest under the player's hands and fading into the distance, so the path reads as
    // depth rather than as a line drawn across the room.
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            listOf(accent.copy(alpha = PATH_NEAR_ALPHA), accent.copy(alpha = PATH_FAR_ALPHA)),
            startX = frame.left,
            endX = frame.right,
        ),
        style = Stroke(width = frame.width * PATH_WIDTH, cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = outline.copy(alpha = PATH_CORE_ALPHA),
        style = Stroke(width = frame.width * PATH_CORE_WIDTH, cap = StrokeCap.Round),
    )
}

@Suppress("LongMethod")
private fun DrawScope.drawOrb(
    orb: ChordOrbUiModel,
    geometry: TrackGeometry,
    frame: Rect,
    colors: HarmonyColorTokens,
    measurer: TextMeasurer,
) {
    val point = geometry.sample(orb.slot)
    val center = frame.pointFor(point)
    val diameter = frame.sizeFor(point)
    val radius = diameter / 2f
    if (radius <= 0f) return

    // Orbs leaving the near end and arriving at the far end fade rather than pop.
    val fade = edgeFade(orb.slot, geometry)
    if (fade <= 0f) return

    val body = when (orb.state) {
        OrbState.ACTIVE -> colors.surfaceRaised
        OrbState.CORRECT -> colors.surfaceRaised
        else -> colors.surface
    }
    val rim = when (orb.state) {
        OrbState.ACTIVE -> colors.accent
        OrbState.CORRECT -> colors.correct
        OrbState.INCORRECT -> colors.incorrect
        OrbState.PREVIOUS -> colors.outline
        OrbState.UPCOMING -> colors.outline
    }
    val alpha = fade * if (orb.state == OrbState.PREVIOUS) PREVIOUS_ALPHA else 1f

    // The pedestal: a flattened ellipse the orb sits on, drawn first so the orb covers its top.
    drawOval(
        color = colors.outline.copy(alpha = PEDESTAL_ALPHA * alpha),
        topLeft = Offset(center.x - radius, center.y + radius * PEDESTAL_OFFSET),
        size = Size(diameter, diameter * PEDESTAL_HEIGHT),
    )

    if (orb.state == OrbState.ACTIVE || orb.state == OrbState.CORRECT) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(rim.copy(alpha = GLOW_INNER_ALPHA * alpha), Color.Transparent),
                center = center,
                radius = radius * GLOW_RADIUS,
            ),
            radius = radius * GLOW_RADIUS,
            center = center,
        )
    }

    drawCircle(color = body.copy(alpha = alpha), radius = radius, center = center)
    drawCircle(
        color = rim.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = (diameter * RIM_WIDTH).coerceAtLeast(1.dp.toPx())),
    )

    val symbol = measurer.measure(
        text = orb.chordSymbol,
        style = TextStyle(
            color = colors.onSurface.copy(alpha = alpha),
            fontSize = (diameter * symbolScale(orb.chordSymbol) * SYMBOL_FRACTION).toSp(),
            fontWeight = FontWeight.SemiBold,
        ),
    )
    drawText(
        textLayoutResult = symbol,
        topLeft = Offset(
            center.x - symbol.size.width / 2f,
            center.y - symbol.size.height / 2f,
        ),
    )

    // The roman numeral is assistance, so it is absent unless the run enabled it — the caller
    // passes null rather than this deciding to hide it.
    orb.functionLabel?.let { label ->
        val function = measurer.measure(
            text = label,
            style = TextStyle(
                color = colors.onSurfaceMuted.copy(alpha = alpha),
                fontSize = (diameter * FUNCTION_FRACTION).toSp(),
            ),
        )
        drawText(
            textLayoutResult = function,
            topLeft = Offset(
                center.x - function.size.width / 2f,
                center.y + radius + diameter * FUNCTION_GAP,
            ),
        )
    }
}

/**
 * Shrinks a long symbol so it stays inside its orb.
 *
 * `Ebmaj7` is more than twice the width of `G7` and the far orbs are the smallest on the track,
 * so a single font size either spills the long symbols over the rim or wastes the short ones.
 * Scaling by length rather than by measurement keeps every orb on the same visual footing:
 * two chords the same length are always drawn the same size, whatever they happen to be.
 */
private fun symbolScale(symbol: String): Float =
    (COMFORTABLE_SYMBOL_LENGTH.toFloat() / symbol.length.coerceAtLeast(1)).coerceIn(MINIMUM_SYMBOL_SCALE, 1f)

/** 1 in the body of the track, falling to 0 as an orb passes either end. */
private fun edgeFade(slot: Float, geometry: TrackGeometry): Float = when {
    slot < geometry.nearestIndex -> (1f - abs(slot - geometry.nearestIndex)).coerceIn(0f, 1f)
    slot > geometry.farthestIndex -> (1f - (slot - geometry.farthestIndex)).coerceIn(0f, 1f)
    else -> 1f
}

/** The approved landscape composition: 1536 x 1024. */
private const val DEFAULT_DESIGN_ASPECT = 1.5f

private const val PATH_STEP = 0.05f
private const val PATH_OVERRUN = 0.6f
private const val PATH_WIDTH = 0.012f
private const val PATH_CORE_WIDTH = 0.004f
private const val PATH_NEAR_ALPHA = 0.45f
private const val PATH_FAR_ALPHA = 0.08f
private const val PATH_CORE_ALPHA = 0.5f

private const val PREVIOUS_ALPHA = 0.55f
private const val PEDESTAL_ALPHA = 0.5f
private const val PEDESTAL_OFFSET = 0.55f
private const val PEDESTAL_HEIGHT = 0.28f
private const val GLOW_RADIUS = 1.9f
private const val GLOW_INNER_ALPHA = 0.5f
private const val RIM_WIDTH = 0.03f
private const val SYMBOL_FRACTION = 0.34f

/** Symbols up to this long are drawn full size; longer ones scale down to fit the orb. */
private const val COMFORTABLE_SYMBOL_LENGTH = 4
private const val MINIMUM_SYMBOL_SCALE = 0.55f
private const val FUNCTION_FRACTION = 0.18f
private const val FUNCTION_GAP = 0.12f
