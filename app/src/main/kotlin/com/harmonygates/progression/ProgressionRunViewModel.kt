package com.harmonygates.progression

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.R
import com.harmonygates.core.designsystem.progression.ChordOrbUiModel
import com.harmonygates.core.designsystem.progression.OrbState
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.midi.capture.MidiPerformanceCapture
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.progression.AdvanceReason
import com.harmonygates.core.music.progression.DefaultProgressionGenerator
import com.harmonygates.core.music.progression.DefaultProgressionRunEngine
import com.harmonygates.core.music.progression.Progression
import com.harmonygates.core.music.progression.ProgressionRunState
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplate
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.core.music.time.SystemMonotonicClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProgressionRunIntent {
    data object Next : ProgressionRunIntent
    data object Restart : ProgressionRunIntent
    data class ChooseTemplate(val template: ProgressionTemplate) : ProgressionRunIntent
    data class ChooseStyle(val style: VoicingStyle) : ProgressionRunIntent
    data object ToggleAllKeys : ProgressionRunIntent
    data object ToggleLoop : ProgressionRunIntent
    data object ToggleRomanNumerals : ProgressionRunIntent
}

data class RunSetup(
    val template: ProgressionTemplate = ProgressionTemplates.MajorTwoFiveOne,
    val style: VoicingStyle = VoicingStyle.ANY_VOICING,
    val allKeys: Boolean = true,
    val loop: Boolean = false,
    val key: SpelledPitchClass = DEFAULT_KEY,
    val showRomanNumerals: Boolean = true,
) {
    companion object {
        val DEFAULT_KEY: SpelledPitchClass = requireNotNull(SpelledPitchClass.parseOrNull("C"))
    }
}

class ProgressionRunViewModel(application: Application) : AndroidViewModel(application) {

    private val midi: MidiInputSource = AndroidMidiInputSource(application, viewModelScope)

    private val soundingNotes: StateFlow<List<Int>> = midi.activeNotes
        .map { notes -> notes.soundingAscending.map { it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val capture = MidiPerformanceCapture(midi, SystemMonotonicClock, viewModelScope)

    private val engine = DefaultProgressionRunEngine(
        capture = capture,
        scope = viewModelScope,
        soundingNotes = soundingNotes,
    )

    private val generator = DefaultProgressionGenerator()
    private val _setup = MutableStateFlow(RunSetup())

    val track: TrackSpec = TrackMapReader.read(
        application.resources.openRawResource(R.raw.progression_run_map).use { it.readBytes().decodeToString() },
    )

    val state: StateFlow<ProgressionRunUiState> = combine(
        engine.state,
        midi.connectionState,
        _setup,
    ) { run, connection, setup ->
        ProgressionRunUiState(
            run = run,
            setup = setup,
            orbs = orbsFor(run, setup),
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgressionRunUiState())

    init {
        viewModelScope.launch {
            midi.start()
            startRun()
        }
        viewModelScope.launch {
            midi.connectionState.collect { connection ->
                if (connection is MidiConnectionState.Error) {
                    engine.pause()
                } else if (connection.isReceiving &&
                    engine.state.value.status == ProgressionRunStatus.PAUSED
                ) {
                    engine.resume()
                }
            }
        }
    }

    fun onIntent(intent: ProgressionRunIntent) {
        viewModelScope.launch {
            when (intent) {
                ProgressionRunIntent.Next -> engine.advanceManually()
                ProgressionRunIntent.Restart -> startRun()
                is ProgressionRunIntent.ChooseTemplate -> {
                    _setup.value = _setup.value.copy(template = intent.template)
                    startRun()
                }

                is ProgressionRunIntent.ChooseStyle -> {
                    _setup.value = _setup.value.copy(style = intent.style)
                    startRun()
                }

                ProgressionRunIntent.ToggleAllKeys -> {
                    _setup.value = _setup.value.copy(allKeys = !_setup.value.allKeys)
                    startRun()
                }

                ProgressionRunIntent.ToggleLoop -> {
                    _setup.value = _setup.value.copy(loop = !_setup.value.loop)
                    startRun()
                }

                ProgressionRunIntent.ToggleRomanNumerals ->
                    _setup.value = _setup.value.copy(showRomanNumerals = !_setup.value.showRomanNumerals)
            }
        }
    }

    private suspend fun startRun() {
        engine.start(progressionFor(_setup.value))
    }

    private fun progressionFor(setup: RunSetup): Progression {
        val generated = if (setup.allKeys) {
            generator.throughAllKeys(setup.template, setup.style)
        } else {
            when (val placed = generator.generate(setup.template, KeyContext(setup.key), setup.style)) {
                is SpellingResult.Spelled -> placed.value
                is SpellingResult.Overflow -> generator.throughAllKeys(setup.template, setup.style)
            }
        }
        return generated.copy(loop = setup.loop)
    }

    private fun orbsFor(run: ProgressionRunState, setup: RunSetup): List<ChordOrbUiModel> {
        val progression = run.progression ?: return emptyList()
        return progression.window(
            activeIndex = run.activeChordIndex,
            behind = track.slotsBehind,
            ahead = track.slotsAhead,
        ).map { visible ->
            ChordOrbUiModel(
                eventId = visible.instanceId,
                chordSymbol = visible.event.displaySymbol,
                functionLabel = visible.event.functionLabel.takeIf { setup.showRomanNumerals },
                state = orbStateFor(visible.relativeSlot, run),
                slot = visible.relativeSlot.toFloat(),
            )
        }
    }

    private fun orbStateFor(relativeSlot: Int, run: ProgressionRunState): OrbState = when {
        relativeSlot < 0 ->
            if (relativeSlot == -1 && run.lastAdvance == AdvanceReason.CORRECT_CHORD) {
                OrbState.CORRECT
            } else {
                OrbState.PREVIOUS
            }

        relativeSlot > 0 -> OrbState.UPCOMING
        run.lastResult?.verdict?.isCorrect == false -> OrbState.INCORRECT
        else -> OrbState.ACTIVE
    }

    private fun statusFor(connection: MidiConnectionState): String = when (connection) {
        is MidiConnectionState.Connected -> connection.endpoint.displayName
        is MidiConnectionState.Unsupported -> "No MIDI support"
        is MidiConnectionState.NoDevice -> "No keyboard"
        is MidiConnectionState.DevicesAvailable -> "Keyboard available"
        is MidiConnectionState.Connecting -> "Connecting"
        is MidiConnectionState.Error -> "Keyboard disconnected"
    }

    override fun onCleared() {
        viewModelScope.launch {
            engine.stop()
            midi.stop()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class ProgressionRunUiState(
    val run: ProgressionRunState = ProgressionRunState(),
    val setup: RunSetup = RunSetup(),
    val orbs: List<ChordOrbUiModel> = emptyList(),
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
) {
    val activeSymbol: String? get() = run.activeEvent?.displaySymbol

    val activeFunction: String?
        get() = run.activeEvent?.functionLabel.takeIf { setup.showRomanNumerals }

    val instruction: String? get() = run.activeEvent?.instruction

    val progressLabel: String
        get() = run.progression?.let { progression ->
            val ordinal = if (run.status == ProgressionRunStatus.COMPLETED) {
                progression.size
            } else {
                (run.activeChordIndex + 1).coerceIn(1, progression.size)
            }
            "Chord $ordinal of ${progression.size}"
        }.orEmpty()

    val lastResult: EvaluationResult? get() = run.lastResult
}
