package com.harmonygates.core.midi

import com.harmonygates.core.music.pitch.MidiNote

/**
 * A normalised MIDI message.
 *
 * 05_MIDI_INPUT_ENGINE.md §4. "Normalised" means the awkward parts of the wire format have
 * already been resolved: running status is expanded, a Note On at velocity zero has become a
 * [NoteOff], and real-time bytes that arrived in the middle of another message have been
 * lifted out. Nothing downstream should ever have to know those things happen.
 *
 * Every event carries a monotonic timestamp taken as close to arrival as possible, because
 * onset spread is measured in tens of milliseconds and a timestamp taken after a hop through
 * the UI would be useless.
 */
public sealed interface MidiEvent {
    public val timestampNanos: Long

    /** MIDI channel 0..15. */
    public val channel: Int

    public data class NoteOn(
        val note: MidiNote,
        /** 1..127. A zero-velocity Note On is normalised to [NoteOff] and never appears here. */
        val velocity: Int,
        override val channel: Int,
        override val timestampNanos: Long,
    ) : MidiEvent {
        init {
            require(velocity in 1..127) { "Note On velocity must be 1..127, got $velocity" }
            require(channel in 0..15) { "Channel must be 0..15, got $channel" }
        }
    }

    public data class NoteOff(
        val note: MidiNote,
        /** Release velocity, 0..127. Most keyboards send 0 or 64. */
        val velocity: Int,
        override val channel: Int,
        override val timestampNanos: Long,
        /** True when this arrived as a Note On at velocity zero, which most keyboards send. */
        val fromZeroVelocityNoteOn: Boolean = false,
    ) : MidiEvent {
        init {
            require(velocity in 0..127) { "Note Off velocity must be 0..127, got $velocity" }
            require(channel in 0..15) { "Channel must be 0..15, got $channel" }
        }
    }

    public data class ControlChange(
        val controller: Int,
        val value: Int,
        override val channel: Int,
        override val timestampNanos: Long,
    ) : MidiEvent {
        init {
            require(controller in 0..127) { "Controller must be 0..127, got $controller" }
            require(value in 0..127) { "Control value must be 0..127, got $value" }
            require(channel in 0..15) { "Channel must be 0..15, got $channel" }
        }

        /** True for the sustain pedal, CC64. */
        public val isSustainPedal: Boolean get() = controller == Controller.SUSTAIN_PEDAL

        /** True for CC123, which asks every sounding note to stop. */
        public val isAllNotesOff: Boolean get() = controller == Controller.ALL_NOTES_OFF

        /**
         * Pedal state for a switch controller.
         *
         * The MIDI specification puts the threshold at 64: 0-63 is off, 64-127 is on. Half-pedal
         * capable instruments send the intermediate values, which this reads as down — a
         * partially depressed pedal is still sustaining.
         */
        public val isSwitchedOn: Boolean get() = value >= SWITCH_THRESHOLD
    }

    public data class PitchBend(
        /** -8192..8191, zero at rest. */
        val value: Int,
        override val channel: Int,
        override val timestampNanos: Long,
    ) : MidiEvent {
        init {
            require(value in -8192..8191) { "Pitch bend must be -8192..8191, got $value" }
            require(channel in 0..15) { "Channel must be 0..15, got $channel" }
        }
    }

    /** Channel pressure, sometimes called aftertouch. Not used for scoring. */
    public data class ChannelPressure(
        val pressure: Int,
        override val channel: Int,
        override val timestampNanos: Long,
    ) : MidiEvent

    /** Per-note aftertouch. Not used for scoring. */
    public data class PolyphonicAftertouch(
        val note: MidiNote,
        val pressure: Int,
        override val channel: Int,
        override val timestampNanos: Long,
    ) : MidiEvent

    public companion object {
        internal const val SWITCH_THRESHOLD = 64
    }
}

/** Controller numbers this app cares about. */
public object Controller {
    public const val SUSTAIN_PEDAL: Int = 64
    public const val SOSTENUTO_PEDAL: Int = 66
    public const val SOFT_PEDAL: Int = 67
    public const val ALL_SOUND_OFF: Int = 120
    public const val RESET_ALL_CONTROLLERS: Int = 121
    public const val ALL_NOTES_OFF: Int = 123
}
