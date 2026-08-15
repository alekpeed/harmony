package com.harmonygates.core.designsystem.artwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The arithmetic that puts a control where the artwork shows it.
 *
 * This is the failure mode worth testing hard: a region a few percent out looks perfect in a
 * screenshot and misses under a finger. The approved home frame is 1536x1024, so the fixtures
 * use that size and the tablet aspect ratios it will actually be shown at.
 */
class ArtworkGeometryTest {

    private val homeFrame = ArtworkSpec(
        nativeWidth = 1536,
        nativeHeight = 1024,
        regions = listOf(
            HitRegion("HIT / Menu", NormalizedRect(0f, 0f, 0.1f, 0.1f), "Open menu"),
            // A region in the exact centre, for the letterboxing assertions.
            HitRegion("HIT / Continue", NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f), "Continue"),
        ),
    )

    private val tolerance = 0.01f

    @Test
    fun `artwork fills a container of the same aspect ratio exactly`() {
        val bounds = ArtworkGeometry.fittedBounds(homeFrame, containerWidth = 3072f, containerHeight = 2048f)

        assertEquals(0f, bounds.left, tolerance)
        assertEquals(0f, bounds.top, tolerance)
        assertEquals(3072f, bounds.width, tolerance)
        assertEquals(2048f, bounds.height, tolerance)
    }

    @Test
    fun `a wider container letterboxes evenly left and right`() {
        // 2000x1000 is wider than 3:2, so the artwork is limited by height.
        val bounds = ArtworkGeometry.fittedBounds(homeFrame, containerWidth = 2000f, containerHeight = 1000f)

        assertEquals(1500f, bounds.width, tolerance, "Height-limited: 1024 -> 1000 scales width to 1500")
        assertEquals(1000f, bounds.height, tolerance)
        assertEquals(250f, bounds.left, tolerance)
        assertEquals(2000f - 250f, bounds.right, tolerance)
        assertEquals(bounds.left, 2000f - bounds.right, tolerance, "Letterboxing must be symmetric")
    }

    @Test
    fun `a taller container letterboxes evenly top and bottom`() {
        // A portrait window: the artwork is limited by width.
        val bounds = ArtworkGeometry.fittedBounds(homeFrame, containerWidth = 768f, containerHeight = 1024f)

        assertEquals(768f, bounds.width, tolerance)
        assertEquals(512f, bounds.height, tolerance)
        assertEquals(256f, bounds.top, tolerance)
        assertEquals(bounds.top, 1024f - bounds.bottom, tolerance)
    }

    @Test
    fun `a region lands in the same relative place at every container size`() {
        val containers = listOf(
            1536f to 1024f,
            3072f to 2048f,
            2560f to 1600f,
            1920f to 1200f,
            // A split-screen window, which is the case a fixed pixel table gets wrong.
            900f to 800f,
        )

        val centre = homeFrame.region("HIT / Continue")!!
        for ((width, height) in containers) {
            val artwork = ArtworkGeometry.fittedBounds(homeFrame, width, height)
            val resolved = ArtworkGeometry.resolve(centre, homeFrame, width, height)

            val centreX = resolved.left + resolved.width / 2f
            val centreY = resolved.top + resolved.height / 2f
            assertEquals(
                artwork.left + artwork.width / 2f,
                centreX,
                tolerance,
                "Centre region drifted horizontally at ${width}x$height",
            )
            assertEquals(
                artwork.top + artwork.height / 2f,
                centreY,
                tolerance,
                "Centre region drifted vertically at ${width}x$height",
            )
        }
    }

    @Test
    fun `every region stays inside the drawn artwork`() {
        val width = 2000f
        val height = 1000f
        val artwork = ArtworkGeometry.fittedBounds(homeFrame, width, height)

        for (region in homeFrame.regions) {
            val resolved = ArtworkGeometry.resolve(region, homeFrame, width, height)
            assertTrue(resolved.left >= artwork.left - tolerance, "${region.id} escaped left")
            assertTrue(resolved.top >= artwork.top - tolerance, "${region.id} escaped top")
            assertTrue(resolved.right <= artwork.right + tolerance, "${region.id} escaped right")
            assertTrue(resolved.bottom <= artwork.bottom + tolerance, "${region.id} escaped bottom")
        }
    }

    @Test
    fun `normalizing native pixel coordinates round trips`() {
        // A control measured at x=384..768, y=256..512 in the Figma frame's own space.
        val normalized = ArtworkGeometry.normalize(homeFrame, 384f, 256f, 768f, 512f)

        assertEquals(0.25f, normalized.left, tolerance)
        assertEquals(0.25f, normalized.top, tolerance)
        assertEquals(0.5f, normalized.right, tolerance)
        assertEquals(0.5f, normalized.bottom, tolerance)

        // Resolved back at native size, it returns to the pixels it was measured at.
        val region = HitRegion("HIT / Test", normalized, "Test")
        val resolved = ArtworkGeometry.resolve(region, homeFrame, 1536f, 1024f)
        assertEquals(384f, resolved.left, tolerance)
        assertEquals(256f, resolved.top, tolerance)
        assertEquals(768f, resolved.right, tolerance)
        assertEquals(512f, resolved.bottom, tolerance)
    }

    @Test
    fun `malformed regions are rejected at construction`() {
        assertFailsWith<IllegalArgumentException>("Out-of-range fractions are a measuring mistake") {
            NormalizedRect(0f, 0f, 1.5f, 1f)
        }
        assertFailsWith<IllegalArgumentException>("An inverted rectangle has no meaning") {
            NormalizedRect(0.8f, 0f, 0.2f, 1f)
        }
        assertFailsWith<IllegalArgumentException>("A zero-area region can never be tapped") {
            NormalizedRect(0.2f, 0.2f, 0.2f, 0.5f)
        }
    }

    @Test
    fun `duplicate region ids are rejected`() {
        val duplicate = HitRegion("HIT / Menu", NormalizedRect(0f, 0f, 0.1f, 0.1f), "Open menu")
        assertFailsWith<IllegalArgumentException> {
            ArtworkSpec(1536, 1024, listOf(duplicate, duplicate))
        }
    }
}
