package com.harmonygates.core.testing

import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory
import com.harmonygates.core.music.time.MonotonicClock
import com.harmonygates.core.music.time.WallClock
import java.time.Duration
import java.time.Instant

/**
 * A monotonic clock the test drives by hand.
 *
 * 14_TESTING_AND_QUALITY.md §6: "Never rely only on wall-clock sleeps in tests." A rolled
 * chord spread over 250 ms should take zero milliseconds to test.
 */
public class TestMonotonicClock(startNanos: Long = 0L) : MonotonicClock {
    private var currentNanos: Long = startNanos

    override fun nowNanos(): Long = currentNanos

    public fun advanceNanos(nanos: Long) {
        require(nanos >= 0) { "A monotonic clock cannot go backwards" }
        currentNanos += nanos
    }

    public fun advanceMillis(millis: Long): Unit = advanceNanos(millis * NANOS_PER_MILLI)

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** A wall clock the test drives by hand. */
public class TestWallClock(private var current: Instant = Instant.parse("2026-01-01T00:00:00Z")) : WallClock {
    override fun now(): Instant = current

    public fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    public fun set(instant: Instant) {
        current = instant
    }
}

/**
 * A random source that returns a scripted sequence.
 *
 * Useful when a test needs a *specific* exercise rather than merely a reproducible one. The
 * sequence repeats, so a test does not have to predict how many draws the code under test makes.
 */
public class ScriptedRandomSource(
    private val ints: List<Int> = listOf(0),
    private val doubles: List<Double> = listOf(0.0),
    private val booleans: List<Boolean> = listOf(false),
) : RandomSource {

    private var intIndex = 0
    private var doubleIndex = 0
    private var booleanIndex = 0

    init {
        require(ints.isNotEmpty() && doubles.isNotEmpty() && booleans.isNotEmpty()) {
            "A scripted random source needs at least one value of each kind"
        }
    }

    private fun nextScriptedInt(): Int = ints[intIndex++ % ints.size]

    override fun nextInt(bound: Int): Int = nextScriptedInt().mod(bound)

    override fun nextInt(from: Int, until: Int): Int = from + nextScriptedInt().mod(until - from)

    override fun nextLong(): Long = nextScriptedInt().toLong()

    override fun nextDouble(): Double = doubles[doubleIndex++ % doubles.size]

    override fun nextBoolean(): Boolean = booleans[booleanIndex++ % booleans.size]

    override fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "Cannot pick from an empty list" }
        return items[nextInt(items.size)]
    }

    override fun <T> pickWeighted(items: List<T>, weight: (T) -> Double): T = pick(items)

    override fun <T> shuffled(items: List<T>): List<T> = items

    override fun <T> sample(items: List<T>, count: Int): List<T> = items.take(count)
}

/** Hands out the same scripted source no matter the seed, for tests that pin an exact outcome. */
public class ScriptedRandomFactory(private val source: RandomSource) : SeededRandomFactory {
    override fun create(seed: Long): RandomSource = source
}
