package com.harmonygates.core.designsystem.progression

/**
 * One perspective slot on the progression track, as fractions of the design frame.
 *
 * The approved composition places eight of these along a path running away from the player.
 * Storing them as fractions rather than as the map's 1536x1024 pixels is the same rule the home
 * screen follows: a slot authored against the design frame lands correctly on any tablet.
 *
 * @param relativeIndex -1 is the chord just played, 0 the play point, positive is upcoming.
 * @param diameter orb size as a fraction of the frame *width*, so an orb stays circular
 *   whatever the frame's aspect ratio turns out to be.
 */
public data class TrackAnchor(
    val relativeIndex: Int,
    val centerX: Float,
    val centerY: Float,
    val diameter: Float,
) {
    init {
        require(diameter > 0f) { "An orb slot needs a positive diameter: $this" }
    }
}

/** A point on the track: where an orb sits and how big it is there. */
public data class TrackPoint(
    val x: Float,
    val y: Float,
    val diameter: Float,
)

/**
 * The path the chords travel along.
 *
 * The eight anchors are positions, not orbs. An orb is somewhere *between* them for most of an
 * advance, so the geometry is sampled continuously and the renderer never has to special-case
 * a chord that is halfway between two slots.
 *
 * Beyond either end the path is extended along its own last direction, which is what lets a
 * chord recede off the near end and a new one arrive from the far end instead of appearing.
 */
public data class TrackGeometry(val anchors: List<TrackAnchor>) {

    init {
        require(anchors.size >= MINIMUM_ANCHORS) { "A track needs at least two slots to run between" }
        require(anchors.map { it.relativeIndex } == anchors.map { it.relativeIndex }.sorted()) {
            "Track slots must be ordered from the nearest to the farthest"
        }
        require(anchors.map { it.relativeIndex }.distinct().size == anchors.size) {
            "Two slots cannot share a relative index"
        }
    }

    public val nearestIndex: Int get() = anchors.first().relativeIndex
    public val farthestIndex: Int get() = anchors.last().relativeIndex

    /** The visible slot count, e.g. 8 for one previous, one active and six upcoming. */
    public val slotCount: Int get() = anchors.size

    /**
     * Where a chord [position] slots along the track sits.
     *
     * Fractional positions interpolate; positions outside the anchored range extrapolate along
     * the end segment, so the geometry answers for the exiting and arriving orbs too.
     */
    public fun sample(position: Float): TrackPoint {
        val lowerIndex = anchors.indexOfLast { it.relativeIndex <= position }
        return when {
            lowerIndex < 0 -> interpolate(anchors[0], anchors[1], position)
            lowerIndex == anchors.lastIndex ->
                interpolate(anchors[anchors.lastIndex - 1], anchors[anchors.lastIndex], position)

            else -> interpolate(anchors[lowerIndex], anchors[lowerIndex + 1], position)
        }
    }

    private fun interpolate(from: TrackAnchor, to: TrackAnchor, position: Float): TrackPoint {
        val span = (to.relativeIndex - from.relativeIndex).toFloat()
        val t = (position - from.relativeIndex) / span
        return TrackPoint(
            x = from.centerX + (to.centerX - from.centerX) * t,
            y = from.centerY + (to.centerY - from.centerY) * t,
            // Clamped so an orb extrapolated well past the near end cannot invert through zero
            // and start growing again on the way out.
            diameter = (from.diameter + (to.diameter - from.diameter) * t).coerceAtLeast(MINIMUM_DIAMETER),
        )
    }

    public companion object {
        private const val MINIMUM_ANCHORS = 2
        private const val MINIMUM_DIAMETER = 0.001f

        /**
         * Builds a track from the map's design-space pixel coordinates.
         *
         * `interface/maps/progression-run.json` publishes the slot path in the 1536x1024 frame
         * it was composed in; this is the one place those pixels become fractions.
         */
        public fun fromDesignPixels(
            designWidth: Int,
            designHeight: Int,
            slots: List<DesignSlot>,
        ): TrackGeometry {
            require(designWidth > 0 && designHeight > 0) { "The design frame needs a positive size" }
            return TrackGeometry(
                slots.sortedBy { it.relativeIndex }.map { slot ->
                    TrackAnchor(
                        relativeIndex = slot.relativeIndex,
                        centerX = slot.centerX / designWidth,
                        centerY = slot.centerY / designHeight,
                        diameter = slot.diameter / designWidth,
                    )
                },
            )
        }
    }
}

/** One slot as the interaction map writes it: design-frame pixels. */
public data class DesignSlot(
    val relativeIndex: Int,
    val centerX: Float,
    val centerY: Float,
    val diameter: Float,
)
