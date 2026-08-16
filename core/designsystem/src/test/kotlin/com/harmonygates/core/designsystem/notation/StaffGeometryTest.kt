package com.harmonygates.core.designsystem.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Staff arithmetic.
 *
 * 08_SIGHT_READING_ENGINE.md §2 asks for the layout geometry to stay in Kotlin, and this is why
 * that is worth doing: a ledger line drawn one position out is invisible in a screenshot and
 * makes a note unreadable to somebody sight-reading it.
 */
class StaffGeometryTest {

    @Test
    fun `the middle line is halfway down the staff`() {
        // Five lines, four spacings tall. The middle line is two down from the top.
        assertEquals(2f, StaffGeometry.offsetFromTopLine(0))
    }

    @Test
    fun `each step is half a space`() {
        assertEquals(2f, StaffGeometry.offsetFromTopLine(0))
        assertEquals(1.5f, StaffGeometry.offsetFromTopLine(1), "A space above the middle line")
        assertEquals(1f, StaffGeometry.offsetFromTopLine(2), "The line above that")
    }

    @Test
    fun `the top and bottom lines are where the staff ends`() {
        assertEquals(0f, StaffGeometry.offsetFromTopLine(4), "Four steps up is the top line")
        assertEquals(4f, StaffGeometry.offsetFromTopLine(-4), "Four steps down is the bottom line")
    }

    @Test
    fun `the five lines are evenly spaced`() {
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), StaffGeometry.lineOffsets)
    }

    // --- Ledger lines ------------------------------------------------------------------------

    @Test
    fun `notes on the staff need no ledger lines`() {
        (-4..4).forEach { steps ->
            assertEquals(
                emptyList(),
                StaffGeometry.ledgerOffsets(steps),
                "Step $steps sits on the staff and needs no extra line",
            )
        }
    }

    @Test
    fun `middle C below a treble staff gets exactly one ledger line`() {
        // Two steps below the bottom line.
        assertEquals(1, StaffGeometry.ledgerOffsets(-6).size)
    }

    @Test
    fun `the space just outside the staff needs no ledger line`() {
        // D4 sits in the space below a treble staff and is written with no extra line; C4, one
        // step further down, is the note that sits on the first ledger. Drawing a line for D4
        // would be wrong notation, not merely redundant.
        assertEquals(emptyList(), StaffGeometry.ledgerOffsets(-5))
        assertEquals(emptyList(), StaffGeometry.ledgerOffsets(5), "And G5 above it, likewise")
        assertEquals(1, StaffGeometry.ledgerOffsets(-6).size, "C4 does sit on one")
    }

    @Test
    fun `a note hanging below a ledger line shares that line`() {
        // B3 hangs under middle C's ledger line and adds none of its own.
        assertEquals(StaffGeometry.ledgerOffsets(-6), StaffGeometry.ledgerOffsets(-7))
    }

    @Test
    fun `a note far above the staff gets a line for every position between`() {
        // Eight steps up is two ledger lines: at six and at eight.
        assertEquals(2, StaffGeometry.ledgerOffsets(8).size)
        assertEquals(3, StaffGeometry.ledgerOffsets(10).size)
    }

    @Test
    fun `ledger lines sit outside the staff, never on it`() {
        listOf(-10, -8, -6, 6, 8, 10).forEach { steps ->
            StaffGeometry.ledgerOffsets(steps).forEach { offset ->
                assertTrue(
                    offset < 0f || offset > 4f,
                    "A ledger line at $offset for step $steps would be drawn on the staff itself",
                )
            }
        }
    }

    @Test
    fun `ledger lines run in the direction of the note`() {
        assertTrue(StaffGeometry.ledgerOffsets(8).all { it < 0f }, "High notes get lines above")
        assertTrue(StaffGeometry.ledgerOffsets(-8).all { it > 4f }, "Low notes get lines below")
    }

    // --- Stems -------------------------------------------------------------------------------

    @Test
    fun `stems point towards the middle of the staff`() {
        assertTrue(StaffGeometry.stemUp(-3), "Below the middle line, stems go up")
        assertTrue(!StaffGeometry.stemUp(3), "Above it, stems go down")
        assertTrue(!StaffGeometry.stemUp(0), "A note on the middle line takes a down stem")
    }

    // --- Horizontal layout ---------------------------------------------------------------------

    @Test
    fun `space is proportional to duration`() {
        val layout = StaffGeometry.layout(listOf(1.0, 2.0, 1.0))

        val widths = layout.map { it.second }
        assertTrue(widths[1] > widths[0], "A half note takes more room than a quarter")
        assertEquals(widths[0], widths[2], TOLERANCE, "Equal durations take equal room")
    }

    @Test
    fun `events run left to right without overlapping or leaving gaps`() {
        val layout = StaffGeometry.layout(listOf(1.0, 1.0, 2.0, 0.5))

        layout.zipWithNext().forEach { (first, second) ->
            assertEquals(
                first.first + first.second,
                second.first,
                TOLERANCE,
                "One event should start exactly where the last one ends",
            )
        }
    }

    @Test
    fun `the whole width is used, after the clef`() {
        val leading = 0.2f
        val layout = StaffGeometry.layout(listOf(1.0, 1.0, 1.0, 1.0), leadingFraction = leading)

        assertEquals(leading, layout.first().first, TOLERANCE, "The first event clears the clef")
        assertEquals(
            1f,
            layout.last().first + layout.last().second,
            TOLERANCE,
            "The last event reaches the right-hand edge",
        )
    }

    @Test
    fun `an empty bar lays out to nothing rather than dividing by zero`() {
        assertEquals(emptyList(), StaffGeometry.layout(emptyList()))
        assertTrue(StaffGeometry.layout(listOf(0.0, 0.0)).all { it.second == 0f })
    }

    @Test
    fun `a leading space that would swallow the system is refused`() {
        val failure = runCatching { StaffGeometry.layout(listOf(1.0), leadingFraction = 0.9f) }

        assertTrue(failure.isFailure, "A clef cannot take ninety percent of the width")
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
