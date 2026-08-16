package com.harmonygates.progression

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProgressionRunIntent {
    data object Next : ProgressionRunIntent
    data object Previous : ProgressionRunIntent
    data object Restart : ProgressionRunIntent
    data object TogglePlaying : ProgressionRunIntent
    data class ChooseTemplate(val template: ProgressionTemplate) : ProgressionRunIntent
    data class ChooseStyle(val style: VoicingStyle) : ProgressionRunIntent
    data class ChooseKey(val key: String) : ProgressionRunIntent
    data class ChooseSound(val sound: String) : ProgressionRunIntent
    data class SetTimeFeel(val feel: TimeFeel) : ProgressionRunIntent
    data object ToggleAllKeys : ProgressionRunIntent
    data object ToggleLoop : ProgressionRunIntent
    data object ToggleRomanNumerals : ProgressionRunIntent
    data object ToggleCountIn : ProgressionRunIntent
    data object IncreaseCountBars : ProgressionRunIntent
    data object DecreaseCountBars : ProgressionRunIntent
    data object IncreaseOctave : ProgressionRunIntent
    data object DecreaseOctave : ProgressionRunIntent
    data object ToggleHighlightRoot : ProgressionRunIntent
    data object ToggleGuideTones : ProgressionRunIntent
    data object ToggleAutoNextGate : ProgressionRunIntent
    data object IncreaseTempo : ProgressionRunIntent
}

enum class TimeFeel { Straight, Swing, Shuffle }

data class RunSetup(
    val template: ProgressionTemplate = ProgressionTemplates.MajorTwoFiveOne,
    val style: VoicingStyle = VoicingStyle.ANY_VOICING,
    val allKeys: Boolean = true,
    val loop: Boolean = false,
    val key: SpelledPitchClass = DEFAULT_KEY,
    val tempoBpm: Int = 120,
    val showRomanNumerals: Boolean = true,
) {
    companion object {
        val DEFAULT_KEY: SpelledPitchClass = requireNotNull(SpelledPitchClass.parseOrNull("C"))
    }
}

private data class RunControls(
    val timeFeel: TimeFeel = TimeFeel.Straight,
    val countIn: Boolean = true,
    val countBars: Int = 2,
    val sound: String = DEFAULT_SOUND,
    val octave: Int = 0,
    val highlightRoot: Boolean = true,
    val showGuideTones: Boolean = false,
    val autoNextGate: Boolean = true,
    val elapsedSeconds: Long = 0,
    val runsCompleted: Int = 0,
    val totalAttempts: Int = 0,
    val totalClean: Int = 0,
    val bestStreak: Int = 0,
    val countingIn: Boolean = false,
)

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
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    private val _setup = MutableStateFlow(RunSetup())
    private val _controls = MutableStateFlow(RunControls())

    val track: TrackSpec = TrackMapReader.read(
        application.resources.openRawResource(R.raw.progression_run_map).use { it.readBytes().decodeToString() },
    )

    val state: StateFlow<ProgressionRunUiState> = combine(
        engine.state,
        midi.connectionState,
        _setup,
        _controls,
    ) { run, connection, setup, controls ->
        ProgressionRunUiState(
            run = run,
            setup = setup,
            orbs = orbsFor(run, setup),
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
            timeFeel = controls.timeFeel,
            countIn = controls.countIn,
            countBars = controls.countBars,
            selectedSound = controls.sound,
            octave = controls.octave,
            highlightRoot = controls.highlightRoot,
            showGuideTones = controls.showGuideTones,
            autoNextGate = controls.autoNextGate,
            countingIn = controls.countingIn,
            elapsedSeconds = controls.elapsedSeconds,
            runsCompleted = controls.runsCompleted,
            totalAttempts = controls.totalAttempts,
            totalClean = controls.totalClean,
            bestStreak = controls.bestStreak,
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
                } else if (connection.isReceiving && engine.state.value.status == ProgressionRunStatus.PAUSED) {
                    engine.resume()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (engine.state.value.status == ProgressionRunStatus.RUNNING) {
                    _controls.value = _controls.value.copy(elapsedSeconds = _controls.value.elapsedSeconds + 1)
                }
            }
        }
        viewModelScope.launch {
            var previous = ProgressionRunStatus.IDLE
            engine.state.collect { run ->
                if (run.status == ProgressionRunStatus.COMPLETED && previous != ProgressionRunStatus.COMPLETED) {
                    val current = _controls.value
                    _controls.value = current.copy(
                        runsCompleted = current.runsCompleted + 1,
                        totalAttempts = current.totalAttempts + run.attempts,
                        totalClean = current.totalClean + run.clean,
                        bestStreak = maxOf(current.bestStreak, run.clean),
                    )
                    if (current.autoNextGate) {
                        delay(700)
                        startRun()
                    }
                }
                previous = run.status
            }
        }
    }

    fun onIntent(intent: ProgressionRunIntent) {
        viewModelScope.launch {
            when (intent) {
                ProgressionRunIntent.Next -> engine.advanceManually()
                ProgressionRunIntent.Previous -> previousChord()
                ProgressionRunIntent.Restart -> startRun()
                ProgressionRunIntent.TogglePlaying -> togglePlaying()
                is ProgressionRunIntent.ChooseTemplate -> {
                    _setup.value = _setup.value.copy(template = intent.template)
                    startRun()
                }
                is ProgressionRunIntent.ChooseStyle -> {
                    _setup.value = _setup.value.copy(style = intent.style)
                    startRun()
                }
                is ProgressionRunIntent.ChooseKey -> {
                    SpelledPitchClass.parseOrNull(intent.key)?.let { key ->
                        _setup.value = _setup.value.copy(key = key, allKeys = false)
                        startRun()
                    }
                }
                is ProgressionRunIntent.ChooseSound -> {
                    _controls.value = _controls.value.copy(sound = intent.sound)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                }
                is ProgressionRunIntent.SetTimeFeel ->
                    _controls.value = _controls.value.copy(timeFeel = intent.feel)
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
                ProgressionRunIntent.ToggleCountIn ->
                    _controls.value = _controls.value.copy(countIn = !_controls.value.countIn)
                ProgressionRunIntent.IncreaseCountBars ->
                    _controls.value = _controls.value.copy(countBars = (_controls.value.countBars + 1).coerceAtMost(4))
                ProgressionRunIntent.DecreaseCountBars ->
                    _controls.value = _controls.value.copy(countBars = (_controls.value.countBars - 1).coerceAtLeast(1))
                ProgressionRunIntent.IncreaseOctave ->
                    _controls.value = _controls.value.copy(octave = (_controls.value.octave + 1).coerceAtMost(3))
                ProgressionRunIntent.DecreaseOctave ->
                    _controls.value = _controls.value.copy(octave = (_controls.value.octave - 1).coerceAtLeast(-3))
                ProgressionRunIntent.ToggleHighlightRoot ->
                    _controls.value = _controls.value.copy(highlightRoot = !_controls.value.highlightRoot)
                ProgressionRunIntent.ToggleGuideTones ->
                    _controls.value = _controls.value.copy(showGuideTones = !_controls.value.showGuideTones)
                ProgressionRunIntent.ToggleAutoNextGate ->
                    _controls.value = _controls.value.copy(autoNextGate = !_controls.value.autoNextGate)
                ProgressionRunIntent.IncreaseTempo -> {
                    val next = if (_setup.value.tempoBpm >= 240) 40 else _setup.value.tempoBpm + 5
                    _setup.value = _setup.value.copy(tempoBpm = next)
                    startRun()
                }
            }
        }
    }

    private suspend fun togglePlaying() {
        when (engine.state.value.status) {
            ProgressionRunStatus.RUNNING -> engine.pause()
            ProgressionRunStatus.PAUSED -> resumeWithCountIn()
            ProgressionRunStatus.COMPLETED, ProgressionRunStatus.IDLE -> startRun()
        }
    }

    private suspend fun resumeWithCountIn() {
        val controls = _controls.value
        if (!controls.countIn) {
            engine.resume()
            return
        }
        _controls.value = controls.copy(countingIn = true)
        val beatMs = (60_000L / _setup.value.tempoBpm.coerceAtLeast(1))
        repeat(controls.countBars * 4) {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            delay(beatMs)
        }
        _controls.value = _controls.value.copy(countingIn = false)
        engine.resume()
    }

    private suspend fun previousChord() {
        val current = engine.state.value
        val progression = current.progression ?: return
        val target = (current.activeChordIndex - 1).coerceAtLeast(0)
        engine.start(progression)
        repeat(target) { engine.advanceManually() }
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
        return generated.copy(loop = setup.loop, tempoBpm = setup.tempoBpm)
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
        relativeSlot < 0 -> if (relativeSlot == -1 && run.lastAdvance == AdvanceReason.CORRECT_CHORD) {
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
        tone.release()
        viewModelScope.launch {
            engine.stop()
            midi.stop()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_SOUND = "Noire Grand Concert Piano"
    }
}

data class ProgressionRunUiState(
    val run: ProgressionRunState = ProgressionRunState(),
    val setup: RunSetup = RunSetup(),
    val orbs: List<ChordOrbUiModel> = emptyList(),
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
    val timeFeel: TimeFeel = TimeFeel.Straight,
    val countIn: Boolean = true,
    val countBars: Int = 2,
    val selectedSound: String = "Noire Grand Concert Piano",
    val octave: Int = 0,
    val highlightRoot: Boolean = true,
    val showGuideTones: Boolean = false,
    val autoNextGate: Boolean = true,
    val countingIn: Boolean = false,
    val elapsedSeconds: Long = 0,
    val runsCompleted: Int = 0,
    val totalAttempts: Int = 0,
    val totalClean: Int = 0,
    val bestStreak: Int = 0,
) {
    val activeSymbol: String? get() = run.activeEvent?.displaySymbol
    val activeFunction: String? get() = run.activeEvent?.functionLabel.takeIf { setup.showRomanNumerals }
    val instruction: String? get() = run.activeEvent?.instruction
    val progressLabel: String
        get() = run.progression?.let { progression ->
            val ordinal = if (run.status == ProgressionRunStatus.COMPLETED) progression.size
            else (run.activeChordIndex + 1).coerceIn(1, progression.size)
            "Chord $ordinal of ${progression.size}"
        }.orEmpty()
    val lastResult: EvaluationResult? get() = run.lastResult
    val availableKeys: List<String> get() = KEY_LABELS
    val availableSounds: List<String> get() = SOUND_LABELS
    val goalProgress: Int get() = run.clean.coerceIn(0, 5)
    val accuracyPercent: Int
        get() {
            val attempts = totalAttempts + run.attempts
            val clean = totalClean + run.clean
            return if (attempts == 0) 0 else ((clean * 100f) / attempts).toInt().coerceIn(0, 100)
        }
    val elapsedLabel: String
        get() = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

    companion object {
        private val KEY_LABELS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
        private val SOUND_LABELS = listOf(
            "Noire Grand Concert Piano",
            "Studio Grand",
            "Rhodes",
            "Wurlitzer",
        )
    }
}
