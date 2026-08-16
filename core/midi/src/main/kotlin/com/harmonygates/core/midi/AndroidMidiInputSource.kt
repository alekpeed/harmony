package com.harmonygates.core.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.harmonygates.core.music.time.MonotonicClock
import com.harmonygates.core.music.time.SystemMonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The production MIDI source, over `android.media.midi`.
 *
 * Deliberately thin. Everything that decides musical meaning lives in [MidiMessageParser] and
 * [ActiveNoteTracker], which are plain Kotlin and fully unit-tested; this class only handles
 * discovery, opening a port, and surviving a cable being pulled.
 *
 * The receive callback does as little as possible — parse, fold, emit — because
 * 10_ANDROID_ARCHITECTURE.md §10 forbids heavy work on the MIDI thread. It never parses JSON,
 * touches a database, or blocks.
 */
public class AndroidMidiInputSource(
    context: Context,
    private val scope: CoroutineScope,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : MidiInputSource {

    private val appContext = context.applicationContext
    private val midiManager: MidiManager? =
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else {
            null
        }

    private val parser = MidiMessageParser()
    private val tracker = ActiveNoteTracker()

    private val _connectionState = MutableStateFlow<MidiConnectionState>(
        if (midiManager == null) MidiConnectionState.Unsupported else MidiConnectionState.NoDevice,
    )
    override val connectionState: StateFlow<MidiConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<MidiEvent>(replay = 0, extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<MidiEvent> = _events.asSharedFlow()

    private val _activeNotes = MutableStateFlow(ActiveNotes())
    override val activeNotes: StateFlow<ActiveNotes> = _activeNotes.asStateFlow()

    /**
     * A dedicated thread for MIDI callbacks.
     *
     * Keeps note delivery off the main thread, so a busy frame cannot delay an onset timestamp
     * and skew the spread measurement a chord is judged by.
     */
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    private var openDevice: MidiDevice? = null
    private var openPort: MidiOutputPort? = null
    private var openEndpoint: MidiEndpoint? = null
    private var started = false

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            scope.launch {
                // A keyboard appearing while nothing is open is the reconnect case: take it.
                if (openPort == null) connectToFirstAvailable() else refreshAvailability()
            }
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            scope.launch {
                val lost = openEndpoint
                if (lost != null && device.endpointId() == lost.id) {
                    handleDisconnection(lost)
                } else {
                    refreshAvailability()
                }
            }
        }
    }

    private val receiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            // Timestamped here rather than downstream: this is the closest the app gets to
            // when the key actually moved.
            val arrivedAt = clock.nowNanos()
            val parsed = parser.parse(data, offset, count, arrivedAt)
            if (parsed.isEmpty()) return

            var notes = _activeNotes.value
            for (event in parsed) notes = tracker.apply(event)
            _activeNotes.value = notes

            // tryEmit rather than emit: this callback must not suspend. A dropped event under
            // extreme backpressure is better than a stalled MIDI thread, and the active-note
            // state above has already been updated regardless.
            for (event in parsed) _events.tryEmit(event)
        }
    }

    override suspend fun start() {
        val manager = midiManager ?: run {
            _connectionState.value = MidiConnectionState.Unsupported
            return
        }
        if (started) return
        started = true

        val thread = HandlerThread("harmony-midi").apply { start() }
        callbackThread = thread
        callbackHandler = Handler(thread.looper)

        registerDeviceCallback(manager, callbackHandler!!)
        connectToFirstAvailable()
    }

    override suspend fun stop() {
        if (!started) return
        started = false
        midiManager?.unregisterDeviceCallback(deviceCallback)
        closePort()
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
        _activeNotes.value = tracker.reset()
        _connectionState.value =
            if (midiManager == null) MidiConnectionState.Unsupported else MidiConnectionState.NoDevice
    }

    override suspend fun availableEndpoints(): List<MidiEndpoint> {
        val manager = midiManager ?: return emptyList()
        return manager.inputCapableDevices().flatMap { it.toEndpoints() }
    }

    override suspend fun connectTo(endpoint: MidiEndpoint) {
        val manager = midiManager ?: return
        val info = manager.inputCapableDevices().firstOrNull { it.endpointId() == endpoint.id }
        if (info == null) {
            _connectionState.value = MidiConnectionState.Error(MidiError.DeviceDisconnected(endpoint))
            return
        }
        _connectionState.value = MidiConnectionState.Connecting(endpoint)
        closePort()

        val device = manager.openDeviceSuspending(info)
        if (device == null) {
            _connectionState.value = MidiConnectionState.Error(
                MidiError.OpenFailed(endpoint, "Android declined to open the device"),
            )
            return
        }

        val port = device.openOutputPort(endpoint.portIndex)
        if (port == null) {
            device.close()
            _connectionState.value = MidiConnectionState.Error(
                MidiError.OpenFailed(endpoint, "Output port ${endpoint.portIndex} is unavailable"),
            )
            return
        }

        parser.reset()
        port.connect(receiver)
        openDevice = device
        openPort = port
        openEndpoint = endpoint
        _connectionState.value = MidiConnectionState.Connected(endpoint)
    }

    override fun clearActiveNotes() {
        _activeNotes.value = tracker.reset()
    }

    // --- Internals ------------------------------------------------------------------------

    private suspend fun connectToFirstAvailable() {
        val endpoints = availableEndpoints()
        when {
            endpoints.isEmpty() -> _connectionState.value = MidiConnectionState.NoDevice
            // One keyboard is the normal case, and asking a player to choose between one option
            // is not a choice. With several, let the UI ask.
            endpoints.size == 1 -> connectTo(endpoints.first())
            else -> _connectionState.value = MidiConnectionState.DevicesAvailable(endpoints)
        }
    }

    private suspend fun refreshAvailability() {
        if (openPort != null) return
        val endpoints = availableEndpoints()
        _connectionState.value = if (endpoints.isEmpty()) {
            MidiConnectionState.NoDevice
        } else {
            MidiConnectionState.DevicesAvailable(endpoints)
        }
    }

    /**
     * The cable came out.
     *
     * 05_MIDI_INPUT_ENGINE.md §9: stop accepting input and clear note state, but leave the
     * exercise alone. Losing a keyboard mid-chord must not lose the player's progress.
     */
    private fun handleDisconnection(endpoint: MidiEndpoint) {
        closePort()
        _activeNotes.value = tracker.reset()
        _connectionState.value = MidiConnectionState.Error(MidiError.DeviceDisconnected(endpoint))
    }

    private fun closePort() {
        openPort?.let { port ->
            runCatching { port.disconnect(receiver) }
            runCatching { port.close() }
        }
        runCatching { openDevice?.close() }
        openPort = null
        openDevice = null
        openEndpoint = null
        parser.reset()
    }

    private fun registerDeviceCallback(manager: MidiManager, handler: Handler) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.registerDeviceCallback(
                MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                HandlerExecutor(handler),
                deviceCallback,
            )
        } else {
            @Suppress("DEPRECATION")
            manager.registerDeviceCallback(deviceCallback, handler)
        }
    }

    /**
     * Devices that can send us notes.
     *
     * Uses the transport-specific enumeration on API 33 and above and the older call below it,
     * as 05_MIDI_INPUT_ENGINE.md §2 requires. A device is only interesting if it has an output
     * port — from the app's point of view, the keyboard's output is our input.
     */
    private fun MidiManager.inputCapableDevices(): List<MidiDeviceInfo> {
        val all = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            @Suppress("DEPRECATION")
            devices.toList()
        }
        return all.filter { it.outputPortCount > 0 }
    }

    private suspend fun MidiManager.openDeviceSuspending(info: MidiDeviceInfo): MidiDevice? =
        suspendCancellableCoroutine { continuation ->
            openDevice(
                info,
                { device -> if (continuation.isActive) continuation.resume(device) },
                callbackHandler,
            )
        }

    private fun MidiDeviceInfo.endpointId(): String = "midi-$id"

    private fun MidiDeviceInfo.toEndpoints(): List<MidiEndpoint> {
        val name = properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI device"
        val manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
        // Every output port is separately openable; most keyboards expose exactly one.
        return (0 until outputPortCount).map { portIndex ->
            MidiEndpoint(
                id = endpointId(),
                name = name,
                manufacturer = manufacturer,
                portIndex = portIndex,
                isUsb = type == MidiDeviceInfo.TYPE_USB,
            )
        }
    }

    /** Runs work on a [Handler]'s thread. `Handler::post` is not an `Executor` on its own. */
    private class HandlerExecutor(private val handler: Handler) : java.util.concurrent.Executor {
        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    private companion object {
        const val EVENT_BUFFER = 128
    }
}
