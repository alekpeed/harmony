package com.harmonygates.exercise

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.midi.capture.MidiPerformanceCapture
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.session.DefaultExerciseSessionEngine
import com.harmonygates.core.music.session.ExerciseSessionState
import com.harmonygates.core.music.session.PauseReason
import com.harmonygates.core.music.session.SessionConfig
import com.harmonygates.core.music.session.SkipReason
import com.harmonygates.core.music.time.SystemMonotonicClock
import com.harmonygates.core.music.voicing.Inversion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the exercise screen can ask for. */
sealed interface ChordGateIntent {
    data object StartSession : ChordGateIntent
    data object Arm : ChordGateIntent
    data object Next : ChordGateIntent
    data object Skip : ChordGateIntent
    data object Hint : ChordGateIntent
    data object Restart : ChordGateIntent
}

/**
 * Drives the Phase 4 vertical slice.
 *
 * Owns the wiring only. The loop itself is `DefaultExerciseSessionEngine` in `core:music`,
 * which is where it belongs: 10_ANDROID_ARCHITECTURE.md §4 asks for one reusable engine, and
 * ear training and sight reading will attach to the same one rather than growing their own.
 */
class ChordGateViewModel(application: Application) : AndroidViewModel(application) {

    private val midi: MidiInputSource = AndroidMidiInputSource(application, viewModelScope)

    private val soundingNotes: StateFlow<List<Int>> = midi.activeNotes
        .map { notes -> notes.soundingAscending.map { it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val capture = MidiPerformanceCapture(midi, SystemMonotonicClock, viewModelScope)

    private val engine = DefaultExerciseSessionEngine(
        capture = capture,
        scope = viewModelScope,
        soundingNotes = soundingNotes,
    )

    private val _sessionSeed = MutableStateFlow(System.currentTimeMillis())

    val state: StateFlow<ChordGateUiState> = combine(
        engine.state,
        midi.connectionState,
        soundingNotes,
    ) { session, connection, sounding ->
        ChordGateUiState(
            session = session,
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
            soundingNotes = sounding,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChordGateUiState())

    init {
        viewModelScope.launch {
            midi.start()
            startSession()
        }
        viewModelScope.launch {
            // 05_MIDI_INPUT_ENGINE.md §9: losing the keyboard must not destroy the session. The
            // exercise is preserved and the player is told; nothing is scored against them.
            midi.connectionState.collect { connection ->
                if (connection is MidiConnectionState.Error) {
                    engine.pause(PauseReason.DeviceDisconnected)
                } else if (connection.isReceiving &&
                    engine.state.value is ExerciseSessionState.Paused
                ) {
                    engine.resume()
                }
            }
        }
    }

    fun onIntent(intent: ChordGateIntent) {
        viewModelScope.launch {
            when (intent) {
                ChordGateIntent.StartSession -> startSession()
                ChordGateIntent.Arm -> engine.arm()
                ChordGateIntent.Next -> engine.next()
                ChordGateIntent.Skip -> engine.skip(SkipReason.PlayerRequested)
                ChordGateIntent.Hint -> engine.requestHint()
                ChordGateIntent.Restart -> {
                    _sessionSeed.value = System.currentTimeMillis()
                    startSession()
                }
            }
        }
    }

    private suspend fun startSession() {
        engine.start(SessionConfig(policy = SEVENTH_CHORD_POLICY, seed = _sessionSeed.value))
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
        viewModelScope.launch { midi.stop() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The Phase 4 exercise policy.
         *
         * Deliberately plain and deliberately data: the phase asks for "several qualities, roots
         * and inversions", and this is that as content rather than as code. Phase 6 moves it into
         * `content/` where a curriculum author owns it.
         */
        val SEVENTH_CHORD_POLICY: ExercisePolicy = ExercisePolicy(
            id = ExercisePolicyId("policy.sevenths.all_roots"),
            skillIds = setOf(SkillId("skill.seventh.build")),
            formulaPool = listOf(
                ChordFormulas.MajorSeventh.id,
                ChordFormulas.DominantSeventh.id,
                ChordFormulas.MinorSeventh.id,
                ChordFormulas.HalfDiminishedSeventh.id,
                ChordFormulas.MinorSixth.id,
                ChordFormulas.MajorSixth.id,
            ),
            inversionPool = listOf(Inversion.ROOT, Inversion.FIRST, Inversion.SECOND),
            presentation = PresentationSpec.Independent,
            sessionLength = 20,
        )
    }
}

/** What the exercise screen draws. */
data class ChordGateUiState(
    val session: ExerciseSessionState = ExerciseSessionState.Idle,
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
    val soundingNotes: List<Int> = emptyList(),
)
