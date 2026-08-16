package com.harmonygates.core.midi

import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.time.MonotonicClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A keyboard that isn't there.
 *
 * 05_MIDI_INPUT_ENGINE.md §12: "The entire app must be testable without a physical keyboard in
 * CI." This is that. It implements the same interface as the real source, so a screen, a
 * session engine or a test cannot tell the difference.
 *
 * Time comes from an injected [MonotonicClock], so a 250 ms rolled chord costs a test no
 * wall-clock time at all (14_TESTING_AND_QUALITY.md §6). Nothing here sleeps.
 */
public class FakeMidiInputSource(
    private val clock: MonotonicClock,
    /** Endpoints this fake pretends to see. */
    initialEndpoints: List<MidiEndpoint> = listOf(DEFAULT_ENDPOINT),
    /** Start already connected, which is what most tests want. */
    autoConnect: Boolean = true,
) : MidiInputSource {

    private val endpoints = initialEndpoints.toMutableList()
    private val tracker = ActiveNoteTracker()

    private val _connectionState = MutableStateFlow<MidiConnectionState>(
        when {
            endpoints.isEmpty() -> MidiConnectionState.NoDevice
            autoConnect -> MidiConnectionState.Connected(endpoints.first())
            else -> MidiConnectionState.DevicesAvailable(endpoints.toList())
        },
    )
    override val connectionState: StateFlow<MidiConnectionState> = _connectionState.asStateFlow()

    // Replay of zero matches the real source: a late collector does not receive past notes.
    // The buffer keeps a synchronous emit from suspending when a collector is briefly busy.
    private val _events = MutableSharedFlow<MidiEvent>(replay = 0, extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<MidiEvent> = _events.asSharedFlow()

    private val _activeNotes = MutableStateFlow(ActiveNotes())
    override val activeNotes: StateFlow<ActiveNotes> = _activeNotes.asStateFlow()

    /** Every event this fake has emitted, for tests that assert on the whole stream. */
    public val emitted: List<MidiEvent> get() = recorded.toList()
    private val recorded = mutableListOf<MidiEvent>()

    private var started = false

    override suspend fun start() {
        started = true
        if (_connectionState.value is MidiConnectionState.NoDevice && endpoints.isNotEmpty()) {
            _connectionState.value = MidiConnectionState.DevicesAvailable(endpoints.toList())
        }
    }

    override suspend fun stop() {
        started = false
        clearActiveNotes()
        _connectionState.value =
            if (endpoints.isEmpty()) MidiConnectionState.NoDevice
            else MidiConnectionState.DevicesAvailable(endpoints.toList())
    }

    override suspend fun availableEndpoints(): List<MidiEndpoint> = endpoints.toList()

    override suspend fun connectTo(endpoint: MidiEndpoint) {
        _connectionState.value = MidiConnectionState.Connecting(endpoint)
        _connectionState.value = MidiConnectionState.Connected(endpoint)
    }

    override fun clearActiveNotes() {
        _activeNotes.value = tracker.reset()
    }

    // --- Simulation ---------------------------------------------------------------------

    /** Emits an already-formed event. */
    public suspend fun emit(event: MidiEvent) {
        recorded += event
        _activeNotes.value = tracker.apply(event)
        _events.emit(event)
    }

    public suspend fun noteOn(note: Int, velocity: Int = DEFAULT_VELOCITY, channel: Int = 0) {
        emit(MidiEvent.NoteOn(MidiNote(note), velocity, channel, clock.nowNanos()))
    }

    /** Releases a key the way real keyboards do, as a Note On at velocity zero. */
    public suspend fun noteOff(note: Int, channel: Int = 0, asZeroVelocityNoteOn: Boolean = true) {
        emit(
            MidiEvent.NoteOff(
                note = MidiNote(note),
                velocity = 0,
                channel = channel,
                timestampNanos = clock.nowNanos(),
                fromZeroVelocityNoteOn = asZeroVelocityNoteOn,
            ),
        )
    }

    public suspend fun sustain(down: Boolean, channel: Int = 0) {
        emit(
            MidiEvent.ControlChange(
                controller = Controller.SUSTAIN_PEDAL,
                value = if (down) SUSTAIN_ON else 0,
                channel = channel,
                timestampNanos = clock.nowNanos(),
            ),
        )
    }

    /** Plays [notes] together, exactly simultaneously. */
    public suspend fun chord(vararg notes: Int, velocity: Int = DEFAULT_VELOCITY) {
        notes.forEach { noteOn(it, velocity) }
    }

    /** Releases [notes] together. */
    public suspend fun releaseChord(vararg notes: Int) {
        notes.forEach { noteOff(it) }
    }

    /**
     * Feeds raw bytes through a real parser.
     *
     * Lets a test exercise running status or a split message end to end, rather than only the
     * tidy events the helpers above produce.
     */
    public suspend fun receiveBytes(bytes: ByteArray, parser: MidiMessageParser = sharedParser) {
        parser.parse(bytes, clock.nowNanos()).forEach { emit(it) }
    }

    private val sharedParser = MidiMessageParser()

    // --- Hotplug ------------------------------------------------------------------------

    /**
     * Pulls the cable.
     *
     * Clears sounding notes, because a chord held at the moment of disconnection is not being
     * held any more, and reports the typed reason so the UI can offer reconnection rather than
     * a dead end (05_MIDI_INPUT_ENGINE.md §9).
     */
    public fun disconnect() {
        val endpoint = _connectionState.value.activeEndpoint ?: endpoints.firstOrNull()
        _activeNotes.value = tracker.reset()
        endpoints.clear()
        _connectionState.value = if (endpoint != null) {
            MidiConnectionState.Error(MidiError.DeviceDisconnected(endpoint))
        } else {
            MidiConnectionState.NoDevice
        }
    }

    /** Plugs a keyboard back in. */
    public fun reconnect(endpoint: MidiEndpoint = DEFAULT_ENDPOINT) {
        if (endpoints.none { it.id == endpoint.id }) endpoints += endpoint
        _connectionState.value = MidiConnectionState.Connected(endpoint)
    }

    /** Reports that this device has no MIDI support at all. */
    public fun reportUnsupported() {
        endpoints.clear()
        _activeNotes.value = tracker.reset()
        _connectionState.value = MidiConnectionState.Unsupported
    }

    /** True once [start] has been called and [stop] has not. */
    public val isStarted: Boolean get() = started

    public companion object {
        public val DEFAULT_ENDPOINT: MidiEndpoint = MidiEndpoint(
            id = "fake-0",
            name = "Simulated Keyboard",
            manufacturer = "Harmony Gates",
            portIndex = 0,
            isUsb = true,
        )

        private const val DEFAULT_VELOCITY = 80
        private const val SUSTAIN_ON = 127
        private const val EVENT_BUFFER = 64
    }
}
