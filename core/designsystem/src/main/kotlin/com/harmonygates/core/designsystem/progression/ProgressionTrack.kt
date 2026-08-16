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

public enum class OrbState {
    PREVIOUS,
    ACTIVE,
    UPCOMING,
    CORRECT,
    INCORRECT,
}

public data class ChordOrbUiModel(
    val eventId: String,
    val chordSymbol: String,
    val functionLabel: String?,
    val state: OrbState,
    val slot: Float,
)

/**
 * Reusable progression renderer. [showPath] lets a screen keep the moving chord-orb language
 * without forcing a connecting line into an environment where the line is visually unwanted.
 */
@Composable
public fun ProgressionTrack(
    geometry: TrackGeometry,
    orbs: List<ChordOrbUiModel>,
    modifier: Modifier = Modifier,
    designAspectRatio: Float = DEFAULT_DESIGN_ASPECT,
    showPath: Boolean = true,
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
        if (showPath) drawPath(geometry, frame, colors.accent, colors.outline)

        orbs.sortedByDescending { it.slot }.forEach { orb ->
            drawOrb(orb, geometry, frame, colors, measurer)
        }
    }
}

private fun fittedFrame(size: Size, aspectRatio: Float): Rect {
    val width = minOf(size.width, size.height * aspectRatio)
    val height = width / aspectRatio
    val left = (size.width - width) / 2f
    val top = (size.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun Rect.pointFor(point: TrackPoint): Offset =
    Offset(left + point.x * width, top + point.y * height)

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

    val fade = edgeFade(orb.slot, geometry)
    if (fade <= 0f) return

    val body = when (orb.state) {
        OrbState.ACTIVE, OrbState.CORRECT -> colors.surfaceRaised
        else -> colors.surface
    }
    val rim = when (orb.state) {
        OrbState.ACTIVE -> colors.accent
        OrbState.CORRECT -> colors.correct
        OrbState.INCORRECT -> colors.incorrect
        OrbState.PREVIOUS, OrbState.UPCOMING -> colors.outline
    }
    val alpha = fade * if (orb.state == OrbState.PREVIOUS) PREVIOUS_ALPHA else 1f

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
        topLeft = Offset(center.x - symbol.size.width / 2f, center.y - symbol.size.height / 2f),
    )

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

private fun symbolScale(symbol: String): Float =
    (COMFORTABLE_SYMBOL_LENGTH.toFloat() / symbol.length.coerceAtLeast(1)).coerceIn(MINIMUM_SYMBOL_SCALE, 1f)

private fun edgeFade(slot: Float, geometry: TrackGeometry): Float = when {
    slot < geometry.nearestIndex -> (1f - abs(slot - geometry.nearestIndex)).coerceIn(0f, 1f)
    slot > geometry.farthestIndex -> (1f - (slot - geometry.farthestIndex)).coerceIn(0f, 1f)
    else -> 1f
}

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
private const val COMFORTABLE_SYMBOL_LENGTH = 4
private const val MINIMUM_SYMBOL_SCALE = 0.55f
private const val FUNCTION_FRACTION = 0.18f
private const val FUNCTION_GAP = 0.12f
