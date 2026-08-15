package com.harmonygates.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keyboard geometry. Wrong here means a visualisation that misleads the player. */
class KeyboardLayoutTest {

    @Test
    fun `the five raised keys of an octave are black`() {
        // C4..B4. C# D# F# G# A# are black; the rest are white.
        val blacks = (60..71).filter { KeyboardLayout.isBlackKey(it) }
        assertEquals(listOf(61, 63, 66, 68, 70), blacks)
    }

    @Test
    fun `there is no black key between E and F, or between B and C`() {
        assertTrue(!KeyboardLayout.isBlackKey(64), "E")
        assertTrue(!KeyboardLayout.isBlackKey(65), "F")
        assertTrue(!KeyboardLayout.isBlackKey(71), "B")
        assertTrue(!KeyboardLayout.isBlackKey(72), "C")
    }

    @Test
    fun `black keys repeat every octave, in both directions`() {
        for (octaveOffset in listOf(-24, -12, 0, 12, 24)) {
            assertTrue(KeyboardLayout.isBlackKey(61 + octaveOffset), "C sharp at offset $octaveOffset")
            assertTrue(!KeyboardLayout.isBlackKey(60 + octaveOffset), "C at offset $octaveOffset")
        }
    }

    @Test
    fun `an octave has seven white keys`() {
        assertEquals(7, KeyboardLayout.whiteNotes(60..71).size)
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71), KeyboardLayout.whiteNotes(60..71))
    }

    @Test
    fun `a black key sits on the boundary between the white keys around it`() {
        val whites = KeyboardLayout.whiteNotes(60..71)
        val whiteWidth = 10f

        // C sharp sits on the C/D boundary: after one white key.
        assertEquals(10f, KeyboardLayout.blackKeyCentre(61, whites, whiteWidth))
        // D sharp sits on the D/E boundary: after two.
        assertEquals(20f, KeyboardLayout.blackKeyCentre(63, whites, whiteWidth))
        // F sharp is after four white keys, because there is no black key between E and F.
        assertEquals(40f, KeyboardLayout.blackKeyCentre(66, whites, whiteWidth))
        assertEquals(50f, KeyboardLayout.blackKeyCentre(68, whites, whiteWidth))
        assertEquals(60f, KeyboardLayout.blackKeyCentre(70, whites, whiteWidth))
    }

    @Test
    fun `a white key has no black-key position`() {
        assertNull(KeyboardLayout.blackKeyCentre(60, KeyboardLayout.whiteNotes(60..71), 10f))
    }

    @Test
    fun `a black key whose white neighbour is out of range is skipped`() {
        // A keyboard starting on C sharp: there is no C below it to anchor against.
        val whites = KeyboardLayout.whiteNotes(61..71)
        assertNull(KeyboardLayout.blackKeyCentre(61, whites, 10f))
    }

    @Test
    fun `the default diagnostics range is a whole number of octaves`() {
        // C2..C7, the window the diagnostics screen opens on.
        assertEquals(36, KeyboardLayout.whiteNotes(36..96).size, "Five octaves plus the top C")
    }
}
