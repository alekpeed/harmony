package com.harmonygates.core.designsystem.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The status vocabularies.
 *
 * 12 §8 requires MIDI state to be discoverable at all times and 12 §10 requires TalkBack labels,
 * so every state here has to carry a glyph and words of its own. A state that fell back to its
 * enum name would reach a screen reader as "IN_PROGRESS".
 */
class StatusPresentationTest {

    @Test
    fun `every MIDI state has its own glyph and its own words`() {
        assertEquals(
            MidiPresentation.entries.size,
            MidiPresentation.entries.map { it.glyph }.toSet().size,
            "Two MIDI states share a glyph",
        )
        MidiPresentation.entries.forEach {
            assertTrue(it.shortLabel.isNotBlank(), "$it has no label")
            assertTrue(it.description().isNotBlank(), "$it has nothing to announce")
        }
    }

    @Test
    fun `a connected keyboard is announced by name when there is one`() {
        assertEquals(
            "MIDI connected: Nord Stage 4",
            MidiPresentation.CONNECTED.description("Nord Stage 4"),
        )
        assertEquals("MIDI connected", MidiPresentation.CONNECTED.description(null))
        assertEquals(
            "No MIDI keyboard connected",
            MidiPresentation.DISCONNECTED.description("Nord Stage 4"),
            "A device name is meaningless when nothing is connected",
        )
    }

    @Test
    fun `every gate state has its own glyph`() {
        assertEquals(
            GatePresentation.entries.size,
            GatePresentation.entries.map { it.glyph }.toSet().size,
        )
        GatePresentation.entries.forEach {
            assertTrue(it.shortLabel.isNotBlank(), "$it has no label")
        }
    }

    @Test
    fun `only a locked gate cannot be started`() {
        assertEquals(
            setOf(GatePresentation.LOCKED),
            GatePresentation.entries.filterNot { it.isPlayable }.toSet(),
        )
    }

    @Test
    fun `every verdict has its own glyph`() {
        assertEquals(
            FeedbackPresentation.entries.size,
            FeedbackPresentation.entries.map { it.glyph }.toSet().size,
            "Two verdicts share a glyph, so colour would be the only difference",
        )
        FeedbackPresentation.entries.forEach {
            assertTrue(it.shortLabel.isNotBlank(), "$it has no label")
        }
    }
}
