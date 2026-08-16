package com.harmonygates.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The supplied Progression Run contract, read as it ships.
 *
 * Against the file in `interface/` rather than the packaged copy, for the same reason the home
 * map is: the build copies it into resources, so a test against the copy would only prove that
 * copying works. If a re-export changes the slot path or the timing, this is what notices.
 */
class TrackMapTest {

    private fun suppliedTrack(): TrackSpec = TrackMapReader.read(mapSource())

    @Test
    fun `the supplied map describes the approved eight-slot track`() {
        val track = suppliedTrack()

        assertEquals(8, track.geometry.slotCount, "One previous, one active, six upcoming")
        assertEquals(-1, track.geometry.nearestIndex)
        assertEquals(6, track.geometry.farthestIndex)
    }

    @Test
    fun `the window is one slot wider than the composition at each end`() {
        val track = suppliedTrack()

        // The orb leaving and the orb arriving have to exist before they can move.
        assertEquals(2, track.slotsBehind)
        assertEquals(7, track.slotsAhead)
    }

    @Test
    fun `the advance timing comes from the map, not from a constant in the app`() {
        val track = suppliedTrack()

        assertEquals(TrackMap.DEFAULT_DURATION_MS, track.advanceDurationMs)
        assertEquals(listOf(0.22f, 1f, 0.36f, 1f), track.easing, "cubic-bezier(0.22, 1, 0.36, 1)")
    }

    @Test
    fun `the track is authored in the approved landscape frame`() {
        assertEquals(1536f / 1024f, suppliedTrack().aspectRatio, TOLERANCE)
    }

    @Test
    fun `the play point is the largest orb and every upcoming slot is smaller`() {
        val geometry = suppliedTrack().geometry
        val diameters = (0..6).map { geometry.sample(it.toFloat()).diameter }

        assertEquals(diameters.maxOrNull(), diameters.first(), "The chord being played is nearest")
        assertEquals(diameters.sortedDescending(), diameters, "Perspective recedes without a step back")
    }

    @Test
    fun `a map with no track still produces a playable screen`() {
        // A re-export that loses its slot path must not take the run down with it: this is a
        // MIDI exercise first and a picture second.
        val track = TrackMapReader.build(TrackMap())

        assertEquals(8, track.geometry.slotCount)
        assertTrue(track.advanceDurationMs > 0)
    }

    private fun mapSource(): String {
        val file = java.io.File("../interface/maps/progression-run.json")
        assertTrue(file.isFile, "Expected the supplied track map at ${file.absolutePath}")
        return file.readText()
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
