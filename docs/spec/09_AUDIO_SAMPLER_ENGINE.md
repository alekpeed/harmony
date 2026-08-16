# Audio Sampler Engine

## 1. Requirements

The internal audio engine is needed for ear-training stimuli, demonstrations, metronome cues, and optional playback of target voicings.

It is not the source of truth for the player's answer; MIDI is.

## 2. Full-Kotlin constraint

V1 must not require C++/NDK. Implement the sampler around Android `AudioTrack` with a Kotlin mixer.

If a future measurement proves the Kotlin implementation cannot meet required latency/voice count on the target hardware, document the evidence before proposing a native engine.

## 3. Sample architecture

Instrument preset:

```kotlin
data class InstrumentPreset(
    val id: InstrumentId,
    val zones: List<SampleZone>,
    val gainDb: Float,
    val maxVoices: Int,
    val pedalBehavior: PedalBehavior
)

data class SampleZone(
    val rootMidi: Int,
    val minMidi: Int,
    val maxMidi: Int,
    val velocityMin: Int,
    val velocityMax: Int,
    val sampleAsset: AssetId,
    val loop: LoopSpec? = null
)
```

## 4. Quality tiers

V1 should support at least:

- acoustic piano
- Rhodes-style electric piano
- Wurlitzer-style electric piano

The software architecture must permit additional organs/synths later.

Use legally distributable samples. Store license metadata with each bank.

## 5. Realism features in priority order

1. multiple sampled pitches or intelligently bounded pitch shifting
2. velocity layers
3. natural release tails
4. sustain pedal handling
5. per-note gain normalization
6. round-robin variants where available
7. sympathetic/pedal resonance as a later enhancement
8. user EQ as later enhancement

Do not hide poor samples under heavy reverb.

## 6. Mixer

Mixer loop responsibilities:

- active voice list
- sample interpolation/resampling
- envelope
- per-voice gain
- stereo pan if needed
- summed float buffer
- limiter/clamp
- conversion to target PCM format
- write to `AudioTrack`

Avoid allocation inside the real-time render loop.

## 7. Preloading

Pre-decode frequently used sample zones to PCM before a timed exercise begins. Ear-training playback must not stall on file decode.

Use an LRU memory policy for large banks.

## 8. Humanization

For demonstration audio only, optional small deterministic velocity/onset variation may reduce machine-like playback. The seed must be stored with the exercise.

Never humanize timed reference clicks or anything used as a timing ground truth.

## 9. External keyboard audio

The player may hear their physical keyboard directly. The app should therefore allow:

- internal target audio on/off
- internal echo of incoming MIDI on/off
- metronome volume
- stimulus volume

Default incoming-MIDI echo should be off to avoid doubled notes when the physical keyboard has local sound enabled.
