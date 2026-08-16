package com.harmonygates.core.music.time

import java.time.Instant

/**
 * Injected time (20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §3).
 *
 * "Never call `System.currentTimeMillis()` or `Random.Default` deep inside domain logic."
 * Timing tests must not depend on wall-clock sleeps (14_TESTING_AND_QUALITY.md §6), which is
 * only possible if every consumer of time takes it as a dependency.
 */
public fun interface MonotonicClock {
    /** Nanoseconds from an arbitrary origin. Only differences are meaningful. */
    public fun nowNanos(): Long
}

/** Calendar time, for "last practised at" and review scheduling. */
public fun interface WallClock {
    public fun now(): Instant
}

/** Production monotonic clock. */
public object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

/** Production wall clock. */
public object SystemWallClock : WallClock {
    override fun now(): Instant = Instant.now()
}
