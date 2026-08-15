package com.harmonygates.core.designsystem.artwork

/**
 * Where an interactive control sits on a piece of artwork, as fractions of the artwork.
 *
 * `interface/README.md` step 4: "Keep interaction/layout logic responsive in Jetpack Compose
 * rather than baking in one-device pixel coordinates." Fractions are how that is enforced —
 * a region authored against the 1536x1024 frame lands correctly on a 2560x1600 tablet, a
 * 1920x1200 one, and inside a split-screen window, with no per-device tables.
 *
 * All four values are 0..1, measured from the artwork's top-left.
 */
public data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) {
            "Normalized bounds must be fractions of the artwork: $this"
        }
        require(right > left && bottom > top) { "Normalized bounds must have positive area: $this" }
    }

    public val width: Float get() = right - left
    public val height: Float get() = bottom - top

    /** Fraction of the artwork this region covers. Used to order nested regions. */
    public val area: Float get() = width * height
}

/** A rectangle in device pixels, resolved against a particular container size. */
public data class PixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    public val width: Float get() = right - left
    public val height: Float get() = bottom - top
}

/**
 * One named transparent region from the approved Figma frame.
 *
 * [id] is the Figma layer name verbatim, e.g. `HIT / Ear Trainer`. Keeping the exact string
 * means a region can be traced back to the design file without a translation table.
 */
public data class HitRegion(
    val id: String,
    val bounds: NormalizedRect,
    /** Spoken by accessibility services, so it must read as an action, not a layer name. */
    val contentDescription: String,
)

/**
 * An approved screen: its native artwork size and the regions placed on it.
 *
 * @param nativeWidth artwork width in pixels, e.g. 1536 for the approved home frame.
 * @param nativeHeight artwork height in pixels, e.g. 1024.
 */
public data class ArtworkSpec(
    val nativeWidth: Int,
    val nativeHeight: Int,
    val regions: List<HitRegion>,
) {
    init {
        require(nativeWidth > 0 && nativeHeight > 0) { "Artwork must have a positive size" }
        val duplicates = regions.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        require(duplicates.isEmpty()) { "Duplicate hit region ids: ${duplicates.keys}" }
    }

    public val aspectRatio: Float get() = nativeWidth.toFloat() / nativeHeight.toFloat()

    public fun region(id: String): HitRegion? = regions.firstOrNull { it.id == id }

    /**
     * Regions ordered so that a nested one can still be tapped.
     *
     * The approved home frame has a Continue button sitting inside the Next Gate card. Laid out
     * in declaration order the card would cover the button and swallow every tap on it, so the
     * larger region is placed first and the smaller on top. Ties break on id, keeping the order
     * stable rather than dependent on however the map happened to be written.
     */
    public val regionsInHitTestOrder: List<HitRegion>
        get() = regions.sortedWith(compareByDescending<HitRegion> { it.bounds.area }.thenBy { it.id })
}

/**
 * Maps normalized region bounds onto whatever size the artwork actually drew at.
 *
 * Kept free of Compose types so it can be unit-tested on the JVM. The arithmetic is the part
 * that goes wrong — a region that is a few percent out is invisible in a screenshot and very
 * obvious under a finger.
 */
public object ArtworkGeometry {

    /**
     * The rectangle a fitted image occupies inside its container.
     *
     * Matches `ContentScale.Fit`: the artwork is scaled until it just fits, and centred, so the
     * letterboxing sits evenly on the two sides that have room to spare.
     */
    public fun fittedBounds(
        spec: ArtworkSpec,
        containerWidth: Float,
        containerHeight: Float,
    ): PixelRect {
        require(containerWidth > 0f && containerHeight > 0f) { "Container must have a positive size" }
        val scale = minOf(containerWidth / spec.nativeWidth, containerHeight / spec.nativeHeight)
        val drawnWidth = spec.nativeWidth * scale
        val drawnHeight = spec.nativeHeight * scale
        val offsetX = (containerWidth - drawnWidth) / 2f
        val offsetY = (containerHeight - drawnHeight) / 2f
        return PixelRect(offsetX, offsetY, offsetX + drawnWidth, offsetY + drawnHeight)
    }

    /** Resolves one region against a container of the given size. */
    public fun resolve(
        region: HitRegion,
        spec: ArtworkSpec,
        containerWidth: Float,
        containerHeight: Float,
    ): PixelRect {
        val artwork = fittedBounds(spec, containerWidth, containerHeight)
        return PixelRect(
            left = artwork.left + region.bounds.left * artwork.width,
            top = artwork.top + region.bounds.top * artwork.height,
            right = artwork.left + region.bounds.right * artwork.width,
            bottom = artwork.top + region.bounds.bottom * artwork.height,
        )
    }

    /**
     * Converts pixel bounds measured on the native artwork into normalized bounds.
     *
     * This is the function to reach for when the Figma export gives coordinates in the frame's
     * own 1536x1024 space: convert once at authoring time and store fractions.
     */
    public fun normalize(
        spec: ArtworkSpec,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): NormalizedRect = NormalizedRect(
        left = left / spec.nativeWidth,
        top = top / spec.nativeHeight,
        right = right / spec.nativeWidth,
        bottom = bottom / spec.nativeHeight,
    )
}
