package com.harmonygates.core.audio

import kotlin.math.abs
import kotlin.math.pow

/**
 * Where a voice is in its life.
 *
 * Release is a state rather than a flag because a released voice is still sounding — a piano
 * note does not stop when the key comes up — and the mixer has to keep rendering it until the
 * tail runs out.
 */
internal enum class VoiceStage { ATTACK, SUSTAIN, RELEASE, FINISHED }

/**
 * One sounding note.
 *
 * Mutable and reused. 09_AUDIO_SAMPLER_ENGINE.md §6: "Avoid allocation inside the real-time
 * render loop." A voice pool of fixed objects is how that is achieved — a chord does not
 * allocate, it claims.
 */
internal class Voice {
    var note: Int = -1
    var velocity: Int = 0
    var sample: PcmSample? = null
    var stage: VoiceStage = VoiceStage.FINISHED

    /** Fractional read position, in source frames. */
    var position: Double = 0.0

    /** Source frames consumed per output frame: pitch shift and sample-rate conversion together. */
    var step: Double = 1.0

    var gain: Float = 1f
    var envelope: Float = 0f
    var releaseRate: Float = 0f

    /** Held by the pedal after the key came up. */
    var sustained: Boolean = false

    /** Rising order of claim, so the oldest voice is the one stolen. */
    var startedAt: Long = 0

    val isActive: Boolean get() = stage != VoiceStage.FINISHED

    fun release() {
        if (stage == VoiceStage.FINISHED) return
        stage = VoiceStage.RELEASE
    }

    fun stop() {
        stage = VoiceStage.FINISHED
        sample = null
        note = -1
        sustained = false
        envelope = 0f
    }
}

/**
 * The Kotlin mixer.
 *
 * Everything 09 §6 lists: an active voice list, resampling, an envelope, per-voice gain, a
 * summed float buffer, a limiter, and conversion to PCM. What it deliberately does not do is
 * allocate — the voice pool and both buffers are made once, at construction, and the render loop
 * only ever writes into them.
 *
 * It is also entirely free of Android. `AudioTrack` is a sink this hands a `ShortArray` to, and
 * keeping the two apart is what lets the mixing be tested exactly, on the JVM, by rendering into
 * a buffer and looking at the numbers.
 */
public class Mixer(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    maxVoices: Int = InstrumentPreset.DEFAULT_MAX_VOICES,
    bufferFrames: Int = DEFAULT_BUFFER_FRAMES,
) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(maxVoices > 0) { "A mixer needs at least one voice" }
        require(bufferFrames > 0) { "A mixer needs a buffer to render into" }
    }

    private val voices = Array(maxVoices) { Voice() }
    private val accumulator = FloatArray(bufferFrames)
    private var claimCounter = 0L
    private var pedalDown = false

    /** Voices currently making sound. Diagnostic; the render loop does not call it. */
    public val activeVoiceCount: Int get() = voices.count { it.isActive }

    public val bufferFrameCount: Int get() = accumulator.size

    /**
     * Starts a note.
     *
     * A note already sounding is retriggered rather than doubled: pressing a key twice on a
     * piano does not produce two strings.
     */
    public fun noteOn(
        note: Int,
        velocity: Int,
        bank: SampleBank,
    ) {
        val (zone, sample) = bank.sampleFor(note, velocity) ?: return
        val voice = voices.firstOrNull { it.note == note && it.isActive } ?: claim() ?: return

        voice.note = note
        voice.velocity = velocity
        voice.sample = sample
        voice.stage = VoiceStage.ATTACK
        voice.position = 0.0
        voice.step = stepFor(zone, sample, note)
        voice.gain = gainFor(velocity, bank.preset.gainDb)
        voice.envelope = 0f
        voice.sustained = false
        voice.startedAt = claimCounter++
        voice.releaseRate = releaseRateFor(bank.preset.releaseSeconds)
    }

    /**
     * Ends a note.
     *
     * With the pedal down the voice is marked sustained instead of released, which is the same
     * distinction `ActiveNoteTracker` makes on the input side: the key is up, the string is not
     * damped.
     */
    public fun noteOff(note: Int, pedalBehavior: PedalBehavior = PedalBehavior.SUSTAIN) {
        voices.filter { it.note == note && it.isActive }.forEach { voice ->
            if (pedalDown && pedalBehavior == PedalBehavior.SUSTAIN) {
                voice.sustained = true
            } else {
                voice.release()
            }
        }
    }

    /** Lifting the pedal releases everything it was holding, and nothing it was not. */
    public fun sustain(down: Boolean) {
        pedalDown = down
        if (!down) {
            voices.filter { it.sustained }.forEach {
                it.sustained = false
                it.release()
            }
        }
    }

    public fun allNotesOff() {
        pedalDown = false
        voices.forEach { it.stop() }
    }

    /**
     * Renders the next block into [out], and returns how many frames were written.
     *
     * The whole buffer is always filled — silence is frames of zero, not a short write — because
     * an underrun in `AudioTrack` is an audible click and a partially filled buffer is the usual
     * way to cause one.
     */
    public fun render(out: ShortArray): Int {
        val frames = minOf(out.size, accumulator.size)
        java.util.Arrays.fill(accumulator, 0, frames, 0f)

        for (voice in voices) {
            if (!voice.isActive) continue
            renderVoice(voice, frames)
        }

        for (index in 0 until frames) {
            out[index] = toPcm(limit(accumulator[index]))
        }
        return frames
    }

    private fun renderVoice(voice: Voice, frames: Int) {
        val sample = voice.sample ?: return voice.stop()
        val data = sample.frames
        val loop = sample.loop
        val lastFrame = data.size - 1

        for (index in 0 until frames) {
            if (voice.stage == VoiceStage.FINISHED) return

            // Linear interpolation between neighbouring frames. Enough for a sampler whose zones
            // are bounded to a few semitones; §5 puts more sampled pitches ahead of better
            // interpolation for exactly that reason.
            val whole = voice.position.toInt()
            if (whole >= lastFrame) {
                if (loop != null) {
                    voice.position = loop.startFrame + (voice.position - loop.endFrame)
                    continue
                }
                voice.stop()
                return
            }
            val fraction = (voice.position - whole).toFloat()
            val current = data[whole].toFloat()
            val next = data[whole + 1].toFloat()
            val interpolated = current + (next - current) * fraction

            voice.envelope = when (voice.stage) {
                VoiceStage.ATTACK -> {
                    val next = voice.envelope + ATTACK_RATE
                    if (next >= 1f) voice.stage = VoiceStage.SUSTAIN
                    next.coerceAtMost(1f)
                }

                VoiceStage.SUSTAIN -> 1f
                VoiceStage.RELEASE -> {
                    val next = voice.envelope - voice.releaseRate
                    if (next <= 0f) {
                        voice.stop()
                        return
                    }
                    next
                }

                VoiceStage.FINISHED -> return
            }

            accumulator[index] += interpolated * voice.envelope * voice.gain / Short.MAX_VALUE
            voice.position += voice.step

            if (loop != null && voice.position >= loop.endFrame) {
                voice.position -= (loop.endFrame - loop.startFrame)
            }
        }
    }

    /**
     * Frees a voice, stealing the oldest if every one is busy.
     *
     * Stealing the oldest rather than refusing the note: a player who holds a wide voicing with
     * the pedal down and then plays another should hear the new chord, and the note that has
     * been decaying longest is the one nobody will miss.
     */
    private fun claim(): Voice? {
        voices.firstOrNull { !it.isActive }?.let { return it }
        return voices.minByOrNull { it.startedAt }
    }

    private fun stepFor(zone: SampleZone, sample: PcmSample, note: Int): Double {
        val semitones = zone.shiftFor(note)
        val pitchRatio = SEMITONE_RATIO.pow(semitones.toDouble())
        // Sample-rate conversion and pitch shift are the same operation, so they multiply rather
        // than being applied in two passes.
        return pitchRatio * (sample.sampleRate.toDouble() / sampleRate)
    }

    private fun gainFor(velocity: Int, gainDb: Float): Float {
        val velocityGain = (velocity.toFloat() / SampleZone.MAX_VELOCITY).pow(VELOCITY_CURVE)
        return velocityGain * TEN.pow(gainDb / DB_DIVISOR)
    }

    private fun releaseRateFor(seconds: Float): Float =
        if (seconds <= 0f) 1f else 1f / (seconds * sampleRate)

    /**
     * A soft limiter.
     *
     * Hard clipping a summed chord produces harmonic distortion that sounds like a fault, and
     * 09 §5 warns against hiding poor audio under effects — so this is the smallest honest
     * thing: linear until it approaches full scale, then compressed into the last of the range.
     */
    private fun limit(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= LIMIT_KNEE) return value
        val excess = magnitude - LIMIT_KNEE
        val compressed = LIMIT_KNEE + excess / (1f + excess / (1f - LIMIT_KNEE))
        return if (value < 0) -compressed else compressed
    }

    private fun toPcm(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()

    public companion object {
        public const val DEFAULT_SAMPLE_RATE: Int = 44_100

        /** About 12 ms at 44.1 kHz: short enough to feel immediate, long enough not to underrun. */
        public const val DEFAULT_BUFFER_FRAMES: Int = 512

        private const val SEMITONE_RATIO = 1.0594630943592953
        private const val ATTACK_RATE = 0.01f
        private const val VELOCITY_CURVE = 1.6f
        private const val LIMIT_KNEE = 0.7f
        private const val TEN = 10f
        private const val DB_DIVISOR = 20f
    }
}
