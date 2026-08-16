package com.harmonygates.core.music.progression

import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceCapture
import com.harmonygates.core.music.performance.PerformanceEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a run is. */
public enum class ProgressionRunStatus {
    IDLE,

    /** The track is live and waiting for the chord at the play point. */
    RUNNING,

    /** Interrupted — the keyboard went away, or the player asked. The track keeps its place. */
    PAUSED,

    /** The last chord of a non-looping run was played. */
    COMPLETED,
}

/** Why the track last moved, or refused to. */
public enum class AdvanceReason {
    /** The chord at the play point was played correctly. */
    CORRECT_CHORD,

    /** The player moved on without playing it. */
    MANUAL,
}

/**
 * Everything a Progression Run screen draws, and nothing about how it is drawn.
 *
 * [advanceCount] rather than a boolean animation flag: the renderer animates towards
 * [activeChordIndex] and this only tells it that a move happened, so the domain never has to
 * know how long a transition takes or whether one is in flight.
 */
public data class ProgressionRunState(
    val progression: Progression? = null,
    val activeChordIndex: Int = 0,
    val status: ProgressionRunStatus = ProgressionRunStatus.IDLE,
    /** Increments once per accepted advance. Also the number of chords cleared on a loop. */
    val advanceCount: Int = 0,
    val lastAdvance: AdvanceReason? = null,
    /** The last judgement made, accepted or not. Null before the first attempt on this chord. */
    val lastResult: EvaluationResult? = null,
    /**
     * True between an accepted chord and the keyboard going quiet.
     *
     * The handoff requires that "a sustained/held accepted voicing must not produce repeated
     * advances" and that the run "require a new qualifying note state before the next
     * acceptance". This flag is that requirement: while it is set, nothing is evaluated.
     */
    val awaitingRelease: Boolean = false,
    val attempts: Int = 0,
    /** Chords cleared on the first attempt, with no wrong answer in between. */
    val clean: Int = 0,
) {
    public val activeEvent: ChordEvent? get() = progression?.eventAt(activeChordIndex)

    public val isRunning: Boolean get() = status == ProgressionRunStatus.RUNNING

    /** The chords the track shows, with the slot each one currently occupies. */
    public fun visibleChords(): List<VisibleChord> =
        progression?.window(activeChordIndex).orEmpty()
}

/**
 * The Progression Run loop.
 *
 * `interface/PROGRESSION_RUN_HANDOFF.md` §7 states the gameplay flow as
 * `MIDI note state -> chord recognition -> evaluate active ChordEvent -> accepted ->
 * advanceTrack()`, and this is the middle of that sentence. It knows nothing about orbs,
 * perspective slots or animation: the renderer reads [state] and draws whatever it says.
 */
public interface ProgressionRunEngine {
    public val state: StateFlow<ProgressionRunState>

    public suspend fun start(progression: Progression)

    /** Judges an attempt against the chord at the play point. */
    public suspend fun submit(attempt: PerformanceAttempt)

    /**
     * Moves on without playing the chord.
     *
     * Deliberately the same advance path as an accepted chord — the handoff asks for exactly
     * that, "rather than maintaining separate track state" — so a manual skip can never leave
     * the track and the progression disagreeing about where the run is.
     */
    public suspend fun advanceManually()

    public suspend fun pause()

    public suspend fun resume()

    public suspend fun stop()
}

/**
 * The production run engine.
 *
 * Two rules do all the work that a naive implementation gets wrong:
 *
 * 1. **Correctness is asked of the event, never of the label.** The chord symbol on an orb is
 *    presentation; [ChordEvent.requirement] is the question. They can differ — a rootless
 *    voicing of `C7` is judged against a chord carrying the ninth and thirteenth the shape
 *    sounds — and judging the label would quietly mark those runs wrong.
 * 2. **An accepted chord locks the track until the keyboard is quiet.** Without that, a held
 *    chord or a pedal still down re-triggers capture and walks the player through three chords
 *    they never played.
 */
public class DefaultProgressionRunEngine(
    private val capture: PerformanceCapture,
    private val scope: CoroutineScope,
    private val evaluator: PerformanceEvaluator = DefaultPerformanceEvaluator(),
    /** Notes currently sounding. Drives the release gate; empty means the hands are off. */
    private val soundingNotes: StateFlow<List<Int>> = MutableStateFlow(emptyList()),
) : ProgressionRunEngine {

    private val _state = MutableStateFlow(ProgressionRunState())
    override val state: StateFlow<ProgressionRunState> = _state.asStateFlow()

    private var collectionJob: Job? = null
    private var attemptsOnThisChord = 0

    override suspend fun start(progression: Progression) {
        stop()
        _state.value = ProgressionRunState(
            progression = progression,
            activeChordIndex = 0,
            status = ProgressionRunStatus.RUNNING,
        )
        attemptsOnThisChord = 0

        collectionJob = scope.launch {
            launch {
                capture.attempts.collect { attempt -> submit(attempt) }
            }
            launch {
                // The release gate. A run only re-arms once the previous chord has actually let
                // go, which is what makes "one accepted chord, one advance" true of a player who
                // holds the voicing to hear it.
                soundingNotes.collect { refreshReleaseGate() }
            }
        }

        armForActiveChord()
    }

    override suspend fun submit(attempt: PerformanceAttempt) {
        val current = _state.value
        val event = current.activeEvent ?: return
        if (!current.isRunning || current.awaitingRelease) return

        val result = evaluator.evaluate(event.requirement, attempt, event.onsetPolicy)
        attemptsOnThisChord++

        if (result.verdict.isCorrect) {
            advance(AdvanceReason.CORRECT_CHORD, result)
            return
        }

        // Wrong or incomplete never advances. The chord stays where it is and capture re-arms,
        // so the player can simply play it again.
        _state.value = current.copy(
            lastResult = result,
            attempts = current.attempts + 1,
        )
        armForActiveChord()
    }

    override suspend fun advanceManually() {
        if (!_state.value.isRunning) return
        advance(AdvanceReason.MANUAL, result = null)
    }

    override suspend fun pause() {
        val current = _state.value
        if (current.status != ProgressionRunStatus.RUNNING) return
        capture.cancel()
        _state.value = current.copy(status = ProgressionRunStatus.PAUSED)
    }

    override suspend fun resume() {
        val current = _state.value
        if (current.status != ProgressionRunStatus.PAUSED) return
        // Re-armed from scratch, never resumed mid-chord: a voicing held across the interruption
        // must not be scored as an answer to the chord that is on the play point now.
        _state.value = current.copy(status = ProgressionRunStatus.RUNNING, awaitingRelease = false)
        armForActiveChord()
    }

    override suspend fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        capture.cancel()
        _state.value = ProgressionRunState()
        attemptsOnThisChord = 0
    }

    // --- Internals ------------------------------------------------------------------------

    private fun advance(reason: AdvanceReason, result: EvaluationResult?) {
        val current = _state.value
        val progression = current.progression ?: return
        val cleanlyPlayed = reason == AdvanceReason.CORRECT_CHORD && attemptsOnThisChord <= 1
        val next = current.activeChordIndex + 1
        attemptsOnThisChord = 0

        val finished = next >= progression.size && !progression.loop
        val advanced = current.copy(
            // A looping run wraps rather than ending, and keeps counting advances, so the
            // renderer sees one continuous stream of chords arriving at the play point.
            activeChordIndex = if (progression.loop) next.mod(progression.size) else next,
            status = if (finished) ProgressionRunStatus.COMPLETED else current.status,
            advanceCount = current.advanceCount + 1,
            lastAdvance = reason,
            lastResult = result,
            attempts = if (result != null) current.attempts + 1 else current.attempts,
            clean = current.clean + if (cleanlyPlayed) 1 else 0,
            // Only a chord still under the fingers can advance twice. A chord the player has
            // already let go of — which is how most of them finish — has nothing to wait for,
            // and gating on it would leave the run armed for a release that never comes.
            awaitingRelease = reason == AdvanceReason.CORRECT_CHORD && soundingNotes.value.isNotEmpty(),
        )
        _state.value = advanced

        if (finished) {
            capture.cancel()
        } else if (advanced.awaitingRelease) {
            refreshReleaseGate()
        } else {
            armForActiveChord()
        }
    }

    /**
     * Opens the gate once the keyboard is quiet.
     *
     * Called from the sounding-notes collector and directly after an advance, because the notes
     * can go away in the moment between the two and a run that missed that edge would sit armed
     * for a release that has already happened.
     */
    private fun refreshReleaseGate() {
        val current = _state.value
        if (!current.awaitingRelease || !current.isRunning) return
        if (soundingNotes.value.isNotEmpty()) return
        _state.value = current.copy(awaitingRelease = false)
        armForActiveChord()
    }

    private fun armForActiveChord() {
        val current = _state.value
        val event = current.activeEvent ?: return
        if (!current.isRunning || current.awaitingRelease) return
        capture.arm(
            CapturePolicy.GuidedChord.copy(
                onsetPolicy = event.onsetPolicy,
                acceptedRange = event.policy.pitchRange,
            ),
        )
    }
}
