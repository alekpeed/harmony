package com.harmonygates.core.designsystem.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic behind the progression track.
 *
 * This is the part that is invisible in a screenshot and obvious in motion: an orb a few percent
 * off its slot looks fine standing still and wrong the moment the track advances.
 */
class TrackGeometryTest {

    /** The eight slots of `interface/maps/progression-run.json`, in its own 1536x1024 frame. */
    private val approved = TrackGeometry.fromDesignPixels(
        designWidth = 1536,
        designHeight = 1024,
        slots = listOf(
            DesignSlot(-1, 380f, 570f, 100f),
            DesignSlot(0, 565f, 548f, 102f),
            DesignSlot(1, 758f, 501f, 86f),
            DesignSlot(2, 914f, 457f, 80f),
            DesignSlot(3, 1027f, 404f, 72f),
            DesignSlot(4, 1138f, 373f, 66f),
            DesignSlot(5, 1245f, 351f, 66f),
            DesignSlot(6, 1348f, 335f, 60f),
        ),
    )

    @Test
    fun `the approved composition has eight slots`() {
        assertEquals(8, approved.slotCount)
        assertEquals(-1, approved.nearestIndex)
        assertEquals(6, approved.farthestIndex)
    }

    @Test
    fun `design pixels become fractions of the frame`() {
        val playPoint = approved.sample(0f)
        assertEquals(565f / 1536f, playPoint.x, TOLERANCE)
        assertEquals(548f / 1024f, playPoint.y, TOLERANCE)
        assertEquals(102f / 1536f, playPoint.diameter, TOLERANCE, "Orb size scales with the frame width")
    }

    @Test
    fun `sampling a slot returns that slot`() {
        val third = approved.sample(3f)
        assertEquals(1027f / 1536f, third.x, TOLERANCE)
        assertEquals(404f / 1024f, third.y, TOLERANCE)
    }

    @Test
    fun `halfway through an advance an orb is halfway between two slots`() {
        val between = approved.sample(1.5f)
        val first = approved.sample(1f)
        val second = approved.sample(2f)

        assertEquals((first.x + second.x) / 2f, between.x, TOLERANCE)
        assertEquals((first.y + second.y) / 2f, between.y, TOLERANCE)
        assertEquals((first.diameter + second.diameter) / 2f, between.diameter, TOLERANCE)
    }

    @Test
    fun `the track keeps running past the play point so a chord can leave`() {
        val leaving = approved.sample(-2f)
        val previous = approved.sample(-1f)

        assertTrue(leaving.x < previous.x, "An exiting chord carries on towards the player")
    }

    @Test
    fun `the track keeps running past the far end so a chord can arrive`() {
        val arriving = approved.sample(7f)
        val farthest = approved.sample(6f)

        assertTrue(arriving.x > farthest.x, "A new chord enters from beyond the last slot")
        assertTrue(arriving.diameter <= farthest.diameter, "And it is no larger than the one ahead of it")
    }

    @Test
    fun `orbs get smaller with distance`() {
        val diameters = (0..6).map { approved.sample(it.toFloat()).diameter }
        assertEquals(
            diameters.sortedDescending(),
            diameters,
            "Perspective only works if every upcoming slot is smaller than the one before it",
        )
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
