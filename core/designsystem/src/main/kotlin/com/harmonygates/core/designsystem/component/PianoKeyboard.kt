package com.harmonygates.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harmonygates.core.designsystem.state.KeyFill
import com.harmonygates.core.designsystem.state.KeyMarker
import com.harmonygates.core.designsystem.state.KeyOutline
import com.harmonygates.core.designsystem.state.KeyPainting
import com.harmonygates.core.designsystem.state.KeyPaintings
import com.harmonygates.core.designsystem.state.KeyRole
import com.harmonygates.core.designsystem.state.KeyVerdict
import com.harmonygates.core.designsystem.state.PianoKeyState
import com.harmonygates.core.designsystem.theme.HarmonyColorTokens
import com.harmonygates.core.designsystem.theme.HarmonyTheme

/**
 * A piano keyboard drawn on a Compose canvas.
 *
 * 08_SIGHT_READING_ENGINE.md §2 and the Android baseline both call for Compose Canvas rather
 * than a bitmap or a third-party renderer, so this stays pure Kotlin and scales to any width.
 *
 * Notes are plain MIDI numbers, not domain types: `core:designsystem` does not depend on
 * `core:music`, which is what stops a drawing routine from ever making a theory decision.
 *
 * Each key carries the layered state of 12 §7 — what the exercise asked for, what is held, what
 * the pedal is holding, and what evaluation made of it — and every one of those is drawn with a
 * marker and an outline as well as a fill, because that section forbids encoding them by colour
 * alone.
 */
@Composable
public fun PianoKeyboard(
    lowNote: Int,
    highNote: Int,
    states: Map<Int, PianoKeyState>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    require(highNote > lowNote) { "A keyboard needs at least two notes, got $lowNote..$highNote" }

    val colors = HarmonyTheme.colors
    val description = describe(lowNote, highNote, states)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val whiteNotes = KeyboardLayout.whiteNotes(lowNote..highNote)
            if (whiteNotes.isEmpty()) return@Canvas

            val whiteWidth = size.width / whiteNotes.size
            val blackWidth = whiteWidth * BLACK_KEY_WIDTH_RATIO
            val blackHeight = size.height * BLACK_KEY_HEIGHT_RATIO

            whiteNotes.forEachIndexed { index, note ->
                val painting = KeyPaintings.of(states[note] ?: PianoKeyState.Inactive)
                drawKey(
                    topLeft = Offset(index * whiteWidth, 0f),
                    keySize = Size(whiteWidth, size.height),
                    painting = painting,
                    colors = colors,
                    black = false,
                )
            }

            for (note in lowNote..highNote) {
                if (!KeyboardLayout.isBlackKey(note)) continue
                // A black key straddles the boundary between the white key below it and the next.
                val centre = KeyboardLayout.blackKeyCentre(note, whiteNotes, whiteWidth) ?: continue
                val painting = KeyPaintings.of(states[note] ?: PianoKeyState.Inactive)
                drawKey(
                    topLeft = Offset(centre - blackWidth / 2f, 0f),
                    keySize = Size(blackWidth, blackHeight),
                    painting = painting,
                    colors = colors,
                    black = true,
                )
            }
        }
    }
}

/**
 * The plain form: which keys are down, and which the pedal is holding.
 *
 * Kept because most of the app only knows those two things — the MIDI diagnostics screen has no
 * exercise to compare against — and making every caller build a state map would be ceremony
 * around a question they are not asking.
 */
@Composable
public fun PianoKeyboard(
    lowNote: Int,
    highNote: Int,
    held: Set<Int>,
    modifier: Modifier = Modifier,
    sustained: Set<Int> = emptySet(),
    height: Dp = 132.dp,
) {
    val states = (held + sustained).associateWith { note ->
        PianoKeyState(held = note in held, sustained = note in sustained)
    }
    PianoKeyboard(
        lowNote = lowNote,
        highNote = highNote,
        states = states,
        modifier = modifier,
        height = height,
    )
}

private fun DrawScope.drawKey(
    topLeft: Offset,
    keySize: Size,
    painting: KeyPainting,
    colors: HarmonyColorTokens,
    black: Boolean,
) {
    drawRoundRect(
        color = fillColor(painting.fill, colors, black),
        topLeft = topLeft,
        size = keySize,
        cornerRadius = CornerRadius(if (black) 1f else 0f),
    )
    drawOutline(topLeft, keySize, painting.outline, colors)
    drawMarker(topLeft, keySize, painting.marker, colors, black)
}

private fun fillColor(fill: KeyFill, colors: HarmonyColorTokens, black: Boolean): Color = when (fill) {
    KeyFill.NATURAL -> if (black) colors.keyBlack else colors.keyWhite
    KeyFill.PRESSED -> if (black) colors.keyBlackPressed else colors.keyWhitePressed
    KeyFill.TARGET -> colors.keyTarget
    KeyFill.SUSTAINED -> colors.accentSecondary
    KeyFill.ERROR -> colors.feedbackError
}

private fun DrawScope.drawOutline(
    topLeft: Offset,
    keySize: Size,
    outline: KeyOutline,
    colors: HarmonyColorTokens,
) {
    val (width, effect) = when (outline) {
        KeyOutline.NONE -> HAIRLINE to null
        KeyOutline.THIN -> HAIRLINE to null
        KeyOutline.THICK -> THICK_STROKE to null
        KeyOutline.DASHED -> THICK_STROKE to PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))
    }
    val colour = when (outline) {
        KeyOutline.NONE -> colors.keyBorder
        KeyOutline.THIN -> colors.outline
        KeyOutline.THICK, KeyOutline.DASHED -> colors.accentSecondary
    }
    drawRoundRect(
        color = colour,
        topLeft = topLeft,
        size = keySize,
        cornerRadius = CornerRadius(0f),
        style = Stroke(width = width, pathEffect = effect),
    )
}

/**
 * The marker, drawn low on the key.
 *
 * Low because a hand covers the top of a keyboard: a marker at the top of a white key is behind
 * the player's fingers exactly when they are trying to read it.
 */
private fun DrawScope.drawMarker(
    topLeft: Offset,
    keySize: Size,
    marker: KeyMarker,
    colors: HarmonyColorTokens,
    black: Boolean,
) {
    if (marker == KeyMarker.NONE) return
    val centre = Offset(
        x = topLeft.x + keySize.width / 2f,
        y = topLeft.y + keySize.height * MARKER_HEIGHT_FRACTION,
    )
    val radius = keySize.width * MARKER_RADIUS_FRACTION
    val colour = if (black) colors.textPrimary else colors.keyLabel

    when (marker) {
        KeyMarker.DOT -> drawCircle(colour, radius, centre)
        KeyMarker.RING -> drawCircle(colour, radius, centre, style = Stroke(MARKER_STROKE))
        KeyMarker.TICK -> {
            drawLine(
                colour,
                Offset(centre.x - radius, centre.y),
                Offset(centre.x - radius / 3f, centre.y + radius),
                MARKER_STROKE,
            )
            drawLine(
                colour,
                Offset(centre.x - radius / 3f, centre.y + radius),
                Offset(centre.x + radius, centre.y - radius),
                MARKER_STROKE,
            )
        }

        KeyMarker.CROSS -> {
            drawLine(
                colour,
                Offset(centre.x - radius, centre.y - radius),
                Offset(centre.x + radius, centre.y + radius),
                MARKER_STROKE,
            )
            drawLine(
                colour,
                Offset(centre.x + radius, centre.y - radius),
                Offset(centre.x - radius, centre.y + radius),
                MARKER_STROKE,
            )
        }

        KeyMarker.DIAMOND -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centre.x, centre.y - radius)
                lineTo(centre.x + radius, centre.y)
                lineTo(centre.x, centre.y + radius)
                lineTo(centre.x - radius, centre.y)
                close()
            }
            drawPath(path, colour, style = Stroke(MARKER_STROKE))
        }

        KeyMarker.BAR -> drawRect(
            color = colour,
            topLeft = Offset(centre.x - radius, centre.y - MARKER_STROKE),
            size = Size(radius * 2f, MARKER_STROKE * 2f),
        )

        KeyMarker.NONE -> Unit
    }
}

private fun describe(lowNote: Int, highNote: Int, states: Map<Int, PianoKeyState>): String =
    buildString {
        append("Keyboard from MIDI note $lowNote to $highNote. ")
        val held = states.filterValues { it.held }.keys.sorted()
        val sustained = states.filterValues { it.sustained && !it.held }.keys.sorted()
        val targets = states.filterValues { it.role != KeyRole.INACTIVE }.keys.sorted()
        val wrong = states.filterValues { it.verdict == KeyVerdict.EXTRA }.keys.sorted()
        val missing = states.filterValues { it.verdict == KeyVerdict.MISSING }.keys.sorted()

        append(if (held.isEmpty()) "No keys held." else "Held: ${held.joinToString()}.")
        if (sustained.isNotEmpty()) append(" Sustained: ${sustained.joinToString()}.")
        if (targets.isNotEmpty()) append(" Asked for: ${targets.joinToString()}.")
        if (wrong.isNotEmpty()) append(" Extra: ${wrong.joinToString()}.")
        if (missing.isNotEmpty()) append(" Missing: ${missing.joinToString()}.")
    }

/**
 * Where the keys go.
 *
 * Extracted from the drawing code so it can be unit-tested: a keyboard that puts C sharp on the
 * wrong side of a white key looks almost right in a screenshot and is obviously wrong to anyone
 * who plays.
 */
internal object KeyboardLayout {

    /** True for the five raised keys of each octave. */
    fun isBlackKey(note: Int): Boolean = when (note.mod(12)) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }

    /** The white keys in [range], ascending. These are what divide the available width. */
    fun whiteNotes(range: IntRange): List<Int> = range.filter { !isBlackKey(it) }

    /**
     * Horizontal centre of a black key, or null when it has no white key below it in range.
     *
     * A black key straddles the boundary between the white key below it and the next one, which
     * is why the position is derived from the white key's index rather than from the note number.
     */
    fun blackKeyCentre(note: Int, whiteNotes: List<Int>, whiteWidth: Float): Float? {
        if (!isBlackKey(note)) return null
        val whiteBelowIndex = whiteNotes.indexOf(note - 1)
        if (whiteBelowIndex < 0) return null
        return (whiteBelowIndex + 1) * whiteWidth
    }
}

private const val BLACK_KEY_WIDTH_RATIO = 0.62f
private const val BLACK_KEY_HEIGHT_RATIO = 0.62f
private const val HAIRLINE = 1f
private const val THICK_STROKE = 3f
private const val DASH = 6f
private const val MARKER_STROKE = 2.5f
private const val MARKER_HEIGHT_FRACTION = 0.82f
private const val MARKER_RADIUS_FRACTION = 0.22f
