package com.harmonygates.core.designsystem.notation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.theme.HarmonyColorTokens
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * A staff, drawn on a Canvas.
 *
 * 08_SIGHT_READING_ENGINE.md §1: "The app needs a training notation renderer, not a
 * general-purpose publishing engraver", and §2 asks for Compose Canvas with the layout geometry
 * in Kotlin rather than a WebView. So this draws lines, heads, stems and accidentals directly,
 * and everything about *where* they go is [StaffGeometry], which is testable without a screen.
 *
 * Note heads are ellipses rather than SMuFL glyphs. §2 permits a music font "where glyphs
 * materially simplify notation"; for heads, stems and ledger lines they do not, and a bundled
 * font is a licence and a megabyte. Accidentals are drawn as text, where the glyph genuinely is
 * the simplest way to be legible.
 */
@Composable
public fun NotationStaff(
    system: StaffSystem,
    modifier: Modifier = Modifier,
    lineSpacing: Dp = DEFAULT_LINE_SPACING,
    description: String = "Music staff",
) {
    val colors = HarmonyTheme.colors
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(lineSpacing * STAFF_HEIGHT_SPACINGS)
            .semantics { contentDescription = description },
    ) {
        val spacing = lineSpacing.toPx()
        val staffTop = spacing * STAFF_TOP_SPACINGS

        drawStaffLines(colors.onSurfaceMuted, staffTop, spacing)
        drawClef(system.clefGlyph, staffTop, spacing, colors.onSurface, measurer)

        system.events.forEach { event ->
            drawEvent(event, staffTop, spacing, colors, measurer)
        }

        system.barlines.forEach { at ->
            val x = size.width * at
            drawLine(
                color = colors.onSurfaceMuted,
                start = Offset(x, staffTop),
                end = Offset(x, staffTop + spacing * (STAFF_LINES - 1)),
                strokeWidth = spacing * BARLINE_WIDTH,
            )
        }
    }
}

private fun DrawScope.drawStaffLines(color: Color, top: Float, spacing: Float) {
    StaffGeometry.lineOffsets.forEach { offset ->
        val y = top + offset * spacing
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = spacing * LINE_WIDTH,
        )
    }
}

private fun DrawScope.drawClef(
    glyph: String,
    top: Float,
    spacing: Float,
    color: Color,
    measurer: TextMeasurer,
) {
    val layout = measurer.measure(
        text = glyph,
        style = TextStyle(color = color, fontSize = (spacing * CLEF_SIZE).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(spacing * CLEF_INSET, top + spacing * CLEF_BASELINE - layout.size.height / 2f),
    )
}

@Suppress("LongMethod")
private fun DrawScope.drawEvent(
    event: NotationEvent,
    top: Float,
    spacing: Float,
    colors: HarmonyColorTokens,
    measurer: TextMeasurer,
) {
    val x = size.width * event.position + size.width * event.width / 2f
    val ink = if (event.isCurrent) colors.accent else colors.onSurface

    if (event.isCurrent) {
        // The playhead: a band behind the event rather than a line beside it, so the eye lands
        // on the note being played rather than next to it.
        drawRect(
            color = colors.accent.copy(alpha = PLAYHEAD_ALPHA),
            topLeft = Offset(size.width * event.position, top - spacing),
            size = Size(size.width * event.width, spacing * (STAFF_LINES + 1)),
        )
    }

    event.chordSymbol?.let { symbol ->
        val layout = measurer.measure(
            text = symbol,
            style = TextStyle(color = ink, fontSize = (spacing * SYMBOL_SIZE).toSp()),
        )
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, top - spacing * SYMBOL_GAP))
    }

    if (event.isRest) {
        // A quarter rest as a plain mark. §1's "training renderer": legible beats correct.
        drawRect(
            color = ink,
            topLeft = Offset(x - spacing * REST_WIDTH / 2f, top + spacing * REST_TOP),
            size = Size(spacing * REST_WIDTH, spacing * REST_HEIGHT),
        )
        return
    }

    event.heads.forEach { head ->
        val y = top + StaffGeometry.offsetFromTopLine(head.steps) * spacing

        StaffGeometry.ledgerOffsets(head.steps).forEach { offset ->
            val ledgerY = top + offset * spacing
            drawLine(
                color = colors.onSurfaceMuted,
                start = Offset(x - spacing * LEDGER_OVERHANG, ledgerY),
                end = Offset(x + spacing * LEDGER_OVERHANG, ledgerY),
                strokeWidth = spacing * LINE_WIDTH,
            )
        }

        // A note head is an ellipse leaning to the right, which is what makes a hand-engraved
        // staff readable at a glance rather than a column of circles.
        rotate(degrees = -HEAD_TILT, pivot = Offset(x, y)) {
            val headSize = Size(spacing * HEAD_WIDTH, spacing * HEAD_HEIGHT)
            val topLeft = Offset(x - headSize.width / 2f, y - headSize.height / 2f)
            if (head.filled) {
                drawOval(color = ink, topLeft = topLeft, size = headSize)
            } else {
                drawOval(
                    color = ink,
                    topLeft = topLeft,
                    size = headSize,
                    style = Stroke(width = spacing * HOLLOW_HEAD_WIDTH),
                )
            }
        }

        val stemX = if (head.stemUp) x + spacing * HEAD_WIDTH / 2f else x - spacing * HEAD_WIDTH / 2f
        val stemEnd = if (head.stemUp) y - spacing * STEM_LENGTH else y + spacing * STEM_LENGTH
        drawLine(
            color = ink,
            start = Offset(stemX, y),
            end = Offset(stemX, stemEnd),
            strokeWidth = spacing * STEM_WIDTH,
        )

        head.accidental?.let { glyph ->
            val layout = measurer.measure(
                text = glyph,
                style = TextStyle(color = ink, fontSize = (spacing * ACCIDENTAL_SIZE).toSp()),
            )
            drawText(
                layout,
                topLeft = Offset(
                    x - spacing * ACCIDENTAL_GAP - layout.size.width,
                    y - layout.size.height / 2f,
                ),
            )
        }

        repeat(head.dots) { dot ->
            drawCircle(
                color = ink,
                radius = spacing * DOT_RADIUS,
                center = Offset(x + spacing * (DOT_GAP + dot * DOT_SPACING), y),
            )
        }
    }
}

private val DEFAULT_LINE_SPACING = 12.dp

private const val STAFF_LINES = 5

/** Room above and below the staff for ledger lines and chord symbols. */
private const val STAFF_HEIGHT_SPACINGS = 12
private const val STAFF_TOP_SPACINGS = 4f

private const val LINE_WIDTH = 0.08f
private const val BARLINE_WIDTH = 0.1f
private const val HEAD_WIDTH = 1.3f
private const val HEAD_HEIGHT = 0.95f
private const val HOLLOW_HEAD_WIDTH = 0.14f
private const val HEAD_TILT = 20f
private const val STEM_LENGTH = 3.2f
private const val STEM_WIDTH = 0.09f
private const val LEDGER_OVERHANG = 0.95f
private const val ACCIDENTAL_SIZE = 2.2f
private const val ACCIDENTAL_GAP = 0.9f
private const val CLEF_SIZE = 4.4f
private const val CLEF_INSET = 0.4f
private const val CLEF_BASELINE = 2f
private const val SYMBOL_SIZE = 1.6f
private const val SYMBOL_GAP = 1.6f
private const val REST_WIDTH = 0.7f
private const val REST_HEIGHT = 1.6f
private const val REST_TOP = 1.2f
private const val DOT_RADIUS = 0.16f
private const val DOT_GAP = 1.1f
private const val DOT_SPACING = 0.5f
private const val PLAYHEAD_ALPHA = 0.18f
