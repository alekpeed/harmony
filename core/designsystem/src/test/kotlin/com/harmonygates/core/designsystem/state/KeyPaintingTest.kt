package com.harmonygates.core.designsystem.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Piano key state, which 12_UI_UX_AND_FIGMA_HANDOFF.md §7 makes the most detailed contract in the
 * design system: nine states, layered, and "do not encode these states only by color".
 *
 * The mapping is a pure function with no access to the palette, so that rule can be checked
 * rather than trusted — and it is checked the way it matters, by asking whether two states a
 * player must tell apart differ in something other than their fill.
 */
class KeyPaintingTest {

    /** The nine states §7 lists, named as the spec names them. */
    private val specStates: Map<String, PianoKeyState> = mapOf(
        "inactive" to PianoKeyState.Inactive,
        "target tone" to PianoKeyState(role = KeyRole.TARGET),
        "required tone" to PianoKeyState(role = KeyRole.REQUIRED),
        "optional tone" to PianoKeyState(role = KeyRole.OPTIONAL),
        "physically held" to PianoKeyState(held = true),
        "sustained" to PianoKeyState(sustained = true),
        "correct performed tone" to PianoKeyState(held = true, verdict = KeyVerdict.CORRECT),
        "incorrect extra tone" to PianoKeyState(held = true, verdict = KeyVerdict.EXTRA),
        "missing tone after evaluation" to PianoKeyState(verdict = KeyVerdict.MISSING),
    )

    @Test
    fun `every state the spec lists is representable`() {
        assertEquals(SPEC_STATE_COUNT, specStates.size, "12 §7 lists nine states")
        specStates.forEach { (name, state) ->
            val painting = KeyPaintings.of(state)
            assertTrue(painting.fill in KeyFill.entries, "$name has no fill")
        }
    }

    @Test
    fun `no two states are told apart by colour alone`() {
        // The pairs a player has to distinguish are all of them, so check all of them.
        val shapes = specStates.mapValues { (_, state) ->
            val painting = KeyPaintings.of(state)
            painting.marker to painting.outline
        }

        val collisions = shapes.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .mapValues { (_, entries) -> entries.map { it.key } }

        assertEquals(
            emptyMap(),
            collisions,
            "These states share a marker and an outline, so a colour-blind player cannot tell " +
                "them apart: $collisions",
        )
    }

    @Test
    fun `the pedal and a finger look different`() {
        val held = KeyPaintings.of(PianoKeyState(held = true))
        val sustained = KeyPaintings.of(PianoKeyState(sustained = true))

        // 05_MIDI_INPUT_ENGINE.md §5 keeps these apart, and a player chasing a stuck note needs
        // to see which of the two they are looking at.
        assertEquals(KeyOutline.THICK, held.outline)
        assertEquals(KeyOutline.DASHED, sustained.outline)
        assertNotEquals(held.fill, sustained.fill)
    }

    @Test
    fun `a key held and sustained at once reads as held`() {
        // The finger is the live fact; the pedal is why it will still sound when the finger goes.
        val both = KeyPaintings.of(PianoKeyState(held = true, sustained = true))

        assertEquals(KeyFill.PRESSED, both.fill)
        assertEquals(KeyOutline.THICK, both.outline)
    }

    @Test
    fun `a verdict outranks what was being asked for`() {
        // A required tone played wrong must not go on saying "required": the player has been
        // told the answer, and the marker they need now is the one about what they did.
        val requiredAndExtra = KeyPaintings.of(
            PianoKeyState(role = KeyRole.REQUIRED, held = true, verdict = KeyVerdict.EXTRA),
        )
        assertEquals(KeyMarker.CROSS, requiredAndExtra.marker)
        assertEquals(KeyFill.ERROR, requiredAndExtra.fill, "A wrong note is not shown as pressed")

        val requiredAndCorrect = KeyPaintings.of(
            PianoKeyState(role = KeyRole.REQUIRED, held = true, verdict = KeyVerdict.CORRECT),
        )
        assertEquals(KeyMarker.TICK, requiredAndCorrect.marker)
    }

    @Test
    fun `a required tone and an optional one are different markers, not different shades`() {
        assertEquals(KeyMarker.DOT, KeyPaintings.of(PianoKeyState(role = KeyRole.REQUIRED)).marker)
        assertEquals(KeyMarker.RING, KeyPaintings.of(PianoKeyState(role = KeyRole.OPTIONAL)).marker)
    }

    @Test
    fun `a tone missing after evaluation is marked even though nothing was played`() {
        val missing = KeyPaintings.of(PianoKeyState(verdict = KeyVerdict.MISSING))

        assertEquals(KeyMarker.DIAMOND, missing.marker)
        assertEquals(KeyFill.NATURAL, missing.fill, "Nothing was played, so nothing looks pressed")
        assertNotEquals(KeyOutline.NONE, missing.outline, "A missing tone must be findable")
    }

    @Test
    fun `an untouched key carries no marks at all`() {
        val inactive = KeyPaintings.of(PianoKeyState.Inactive)

        assertEquals(KeyMarker.NONE, inactive.marker)
        assertEquals(KeyOutline.NONE, inactive.outline)
        assertEquals(KeyFill.NATURAL, inactive.fill)
    }

    @Test
    fun `the mapping is total`() {
        // Every combination, not just the nine the spec names: layering means the app can
        // produce combinations nobody wrote down, and none of them may throw.
        for (role in KeyRole.entries) {
            for (verdict in KeyVerdict.entries) {
                for (held in listOf(true, false)) {
                    for (sustained in listOf(true, false)) {
                        val painting = KeyPaintings.of(PianoKeyState(role, held, sustained, verdict))
                        assertTrue(painting.marker in KeyMarker.entries)
                    }
                }
            }
        }
    }

    private companion object {
        const val SPEC_STATE_COUNT = 9
    }
}
