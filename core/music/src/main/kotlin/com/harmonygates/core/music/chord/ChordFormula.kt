package com.harmonygates.core.music.chord

/** Stable identifier for a chord formula. Content JSON references these strings. */
@JvmInline
public value class ChordFormulaId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Chord formula id must not be blank" }
    }

    override fun toString(): String = value
}

/** Broad sonority family. Drives roman-numeral casing and curriculum grouping. */
public enum class ChordQuality {
    MAJOR,
    MINOR,
    DOMINANT,
    DIMINISHED,
    HALF_DIMINISHED,
    AUGMENTED,
    SUSPENDED,
    MINOR_MAJOR,
    QUARTAL,
    OTHER,
}

/**
 * The degrees that define a sonority.
 *
 * The required/optional split is the engine's answer to a recurring jazz question: is the
 * fifth of a G13 part of the chord? It is *available*, not *mandatory*, and pretending
 * otherwise would fail every real rootless voicing. 21_CONTENT_AUTHORING_GUIDE.md §7 makes
 * the same point about instruction copy — a context-dependent practice must not be encoded
 * as a universal law.
 *
 * @param requiredDegrees tones without which the sonority is a different chord.
 * @param optionalDegrees tones that belong but may be omitted.
 * @param forbiddenDegrees tones that contradict the sonority, e.g. a natural 5 in `7alt`.
 * @param stackOverride the written stack when it differs from required-plus-optional. A
 *   dominant thirteenth *permits* a natural eleventh in a performance but is not *written*
 *   with one, and conflating those two facts would either mark good answers wrong or print a
 *   chord nobody plays.
 * @param aliases every chord-symbol suffix that parses to this formula.
 * @param canonicalSuffix the suffix used when rendering a symbol back to text.
 */
public data class ChordFormula(
    val id: ChordFormulaId,
    val quality: ChordQuality,
    val requiredDegrees: Set<ChordDegree>,
    val optionalDegrees: Set<ChordDegree> = emptySet(),
    val forbiddenDegrees: Set<ChordDegree> = emptySet(),
    val stackOverride: Set<ChordDegree>? = null,
    val aliases: Set<String> = emptySet(),
    val canonicalSuffix: String,
    /** Human-readable name for instruction copy and diagnostics. */
    val displayName: String,
) {
    init {
        require(ChordDegree.ROOT in requiredDegrees) {
            "Every formula must declare the root as required: $id"
        }
        val overlap = requiredDegrees intersect optionalDegrees
        require(overlap.isEmpty()) { "Degrees cannot be both required and optional in $id: $overlap" }
        val contradiction = (requiredDegrees + optionalDegrees) intersect forbiddenDegrees
        require(contradiction.isEmpty()) { "Degrees cannot be both present and forbidden in $id: $contradiction" }
        stackOverride?.let { stack ->
            require(requiredDegrees.all { it in stack }) { "The written stack of $id must include every required tone" }
            require(stack.all { it in requiredDegrees || it in optionalDegrees }) {
                "The written stack of $id may only contain tones the chord permits"
            }
        }
    }

    /** Every tone the chord permits: required plus optional. */
    public val permittedDegrees: Set<ChordDegree> get() = requiredDegrees + optionalDegrees

    /** The tones a lead sheet would write out, in stack order. */
    public val canonicalDegrees: Set<ChordDegree> get() = stackOverride ?: permittedDegrees

    /** The third, or the suspended tone that replaces it. */
    public val qualityDegree: ChordDegree?
        get() = canonicalDegrees.firstOrNull { it.number == 3 }
            ?: canonicalDegrees.firstOrNull { it.number == 4 || it.number == 2 }

    /** The seventh, or the sixth in a 6-chord. */
    public val guideToneDegree: ChordDegree?
        get() = canonicalDegrees.firstOrNull { it.number == 7 }
            ?: canonicalDegrees.firstOrNull { it.number == 6 }

    public fun contains(degree: ChordDegree): Boolean = degree in canonicalDegrees
}
