package com.harmonygates.core.audio

/** A named instrument bank. */
@JvmInline
public value class InstrumentId(public val value: String) {
    init {
        require(value.isNotBlank()) { "An instrument id must not be blank" }
    }

    override fun toString(): String = value
}

/** A sample file inside a bank. */
@JvmInline
public value class AssetId(public val value: String) {
    override fun toString(): String = value
}

/** Where a sample loops, when it does. */
public data class LoopSpec(val startFrame: Int, val endFrame: Int) {
    init {
        require(startFrame >= 0) { "A loop cannot start before the sample does" }
        require(endFrame > startFrame) { "A loop must have positive length" }
    }
}

/**
 * Decoded audio, ready to play.
 *
 * Mono `ShortArray` rather than a file handle, because 09_AUDIO_SAMPLER_ENGINE.md §7 requires
 * that "ear-training playback must not stall on file decode". If a zone is in a bank, it is
 * already PCM.
 */
public class PcmSample(
    public val frames: ShortArray,
    public val sampleRate: Int,
    /** The pitch this recording actually sounds. Resampling is measured from here. */
    public val rootMidi: Int,
    public val loop: LoopSpec? = null,
) {
    init {
        require(frames.isNotEmpty()) { "A sample with no audio in it is not a sample" }
        require(sampleRate > 0) { "Sample rate must be positive: $sampleRate" }
        require(rootMidi in 0..MAX_MIDI) { "Root pitch out of MIDI range: $rootMidi" }
        loop?.let { require(it.endFrame <= frames.size) { "Loop runs past the end of the sample" } }
    }

    public val frameCount: Int get() = frames.size

    private companion object {
        const val MAX_MIDI = 127
    }
}

/**
 * One recorded pitch, and the range of notes and velocities it covers.
 *
 * 09 §3's shape. A zone that covers a wide range is resampled further from its root and sounds
 * progressively less like the instrument, which is why §5 puts "multiple sampled pitches or
 * intelligently bounded pitch shifting" first on the realism list — the bound belongs here, in
 * how wide a zone is allowed to be.
 */
public data class SampleZone(
    val sampleAsset: AssetId,
    val rootMidi: Int,
    val minMidi: Int,
    val maxMidi: Int,
    val velocityMin: Int = 1,
    val velocityMax: Int = MAX_VELOCITY,
    val loop: LoopSpec? = null,
) {
    init {
        require(minMidi <= rootMidi && rootMidi <= maxMidi) {
            "A zone's root ($rootMidi) must lie inside its range ($minMidi..$maxMidi)"
        }
        require(velocityMin in 1..MAX_VELOCITY && velocityMax in velocityMin..MAX_VELOCITY) {
            "Velocity layer must be a range within 1..$MAX_VELOCITY: $velocityMin..$velocityMax"
        }
    }

    public fun covers(note: Int, velocity: Int): Boolean =
        note in minMidi..maxMidi && velocity in velocityMin..velocityMax

    /** Semitones this note must be shifted from the recorded pitch. */
    public fun shiftFor(note: Int): Int = note - rootMidi

    public companion object {
        public const val MAX_VELOCITY: Int = 127
    }
}

/** What the sustain pedal does to this instrument. */
public enum class PedalBehavior {
    /** Held notes ring until the pedal lifts. A piano. */
    SUSTAIN,

    /** The pedal does nothing. An organ. */
    IGNORE,
}

/**
 * An instrument, as authored.
 *
 * 09 §4 asks for acoustic piano, Rhodes and Wurlitzer in v1 and for the architecture to admit
 * more later, which is why nothing here names a specific instrument: a preset is data, and a new
 * one is a new file rather than a new class.
 */
public data class InstrumentPreset(
    val id: InstrumentId,
    val displayName: String,
    val zones: List<SampleZone>,
    val gainDb: Float = 0f,
    val maxVoices: Int = DEFAULT_MAX_VOICES,
    val pedalBehavior: PedalBehavior = PedalBehavior.SUSTAIN,
    val releaseSeconds: Float = DEFAULT_RELEASE_SECONDS,
    /**
     * Licence text for the samples.
     *
     * 09 §4: "Use legally distributable samples. Store license metadata with each bank." A bank
     * whose licence nobody wrote down is a bank nobody can ship.
     */
    val licence: String = "",
) {
    init {
        require(zones.isNotEmpty()) { "An instrument with no zones makes no sound" }
        require(maxVoices > 0) { "An instrument needs at least one voice" }
        require(releaseSeconds >= 0f) { "A negative release is not a release" }
    }

    /**
     * The zone to play for a note.
     *
     * The nearest root among the zones that cover the note, so a bank with several sampled
     * pitches always resamples by the smallest interval it can. Ties break towards the lower
     * root, which keeps the choice deterministic — and a stimulus that picked a different zone
     * on replay would break 07_EAR_TRAINING_ENGINE.md §3's reproducibility.
     */
    public fun zoneFor(note: Int, velocity: Int): SampleZone? =
        zones.filter { it.covers(note, velocity) }
            .minWithOrNull(compareBy({ kotlin.math.abs(it.shiftFor(note)) }, { it.rootMidi }))

    public val range: IntRange
        get() = zones.minOf { it.minMidi }..zones.maxOf { it.maxMidi }

    public companion object {
        public const val DEFAULT_MAX_VOICES: Int = 32
        public const val DEFAULT_RELEASE_SECONDS: Float = 0.35f
    }
}

/** A preset plus the decoded audio for it. */
public class SampleBank(
    public val preset: InstrumentPreset,
    private val samples: Map<AssetId, PcmSample>,
) {
    init {
        val missing = preset.zones.map { it.sampleAsset }.filterNot { it in samples }
        require(missing.isEmpty()) { "Zones reference samples that were never decoded: $missing" }
    }

    public fun sampleFor(zone: SampleZone): PcmSample = samples.getValue(zone.sampleAsset)

    public fun sampleFor(note: Int, velocity: Int): Pair<SampleZone, PcmSample>? {
        val zone = preset.zoneFor(note, velocity) ?: return null
        return zone to samples.getValue(zone.sampleAsset)
    }

    /** Total decoded audio held, in frames. Feeds the LRU policy of §7. */
    public val frameCount: Int get() = samples.values.sumOf { it.frameCount }
}
