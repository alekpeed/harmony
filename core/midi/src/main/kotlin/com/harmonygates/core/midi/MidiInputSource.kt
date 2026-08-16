package com.harmonygates.core.midi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where MIDI comes from.
 *
 * The single seam the rest of the app sees (AGENTS.md: "MIDI access goes through
 * `MidiInputSource`"). Production talks to `android.media.midi`; tests and CI use
 * [FakeMidiInputSource]. Nothing above this interface can tell which it has, which is what
 * makes the whole app testable without a keyboard (05_MIDI_INPUT_ENGINE.md §12).
 */
public interface MidiInputSource {

    /** Where the connection stands. Always has a current value. */
    public val connectionState: StateFlow<MidiConnectionState>

    /**
     * Normalised events, hot.
     *
     * A collector that starts late does not receive earlier notes — a note played before anyone
     * was listening is not a note the player is answering with.
     */
    public val events: Flow<MidiEvent>

    /** Which notes are sounding, folded from [events]. */
    public val activeNotes: StateFlow<ActiveNotes>

    /** Begins discovery and opens a device when one is available. Idempotent. */
    public suspend fun start()

    /** Closes the port and stops discovery. Idempotent. */
    public suspend fun stop()

    /** Endpoints currently visible, whether or not one is open. */
    public suspend fun availableEndpoints(): List<MidiEndpoint>

    /**
     * Opens a specific endpoint.
     *
     * Used when a player has more than one keyboard attached and picks between them, and by the
     * reconnect path after a hotplug.
     */
    public suspend fun connectTo(endpoint: MidiEndpoint)

    /**
     * Forgets sounding notes without closing the port.
     *
     * Armed before a new attempt so a chord held over from the previous one cannot be scored as
     * part of the next.
     */
    public fun clearActiveNotes()
}
