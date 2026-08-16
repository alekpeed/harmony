package com.harmonygates.core.music

import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.progression.AdvanceReason
import com.harmonygates.core.music.progression.DefaultProgressionGenerator
import com.harmonygates.core.music.progression.DefaultProgressionRunEngine
import com.harmonygates.core.music.progression.Progression
import com.harmonygates.core.music.progression.ProgressionRunStatus
import com.harmonygates.core.music.progression.ProgressionTemplate
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import com.harmonygates.core.music.realize.DefaultChordRealizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Progression Run loop.
 *
 * The rules under test are the ones `interface/PROGRESSION_RUN_HANDOFF.md` §7 states as
 * requirements rather than as suggestions: only the chord at `activeChordIndex` advances the
 * track, wrong input never does, and a held chord advances it exactly once.
 *
 * Capture is a fake, so a chord "arrives" the instant the test says it does and the whole suite
 * runs without a keyboard. `UnconfinedTestDispatcher` is required, not stylistic: the engine's
 * collectors have to be subscribed before an attempt is delivered, and a replay-free
 * `SharedFlow` drops anything nobody is listening for yet.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressionRunEngineTest {

    private val generator = DefaultProgressionGenerator()
    private val realizer = DefaultChordRealizer()

    private fun progression(
        template: ProgressionTemplate = ProgressionTemplates.MajorTwoFiveOne,
        style: VoicingStyle = VoicingStyle.ANY_VOICING,
    ): Progression {
        val key = KeyContext(requireNotNull(SpelledPitchClass.parseOrNull("C")))
        return assertIs<SpellingResult.Spelled<Progression>>(generator.generate(template, key, style)).value
    }

    /** The chord currently at the play point, in the fourth octave. */
    private fun notesForActiveChord(engine: DefaultProgressionRunEngine): List<Int> {
        val event = requireNotNull(engine.state.value.activeEvent) { "Nothing is at the play point" }
        return realizer.chordTones(event.chord).map { 60 + it.pitchClass.value }.sorted()
    }

    @Test
    fun `a run starts on the first chord and arms for it`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)

        engine.start(progression())

        assertEquals(0, engine.state.value.activeChordIndex)
        assertEquals("Dm7", engine.state.value.activeEvent?.displaySymbol)
        assertEquals(ProgressionRunStatus.RUNNING, engine.state.value.status)
        assertEquals(1, capture.armCount, "The run arms for the chord it is showing")
    }

    @Test
    fun `the right chord advances the track`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())

        capture.deliver(attemptOf(notesForActiveChord(engine)))

        assertEquals(1, engine.state.value.activeChordIndex)
        assertEquals("G7", engine.state.value.activeEvent?.displaySymbol)
        assertEquals(AdvanceReason.CORRECT_CHORD, engine.state.value.lastAdvance)
        assertEquals(1, engine.state.value.clean, "Cleared first time")
    }

    @Test
    fun `a wrong chord does not advance the track`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())

        capture.play(60, 64, 67) // A C major triad, when a Dm7 was asked for.

        assertEquals(0, engine.state.value.activeChordIndex, "Wrong input never moves the track")
        assertFalse(engine.state.value.lastResult?.verdict?.isCorrect ?: true)
        assertEquals(2, capture.armCount, "The same chord is re-armed so it can be tried again")
    }

    @Test
    fun `an incomplete chord does not advance the track`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())

        capture.play(62, 65) // D and F: the start of a Dm7, not a Dm7.

        assertEquals(0, engine.state.value.activeChordIndex)
    }

    @Test
    fun `a chord held after it is accepted cannot advance the track twice`() =
        runTest(UnconfinedTestDispatcher()) {
            val sounding = MutableStateFlow(emptyList<Int>())
            val capture = TestCapture()
            val engine = DefaultProgressionRunEngine(capture, backgroundScope, soundingNotes = sounding)
            engine.start(progression())

            val chord = notesForActiveChord(engine)
            sounding.value = chord // The hands are still down when the chord is accepted.
            capture.deliver(attemptOf(chord))

            assertEquals(1, engine.state.value.activeChordIndex)
            assertTrue(engine.state.value.awaitingRelease, "The keyboard is still holding the chord")

            // A sustained voicing re-delivered — a pedal down, a capture that settles twice.
            capture.deliver(attemptOf(chord))

            assertEquals(
                1,
                engine.state.value.activeChordIndex,
                "One accepted chord is one advance, however long it is held",
            )
        }

    @Test
    fun `letting go re-arms the run for the next chord`() = runTest(UnconfinedTestDispatcher()) {
        val sounding = MutableStateFlow(emptyList<Int>())
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope, soundingNotes = sounding)
        engine.start(progression())

        val first = notesForActiveChord(engine)
        sounding.value = first
        capture.deliver(attemptOf(first))
        val armsWhileHolding = capture.armCount

        sounding.value = emptyList()

        assertFalse(engine.state.value.awaitingRelease)
        assertEquals(armsWhileHolding + 1, capture.armCount, "Releasing arms for the next chord")

        capture.deliver(attemptOf(notesForActiveChord(engine)))
        assertEquals(2, engine.state.value.activeChordIndex, "And the next chord is playable")
    }

    @Test
    fun `a chord already released when it is accepted arms immediately`() =
        runTest(UnconfinedTestDispatcher()) {
            val capture = TestCapture()
            // Sounding stays empty throughout: the attempt completed because the keys came up,
            // which is how most chords finish.
            val engine = DefaultProgressionRunEngine(capture, backgroundScope)
            engine.start(progression())

            capture.deliver(attemptOf(notesForActiveChord(engine)))

            assertFalse(
                engine.state.value.awaitingRelease,
                "There is nothing to wait for when the hands are already off the keys",
            )
            assertEquals(2, capture.armCount, "Armed for the second chord straight away")
        }

    @Test
    fun `playing the whole progression completes the run`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        val progression = progression()
        engine.start(progression)

        repeat(progression.size) {
            capture.deliver(attemptOf(notesForActiveChord(engine)))
        }

        assertEquals(ProgressionRunStatus.COMPLETED, engine.state.value.status)
        assertEquals(3, engine.state.value.advanceCount)
        assertEquals(3, engine.state.value.clean)
        assertTrue(capture.cancelCount > 0, "A finished run stops listening")
    }

    @Test
    fun `a looping run wraps instead of finishing`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        val turnaround = progression(ProgressionTemplates.Turnaround)
        engine.start(turnaround)

        repeat(turnaround.size + 1) {
            capture.deliver(attemptOf(notesForActiveChord(engine)))
        }

        assertEquals(ProgressionRunStatus.RUNNING, engine.state.value.status)
        assertEquals(1, engine.state.value.activeChordIndex, "Back round to the second chord")
        assertEquals(turnaround.size + 1, engine.state.value.advanceCount)
    }

    @Test
    fun `moving on by hand uses the same advance path`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())

        engine.advanceManually()

        assertEquals(1, engine.state.value.activeChordIndex)
        assertEquals(AdvanceReason.MANUAL, engine.state.value.lastAdvance)
        assertEquals(0, engine.state.value.clean, "A skipped chord was not played")
        assertEquals(2, capture.armCount, "The next chord is armed like any other")
    }

    @Test
    fun `a paused run keeps its place and re-arms on resume`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())
        capture.deliver(attemptOf(notesForActiveChord(engine)))

        engine.pause()
        assertEquals(ProgressionRunStatus.PAUSED, engine.state.value.status)

        // Nothing played while paused counts.
        capture.play(60, 64, 67)
        assertEquals(1, engine.state.value.activeChordIndex)

        engine.resume()
        assertEquals(ProgressionRunStatus.RUNNING, engine.state.value.status)
        assertEquals("G7", engine.state.value.activeEvent?.displaySymbol, "Still on the chord it paused on")

        capture.deliver(attemptOf(notesForActiveChord(engine)))
        assertEquals(2, engine.state.value.activeChordIndex)
    }

    @Test
    fun `the run judges the event, not the symbol on the orb`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression(style = VoicingStyle.ROOTLESS_A))

        // The orb says Dm7. Playing a literal D F A C — the symbol, rooted — is not the answer,
        // because the event asks for the rootless shape.
        capture.play(50, 53, 57, 60)
        assertEquals(0, engine.state.value.activeChordIndex)

        // F3 A3 C4 E4 is.
        capture.play(53, 57, 60, 64)
        assertEquals(1, engine.state.value.activeChordIndex)
    }
}
