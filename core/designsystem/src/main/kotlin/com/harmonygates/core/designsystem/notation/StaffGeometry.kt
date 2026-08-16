package com.harmonygates.core.designsystem.notation

/**
 * A note head to draw, in staff coordinates.
 *
 * `core:designsystem` must not depend on `core:music`, so nothing here is a pitch — the caller
 * has already worked out where the note sits and what accidental it carries. That keeps the
 * renderer incapable of forming an opinion about music, which is the same rule the chord orbs
 * follow.
 *
 * @param steps position above the middle line: 0 is the middle line, 1 the space above it.
 * @param accidental the glyph to draw before it, or null for none.
 * @param filled whether the head is solid. A quarter is, a half is not.
 */
public data class NoteHead(
    val steps: Int,
    val accidental: String? = null,
    val filled: Boolean = true,
    val stemUp: Boolean = true,
    val dots: Int = 0,
)

/** One thing on the staff at one horizontal position. */
public data class NotationEvent(
    /** 0..1 across the width of the system. */
    val position: Float,
    /** How wide this event's slot is, as a fraction of the system. */
    val width: Float,
    val heads: List<NoteHead>,
    val isRest: Boolean = false,
    /** Written above the staff. */
    val chordSymbol: String? = null,
    /** Highlighted as the current beat. */
    val isCurrent: Boolean = false,
)

/** A staff to draw. */
public data class StaffSystem(
    val clefGlyph: String,
    val keySignatureAccidentals: List<Int>,
    val timeSignature: Pair<Int, Int>?,
    val events: List<NotationEvent>,
    /** Fractions of the width where barlines fall. */
    val barlines: List<Float> = emptyList(),
)

/**
 * The arithmetic of a five-line staff.
 *
 * Kept apart from the drawing for the same reason `ArtworkGeometry` is: this is the part that
 * can be wrong in a way a screenshot will not reveal, and it is testable on the JVM. 08 §2 asks
 * for layout geometry in Kotlin, and this is that geometry.
 *
 * Vertical positions are in *steps*, where one step is half a staff space — a line to the space
 * above it. Everything is expressed as a fraction of the staff's line spacing, so the same
 * numbers serve any size.
 */
public object StaffGeometry {

    /** A five-line staff spans eight steps: four either side of the middle line. */
    public const val STAFF_STEPS: Int = 8

    /** Where a step sits, measured downward from the top line, in line spacings. */
    public fun offsetFromTopLine(steps: Int): Float = (TOP_LINE_STEPS - steps) / 2f

    /** The vertical positions of the five lines, in line spacings from the top. */
    public val lineOffsets: List<Float> = (0 until STAFF_LINES).map { it.toFloat() }

    /**
     * Ledger-line positions for a note, in line spacings from the top line.
     *
     * Negative values sit above the staff, values beyond four below it. A note two spaces below
     * the staff needs a line at each intervening line position, not one at its own head.
     */
    public fun ledgerOffsets(steps: Int): List<Float> = when {
        steps > TOP_LINE_STEPS -> {
            // Even steps are lines; a note on a space needs the line below it, not its own.
            val highest = steps - if (steps % 2 == 0) 0 else 1
            (TOP_LINE_STEPS + 2..highest step 2).map { offsetFromTopLine(it) }
        }

        steps < BOTTOM_LINE_STEPS -> {
            val lowest = steps + if (steps % 2 == 0) 0 else 1
            (lowest..BOTTOM_LINE_STEPS - 2 step 2).map { offsetFromTopLine(it) }
        }

        else -> emptyList()
    }

    /**
     * Which way the stem points.
     *
     * Below the middle line stems go up, above it they go down — the convention that keeps
     * stems inside the staff. A note exactly on the middle line takes a down stem.
     */
    public fun stemUp(steps: Int): Boolean = steps < 0

    /**
     * Lays events out across the width.
     *
     * Space is proportional to duration, which is what makes a half note look twice as long as a
     * quarter and lets a reader see the rhythm before working it out. Not true engraving —
     * 08 §1 is explicit that this is "a training notation renderer, not a general-purpose
     * publishing engraver" — but enough that the page reads.
     *
     * @param durations each event's length, in any consistent unit.
     * @param leadingFraction space reserved at the left for clef, key and time signature.
     */
    public fun layout(
        durations: List<Double>,
        leadingFraction: Float = DEFAULT_LEADING,
    ): List<Pair<Float, Float>> {
        require(leadingFraction in 0f..HALF) { "The leading space cannot take the whole system" }
        if (durations.isEmpty()) return emptyList()

        val total = durations.sum()
        if (total <= 0.0) return durations.map { leadingFraction to 0f }

        val available = 1f - leadingFraction
        var cursor = leadingFraction
        return durations.map { duration ->
            val width = (duration / total).toFloat() * available
            val start = cursor
            cursor += width
            start to width
        }
    }

    private const val STAFF_LINES = 5

    /** The top line is four steps above the middle line; the bottom line four below. */
    private const val TOP_LINE_STEPS = 4
    private const val BOTTOM_LINE_STEPS = -4

    private const val DEFAULT_LEADING = 0.18f
    private const val HALF = 0.5f
}
