package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.CaptureState
import com.harmonygates.core.music.performance.NormalizedNoteEvent
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.PerformanceCapture
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.session.DefaultExerciseSessionEngine
import com.harmonygates.core.music.session.ExerciseSessionState
import com.harmonygates.core.music.session.PauseReason
import com.harmonygates.core.music.session.SessionConfig
import com.harmonygates.core.music.session.SkipReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The exercise loop, end to end.
 *
 * 01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §2 is the shape being tested: present, arm, perform,
 * evaluate, feed back, next. Capture is a fake here, so the loop can be driven exactly and the
 * whole suite runs without a keyboard.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExerciseSessionEngineTest {

    /** A capture the test drives by hand. */
    private class FakeCapture : PerformanceCapture {
        private val _state = MutableStateFlow(CaptureState.IDLE)
        override val state: StateFlow<CaptureState> = _state.asStateFlow()

        private val _attempts = MutableSharedFlow<PerformanceAttempt>(extraBufferCapacity = 8)
        override val attempts: Flow<PerformanceAttempt> = _attempts.asSharedFlow()

        var armCount = 0
            private set
        var cancelCount = 0
            private set
        var lastPolicy: CapturePolicy? = null
            private set

        override fun arm(policy: CapturePolicy) {
            armCount++
            lastPolicy = policy
            _state.value = CaptureState.ARMED
        }

        override fun cancel() {
            cancelCount++
            _state.value = CaptureState.IDLE
        }

        override fun submitNow() = Unit

        suspend fun deliver(attempt: PerformanceAttempt) {
            _state.value = CaptureState.COMPLETED
            _attempts.emit(attempt)
        }
    }

    private val realizer = DefaultChordRealizer()

    private val policy = ExercisePolicy(
        id = ExercisePolicyId("policy.chordgate.sevenths"),
        skillIds = setOf(SkillId("skill.seventh.build")),
        formulaPool = listOf(
            ChordFormulas.MajorSeventh.id,
            ChordFormulas.DominantSeventh.id,
            ChordFormulas.MinorSeventh.id,
        ),
        presentation = PresentationSpec.Independent,
        sessionLength = 4,
    )

    private fun attemptOf(notes: List<Int>): PerformanceAttempt = PerformanceAttempt(
        startedAtNanos = 0,
        completedAtNanos = 100,
        noteEvents = notes.map { NormalizedNoteEvent(MidiNote(it), 80, 0) },
        finalEffectiveNotes = notes.map { MidiNote(it) }.sorted(),
        onsetSpreadNanos = 0,
        sustainUsed = false,
    )

    /**
     * Plays the chord on screen.
     *
     * Derived from the displayed symbol rather than from the engine's internals, which is both
     * what a player does and a check that the symbol shown is enough to answer with.
     */
    private fun correctNotesFor(state: ExerciseSessionState): List<Int> {
        val symbol = requireNotNull(state.visibleExercise?.chordSymbol) { "No chord on screen" }
        val chord = JazzChordParser.parseOrThrow(symbol)
        return realizer.chordTones(chord).map { 60 + it.pitchClass.value }
    }

    @Test
    fun `a session presents an exercise and arms on request`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        val presenting = assertIs<ExerciseSessionState.Presenting>(engine.state.value)
        assertEquals(1, presenting.exercise.exerciseNumber)
        assertEquals(4, presenting.exercise.totalExercises)
        assertTrue(presenting.exercise.chordSymbol != null)

        engine.arm()
        assertIs<ExerciseSessionState.Armed>(engine.state.value)
        assertEquals(1, capture.armCount)
    }

    @Test
    fun `a correct answer produces a correct verdict and offers the next exercise`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()

        capture.deliver(attemptOf(correctNotesFor(engine.state.value)))


        val feedback = assertIs<ExerciseSessionState.Feedback>(engine.state.value)
        assertTrue(feedback.result.isCorrect, "Got ${feedback.result.semanticErrors}")
        assertTrue(feedback.nextAvailable)
    }

    @Test
    fun `a wrong answer is diagnosed rather than merely rejected`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()
        capture.deliver(attemptOf(listOf(60, 64, 67)))


        val feedback = assertIs<ExerciseSessionState.Feedback>(engine.state.value)
        if (!feedback.result.isCorrect) {
            assertTrue(
                feedback.result.semanticErrors.isNotEmpty(),
                "A wrong answer must come with a reason",
            )
        }
    }

    @Test
    fun `a full session runs to a summary`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 99))
        repeat(4) {
            val notes = correctNotesFor(engine.state.value)
            engine.arm()
            capture.deliver(attemptOf(notes))
                assertIs<ExerciseSessionState.Feedback>(engine.state.value)
            engine.next()
        }

        val completed = assertIs<ExerciseSessionState.Completed>(engine.state.value)
        assertEquals(4, completed.summary.total)
        assertEquals(4, completed.summary.attempted)
        assertEquals(4, completed.summary.correct)
        assertEquals(1.0, completed.summary.accuracy)
    }

    @Test
    fun `a session is reproducible from its seed`() = runTest(UnconfinedTestDispatcher()) {
        suspend fun chordsFor(seed: Long): List<String> {
            val capture = FakeCapture()
            val engine = DefaultExerciseSessionEngine(capture, backgroundScope)
            engine.start(SessionConfig(policy, seed = seed))
                val symbols = mutableListOf<String>()
            repeat(4) {
                symbols += assertIs<ExerciseSessionState.Presenting>(engine.state.value)
                    .exercise.chordSymbol.orEmpty()
                engine.arm()
                capture.deliver(attemptOf(listOf(60)))
                        engine.next()
            }
            return symbols
        }

        assertEquals(chordsFor(seed = 7), chordsFor(seed = 7))
        assertTrue(chordsFor(seed = 7) != chordsFor(seed = 8))
    }

    @Test
    fun `skipping records the exercise and moves on`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.skip(SkipReason.PlayerRequested)

        assertIs<ExerciseSessionState.Presenting>(engine.state.value)
        assertEquals(1, engine.records.size)
        assertTrue(engine.records.single().skipped)
        assertTrue(
            engine.records.single().result.verdict.isInconclusive,
            "A skip teaches the mastery model nothing",
        )
    }

    @Test
    fun `pausing cancels capture and resuming requires a fresh arm`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()
        engine.pause(PauseReason.AppBackgrounded)

        assertIs<ExerciseSessionState.Paused>(engine.state.value)
        assertTrue(capture.cancelCount > 0, "A backgrounded app must not keep listening")

        engine.resume()
        assertIs<ExerciseSessionState.Presenting>(
            engine.state.value,
            "10_ANDROID_ARCHITECTURE.md §9 requires a re-arm, so a chord held across the " +
                "interruption cannot be scored",
        )
    }

    @Test
    fun `losing the keyboard mid-exercise is not counted against the player`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()
        capture.deliver(
            attemptOf(listOf(60, 64)).copy(
                completion = com.harmonygates.core.music.performance.CaptureCompletion.DeviceLost,
            ),
        )

        val feedback = assertIs<ExerciseSessionState.Feedback>(engine.state.value)
        assertTrue(feedback.result.verdict.isInconclusive)

        engine.next()
        engine.arm()
        assertIs<ExerciseSessionState.Armed>(engine.state.value, "The session carries on")
    }

    @Test
    fun `hints are recorded before the answer, for later mastery weighting`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()
        engine.requestHint()
        engine.requestHint()
        capture.deliver(attemptOf(listOf(60)))


        assertEquals(2, engine.records.single().hintsUsed)
    }

    @Test
    fun `the capture policy comes from the exercise policy`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()

        assertEquals(policy.onsetPolicy, capture.lastPolicy?.onsetPolicy)
        assertEquals(policy.pitchRange, capture.lastPolicy?.acceptedRange)
    }

    @Test
    fun `stopping returns to idle and stops listening`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()
        engine.stop()

        assertIs<ExerciseSessionState.Idle>(engine.state.value)
        assertTrue(capture.cancelCount > 0)
    }

    @Test
    fun `live sounding notes move the session into capturing`() = runTest(UnconfinedTestDispatcher()) {
        val capture = FakeCapture()
        val sounding = MutableStateFlow(emptyList<Int>())
        val engine = DefaultExerciseSessionEngine(capture, backgroundScope, soundingNotes = sounding)

        engine.start(SessionConfig(policy, seed = 1))
        engine.arm()
        sounding.value = listOf(60, 64)

        val capturing = assertIs<ExerciseSessionState.Capturing>(engine.state.value)
        assertEquals(listOf(60, 64), capturing.live.soundingNotes)
    }

}
