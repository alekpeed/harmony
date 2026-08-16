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
        assertEquals(1, capture.armCount)
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
        assertEquals(1, engine.state.value.clean)
    }

    @Test
    fun `a wrong chord does not advance the track`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())
        capture.play(60, 64, 67)
        assertEquals(0, engine.state.value.activeChordIndex)
        assertFalse(engine.state.value.lastResult?.verdict?.isCorrect ?: true)
        assertEquals(2, capture.armCount)
    }

    @Test
    fun `an incomplete chord does not advance the track`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())
        capture.play(62, 65)
        assertEquals(0, engine.state.value.activeChordIndex)
    }

    @Test
    fun `a chord held after it is accepted cannot advance the track twice`() = runTest(UnconfinedTestDispatcher()) {
        val sounding = MutableStateFlow(emptyList<Int>())
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope, soundingNotes = sounding)
        engine.start(progression())
        val chord = notesForActiveChord(engine)
        sounding.value = chord
        capture.deliver(attemptOf(chord))
        assertEquals(1, engine.state.value.activeChordIndex)
        assertTrue(engine.state.value.awaitingRelease)
        capture.deliver(attemptOf(chord))
        assertEquals(1, engine.state.value.activeChordIndex)
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
        assertEquals(armsWhileHolding + 1, capture.armCount)
        capture.deliver(attemptOf(notesForActiveChord(engine)))
        assertEquals(2, engine.state.value.activeChordIndex)
    }

    @Test
    fun `a chord already released when it is accepted arms immediately`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())
        capture.deliver(attemptOf(notesForActiveChord(engine)))
        assertFalse(engine.state.value.awaitingRelease)
        assertEquals(2, capture.armCount)
    }

    @Test
    fun `playing the whole progression completes the run without leaving its bounds`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        val progression = progression()
        engine.start(progression)
        repeat(progression.size) {
            capture.deliver(attemptOf(notesForActiveChord(engine)))
        }
        assertEquals(ProgressionRunStatus.COMPLETED, engine.state.value.status)
        assertEquals(progression.size - 1, engine.state.value.activeChordIndex)
        assertEquals(progression.events.last().displaySymbol, engine.state.value.activeEvent?.displaySymbol)
        assertEquals(3, engine.state.value.advanceCount)
        assertEquals(3, engine.state.value.clean)
        assertTrue(capture.cancelCount > 0)
    }

    @Test
    fun `a looping run wraps instead of finishing`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        val turnaround = progression(ProgressionTemplates.Turnaround).copy(loop = true)
        engine.start(turnaround)
        repeat(turnaround.size + 1) {
            capture.deliver(attemptOf(notesForActiveChord(engine)))
        }
        assertEquals(ProgressionRunStatus.RUNNING, engine.state.value.status)
        assertEquals(1, engine.state.value.activeChordIndex)
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
        assertEquals(0, engine.state.value.clean)
        assertEquals(2, capture.armCount)
    }

    @Test
    fun `a paused run keeps its place and re-arms on resume`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression())
        capture.deliver(attemptOf(notesForActiveChord(engine)))
        engine.pause()
        assertEquals(ProgressionRunStatus.PAUSED, engine.state.value.status)
        capture.play(60, 64, 67)
        assertEquals(1, engine.state.value.activeChordIndex)
        engine.resume()
        assertEquals(ProgressionRunStatus.RUNNING, engine.state.value.status)
        assertEquals("G7", engine.state.value.activeEvent?.displaySymbol)
        capture.deliver(attemptOf(notesForActiveChord(engine)))
        assertEquals(2, engine.state.value.activeChordIndex)
    }

    @Test
    fun `the run judges the event, not the symbol on the orb`() = runTest(UnconfinedTestDispatcher()) {
        val capture = TestCapture()
        val engine = DefaultProgressionRunEngine(capture, backgroundScope)
        engine.start(progression(style = VoicingStyle.ROOTLESS_A))
        capture.play(50, 53, 57, 60)
        assertEquals(0, engine.state.value.activeChordIndex)
        capture.play(53, 57, 60, 64)
        assertEquals(1, engine.state.value.activeChordIndex)
    }
}
