package com.harmonygates.home

import com.harmonygates.core.designsystem.artwork.ArtworkSpec
import com.harmonygates.core.designsystem.artwork.HitRegion
import com.harmonygates.core.designsystem.artwork.NormalizedRect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The interaction map supplied alongside an approved screen.
 *
 * Mirrors the schema of `interface/maps/home.json`. Reading it as data rather than
 * transcribing its twenty rectangles into Kotlin means a re-export of the design updates the
 * app by replacing one file — which is what `interface/README.md` step 5 anticipates as more
 * screens arrive.
 *
 * Unknown fields are ignored so that a map carrying extra design metadata still loads.
 */
@Serializable
data class InteractionMap(
    val schemaVersion: Int = 1,
    val screen: String = "",
    val designSize: DesignSize = DesignSize(),
    val regions: List<MappedRegion> = emptyList(),
) {
    @Serializable
    data class DesignSize(
        val width: Int = 0,
        val height: Int = 0,
    )

    @Serializable
    data class MappedRegion(
        /** Stable id from the map. Informational; the app binds on [figmaLayer]. */
        val id: String = "",
        /** The Figma layer name, which is what `HomeAction` is keyed to. */
        @SerialName("figmaLayer") val figmaLayer: String,
        val action: String = "",
        @SerialName("boundsNormalized") val boundsNormalized: NormalizedBounds,
    )

    /** Origin-plus-size, as the map writes it. Converted to edges for the geometry layer. */
    @Serializable
    data class NormalizedBounds(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) {
        fun toRect(): NormalizedRect = NormalizedRect(
            left = x,
            top = y,
            // Clamped because a region flush against the right or bottom edge can exceed 1.0 by
            // a rounding step, and refusing the whole map over a float's last digit would be
            // the wrong trade.
            right = (x + width).coerceAtMost(1f),
            bottom = (y + height).coerceAtMost(1f),
        )
    }
}

/**
 * Turns a supplied map into the artwork spec the design system draws with.
 *
 * A region naming a layer the app does not know is skipped rather than fatal: a design file may
 * legitimately gain a control before the app has a destination for it, and losing the other
 * nineteen over one unknown name would be a poor trade. Skipped names are returned so a caller
 * can surface them, and `HomeActionTest` fails the build if the two lists drift apart.
 */
object InteractionMapReader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class Result(
        val spec: ArtworkSpec,
        val unmappedLayers: List<String>,
    )

    fun read(source: String, fallbackWidth: Int, fallbackHeight: Int): Result {
        val map = json.decodeFromString<InteractionMap>(source)
        return build(map, fallbackWidth, fallbackHeight)
    }

    fun build(map: InteractionMap, fallbackWidth: Int, fallbackHeight: Int): Result {
        val unmapped = mutableListOf<String>()
        val regions = map.regions.mapNotNull { region ->
            val action = HomeAction.forMappedRegion(region.action, region.figmaLayer)
            if (action == null) {
                unmapped += region.figmaLayer
                return@mapNotNull null
            }
            HitRegion(
                id = action.figmaRegionId,
                bounds = region.boundsNormalized.toRect(),
                // The spoken label comes from the app, not the map: the map names layers, and a
                // layer name is not something anyone wants read aloud.
                contentDescription = action.label,
            )
        }

        return Result(
            spec = ArtworkSpec(
                nativeWidth = map.designSize.width.takeIf { it > 0 } ?: fallbackWidth,
                nativeHeight = map.designSize.height.takeIf { it > 0 } ?: fallbackHeight,
                regions = regions,
            ),
            unmappedLayers = unmapped,
        )
    }
}
