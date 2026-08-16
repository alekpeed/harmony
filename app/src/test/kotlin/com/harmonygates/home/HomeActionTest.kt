package com.harmonygates.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `the declared native size matches the handoff README`() {
        assertEquals(1536, HomeArtwork.DECLARED_WIDTH)
        assertEquals(1024, HomeArtwork.DECLARED_HEIGHT)
    }

    @Test
    fun `map action ids are unique`() {
        val ids = HomeAction.entries.map { it.mapActionId }
        assertEquals(ids.size, ids.distinct().size, "Two actions claim the same semantic action id")
        assertTrue(ids.none { it.isBlank() }, "Every action needs a semantic id from the map")
    }

    @Test
    fun `the supplied map binds every one of its regions to an action`() {
        val result = readSuppliedMap()
        assertTrue(
            result.unmappedLayers.isEmpty(),
            "interface/maps/home.json contains regions the app cannot place: ${result.unmappedLayers}",
        )
        assertEquals(
            approvedRegionIds.size,
            result.spec.regions.size,
            "Every approved region should reach the artwork spec",
        )
    }

    @Test
    fun `the supplied map agrees with the app on both keys for every region`() {
        val map = suppliedMap()
        for (region in map.regions) {
            val byAction = HomeAction.forMapAction(region.action)
            val byLayer = HomeAction.forRegion(region.figmaLayer)
            assertNotNull(byAction, "No action for semantic id '${region.action}'")
            assertNotNull(byLayer, "No action for layer '${region.figmaLayer}'")
            assertEquals(
                byLayer,
                byAction,
                "'${region.figmaLayer}' and '${region.action}' resolve to different actions",
            )
        }
    }

    @Test
    fun `the supplied map declares the size the README promises`() {
        val map = suppliedMap()
        assertEquals(HomeArtwork.DECLARED_WIDTH, map.designSize.width)
        assertEquals(HomeArtwork.DECLARED_HEIGHT, map.designSize.height)
    }

    @Test
    fun `every mapped region sits inside the artwork`() {
        for (region in readSuppliedMap().spec.regions) {
            val bounds = region.bounds
            assertTrue(bounds.left >= 0f && bounds.top >= 0f, "${region.id} starts outside the artwork")
            assertTrue(bounds.right <= 1f && bounds.bottom <= 1f, "${region.id} extends past the artwork")
            assertTrue(bounds.area > 0f, "${region.id} has no tappable area")
        }
    }

    @Test
    fun `a region nested inside another is hit tested first`() {
        // The approved frame puts Continue inside the Next Gate card. In declaration order the
        // card would cover the button and swallow every tap on it.
        val spec = readSuppliedMap().spec
        val order = spec.regionsInHitTestOrder.map { it.id }
        val card = order.indexOf(HomeAction.NextGateCard.figmaRegionId)
        val continueButton = order.indexOf(HomeAction.Continue.figmaRegionId)

        assertTrue(card >= 0 && continueButton >= 0, "Both regions should be present")
        assertTrue(
            continueButton > card,
            "Continue must be laid out after the card that contains it, so it receives taps",
        )
    }

    @Test
    fun `an unknown layer is skipped rather than losing the whole map`() {
        val map = InteractionMap(
            designSize = InteractionMap.DesignSize(1536, 1024),
            regions = listOf(
                InteractionMap.MappedRegion(
                    figmaLayer = "HIT / Theory Lab",
                    action = "navigate_theory_lab",
                    boundsNormalized = InteractionMap.NormalizedBounds(0.1f, 0.1f, 0.2f, 0.2f),
                ),
                InteractionMap.MappedRegion(
                    figmaLayer = "HIT / Not Yet Built",
                    action = "navigate_nowhere",
                    boundsNormalized = InteractionMap.NormalizedBounds(0.5f, 0.5f, 0.2f, 0.2f),
                ),
            ),
        )

        val result = InteractionMapReader.build(map, 1536, 1024)
        assertEquals(1, result.spec.regions.size, "The known region should survive")
        assertEquals(listOf("HIT / Not Yet Built"), result.unmappedLayers)
    }

    @Test
    fun `bounds flush against an edge are clamped rather than rejected`() {
        // Rounding in an export can push right or bottom a hair past 1.0; refusing the whole
        // map over a float's last digit would be the wrong trade.
        val rect = InteractionMap.NormalizedBounds(0.9f, 0.9f, 0.1000001f, 0.1000001f).toRect()
        assertEquals(1f, rect.right)
        assertEquals(1f, rect.bottom)
    }

    private fun suppliedMap(): InteractionMap = JSON.decodeFromString(mapSource())

    private fun readSuppliedMap(): InteractionMapReader.Result =
        InteractionMapReader.read(mapSource(), HomeArtwork.DECLARED_WIDTH, HomeArtwork.DECLARED_HEIGHT)

    /**
     * Reads the map from `interface/` rather than from the packaged resource.
     *
     * Testing the supplied file directly is the point: the build copies it into resources, so a
     * test against the copy would only prove the copy worked. Unit tests run with the module
     * directory as their working directory.
     */
    private fun mapSource(): String {
        val file = java.io.File("../interface/maps/home.json")
        assertTrue(file.isFile, "Expected the supplied interaction map at ${file.absolutePath}")
        return file.readText()
    }

    private companion object {
        val JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
