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

/** Everything the Progression Run screen can ask for. */
sealed interface ProgressionRunIntent {
    data object Next : ProgressionRunIntent
    data object Restart : ProgressionRunIntent
    data class ChooseTemplate(val template: ProgressionTemplate) : ProgressionRunIntent
    data class ChooseStyle(val style: VoicingStyle) : ProgressionRunIntent
    data object ToggleAllKeys : ProgressionRunIntent
    data object ToggleRomanNumerals : ProgressionRunIntent
}

/** How this run was set up. */
data class RunSetup(
    val template: ProgressionTemplate = ProgressionTemplates.MajorTwoFiveOne,
    val style: VoicingStyle = VoicingStyle.ANY_VOICING,
    val allKeys: Boolean = true,
    val key: SpelledPitchClass = DEFAULT_KEY,
    val showRomanNumerals: Boolean = true,
) {
    companion object {
        val DEFAULT_KEY: SpelledPitchClass = requireNotNull(SpelledPitchClass.parseOrNull("C"))
    }
}

/**
 * Drives Progression Run.
 *
 * Wiring only, exactly as `ChordGateViewModel` is: the run itself is
 * `DefaultProgressionRunEngine` in `core:music`, so the rule that a chord is judged by the
 * domain and never by the screen holds here by construction rather than by discipline.
 */
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

    /** The track as supplied in `interface/`, read once. */
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
            // Losing the keyboard pauses the run rather than ending it, and the track keeps its
            // place — the same contract the exercise session has (05_MIDI_INPUT_ENGINE.md §9).
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

                ProgressionRunIntent.ToggleRomanNumerals ->
                    _setup.value = _setup.value.copy(showRomanNumerals = !_setup.value.showRomanNumerals)
            }
        }
    }

    private suspend fun startRun() {
        engine.start(progressionFor(_setup.value))
    }

    private fun progressionFor(setup: RunSetup): Progression {
        if (setup.allKeys) return generator.throughAllKeys(setup.template, setup.style)
        return when (val placed = generator.generate(setup.template, KeyContext(setup.key), setup.style)) {
            is SpellingResult.Spelled -> placed.value
            // A template that cannot be written in the chosen key falls back to the twelve-key
            // form rather than to an error screen: the run is still playable, just longer.
            is SpellingResult.Overflow -> generator.throughAllKeys(setup.template, setup.style)
        }
    }

    /**
     * The chords the track shows, and where each one is.
     *
     * The window is one slot wider than the visible composition at both ends, because the orb
     * leaving the near end and the orb arriving at the far end have to exist before they can
     * move. Slots here are integers; the screen adds the fraction of an advance in flight.
     */
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

/** What the Progression Run screen draws. */
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

    /** "Chord 4 of 36". */
    val progressLabel: String
        get() = run.progression?.let { "Chord ${run.activeChordIndex + 1} of ${it.size}" }.orEmpty()

    val lastResult: EvaluationResult? get() = run.lastResult
}
