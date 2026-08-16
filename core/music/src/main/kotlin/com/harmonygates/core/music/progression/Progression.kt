package com.harmonygates.core.music.progression

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.key.FunctionalChord
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.performance.OnsetPolicy
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingPolicy

/** Where a progression came from. Mirrors the `source` field of `interface/maps/progression-run.json`. */
public enum class ProgressionSource {
    /** A built-in template such as a major ii-V-I. */
    PRESET,

    /** Authored curriculum content. */
    CURRICULUM,

    /** A standard's form. */
    STANDARD,

    /** Produced by a generator from a seed. */
    GENERATED,

    /** Assembled by the player. */
    CUSTOM,
}

/**
 * One chord in a progression.
 *
 * `interface/PROGRESSION_RUN_HANDOFF.md` §8 lists what a chord event has to carry: symbol,
 * function label, root, quality, extensions, alterations, bass, duration, and the root, bass,
 * inversion, omission and voicing-family policies that decide whether a performance counts. It
 * also says not to duplicate theory models that already exist — so all of that lives in the
 * [chord] and [policy] this event already holds, and nothing here restates it.
 *
 * @param chord the chord correctness is judged against. For a rootless family this is the
 *   symbol extended with the tensions the shape sounds, which is why [displaySymbol] is stored
 *   separately: the player is asked for `C7`, not for `C7add9add13`.
 * @param policy which renderings of that chord this event accepts.
 * @param function the roman numeral, when the progression was built from one. Shown as the
 *   secondary label on an orb, and only when the run's assistance settings allow it.
 */
public data class ChordEvent(
    val id: String,
    val chord: ChordSpec,
    val policy: VoicingPolicy,
    val displaySymbol: String = chord.symbol,
    val function: FunctionalChord? = null,
    val durationBeats: Int = DEFAULT_DURATION_BEATS,
    val voicingFamily: VoicingFamily? = null,
    val onsetPolicy: OnsetPolicy = OnsetPolicy.NormalRoll,
    /** What the player is being asked for beyond the symbol, e.g. "Rootless A: 3 13 b7 9". */
    val instruction: String? = null,
) {
    init {
        require(id.isNotBlank()) { "A chord event needs an id" }
        require(durationBeats > 0) { "A chord event must last at least one beat: $durationBeats" }
    }

    /** The roman numeral, when there is one. */
    public val functionLabel: String? get() = function?.symbol

    /**
     * What a performance has to satisfy to advance the track.
     *
     * The run engine evaluates this, never the string in [displaySymbol] — which is the rule
     * the handoff states twice: "evaluate the actual `ChordEvent` at `activeChordIndex`", "do
     * not judge correctness from the string displayed inside the orb".
     */
    public val requirement: ExerciseRequirement
        get() = ExerciseRequirement.ChordPolicyMatch(chord, policy)

    override fun toString(): String = displaySymbol

    public companion object {
        public const val DEFAULT_DURATION_BEATS: Int = 4
    }
}

/**
 * An ordered run of chords.
 *
 * Length is deliberately unbounded. The handoff is explicit that the eight visible orb slots
 * are a viewport and not a limit, and that the four Figma frames are animation snapshots rather
 * than a four-chord exercise — so a ii-V-I of three events and a cycle-of-fourths drill of
 * thirty-six are the same type, and the renderer windows whichever it is given.
 */
public data class Progression(
    val id: String,
    val title: String,
    val key: KeyContext,
    val events: List<ChordEvent>,
    val source: ProgressionSource = ProgressionSource.PRESET,
    val loop: Boolean = false,
    val tempoBpm: Int = DEFAULT_TEMPO_BPM,
    val meter: String = DEFAULT_METER,
) {
    init {
        require(id.isNotBlank()) { "A progression needs an id" }
        require(events.isNotEmpty()) { "A progression needs at least one chord" }
        require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
        val duplicates = events.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        require(duplicates.isEmpty()) { "Duplicate chord event ids: ${duplicates.keys}" }
    }

    public val size: Int get() = events.size

    public fun eventAt(index: Int): ChordEvent? = events.getOrNull(index)

    /**
     * The events visible around [activeIndex], each with the perspective slot it occupies.
     *
     * This is the windowing the track renderer draws: one chord behind the play point, one on
     * it, and the rest running away up the track. Everything outside the window stays in the
     * progression as data, which is what makes orb recycling possible rather than mounting one
     * object per chord.
     */
    public fun window(
        activeIndex: Int,
        behind: Int = DEFAULT_SLOTS_BEHIND,
        ahead: Int = DEFAULT_SLOTS_AHEAD,
    ): List<VisibleChord> {
        require(behind >= 0 && ahead >= 0) { "A window cannot have negative width" }
        return (activeIndex - behind..activeIndex + ahead).mapNotNull { index ->
            val resolved = if (loop) index.mod(size) else index
            events.getOrNull(resolved)?.let { event ->
                VisibleChord(
                    event = event,
                    eventIndex = index,
                    relativeSlot = index - activeIndex,
                    // A looping run passes the same chord more than once, so the identity a
                    // renderer keys on has to be the pass, not the chord.
                    instanceId = if (loop) "${event.id}#${index.floorDiv(size)}" else event.id,
                )
            }
        }
    }

    public companion object {
        public const val DEFAULT_TEMPO_BPM: Int = 120
        public const val DEFAULT_METER: String = "4/4"

        /** One chord of history, six upcoming: the eight-slot landscape composition. */
        public const val DEFAULT_SLOTS_BEHIND: Int = 1
        public const val DEFAULT_SLOTS_AHEAD: Int = 6
    }
}

/** One chord as the track currently shows it. */
public data class VisibleChord(
    val event: ChordEvent,
    /** Index into the progression. Can exceed the event count on a looping run. */
    val eventIndex: Int,
    /** Perspective slot: -1 is the chord just played, 0 the play point, positive is upcoming. */
    val relativeSlot: Int,
    /** Stable across an advance, so a renderer moves an orb rather than replacing it. */
    val instanceId: String,
)
