package com.harmonygates.core.music.performance

import com.harmonygates.core.music.pitch.MidiNote

/**
 * One note within an attempt, with when it started and when it stopped.
 *
 * The release time is what lets an accidental neighbouring key be told apart from a note the
 * player meant: both are note-ons, and only the duration separates them.
 */
public data class NormalizedNoteEvent(
    val note: MidiNote,
    val velocity: Int,
    val onsetNanos: Long,
    /** Null while the key is still down at the moment the attempt completed. */
    val releaseNanos: Long? = null,
    /** True when the note was still sounding only because the pedal was down. */
    val heldByPedal: Boolean = false,
) {
    /** How long the key was down, or null if it never came up. */
    public val durationNanos: Long? get() = releaseNanos?.let { it - onsetNanos }

    public fun releasedWithin(graceNanos: Long): Boolean =
        durationNanos?.let { it <= graceNanos } == true
}

/**
 * What the player actually did.
 *
 * 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §2 is explicit that the event history survives:
 * "Do not throw away event history after reducing to final notes. Rhythm and accidental-note
 * diagnosis need it." So [noteEvents] is the record and [finalEffectiveNotes] is the reduction,
 * and both are kept.
 */
public data class PerformanceAttempt(
    val startedAtNanos: Long,
    val completedAtNanos: Long,
    val noteEvents: List<NormalizedNoteEvent>,
    /** The notes being judged, after grace-period and sustain policy have been applied. */
    val finalEffectiveNotes: List<MidiNote>,
    /** First onset to last onset. Null when fewer than two notes were played. */
    val onsetSpreadNanos: Long?,
    val sustainUsed: Boolean,
    /** Why capture ended. Diagnostic, and the reason a device loss is not scored as a wrong answer. */
    val completion: CaptureCompletion = CaptureCompletion.OnsetsSettled,
) {
    public val isEmpty: Boolean get() = finalEffectiveNotes.isEmpty()

    /** Time from arming to the first note. Response speed, which is not rhythmic accuracy. */
    public val responseLatencyNanos: Long?
        get() = noteEvents.minOfOrNull { it.onsetNanos }?.let { it - startedAtNanos }

    public val onsetSpreadMillis: Long? get() = onsetSpreadNanos?.let { it / NANOS_PER_MILLI }

    /** Notes the player let go of within [graceNanos] — candidates for an accidental key. */
    public fun briefNotes(graceNanos: Long): List<NormalizedNoteEvent> =
        noteEvents.filter { it.releasedWithin(graceNanos) }

    public companion object {
        /** Shared by every module that converts a policy in milliseconds into a timestamp. */
        public const val NANOS_PER_MILLI: Long = 1_000_000L

        /** An attempt where nothing was played. */
        public fun empty(startedAtNanos: Long, completedAtNanos: Long): PerformanceAttempt =
            PerformanceAttempt(
                startedAtNanos = startedAtNanos,
                completedAtNanos = completedAtNanos,
                noteEvents = emptyList(),
                finalEffectiveNotes = emptyList(),
                onsetSpreadNanos = null,
                sustainUsed = false,
                completion = CaptureCompletion.Abandoned,
            )
    }
}

/** Why an attempt stopped collecting. */
public enum class CaptureCompletion {
    /** The quiet window elapsed after the last onset: the normal ending. */
    OnsetsSettled,

    /** The player lifted every key. */
    AllKeysReleased,

    /** The roll window ran out while notes were still arriving. */
    MaxRollWindowReached,

    /** Something outside the player asked for the answer — a beat boundary, or a button. */
    ExplicitSubmit,

    /** The keyboard went away mid-chord. Never scored as a wrong answer. */
    DeviceLost,

    /** Capture was armed and nothing was played. */
    Abandoned,
}

/**
 * How much onset spread an exercise tolerates (05_MIDI_INPUT_ENGINE.md §7).
 *
 * Some exercises are about simultaneity; most are not. A jazz voicing rolled across 90 ms is a
 * normal way to play a chord, and failing it would teach the wrong lesson — but a rhythm
 * exercise that accepted the same spread would teach nothing at all.
 */
public sealed interface OnsetPolicy {
    /** The notes must land together. */
    public data class Simultaneous(val maxSpreadMillis: Int) : OnsetPolicy

    /** A roll is fine up to a limit. */
    public data class RolledAllowed(val maxSpreadMillis: Int) : OnsetPolicy

    /** Spread is not judged at all. */
    public data object Unrestricted : OnsetPolicy

    /** The limit in nanoseconds, or null when spread is not judged. */
    public val maxSpreadNanos: Long?
        get() = when (this) {
            is Simultaneous -> maxSpreadMillis * PerformanceAttempt.NANOS_PER_MILLI
            is RolledAllowed -> maxSpreadMillis * PerformanceAttempt.NANOS_PER_MILLI
            Unrestricted -> null
        }

    /** True when [spreadNanos] is within what this policy allows. */
    public fun permits(spreadNanos: Long?): Boolean {
        val limit = maxSpreadNanos ?: return true
        return (spreadNanos ?: 0L) <= limit
    }

    public companion object {
        /** Tight enough to mean "together", loose enough that two hands still qualify. */
        public val Together: OnsetPolicy = Simultaneous(maxSpreadMillis = 60)

        /** The default for chord exercises: a rolled voicing is a normal way to play one. */
        public val NormalRoll: OnsetPolicy = RolledAllowed(maxSpreadMillis = 250)
    }
}
