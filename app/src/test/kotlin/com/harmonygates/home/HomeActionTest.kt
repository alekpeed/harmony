package com.harmonygates.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The contract between the app and the approved Figma frame.
 *
 * The region names are copied here verbatim from `interface/README.md`. That duplication is the
 * point: if the design file renames a layer, this test fails and names the layer, rather than a
 * control silently going dead on a screen nobody notices until a player taps it.
 */
class HomeActionTest {

    /** Exactly the twenty regions listed in interface/README.md, in the order given there. */
    private val approvedRegionIds = listOf(
        "HIT / Menu",
        "HIT / Nav Home",
        "HIT / Nav Map",
        "HIT / Nav Practice",
        "HIT / Nav Stats",
        "HIT / Nav Library",
        "HIT / Nav Profile",
        "HIT / Nav Settings",
        "HIT / Profile Summary",
        "HIT / Chord Gates",
        "HIT / Ear Trainer",
        "HIT / Sight Reading",
        "HIT / Progression Run",
        "HIT / Voice Leading",
        "HIT / Theory Lab",
        "HIT / Daily Challenge",
        "HIT / My Journey",
        "HIT / Next Gate Card",
        "HIT / Continue",
        "HIT / Streak Summary",
    )

    @Test
    fun `every approved region has exactly one action`() {
        for (regionId in approvedRegionIds) {
            assertNotNull(
                HomeAction.forRegion(regionId),
                "The approved home frame contains '$regionId' but nothing is wired to it",
            )
        }
    }

    @Test
    fun `no action refers to a region the design file does not contain`() {
        for (action in HomeAction.entries) {
            assertTrue(
                action.figmaRegionId in approvedRegionIds,
                "${action.name} points at '${action.figmaRegionId}', which is not in the approved frame",
            )
        }
    }

    @Test
    fun `the action list and the approved region list are the same size`() {
        assertEquals(approvedRegionIds.size, HomeAction.entries.size)
    }

    @Test
    fun `region ids are unique`() {
        val ids = HomeAction.entries.map { it.figmaRegionId }
        assertEquals(ids.size, ids.distinct().size, "Two actions claim the same region")
    }

    @Test
    fun `every action has a spoken label that is not just its layer name`() {
        for (action in HomeAction.entries) {
            assertTrue(action.label.isNotBlank(), "${action.name} has no accessibility label")
            assertTrue(
                !action.label.startsWith("HIT"),
                "${action.name} reads its Figma layer name aloud instead of its action",
            )
        }
    }

    @Test
    fun `the theory lab is reachable today and the rest are honest about their phase`() {
        assertTrue(
            HomeAction.TheoryLab.destination.isImplemented,
            "The Phase 1 harness exists, so its control must work",
        )
        for (action in HomeAction.entries) {
            if (!action.destination.isImplemented) {
                assertTrue(
                    action.destination.arrivesInPhase > 1,
                    "${action.name} is unimplemented but claims to arrive in phase " +
                        "${action.destination.arrivesInPhase}",
                )
            }
        }
    }

    @Test
    fun `an unknown region resolves to nothing rather than to the wrong action`() {
        assertNull(HomeAction.forRegion("HIT / Not A Real Region"))
        assertNull(HomeAction.forRegion(""))
        assertNull(HomeAction.forRegion("Menu"), "Matching must be on the full layer name")
    }

    @Test
    fun `the artwork spec matches the native size in the handoff README`() {
        assertEquals(1536, HomeArtwork.NATIVE_WIDTH)
        assertEquals(1024, HomeArtwork.NATIVE_HEIGHT)
        assertEquals(1536f / 1024f, HomeArtwork.spec.aspectRatio, 0.0001f)
    }

    @Test
    fun `the spec contains a region for every bound that has been measured`() {
        assertEquals(
            HomeArtwork.regionBounds.size,
            HomeArtwork.spec.regions.size,
            "Every measured region should reach the spec",
        )
        for ((action, _) in HomeArtwork.regionBounds) {
            assertNotNull(
                HomeArtwork.spec.region(action.figmaRegionId),
                "${action.name} was measured but is missing from the spec",
            )
        }
    }

    @Test
    fun `the artwork is reported unavailable until both the export and the regions exist`() {
        // Guards against the half-integrated state: artwork present with no regions is a
        // screen full of dead pixels, and regions with no artwork is invisible buttons.
        val expected = HomeArtwork.drawableResId != null && HomeArtwork.regionBounds.isNotEmpty()
        assertEquals(expected, HomeArtwork.isAvailable)
    }
}
