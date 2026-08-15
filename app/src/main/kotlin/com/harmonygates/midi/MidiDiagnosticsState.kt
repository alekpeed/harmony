package com.harmonygates.midi

import com.harmonygates.core.midi.ActiveNotes
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiEndpoint
import com.harmonygates.core.midi.MidiError
import com.harmonygates.core.midi.MidiEvent

/**
 * What the MIDI diagnostics screen shows.
 *
 * Phase 2 deliverable. It is the first place a player looks when the keyboard is not working,
 * so every state has to say what is wrong *and* what to do about it — 10_ANDROID_ARCHITECTURE.md
 * §11 asks for actionable user messages with the technical detail kept in the logs.
 */
data class MidiDiagnosticsState(
    val connection: MidiConnectionState = MidiConnectionState.NoDevice,
    val endpoints: List<MidiEndpoint> = emptyList(),
    val activeNotes: ActiveNotes = ActiveNotes(),
    val recentEvents: List<MidiEventLine> = emptyList(),
    /** Lowest and highest notes seen since the screen opened; the raw material for §10 range discovery. */
    val observedLowNote: Int? = null,
    val observedHighNote: Int? = null,
    val eventCount: Int = 0,
    /** True when the on-screen simulator is driving input instead of a real device. */
    val isSimulated: Boolean = false,
) {
    val isReceiving: Boolean get() = connection.isReceiving

    /** Keyboard range to draw. Widens to include anything actually played. */
    val keyboardRange: IntRange
        get() {
            val low = minOf(observedLowNote ?: DEFAULT_LOW, DEFAULT_LOW)
            val high = maxOf(observedHighNote ?: DEFAULT_HIGH, DEFAULT_HIGH)
            return low..high
        }

    /** A short line describing the connection, for the status chip. */
    val statusLabel: String
        get() = when (val state = connection) {
            is MidiConnectionState.Unsupported -> "No MIDI support"
            is MidiConnectionState.NoDevice -> "No keyboard"
            is MidiConnectionState.DevicesAvailable -> "${state.devices.size} available"
            is MidiConnectionState.Connecting -> "Connecting"
            is MidiConnectionState.Connected -> state.endpoint.displayName
            is MidiConnectionState.Error -> "Disconnected"
        }

    /** What to tell the player, and what they can do next. */
    val guidance: String
        get() = when (val state = connection) {
            is MidiConnectionState.Unsupported ->
                "This device has no MIDI support, so a keyboard cannot be used here. " +
                    "Everything else in the app still works."

            is MidiConnectionState.NoDevice ->
                "Connect a class-compliant USB MIDI keyboard. If it is already plugged in, " +
                    "check that the cable carries data rather than only power."

            is MidiConnectionState.DevicesAvailable ->
                "Choose which keyboard to listen to."

            is MidiConnectionState.Connecting ->
                "Opening the port."

            is MidiConnectionState.Connected ->
                "Play any key. Notes appear below as they arrive."

            is MidiConnectionState.Error -> when (val reason = state.reason) {
                is MidiError.DeviceDisconnected ->
                    "${reason.endpoint.displayName} was disconnected. Plug it back in and it " +
                        "will reconnect on its own; your progress is untouched."

                is MidiError.OpenFailed ->
                    "Could not open ${reason.endpoint.displayName}. Another app may be holding " +
                        "it — close that app and try again."

                is MidiError.MidiUnsupported ->
                    "This device has no MIDI support."

                is MidiError.Unknown ->
                    "MIDI stopped working: ${reason.reason}"
            }
        }

    private companion object {
        /** A five-octave window, which covers what most exercises will use. */
        const val DEFAULT_LOW = 36
        const val DEFAULT_HIGH = 96
    }
}

/** One line of the event log, already formatted for display. */
data class MidiEventLine(
    val label: String,
    val detail: String,
    val kind: Kind,
) {
    enum class Kind { NOTE_ON, NOTE_OFF, PEDAL, OTHER }

    companion object {
        /** Formats an event for the log. Note names are deliberately absent here — that is theory. */
        fun of(event: MidiEvent): MidiEventLine = when (event) {
            is MidiEvent.NoteOn -> MidiEventLine(
                label = "Note on ${event.note.value}",
                detail = "velocity ${event.velocity} · ch ${event.channel + 1}",
                kind = Kind.NOTE_ON,
            )

            is MidiEvent.NoteOff -> MidiEventLine(
                label = "Note off ${event.note.value}",
                detail = if (event.fromZeroVelocityNoteOn) {
                    "as note-on velocity 0 · ch ${event.channel + 1}"
                } else {
                    "velocity ${event.velocity} · ch ${event.channel + 1}"
                },
                kind = Kind.NOTE_OFF,
            )

            is MidiEvent.ControlChange -> MidiEventLine(
                label = if (event.isSustainPedal) {
                    "Sustain ${if (event.isSwitchedOn) "down" else "up"}"
                } else {
                    "CC ${event.controller}"
                },
                detail = "value ${event.value} · ch ${event.channel + 1}",
                kind = if (event.isSustainPedal) Kind.PEDAL else Kind.OTHER,
            )

            is MidiEvent.PitchBend -> MidiEventLine(
                label = "Pitch bend",
                detail = "${event.value} · ch ${event.channel + 1}",
                kind = Kind.OTHER,
            )

            is MidiEvent.ChannelPressure -> MidiEventLine(
                label = "Channel pressure",
                detail = "${event.pressure} · ch ${event.channel + 1}",
                kind = Kind.OTHER,
            )

            is MidiEvent.PolyphonicAftertouch -> MidiEventLine(
                label = "Aftertouch ${event.note.value}",
                detail = "${event.pressure} · ch ${event.channel + 1}",
                kind = Kind.OTHER,
            )
        }
    }
}

/** Everything the screen can ask for. */
sealed interface MidiDiagnosticsIntent {
    data object Refresh : MidiDiagnosticsIntent
    data class Connect(val endpoint: MidiEndpoint) : MidiDiagnosticsIntent
    data object ClearNotes : MidiDiagnosticsIntent
    data object ClearLog : MidiDiagnosticsIntent

    /** Swaps the real source for the simulator, so the screen can be checked with no hardware. */
    data class UseSimulator(val enabled: Boolean) : MidiDiagnosticsIntent

    /** Simulator only: play or release a note. */
    data class SimulateNote(val note: Int, val on: Boolean) : MidiDiagnosticsIntent

    /** Simulator only: move the pedal. */
    data class SimulateSustain(val down: Boolean) : MidiDiagnosticsIntent

    /** Simulator only: pull the cable, to check the reconnect path. */
    data object SimulateDisconnect : MidiDiagnosticsIntent
}
