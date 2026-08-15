package com.harmonygates.core.midi

import app.cash.turbine.test
import com.harmonygates.core.testing.TestMonotonicClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The simulator, and the hotplug behaviour 05_MIDI_INPUT_ENGINE.md §9 requires.
 *
 * These are the tests that let continuous integration exercise the MIDI path with no hardware
 * attached — 18_ACCEPTANCE_CRITERIA.md lists that as release-blocking. Time is injected, so a
 * 250 ms rolled chord costs the suite nothing.
 */
class FakeMidiInputSourceTest {

    private val clock = TestMonotonicClock()

    private fun source(
        endpoints: List<MidiEndpoint> = listOf(FakeMidiInputSource.DEFAULT_ENDPOINT),
        autoConnect: Boolean = true,
    ) = FakeMidiInputSource(clock, endpoints, autoConnect)

    @Test
    fun `it starts connected so a test can simply play`() = runTest {
        val midi = source()
        assertIs<MidiConnectionState.Connected>(midi.connectionState.value)
        assertTrue(midi.connectionState.value.isReceiving)
    }

    @Test
    fun `notes reach the active-note state`() = runTest {
        val midi = source()
        midi.chord(60, 64, 67, 71)

        assertEquals(
            listOf(60, 64, 67, 71),
            midi.activeNotes.value.soundingAscending.map { it.value },
        )
    }

    @Test
    fun `events are emitted to collectors`() = runTest {
        val midi = source()
        midi.events.test {
            midi.noteOn(60)
            midi.noteOff(60)

            assertIs<MidiEvent.NoteOn>(awaitItem())
            assertIs<MidiEvent.NoteOff>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timestamps come from the injected clock`() = runTest {
        val midi = source()
        midi.noteOn(60)
        clock.advanceMillis(80)
        midi.noteOn(64)

        val spread = midi.emitted[1].timestampNanos - midi.emitted[0].timestampNanos
        assertEquals(80_000_000L, spread, "An 80 ms hand spread, and no wall-clock time spent")
    }

    @Test
    fun `a rolled chord is simulated without sleeping`() = runTest {
        val midi = source()
        // 250 ms across four notes, the roll threshold in §6.
        listOf(60, 64, 67, 71).forEach { note ->
            midi.noteOn(note)
            clock.advanceMillis(ROLL_STEP_MS)
        }

        assertEquals(4, midi.activeNotes.value.sounding.size)
        val spread = midi.emitted.last().timestampNanos - midi.emitted.first().timestampNanos
        assertEquals(240_000_000L, spread)
    }

    @Test
    fun `sustain works through the source`() = runTest {
        val midi = source()
        midi.noteOn(60)
        midi.sustain(down = true)
        midi.noteOff(60)

        assertEquals(1, midi.activeNotes.value.sounding.size, "Still ringing under the pedal")

        midi.sustain(down = false)
        assertTrue(midi.activeNotes.value.isSilent)
    }

    @Test
    fun `raw bytes go through the real parser`() = runTest {
        val midi = source()
        // Running status: one status byte, three notes.
        midi.receiveBytes(byteArrayOf(0x90.toByte(), 60, 100, 64, 100, 67, 100))

        assertEquals(
            listOf(60, 64, 67),
            midi.activeNotes.value.soundingAscending.map { it.value },
        )
    }

    @Test
    fun `disconnecting mid-chord clears notes and reports why`() = runTest {
        val midi = source()
        midi.chord(60, 64, 67)
        assertEquals(3, midi.activeNotes.value.sounding.size)

        midi.disconnect()

        assertTrue(
            midi.activeNotes.value.isSilent,
            "A chord held when the cable was pulled is not being held any more",
        )
        val state = assertIs<MidiConnectionState.Error>(midi.connectionState.value)
        val reason = assertIs<MidiError.DeviceDisconnected>(state.reason)
        assertEquals(FakeMidiInputSource.DEFAULT_ENDPOINT, reason.endpoint)
        assertTrue(!midi.connectionState.value.isReceiving)
    }

    @Test
    fun `reconnecting restores a usable connection`() = runTest {
        val midi = source()
        midi.disconnect()
        midi.reconnect()

        assertIs<MidiConnectionState.Connected>(midi.connectionState.value)
        midi.chord(60, 64)
        assertEquals(2, midi.activeNotes.value.sounding.size, "Playing works again after a replug")
    }

    @Test
    fun `the connection state reports the endpoint through a disconnect`() = runTest {
        val midi = source()
        val before = midi.connectionState.value.activeEndpoint
        midi.disconnect()

        assertEquals(before, midi.connectionState.value.activeEndpoint, "The UI can name what was lost")
    }

    @Test
    fun `several keyboards are offered rather than chosen for the player`() = runTest {
        val second = FakeMidiInputSource.DEFAULT_ENDPOINT.copy(id = "fake-1", name = "Second Keyboard")
        val midi = source(
            endpoints = listOf(FakeMidiInputSource.DEFAULT_ENDPOINT, second),
            autoConnect = false,
        )
        midi.start()

        val state = assertIs<MidiConnectionState.DevicesAvailable>(midi.connectionState.value)
        assertEquals(2, state.devices.size)

        midi.connectTo(second)
        assertEquals(second, assertIs<MidiConnectionState.Connected>(midi.connectionState.value).endpoint)
    }

    @Test
    fun `a device with no MIDI support says so`() = runTest {
        val midi = source()
        midi.reportUnsupported()

        assertIs<MidiConnectionState.Unsupported>(midi.connectionState.value)
        assertTrue(!midi.connectionState.value.isReceiving)
    }

    @Test
    fun `no endpoints means no device rather than an error`() = runTest {
        val midi = source(endpoints = emptyList(), autoConnect = false)
        assertIs<MidiConnectionState.NoDevice>(midi.connectionState.value)
    }

    @Test
    fun `clearing active notes leaves the connection open`() = runTest {
        val midi = source()
        midi.chord(60, 64)
        midi.clearActiveNotes()

        assertTrue(midi.activeNotes.value.isSilent)
        assertTrue(midi.connectionState.value.isReceiving, "Arming an attempt must not drop the device")
    }

    @Test
    fun `start and stop are idempotent`() = runTest {
        val midi = source()
        midi.start()
        midi.start()
        assertTrue(midi.isStarted)

        midi.stop()
        midi.stop()
        assertTrue(!midi.isStarted)
    }

    private companion object {
        const val ROLL_STEP_MS = 80L
    }
}
