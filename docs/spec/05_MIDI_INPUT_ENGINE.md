# MIDI Input Engine

## 1. Goal

Turn raw Android MIDI messages into stable, timestamped musical performance state with minimal latency and no false chord submissions caused by normal human finger spread.

## 2. Android API

Use `android.media.midi.MidiManager` as the normal transport abstraction.

Check `PackageManager.FEATURE_MIDI` at startup. On modern Android, enumerate MIDI 1.0 byte-stream devices using the transport-specific API where available; retain an API-compatible path for older supported versions.

USB class-compliant keyboards normally appear through Android's MIDI service. Do not manually implement the USB-MIDI class protocol unless a specific target keyboard proves incompatible.

## 3. MIDI connection states

```kotlin
sealed interface MidiConnectionState {
    data object Unsupported : MidiConnectionState
    data object NoDevice : MidiConnectionState
    data class DevicesAvailable(val devices: List<MidiEndpoint>) : MidiConnectionState
    data class Connecting(val endpoint: MidiEndpoint) : MidiConnectionState
    data class Connected(val endpoint: MidiEndpoint) : MidiConnectionState
    data class Error(val reason: MidiError) : MidiConnectionState
}
```

Expose this as `StateFlow`.

## 4. Message normalization

Normalize input into:

```kotlin
sealed interface MidiEvent {
    val timestampNanos: Long

    data class NoteOn(
        val note: MidiNote,
        val velocity: Int,
        val channel: Int,
        override val timestampNanos: Long
    ) : MidiEvent

    data class NoteOff(...) : MidiEvent
    data class ControlChange(...) : MidiEvent
    data class PitchBend(...) : MidiEvent
}
```

Treat Note On with velocity 0 as Note Off.

## 5. Sustain pedal

CC64 changes semantic held state.

Maintain separately:

- physically depressed notes
- pedal-sustained notes
- effective sounding notes

For chord-answer capture, configurable policies:

- ignore sustained remnants from the previous attempt
- include pedal-held notes
- require pedal release between trials

Default guided chord mode should avoid marking a correct new chord wrong because of stale sustain. Sequence/sight-reading modes may require literal sustain behavior.

## 6. Chord capture window

Humans do not depress simultaneous chord notes on the exact same millisecond.

Use an onset aggregation state machine:

```text
IDLE
  first qualifying NoteOn -> COLLECTING
COLLECTING
  accumulate NoteOns
  reset short quiet timer on each new onset
  when quiet interval elapsed OR max collection duration reached -> CANDIDATE
CANDIDATE
  wait optional stabilization period
  evaluate
```

Starting default values for user testing, not permanent constants:

- inter-note quiet window: ~60–100 ms
- maximum chord roll window: ~250–350 ms
- stabilization: ~30–60 ms

Expose them as internal tuning parameters and instrument-test them.

## 7. Rolled-chord tolerance

Some exercises intentionally require simultaneity; others should accept a slightly rolled jazz voicing.

`OnsetPolicy`:

```kotlin
sealed interface OnsetPolicy {
    data class Simultaneous(val maxSpreadMs: Int): OnsetPolicy
    data class RolledAllowed(val maxSpreadMs: Int): OnsetPolicy
    data object Unrestricted: OnsetPolicy
}
```

## 8. Debounce and noise

Ignore/handle:

- duplicate NoteOn caused by device quirks
- repeated NoteOff
- active sensing
- unsupported system messages
- channel messages not needed by the current exercise

Never solve MIDI noise by adding arbitrary multi-hundred-millisecond delays to the UI.

## 9. Device hotplug

The app must survive:

- keyboard disconnected while playing
- keyboard reconnected
- switching keyboards
- activity background/foreground
- USB hub disconnect

On disconnection:

1. stop accepting scoreable input
2. clear active-note state
3. preserve the current exercise
4. show non-destructive reconnect UI
5. resume after reconnection

## 10. Keyboard range discovery

MIDI device metadata usually does not guarantee physical key count/range. During onboarding allow:

- auto-detect from observed minimum/maximum over calibration
- manual choices: 25/49/61/73/76/88 key common ranges
- custom low/high MIDI note

Exercise generation must respect the configured playable range.

## 11. Latency telemetry

In debug builds log:

- raw MIDI timestamp
- normalization timestamp
- evaluator receipt timestamp
- UI feedback timestamp

This allows measurement of app processing latency separately from human response time.

## 12. MIDI simulator

Implement a fake `MidiInputSource` that can feed event scripts into the app and instrument tests. The entire app must be testable without a physical keyboard in CI.

```kotlin
interface MidiInputSource {
    val connectionState: StateFlow<MidiConnectionState>
    val events: Flow<MidiEvent>
}
```

Production and fake sources implement the same interface.
