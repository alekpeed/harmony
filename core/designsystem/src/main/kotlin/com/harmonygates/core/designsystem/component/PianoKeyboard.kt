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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * [held] and [sustained] are drawn differently on purpose. 05_MIDI_INPUT_ENGINE.md §5 keeps
 * fingers-on-keys and pedal-held notes apart, and a player debugging a stuck note needs to see
 * which of the two they are looking at.
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
    require(highNote > lowNote) { "A keyboard needs at least two notes, got $lowNote..$highNote" }

    val colors = HarmonyTheme.colors
    val whiteKeyFill = Color(0xFFF7F3EC)
    val blackKeyFill = Color(0xFF15120E)
    val heldFill = colors.accent
    val sustainedFill = colors.partial
    val outline = colors.outline

    val description = buildString {
        append("Keyboard from MIDI note $lowNote to $highNote. ")
        append(if (held.isEmpty()) "No keys held." else "Held: ${held.sorted().joinToString()}.")
        if (sustained.isNotEmpty()) append(" Sustained: ${sustained.sorted().joinToString()}.")
    }

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

            drawWhiteKeys(whiteNotes, whiteWidth, whiteKeyFill, heldFill, sustainedFill, outline, held, sustained)
            drawBlackKeys(
                lowNote, highNote, whiteNotes, whiteWidth, blackWidth, blackHeight,
                blackKeyFill, heldFill, sustainedFill, held, sustained,
            )
        }
    }
}

private fun DrawScope.drawWhiteKeys(
    whiteNotes: List<Int>,
    whiteWidth: Float,
    fill: Color,
    heldFill: Color,
    sustainedFill: Color,
    outline: Color,
    held: Set<Int>,
    sustained: Set<Int>,
) {
    whiteNotes.forEachIndexed { index, note ->
        val left = index * whiteWidth
        val colour = when {
            note in held -> heldFill
            note in sustained -> sustainedFill
            else -> fill
        }
        drawRoundRect(
            color = colour,
            topLeft = Offset(left, 0f),
            size = Size(whiteWidth, size.height),
            cornerRadius = CornerRadius(0f, 0f),
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(left, 0f),
            size = Size(whiteWidth, size.height),
            cornerRadius = CornerRadius(0f, 0f),
            style = Stroke(width = 1f),
        )
    }
}

private fun DrawScope.drawBlackKeys(
    lowNote: Int,
    highNote: Int,
    whiteNotes: List<Int>,
    whiteWidth: Float,
    blackWidth: Float,
    blackHeight: Float,
    fill: Color,
    heldFill: Color,
    sustainedFill: Color,
    held: Set<Int>,
    sustained: Set<Int>,
) {
    for (note in lowNote..highNote) {
        if (!note.isBlackKey()) continue
        // A black key straddles the boundary between the white key below it and the next one.
        val centre = KeyboardLayout.blackKeyCentre(note, whiteNotes, whiteWidth) ?: continue
        val colour = when {
            note in held -> heldFill
            note in sustained -> sustainedFill
            else -> fill
        }
        drawRoundRect(
            color = colour,
            topLeft = Offset(centre - blackWidth / 2f, 0f),
            size = Size(blackWidth, blackHeight),
            cornerRadius = CornerRadius(1f, 1f),
        )
    }
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

private fun Int.isBlackKey(): Boolean = KeyboardLayout.isBlackKey(this)

private const val BLACK_KEY_WIDTH_RATIO = 0.62f
private const val BLACK_KEY_HEIGHT_RATIO = 0.62f
