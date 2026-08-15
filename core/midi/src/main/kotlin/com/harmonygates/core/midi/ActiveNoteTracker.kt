package com.harmonygates.core.midi

import com.harmonygates.core.music.pitch.MidiNote

/**
 * What is sounding right now.
 *
 * 05_MIDI_INPUT_ENGINE.md §5 requires three sets, kept apart:
 *
 * - [physicallyHeld] — keys the player's fingers are on
 * - [pedalSustained] — keys released, still sounding because the pedal is down
 * - [sounding] — what a listener actually hears, the union of the two
 *
 * They are genuinely different questions. "Did you play a C major seventh?" is about what is
 * sounding. "Are your fingers still on it?" is about the first set. A single "active notes"
 * collection would have to pick one and be wrong about the other.
 */
public data class ActiveNotes(
    val physicallyHeld: Set<MidiNote> = emptySet(),
    val pedalSustained: Set<MidiNote> = emptySet(),
    val sustainPedalDown: Boolean = false,
    /** Velocity of each held note, for feedback and for later scoring. */
    val velocities: Map<MidiNote, Int> = emptyMap(),
) {
    /** Everything a listener would hear. */
    public val sounding: Set<MidiNote> get() = physicallyHeld + pedalSustained

    public val isSilent: Boolean get() = sounding.isEmpty()

    /** Sounding notes in pitch order, which is how a keyboard visualisation wants them. */
    public val soundingAscending: List<MidiNote> get() = sounding.sorted()
}

/**
 * Folds MIDI events into [ActiveNotes].
 *
 * Pure and synchronous, so the noisy cases 05_MIDI_INPUT_ENGINE.md §8 lists — duplicate note-on
 * from a device quirk, repeated note-off, a pedal held across a chord change — are all ordinary
 * unit tests rather than something only a physical keyboard can produce.
 *
 * Not thread-safe: one tracker per source, fed from that source's callback.
 */
public class ActiveNoteTracker {

    private val physicallyHeld = LinkedHashSet<MidiNote>()
    private val pedalSustained = LinkedHashSet<MidiNote>()
    private val velocities = LinkedHashMap<MidiNote, Int>()
    private var sustainDown = false

    /** Current state. A snapshot; later mutation does not change a value already handed out. */
    public fun snapshot(): ActiveNotes = ActiveNotes(
        physicallyHeld = physicallyHeld.toSet(),
        pedalSustained = pedalSustained.toSet(),
        sustainPedalDown = sustainDown,
        velocities = velocities.toMap(),
    )

    /**
     * Applies one event and returns the state after it.
     *
     * Events that cannot change what is sounding — pitch bend, aftertouch — leave the state
     * untouched rather than being rejected, so a caller can feed the whole stream through.
     */
    public fun apply(event: MidiEvent): ActiveNotes {
        when (event) {
            is MidiEvent.NoteOn -> {
                // A repeated Note On for a held key is a device quirk, not a second note. Set
                // semantics absorb it; the velocity is updated because a re-strike is louder.
                physicallyHeld += event.note
                velocities[event.note] = event.velocity
                // Re-striking a pedal-held note takes it back under the finger.
                pedalSustained -= event.note
            }

            is MidiEvent.NoteOff -> {
                val wasHeld = physicallyHeld.remove(event.note)
                if (sustainDown && wasHeld) {
                    // The finger left, the pedal did not: the note keeps sounding.
                    pedalSustained += event.note
                } else if (!sustainDown) {
                    pedalSustained -= event.note
                    velocities -= event.note
                }
            }

            is MidiEvent.ControlChange -> applyControlChange(event)

            is MidiEvent.PitchBend,
            is MidiEvent.ChannelPressure,
            is MidiEvent.PolyphonicAftertouch,
            -> Unit
        }
        return snapshot()
    }

    /** Applies a sequence, returning the state after the last event. */
    public fun applyAll(events: Iterable<MidiEvent>): ActiveNotes {
        var state = snapshot()
        for (event in events) state = apply(event)
        return state
    }

    /**
     * Drops everything.
     *
     * Called on disconnect. 05_MIDI_INPUT_ENGINE.md §9 requires active-note state to be cleared
     * when a device goes away — otherwise a chord that was being held at the moment the cable
     * was pulled stays "sounding" forever and poisons the next attempt.
     */
    public fun reset(): ActiveNotes {
        physicallyHeld.clear()
        pedalSustained.clear()
        velocities.clear()
        sustainDown = false
        return snapshot()
    }

    /**
     * Drops sustained remnants but keeps what the fingers are still on.
     *
     * This is the "ignore sustained remnants from the previous attempt" policy in §5: a new
     * exercise should not mark a correct chord wrong because the pedal was still down from the
     * last one.
     */
    public fun clearSustainedRemnants(): ActiveNotes {
        pedalSustained.forEach { velocities -= it }
        pedalSustained.clear()
        return snapshot()
    }

    private fun applyControlChange(event: MidiEvent.ControlChange) {
        when {
            event.isSustainPedal -> {
                val nowDown = event.isSwitchedOn
                if (sustainDown && !nowDown) {
                    // Pedal up: everything held only by the pedal stops.
                    pedalSustained.forEach { velocities -= it }
                    pedalSustained.clear()
                }
                sustainDown = nowDown
            }

            event.isAllNotesOff || event.controller == Controller.ALL_SOUND_OFF -> {
                physicallyHeld.clear()
                pedalSustained.clear()
                velocities.clear()
            }

            event.controller == Controller.RESET_ALL_CONTROLLERS -> {
                if (sustainDown) {
                    pedalSustained.forEach { velocities -= it }
                    pedalSustained.clear()
                }
                sustainDown = false
            }
        }
    }
}
