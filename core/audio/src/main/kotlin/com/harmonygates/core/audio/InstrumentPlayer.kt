package com.harmonygates.core.audio

import com.harmonygates.core.music.pitch.MidiNote
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Something that can make a note sound.
 *
 * 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §6's interface. Deliberately the same vocabulary as the
 * MIDI input side — note on, note off, sustain, all notes off — because a demonstration is the
 * app playing the thing it would otherwise be listening for.
 */
public interface InstrumentPlayer {
    public suspend fun load(preset: InstrumentId)

    public fun noteOn(note: MidiNote, velocity: Int)

    public fun noteOff(note: MidiNote)

    public fun sustain(down: Boolean)

    public fun allNotesOff()
}

/**
 * Builds playable banks without a licensed sample library.
 *
 * 09_AUDIO_SAMPLER_ENGINE.md §4 requires "legally distributable samples", and none have been
 * supplied. Rather than block ear training on a licensing decision, this synthesises a decaying
 * harmonic tone per zone — a recognisable pitch with a plausible envelope, and honestly not a
 * piano.
 *
 * It is deliberately built through the same `SampleBank` the real thing will use, so swapping in
 * a licensed bank is a change of source and nothing else. §5 also warns against hiding poor
 * samples under effects; the answer here is to be plain about what this is rather than to dress
 * it up.
 */
public object SynthesisedBanks {

    /**
     * A struck tone: a few harmonics, each decaying at its own rate.
     *
     * Higher partials fade faster, which is what makes a struck string sound struck rather than
     * like an organ. Deterministic to the sample, so a stimulus rendered twice is identical —
     * 07_EAR_TRAINING_ENGINE.md §3 requires reported errors to be reproducible.
     */
    public fun tone(
        rootMidi: Int,
        sampleRate: Int = Mixer.DEFAULT_SAMPLE_RATE,
        seconds: Float = DEFAULT_SECONDS,
        harmonics: List<Float> = PIANO_HARMONICS,
    ): PcmSample {
        val frames = (sampleRate * seconds).toInt()
        val frequency = frequencyOf(rootMidi)
        val data = ShortArray(frames)

        for (index in 0 until frames) {
            val time = index.toDouble() / sampleRate
            var value = 0.0
            harmonics.forEachIndexed { harmonic, amplitude ->
                val partial = harmonic + 1
                val decay = exp(-time * BASE_DECAY * partial)
                value += amplitude * decay * sin(TWO_PI * frequency * partial * time)
            }
            // A short fade in, so the very first frame is not a step from silence — a click at
            // the start of every stimulus would be the most audible thing in the exercise.
            val attack = (index / (sampleRate * ATTACK_SECONDS)).coerceAtMost(1.0)
            data[index] = (value * attack * Short.MAX_VALUE * HEADROOM).toInt().toShort()
        }
        return PcmSample(frames = data, sampleRate = sampleRate, rootMidi = rootMidi)
    }

    /**
     * A bank covering [range], sampled every [zoneSpacing] semitones.
     *
     * Spacing is what bounds the pitch shifting §5 asks to be bounded: a zone three semitones
     * wide is resampled by at most a minor third, which is well inside the range where linear
     * interpolation still sounds like the note it is meant to be.
     */
    public fun bank(
        id: InstrumentId,
        displayName: String,
        range: IntRange = DEFAULT_RANGE,
        zoneSpacing: Int = DEFAULT_ZONE_SPACING,
        sampleRate: Int = Mixer.DEFAULT_SAMPLE_RATE,
    ): SampleBank {
        require(zoneSpacing > 0) { "Zones must be spaced by at least a semitone" }

        val roots = range.step(zoneSpacing).toList()
        val zones = roots.map { root ->
            val half = zoneSpacing / 2
            SampleZone(
                sampleAsset = AssetId("$id.$root"),
                rootMidi = root,
                minMidi = maxOf(range.first, root - half),
                maxMidi = minOf(range.last, root + zoneSpacing - half - 1),
            )
        }
        // The last zone stretches to the top of the range, so no note falls between zones.
        val covered = zones.dropLast(1) + zones.last().copy(maxMidi = range.last)

        return SampleBank(
            preset = InstrumentPreset(
                id = id,
                displayName = displayName,
                zones = covered,
                licence = "Synthesised at runtime. No third-party samples, nothing to licence.",
            ),
            samples = roots.associate { root ->
                AssetId("$id.$root") to tone(root, sampleRate)
            },
        )
    }

    /** The practice tone used until a licensed bank is chosen. */
    public fun practiceTone(sampleRate: Int = Mixer.DEFAULT_SAMPLE_RATE): SampleBank = bank(
        id = InstrumentId("instrument.practice_tone"),
        displayName = "Practice tone",
        sampleRate = sampleRate,
    )

    private fun frequencyOf(midi: Int): Double =
        A440 * Math.pow(2.0, (midi - A440_MIDI) / SEMITONES_PER_OCTAVE)

    /** Fundamental strongest, partials falling away. */
    private val PIANO_HARMONICS = listOf(1.0f, 0.35f, 0.18f, 0.09f, 0.05f)

    /** Two octaves either side of middle C, the practical keyboard span. */
    private val DEFAULT_RANGE = 36..96

    private const val DEFAULT_ZONE_SPACING = 3
    private const val DEFAULT_SECONDS = 2.5f
    private const val BASE_DECAY = 1.6
    private const val ATTACK_SECONDS = 0.004
    private const val HEADROOM = 0.55
    private const val TWO_PI = 2 * PI
    private const val A440 = 440.0
    private const val A440_MIDI = 69.0
    private const val SEMITONES_PER_OCTAVE = 12.0
}
