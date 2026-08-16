package com.harmonygates.progression

import com.harmonygates.core.designsystem.progression.DesignSlot
import com.harmonygates.core.designsystem.progression.TrackGeometry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Progression Run rendering contract, as supplied in `interface/maps/progression-run.json`.
 *
 * `interface/PROGRESSION_RUN_HANDOFF.md` §11 names that file "the current runtime/rendering
 * contract", so the slot path, the advance duration and the easing curve are read from it
 * rather than transcribed into Kotlin. A re-composed track is then a re-exported map, not a
 * code change — which is the same deal the home screen's interaction map already has.
 *
 * The map's non-track hit regions are deliberately *not* read. §11 of the map itself marks them
 * as pending — "do not reuse coordinates from the deleted 49:* prototype frames" — so the
 * screen's own controls are laid out in Compose until a remapped frame arrives.
 */
@Serializable
data class TrackMap(
    val schemaVersion: Int = 1,
    val screen: String = "",
    val designSize: DesignSize = DesignSize(),
    val trackRenderer: TrackRenderer = TrackRenderer(),
) {
    @Serializable
    data class DesignSize(val width: Int = 0, val height: Int = 0)

    @Serializable
    data class TrackRenderer(
        val visibleWindow: VisibleWindow = VisibleWindow(),
        val slotPath: List<Slot> = emptyList(),
        val advanceAnimation: AdvanceAnimation = AdvanceAnimation(),
    )

    @Serializable
    data class VisibleWindow(
        val previous: Int = 1,
        val current: Int = 1,
        val upcoming: Int = 6,
    )

    @Serializable
    data class Slot(
        val relativeIndex: Int,
        @SerialName("centerPx") val center: Point,
        @SerialName("diameterPx") val diameter: Float,
        val role: String = "",
    )

    @Serializable
    data class Point(val x: Float, val y: Float)

    @Serializable
    data class AdvanceAnimation(
        val durationMs: Int = DEFAULT_DURATION_MS,
        val easing: String = DEFAULT_EASING,
    )

    companion object {
        const val DEFAULT_DURATION_MS = 650
        const val DEFAULT_EASING = "cubic-bezier(0.22, 1, 0.36, 1)"
    }
}

/** Everything the screen needs in order to draw the track. */
data class TrackSpec(
    val geometry: TrackGeometry,
    val aspectRatio: Float,
    val slotsBehind: Int,
    val slotsAhead: Int,
    val advanceDurationMs: Int,
    /** The four control points of the easing curve, as written in the map. */
    val easing: List<Float>,
)

/**
 * Turns a supplied map into a track the renderer can draw.
 *
 * Falls back to the approved composition when a field is missing rather than to nothing: a map
 * that loses its slot path should leave the screen playable, because the run is a MIDI exercise
 * first and a picture second.
 */
object TrackMapReader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun read(source: String): TrackSpec = build(json.decodeFromString<TrackMap>(source))

    fun build(map: TrackMap): TrackSpec {
        val width = map.designSize.width.takeIf { it > 0 } ?: DEFAULT_DESIGN_WIDTH
        val height = map.designSize.height.takeIf { it > 0 } ?: DEFAULT_DESIGN_HEIGHT
        val slots = map.trackRenderer.slotPath.takeIf { it.isNotEmpty() } ?: DEFAULT_SLOTS

        val geometry = TrackGeometry.fromDesignPixels(
            designWidth = width,
            designHeight = height,
            slots = slots.map { DesignSlot(it.relativeIndex, it.center.x, it.center.y, it.diameter) },
        )

        return TrackSpec(
            geometry = geometry,
            aspectRatio = width.toFloat() / height.toFloat(),
            // One slot wider than the visible window at each end: the orb leaving and the orb
            // arriving have to exist somewhere before they can animate into place.
            slotsBehind = map.trackRenderer.visibleWindow.previous + 1,
            slotsAhead = map.trackRenderer.visibleWindow.upcoming + 1,
            advanceDurationMs = map.trackRenderer.advanceAnimation.durationMs,
            easing = parseCubicBezier(map.trackRenderer.advanceAnimation.easing),
        )
    }

    /** Reads `cubic-bezier(a, b, c, d)`, falling back to the approved curve. */
    private fun parseCubicBezier(value: String): List<Float> {
        val numbers = CUBIC_BEZIER.find(value)
            ?.groupValues
            ?.drop(1)
            ?.mapNotNull { it.trim().toFloatOrNull() }
            .orEmpty()
        return if (numbers.size == CONTROL_POINTS) numbers else DEFAULT_EASING
    }

    private val CUBIC_BEZIER =
        Regex("""cubic-bezier\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*,\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)""")

    private const val CONTROL_POINTS = 4

    val DEFAULT_EASING: List<Float> = listOf(0.22f, 1f, 0.36f, 1f)

    const val DEFAULT_DESIGN_WIDTH = 1536
    const val DEFAULT_DESIGN_HEIGHT = 1024

    /** The approved eight-slot path, used only when the map does not supply one. */
    private val DEFAULT_SLOTS = listOf(
        TrackMap.Slot(-1, TrackMap.Point(380f, 570f), 100f),
        TrackMap.Slot(0, TrackMap.Point(565f, 548f), 102f),
        TrackMap.Slot(1, TrackMap.Point(758f, 501f), 86f),
        TrackMap.Slot(2, TrackMap.Point(914f, 457f), 80f),
        TrackMap.Slot(3, TrackMap.Point(1027f, 404f), 72f),
        TrackMap.Slot(4, TrackMap.Point(1138f, 373f), 66f),
        TrackMap.Slot(5, TrackMap.Point(1245f, 351f), 66f),
        TrackMap.Slot(6, TrackMap.Point(1348f, 335f), 60f),
    )
}
