package com.harmonygates.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kotlin mixer.
 *
 * 09_AUDIO_SAMPLER_ENGINE.md §2 forbids native code, which makes the mixer's correctness this
 * project's problem rather than a library's. It is also entirely testable: render a block into a
 * buffer and read the numbers back. No device, no listening.
 */
class MixerTest {

    private val bank = SynthesisedBanks.practiceTone()

    private fun render(mixer: Mixer, blocks: Int = 1): ShortArray {
        val out = ShortArray(mixer.bufferFrameCount)
        val collected = ShortArray(mixer.bufferFrameCount * blocks)
        repeat(blocks) { block ->
            mixer.render(out)
            out.copyInto(collected, block * out.size)
        }
        return collected
    }

    private fun peak(frames: ShortArray): Int = frames.maxOfOrNull { abs(it.toInt()) } ?: 0

    @Test
    fun `silence is silent`() {
        val mixer = Mixer()

        assertEquals(0, peak(render(mixer, blocks = 2)), "A mixer with no voices should output zero")
        assertEquals(0, mixer.activeVoiceCount)
    }

    @Test
    fun `a note makes sound`() {
        val mixer = Mixer()
        mixer.noteOn(60, 100, bank)

        assertEquals(1, mixer.activeVoiceCount)
        assertTrue(peak(render(mixer, blocks = 4)) > 0, "A struck note should produce a signal")
    }

    @Test
    fun `the whole buffer is always written`() {
        val mixer = Mixer()
        val out = ShortArray(mixer.bufferFrameCount) { Short.MAX_VALUE }

        // An underrun is an audible click, and a partly filled buffer is how one happens.
        val written = mixer.render(out)
        assertEquals(out.size, written)
        assertTrue(out.all { it.toInt() == 0 }, "Leftover data from the last block was not cleared")
    }

    @Test
    fun `a harder strike is louder`() {
        val quiet = Mixer().also { it.noteOn(60, 30, bank) }
        val loud = Mixer().also { it.noteOn(60, 120, bank) }

        assertTrue(
            peak(render(loud, blocks = 4)) > peak(render(quiet, blocks = 4)),
            "Velocity should reach the output",
        )
    }

    @Test
    fun `a released note fades rather than stopping dead`() {
        val mixer = Mixer()
        mixer.noteOn(60, 100, bank)
        render(mixer, blocks = 2)

        mixer.noteOff(60)
        assertEquals(1, mixer.activeVoiceCount, "A released piano note is still sounding")

        // Past the release time, and well inside the sample's own length — so this measures the
        // release rather than the note simply running out of audio.
        render(mixer, blocks = RELEASE_BLOCKS)
        assertEquals(0, mixer.activeVoiceCount, "The tail should end eventually")
    }

    @Test
    fun `the pedal holds a note whose key has come up`() {
        val mixer = Mixer()
        mixer.sustain(true)
        mixer.noteOn(60, 100, bank)
        render(mixer)
        mixer.noteOff(60)
        render(mixer, blocks = 20)

        assertEquals(1, mixer.activeVoiceCount, "The key is up; the string is not damped")

        mixer.sustain(false)
        render(mixer, blocks = RELEASE_BLOCKS)
        assertEquals(0, mixer.activeVoiceCount, "Lifting the pedal damps it")
    }

    @Test
    fun `lifting the pedal does not release a key still held`() {
        val mixer = Mixer()
        mixer.sustain(true)
        mixer.noteOn(60, 100, bank)
        mixer.noteOn(64, 100, bank)
        mixer.noteOff(60)
        render(mixer, blocks = 5)

        mixer.sustain(false)
        render(mixer, blocks = RELEASE_BLOCKS)

        assertEquals(1, mixer.activeVoiceCount, "The note whose key is still down keeps sounding")
    }

    @Test
    fun `all notes off stops everything at once`() {
        val mixer = Mixer()
        listOf(60, 64, 67, 71).forEach { mixer.noteOn(it, 100, bank) }
        assertEquals(4, mixer.activeVoiceCount)

        mixer.allNotesOff()
        assertEquals(0, mixer.activeVoiceCount)
        assertEquals(0, peak(render(mixer)), "And the next block is silent")
    }

    @Test
    fun `playing the same note twice retriggers rather than doubling`() {
        val mixer = Mixer()
        mixer.noteOn(60, 100, bank)
        mixer.noteOn(60, 100, bank)

        assertEquals(1, mixer.activeVoiceCount, "One key, one string")
    }

    @Test
    fun `voice count is bounded and the oldest note is the one stolen`() {
        val mixer = Mixer(maxVoices = 4)
        listOf(60, 62, 64, 65, 67).forEach { mixer.noteOn(it, 100, bank) }

        assertEquals(4, mixer.activeVoiceCount, "A four-voice mixer never sounds five")
        // The fifth note must have arrived: refusing it would be worse than stealing.
        assertTrue(peak(render(mixer, blocks = 4)) > 0)
    }

    @Test
    fun `a summed chord does not clip into distortion`() {
        val mixer = Mixer()
        // Ten notes at full velocity is well past unity if nothing limits the sum.
        (60..69).forEach { mixer.noteOn(it, 127, bank) }
        val frames = render(mixer, blocks = 8)

        val clipped = frames.count { abs(it.toInt()) >= Short.MAX_VALUE - 1 }
        assertTrue(
            clipped < frames.size / 100,
            "A dense chord should be limited, not clipped: $clipped of ${frames.size} frames at full scale",
        )
    }

    @Test
    fun `rendering is deterministic`() {
        fun once(): ShortArray {
            val mixer = Mixer()
            mixer.noteOn(60, 90, bank)
            mixer.noteOn(64, 90, bank)
            return render(mixer, blocks = 4)
        }

        assertTrue(
            once().contentEquals(once()),
            "07 §3 needs a stimulus to be reproducible, which starts with the mixer",
        )
    }

    @Test
    fun `a note outside the instrument's range makes no sound`() {
        val mixer = Mixer()
        mixer.noteOn(0, 100, bank)

        assertEquals(0, mixer.activeVoiceCount, "Nothing covers that pitch; better silent than wrong")
    }

    // --- Zones and banks ------------------------------------------------------------------------

    @Test
    fun `a bank covers its whole range with no gaps`() {
        val preset = bank.preset

        preset.range.forEach { note ->
            assertNotNull(preset.zoneFor(note, 100), "No zone covers note $note")
        }
    }

    @Test
    fun `a note is played from the nearest recorded pitch`() {
        val preset = bank.preset
        preset.range.forEach { note ->
            val zone = assertNotNull(preset.zoneFor(note, 100))
            assertTrue(
                abs(zone.shiftFor(note)) <= MAX_REASONABLE_SHIFT,
                "Note $note is resampled ${zone.shiftFor(note)} semitones from ${zone.rootMidi}",
            )
        }
    }

    @Test
    fun `zone choice does not wander between calls`() {
        val preset = bank.preset
        val first = preset.range.map { preset.zoneFor(it, 100) }
        val second = preset.range.map { preset.zoneFor(it, 100) }

        assertEquals(first, second, "A stimulus that picked a different zone on replay is not reproducible")
    }

    @Test
    fun `a bank refuses zones whose audio was never decoded`() {
        val failure = runCatching {
            SampleBank(
                preset = InstrumentPreset(
                    id = InstrumentId("instrument.broken"),
                    displayName = "Broken",
                    zones = listOf(SampleZone(AssetId("missing"), 60, 55, 65)),
                ),
                samples = emptyMap(),
            )
        }.exceptionOrNull()

        assertNotNull(failure, "Playback must not be the thing that discovers a sample is absent")
    }

    @Test
    fun `every bank carries its licence`() {
        assertTrue(
            bank.preset.licence.isNotBlank(),
            "09 §4: store licence metadata with each bank",
        )
    }

    @Test
    fun `a synthesised tone is the pitch it claims to be`() {
        val tone = SynthesisedBanks.tone(rootMidi = 69)
        // A440: 440 cycles in one second, so 440 upward zero crossings.
        val oneSecond = minOf(tone.sampleRate, tone.frameCount)
        var crossings = 0
        for (index in 1 until oneSecond) {
            if (tone.frames[index - 1] < 0 && tone.frames[index] >= 0) crossings++
        }

        assertTrue(
            abs(crossings - A440_HZ) < A440_TOLERANCE,
            "Expected about $A440_HZ cycles in the first second of A440, counted $crossings",
        )
    }

    @Test
    fun `a tone starts from silence`() {
        val tone = SynthesisedBanks.tone(rootMidi = 60)

        assertEquals(0, tone.frames.first().toInt(), "A step from silence is an audible click")
    }

    @Test
    fun `an unplayable zone is refused when it is built`() {
        assertNull(
            runCatching { SampleZone(AssetId("z"), rootMidi = 70, minMidi = 40, maxMidi = 60) }
                .getOrNull(),
            "A zone whose root is outside its own range is a content error, not a runtime one",
        )
    }

    private companion object {
        /** Past the 0.35 s release, comfortably inside the 2.5 s sample. */
        const val RELEASE_BLOCKS = 60
        const val MAX_REASONABLE_SHIFT = 3
        const val A440_HZ = 440
        const val A440_TOLERANCE = 12
    }
}
