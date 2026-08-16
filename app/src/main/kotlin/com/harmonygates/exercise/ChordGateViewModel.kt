package com.harmonygates.exercise

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.harmonygates.core.data.content.ContentRepository
import com.harmonygates.core.data.progress.ProgressRepository
import com.harmonygates.core.data.progress.SessionRecord
import com.harmonygates.core.midi.AndroidMidiInputSource
import com.harmonygates.core.midi.MidiConnectionState
import com.harmonygates.core.midi.MidiInputSource
import com.harmonygates.core.midi.capture.MidiPerformanceCapture
import com.harmonygates.core.music.campaign.CampaignEvaluator
import com.harmonygates.core.music.campaign.GateSessionPlanner
import com.harmonygates.core.music.campaign.GateStatus
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.session.DefaultExerciseSessionEngine
import com.harmonygates.core.music.session.ExerciseSessionState
import com.harmonygates.core.music.session.PauseReason
import com.harmonygates.core.music.session.SessionConfig
import com.harmonygates.core.music.session.SkipReason
import com.harmonygates.core.music.time.SystemMonotonicClock
import com.harmonygates.data.HarmonyGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

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
 * Drives a chord-gate session, and records what happened.
 *
 * Phase 5's acceptance criterion — "app restart preserves progress" — is met here rather than in
 * the engine: the engine still knows nothing about storage, and every attempt it produces is
 * written through the repository as it happens. Writing per attempt rather than per session is
 * deliberate; a session abandoned halfway is still evidence about the player, and losing it
 * would punish them for putting the tablet down.
 */
class ChordGateViewModel(
    application: Application,
    private val request: SessionRequest,
    private val progress: ProgressRepository = HarmonyGraph.progress(application),
    private val content: ContentRepository = HarmonyGraph.content(application),
) : AndroidViewModel(application) {

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

    private val planner = GateSessionPlanner()

    private val _sessionSeed = MutableStateFlow(System.currentTimeMillis())
    private val _policy = MutableStateFlow<ExercisePolicy?>(null)
    private val _title = MutableStateFlow<String?>(null)

    /** How many of the engine's records have already been written. */
    private var recordedCount = 0
    private var sessionId: String = UUID.randomUUID().toString()

    val state: StateFlow<ChordGateUiState> = combine(
        engine.state,
        midi.connectionState,
        soundingNotes,
        _title,
    ) { session, connection, sounding, title ->
        ChordGateUiState(
            session = session,
            midiConnected = connection.isReceiving,
            midiStatus = statusFor(connection),
            soundingNotes = sounding,
            gateTitle = title,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChordGateUiState())

    init {
        viewModelScope.launch {
            midi.start()
            startSession()
        }
        viewModelScope.launch {
            // Every attempt is persisted as the engine finishes judging it, and gate completion
            // is re-checked from the stored evidence rather than guessed at from this session.
            engine.state.collect { session ->
                if (session is ExerciseSessionState.Feedback) persistNewRecords()
                if (session is ExerciseSessionState.Completed) finishSession()
            }
        }
        viewModelScope.launch {
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
        val gate = (request as? SessionRequest.Gate)?.let { content.gate(it.gateId) }
        val policyId = gate?.exercisePolicyId ?: (request as SessionRequest.Practice).policyId
        val policy = content.exercisePolicy(policyId) ?: return
        val profile = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
        val seed = _sessionSeed.value

        // A gate session is shaped by what the player already knows: the planner drops the
        // tutorial for someone who has demonstrated the skill, which 02 §3 asks for by name.
        val shaped = gate?.let {
            val plan = planner.plan(it, policy, progress.allMastery(profile), seed, Instant.now())
            policy.copy(
                rootPool = plan.rootOrder.ifEmpty { policy.rootPool },
                sessionLength = plan.totalExercises,
            )
        } ?: policy

        _policy.value = shaped
        _title.value = gate?.title
        recordedCount = 0
        sessionId = UUID.randomUUID().toString()

        progress.startSession(
            profile,
            SessionRecord(
                id = sessionId,
                gateId = request.gateId,
                exercisePolicyId = policyId.value,
                seed = seed,
                startedAt = Instant.now(),
                endedAt = null,
                exercisesPlanned = shaped.sessionLength,
            ),
        )
        engine.start(SessionConfig(policy = shaped, seed = seed))
    }

    /**
     * Writes whatever the engine has judged since the last check.
     *
     * The engine exposes its records as a list rather than a stream, so this takes the tail it
     * has not seen. Counting rather than diffing keeps it correct when two attempts land in the
     * same frame, which a fast player and a slow recomposition can manage between them.
     */
    private suspend fun persistNewRecords() {
        val records = engine.records
        if (records.size <= recordedCount) return
        val profile = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
        val now = Instant.now()

        records.drop(recordedCount).forEach { record ->
            progress.recordAttempt(profile, sessionId, record, now)
        }
        recordedCount = records.size
    }

    private suspend fun finishSession() {
        persistNewRecords()
        val profile = progress.currentProfile(HarmonyGraph.CONTENT_VERSION)
        progress.endSession(sessionId, Instant.now())

        // Whether the gate passed is asked of the stored evidence, never of this session's tally:
        // a gate is complete because the mastery says so, and recomputing always agrees.
        val gateId = request.gateId ?: return
        val curriculum = content.curriculum()
        val state = CampaignEvaluator().evaluate(curriculum, progress.allMastery(profile))
        if (state.gate(gateId)?.status == GateStatus.COMPLETE) {
            progress.recordGateCompletion(profile, gateId, Instant.now())
        }
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

        /** Builds a view model for one particular session. */
        fun factory(application: Application, request: SessionRequest): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ChordGateViewModel(application, request) }
            }
    }
}

/** What the exercise screen draws. */
data class ChordGateUiState(
    val session: ExerciseSessionState = ExerciseSessionState.Idle,
    val midiConnected: Boolean = false,
    val midiStatus: String = "No keyboard",
    val soundingNotes: List<Int> = emptyList(),
    /** Set when the session belongs to a gate. */
    val gateTitle: String? = null,
)
