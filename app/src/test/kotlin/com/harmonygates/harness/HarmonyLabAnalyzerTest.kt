package com.harmonygates.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The harness screen's state mapping.
 *
 * The point of these tests is the boundary rather than the theory: every musical claim on the
 * screen must have come from `core:music`, so the assertions check that the state carries the
 * domain's answer rather than one the UI layer invented.
 */
class HarmonyLabAnalyzerTest {

    private val analyzer = HarmonyLabAnalyzer()

    @Test
    fun `a valid symbol produces degrees, tones and a voicing`() {
        val state = analyzer.analyze("Cmaj7")

        assertTrue(state.isValid)
        assertEquals("Cmaj7", state.chordSymbol)
        assertEquals(listOf("1", "3", "5", "7"), state.degrees)
        assertEquals(listOf("C", "E", "G", "B"), state.tones)
        assertEquals(4, state.voicing.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `spelling reaches the screen unflattened`() {
        val state = analyzer.analyze("Db7")
        assertEquals(listOf("Db", "F", "Ab", "Cb"), state.tones, "The screen must show Cb, not B")
    }

    @Test
    fun `a slash chord reports its bass and voices it in the bass`() {
        val state = analyzer.analyze("C/E")

        assertTrue(state.isValid)
        assertEquals("E", state.bassNote)
        assertTrue(state.voicing.first().startsWith("E"), "E should be the lowest note, got ${state.voicing}")
    }

    @Test
    fun `a chord without a slash is shown in root position`() {
        val state = analyzer.analyze("Am7")
        assertNull(state.bassNote)
        assertTrue(state.voicing.first().startsWith("A"), "Expected root position, got ${state.voicing}")
    }

    @Test
    fun `an unreadable symbol reports the domain's own message`() {
        val state = analyzer.analyze("H7")

        assertFalse(state.isValid)
        assertNotNull(state.errorMessage)
        assertTrue("note name" in state.errorMessage.orEmpty(), "Got: ${state.errorMessage}")
    }

    @Test
    fun `blank input asks for a symbol rather than reporting an error`() {
        val state = analyzer.analyze("   ")
        assertFalse(state.isValid)
        assertTrue("Type a chord symbol" in state.errorMessage.orEmpty())
    }

    @Test
    fun `a chord that cannot be notated says so instead of showing a wrong spelling`() {
        val state = analyzer.analyze("Cbdim7")
        assertFalse(state.isValid)
        assertTrue("enharmonic" in state.errorMessage.orEmpty(), "Got: ${state.errorMessage}")
    }

    @Test
    fun `the view model starts with a parsed chord and updates on intent`() {
        val viewModel = HarmonyLabViewModel(analyzer)
        assertEquals("Cmaj7", viewModel.state.value.chordSymbol)

        viewModel.onIntent(HarmonyLabIntent.SymbolChanged("F#7#9"))
        val updated = viewModel.state.value
        assertEquals("F#7#9", updated.chordSymbol)
        assertEquals(
            listOf("F#", "A#", "C#", "E", "Gx"),
            updated.tones,
            "The sharp ninth of F#7 is a G double-sharp, not an A",
        )
    }
}
