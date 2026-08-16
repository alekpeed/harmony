package com.harmonygates.core.music.random

import kotlin.random.Random

/**
 * Deterministic randomness for exercise generation.
 *
 * Non-negotiable design rule 6: "Generated exercises must be deterministic when supplied the
 * same seed." That only holds if nothing in the generation path reaches for a global RNG, so
 * randomness is a dependency here rather than an ambient capability.
 */
public interface RandomSource {
    /** Uniform in `0 until bound`. */
    public fun nextInt(bound: Int): Int

    /** Uniform in `from until until`. */
    public fun nextInt(from: Int, until: Int): Int

    public fun nextLong(): Long

    /** Uniform in `[0, 1)`. */
    public fun nextDouble(): Double

    public fun nextBoolean(): Boolean

    /** Uniformly picks one element. */
    public fun <T> pick(items: List<T>): T

    /** Picks one element with the given relative weights. */
    public fun <T> pickWeighted(items: List<T>, weight: (T) -> Double): T

    /** A shuffled copy; the receiver is untouched. */
    public fun <T> shuffled(items: List<T>): List<T>

    /** [count] distinct elements, or all of them when the list is shorter. */
    public fun <T> sample(items: List<T>, count: Int): List<T>
}

/** Creates a [RandomSource] fixed to a seed. */
public fun interface SeededRandomFactory {
    public fun create(seed: Long): RandomSource
}

/**
 * Kotlin's `Random(seed)` behind the domain interface.
 *
 * The implementation is specified by the standard library and stable across platforms and
 * releases, so a stored seed reproduces the same exercise on any device — which is what makes
 * a diagnostic export reproducible (10_ANDROID_ARCHITECTURE.md §12).
 */
public class KotlinRandomSource(private val random: Random) : RandomSource {

    override fun nextInt(bound: Int): Int = random.nextInt(bound)

    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)

    override fun nextLong(): Long = random.nextLong()

    override fun nextDouble(): Double = random.nextDouble()

    override fun nextBoolean(): Boolean = random.nextBoolean()

    override fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "Cannot pick from an empty list" }
        return items[random.nextInt(items.size)]
    }

    override fun <T> pickWeighted(items: List<T>, weight: (T) -> Double): T {
        require(items.isNotEmpty()) { "Cannot pick from an empty list" }
        val weights = items.map { weight(it) }
        require(weights.all { it >= 0.0 }) { "Weights must be non-negative" }
        val total = weights.sum()
        require(total > 0.0) { "At least one weight must be positive" }
        var remaining = random.nextDouble() * total
        for (index in items.indices) {
            remaining -= weights[index]
            if (remaining <= 0.0) return items[index]
        }
        // Only reachable through floating-point drift at the very end of the range.
        return items.last()
    }

    override fun <T> shuffled(items: List<T>): List<T> = items.shuffled(random)

    override fun <T> sample(items: List<T>, count: Int): List<T> {
        require(count >= 0) { "Sample size must be non-negative: $count" }
        return shuffled(items).take(count)
    }
}

/** Production factory. */
public object DefaultSeededRandomFactory : SeededRandomFactory {
    override fun create(seed: Long): RandomSource = KotlinRandomSource(Random(seed))
}
