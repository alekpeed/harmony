package com.harmonygates.core.music.score

/**
 * A musical duration or position, exactly.
 *
 * 08_SIGHT_READING_ENGINE.md §3 is unusually direct about this: "Use rational musical duration
 * internally. Do not represent eighth notes as accumulated floating-point seconds." The reason
 * shows up the moment a bar of triplets is summed — three of a third is exactly one in
 * rationals and 0.9999999999999999 in doubles, and a renderer that has quietly overrun the
 * barline draws the next measure a pixel out and the one after that two.
 *
 * Always stored in lowest terms with a positive denominator, so equality is value equality and
 * a half note is a half note however it was arrived at.
 */
public data class RationalBeat(
    val numerator: Long,
    val denominator: Long,
) : Comparable<RationalBeat> {

    init {
        require(denominator != 0L) { "A duration cannot have a denominator of zero" }
    }

    /** The double value. For rendering and for talking to a clock — never for arithmetic. */
    public val toDouble: Double get() = numerator.toDouble() / denominator

    public val isZero: Boolean get() = numerator == 0L

    public operator fun plus(other: RationalBeat): RationalBeat =
        of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    public operator fun minus(other: RationalBeat): RationalBeat =
        of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)

    public operator fun times(factor: Int): RationalBeat = of(numerator * factor, denominator)

    public operator fun times(other: RationalBeat): RationalBeat =
        of(numerator * other.numerator, denominator * other.denominator)

    public operator fun div(divisor: Int): RationalBeat {
        require(divisor != 0) { "Cannot divide a duration by zero" }
        return of(numerator, denominator * divisor)
    }

    override fun compareTo(other: RationalBeat): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    /**
     * Adds half again, the way a dot does.
     *
     * A dotted quarter is exactly three eighths, and this is the only place that fact has to be
     * written down.
     */
    public fun dotted(dots: Int = 1): RationalBeat {
        require(dots >= 0) { "A negative number of dots is not notation" }
        var total = this
        var added = this
        repeat(dots) {
            added /= 2
            total += added
        }
        return total
    }

    /** Milliseconds at a tempo, for the one place where beats have to become time. */
    public fun toMillis(tempoBpm: Int): Long {
        require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
        return numerator * MILLIS_PER_MINUTE / (denominator * tempoBpm)
    }

    override fun toString(): String = if (denominator == 1L) "$numerator" else "$numerator/$denominator"

    public companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        public val ZERO: RationalBeat = RationalBeat(0, 1)

        /** One quarter note, the unit everything else is expressed in. */
        public val QUARTER: RationalBeat = RationalBeat(1, 1)
        public val WHOLE: RationalBeat = RationalBeat(4, 1)
        public val HALF: RationalBeat = RationalBeat(2, 1)
        public val EIGHTH: RationalBeat = RationalBeat(1, 2)
        public val SIXTEENTH: RationalBeat = RationalBeat(1, 4)

        /** One of a triplet in the time of a quarter. */
        public val TRIPLET_EIGHTH: RationalBeat = RationalBeat(1, 3)

        /** Reduces to lowest terms and moves any sign onto the numerator. */
        public fun of(numerator: Long, denominator: Long): RationalBeat {
            require(denominator != 0L) { "A duration cannot have a denominator of zero" }
            val sign = if (denominator < 0) -1 else 1
            val n = numerator * sign
            val d = denominator * sign
            val divisor = gcd(kotlin.math.abs(n), d)
            return RationalBeat(n / divisor, d / divisor)
        }

        public fun of(whole: Int): RationalBeat = RationalBeat(whole.toLong(), 1)

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) maxOf(a, 1) else gcd(b, a % b)
    }
}

/** A time signature. */
public data class TimeSignature(val beats: Int, val beatUnit: Int) {
    init {
        require(beats > 0) { "A bar needs at least one beat" }
        require(beatUnit in ALLOWED_UNITS) { "Unsupported beat unit: $beatUnit" }
    }

    /** How long a full bar is, in quarter notes. */
    public val measureLength: RationalBeat
        get() = RationalBeat.of(beats.toLong() * QUARTER_UNIT, beatUnit.toLong())

    /** How long one beat is, in quarter notes: a quarter in 4/4, an eighth in 6/8. */
    public val beatLength: RationalBeat get() = RationalBeat.of(QUARTER_UNIT, beatUnit.toLong())

    override fun toString(): String = "$beats/$beatUnit"

    public companion object {
        private const val QUARTER_UNIT = 4L
        private val ALLOWED_UNITS = setOf(1, 2, 4, 8, 16)

        public val FOUR_FOUR: TimeSignature = TimeSignature(4, 4)
        public val THREE_FOUR: TimeSignature = TimeSignature(3, 4)
        public val SIX_EIGHT: TimeSignature = TimeSignature(6, 8)
    }
}
