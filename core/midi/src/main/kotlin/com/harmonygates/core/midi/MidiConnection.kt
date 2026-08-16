package com.harmonygates.core.midi

/**
 * A MIDI input port the app could listen to.
 *
 * [id] is stable for as long as the device stays plugged in, which is what reconnect logic
 * matches on. It is not stable across unplug and replug — Android issues a new device id — so
 * [name] and [manufacturer] carry the identity a player recognises.
 */
public data class MidiEndpoint(
    val id: String,
    val name: String,
    val manufacturer: String?,
    val portIndex: Int,
    /** True when Android reports the device as physically attached over USB. */
    val isUsb: Boolean,
) {
    /** What to show a player. Falls back to the raw name when there is no product string. */
    public val displayName: String get() = name.ifBlank { "MIDI device $id" }
}

/** Why MIDI is not working. Typed, so the UI can say something actionable. */
public sealed interface MidiError {
    /** The device or the build has no MIDI service at all. */
    public data object MidiUnsupported : MidiError

    /** The endpoint went away — unplugged, hub powered down, or claimed by another app. */
    public data class DeviceDisconnected(val endpoint: MidiEndpoint) : MidiError

    /** Android refused to open the port. */
    public data class OpenFailed(val endpoint: MidiEndpoint, val reason: String) : MidiError

    /** Anything the platform reported that does not fit the cases above. */
    public data class Unknown(val reason: String) : MidiError
}

/**
 * Where the MIDI connection currently stands (05_MIDI_INPUT_ENGINE.md §3).
 *
 * Modelled as a closed set of states rather than a bag of booleans, so a screen cannot render
 * "connected" and "no device" at once, and so every state has somewhere to put its own data.
 */
public sealed interface MidiConnectionState {
    /** No MIDI service on this device. */
    public data object Unsupported : MidiConnectionState

    /** MIDI works, but nothing is plugged in. */
    public data object NoDevice : MidiConnectionState

    /** Devices are present and none has been chosen yet. */
    public data class DevicesAvailable(val devices: List<MidiEndpoint>) : MidiConnectionState

    public data class Connecting(val endpoint: MidiEndpoint) : MidiConnectionState

    public data class Connected(val endpoint: MidiEndpoint) : MidiConnectionState

    public data class Error(val reason: MidiError) : MidiConnectionState

    /** True only when notes can actually arrive. */
    public val isReceiving: Boolean get() = this is Connected

    /** The endpoint this state concerns, when it concerns one. */
    public val activeEndpoint: MidiEndpoint?
        get() = when (this) {
            is Connecting -> endpoint
            is Connected -> endpoint
            is Error -> (reason as? MidiError.DeviceDisconnected)?.endpoint
            else -> null
        }
}
