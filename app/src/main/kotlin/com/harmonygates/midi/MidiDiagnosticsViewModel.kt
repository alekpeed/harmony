package com.harmonygates.midi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.FakeMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiEvent
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.time.SystemMonotonicClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the MIDI diagnostics screen.
 *
 * Holds a [MidiInputSource] and nothing else of substance — parsing, sustain semantics and
 * note tracking all happen inside `core:midi`, so this only collects and formats.
 *
 * The simulator toggle exists because the screen has to be checkable on a tablet that has no
 * keyboard attached, which is the situation this project has been in for its whole life so far.
 */
class MidiDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val realSource: MidiInputSource by lazy {
        AndroidMidiInputSource(application, viewModelScope)
    }
    private var simulator: FakeMidiInputSource? = null

    private var source: MidiInputSource = realSource
    private var collectionJob: Job? = null

    private val _state = MutableStateFlow(MidiDiagnosticsState())
    val state: StateFlow<MidiDiagnosticsState> = _state.asStateFlow()

    init {
        attach(realSource, simulated = false)
    }

    fun onIntent(intent: MidiDiagnosticsIntent) {
        when (intent) {
            MidiDiagnosticsIntent.Refresh -> viewModelScope.launch { refreshEndpoints() }

            is MidiDiagnosticsIntent.Connect -> viewModelScope.launch {
                source.connectTo(intent.endpoint)
            }

            MidiDiagnosticsIntent.ClearNotes -> source.clearActiveNotes()

            MidiDiagnosticsIntent.ClearLog -> _state.value = _state.value.copy(
                recentEvents = emptyList(),
                eventCount = 0,
                observedLowNote = null,
                observedHighNote = null,
            )

            is MidiDiagnosticsIntent.UseSimulator -> switchSource(toSimulator = intent.enabled)

            is MidiDiagnosticsIntent.SimulateNote -> viewModelScope.launch {
                val fake = simulator ?: return@launch
                if (intent.on) fake.noteOn(intent.note) else fake.noteOff(intent.note)
            }

            is MidiDiagnosticsIntent.SimulateSustain -> viewModelScope.launch {
                simulator?.sustain(intent.down)
            }

            MidiDiagnosticsIntent.SimulateDisconnect -> simulator?.disconnect()
        }
    }

    private fun switchSource(toSimulator: Boolean) {
        viewModelScope.launch {
            source.stop()
            if (toSimulator) {
                val fake = FakeMidiInputSource(SystemMonotonicClock)
                simulator = fake
                attach(fake, simulated = true)
            } else {
                simulator = null
                attach(realSource, simulated = false)
            }
        }
    }

    private fun attach(newSource: MidiInputSource, simulated: Boolean) {
        collectionJob?.cancel()
        source = newSource
        _state.value = MidiDiagnosticsState(isSimulated = simulated)

        collectionJob = viewModelScope.launch {
            launch {
                newSource.connectionState.collect { connection ->
                    _state.value = _state.value.copy(connection = connection)
                    if (connection is MidiConnectionState.DevicesAvailable) {
                        _state.value = _state.value.copy(endpoints = connection.devices)
                    }
                }
            }
            launch {
                newSource.activeNotes.collect { notes ->
                    _state.value = _state.value.copy(activeNotes = notes)
                }
            }
            launch {
                newSource.events.collect { event -> record(event) }
            }
            newSource.start()
            refreshEndpoints()
        }
    }

    private suspend fun refreshEndpoints() {
        _state.value = _state.value.copy(endpoints = source.availableEndpoints())
    }

    /**
     * Adds an event to the log.
     *
     * The log is capped: a diagnostics screen left open during a practice session would
     * otherwise accumulate tens of thousands of lines and eventually cost more memory than the
     * rest of the app.
     */
    private fun record(event: MidiEvent) {
        val current = _state.value
        val note = (event as? MidiEvent.NoteOn)?.note ?: (event as? MidiEvent.NoteOff)?.note
        _state.value = current.copy(
            recentEvents = (listOf(MidiEventLine.of(event)) + current.recentEvents).take(LOG_LIMIT),
            eventCount = current.eventCount + 1,
            observedLowNote = note.lowest(current.observedLowNote),
            observedHighNote = note.highest(current.observedHighNote),
        )
    }

    private fun MidiNote?.lowest(existing: Int?): Int? = when {
        this == null -> existing
        existing == null -> value
        else -> minOf(existing, value)
    }

    private fun MidiNote?.highest(existing: Int?): Int? = when {
        this == null -> existing
        existing == null -> value
        else -> maxOf(existing, value)
    }

    override fun onCleared() {
        collectionJob?.cancel()
    }

    private companion object {
        const val LOG_LIMIT = 60
    }
}
