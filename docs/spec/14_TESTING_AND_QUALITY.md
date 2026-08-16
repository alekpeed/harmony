# Testing and Quality

## 1. Testing priorities

The costliest failures are not crashes; they are musically incorrect judgments. Testing therefore prioritizes domain correctness and MIDI behavior.

## 2. Test pyramid

### Pure JVM unit tests

Target heavily:

- chord parser
- spelling
- chord formula generation
- inversion
- transposition
- voicing policies
- progression generation
- voice-leading metrics
- scoring
- mastery update
- deterministic random generation

### Android unit/instrumented tests

- Room migrations
- DataStore settings
- lifecycle state retention
- fake MIDI integration
- audio engine startup/stop

### Compose UI tests

- state-to-screen mapping
- MIDI disconnect banner
- exercise transitions
- accessibility labels
- gate state display

### Physical-device tests

Mandatory before release:

- actual target Samsung tablet
- at least one class-compliant USB MIDI keyboard
- sustain pedal
- USB unplug/replug
- screen resize/multi-window if supported
- Bluetooth/headphone audio optional path

## 3. Golden theory fixtures

Create a human-reviewed fixture set with hundreds of known cases.

Example:

```json
{
  "symbol": "Db7b9",
  "expectedDegrees": ["1","3","5","b7","b9"],
  "expectedSpellings": ["Db","F","Ab","Cb","Ebb"]
}
```

Fixtures should intentionally contain awkward spellings to catch pitch-class-only logic.

## 4. Property tests

Examples:

- transposing a pitch-class collection by 12 semitones preserves it
- transpose by `n`, then `-n`, returns equivalent structure
- inversion preserves chord pitch-class multiset
- generated voicing stays inside requested range
- required tones are never omitted by generator

## 5. MIDI scripted tests

Scripts should model:

- perfectly simultaneous chord
- 80 ms hand spread
- 250 ms rolled chord
- accidental neighbor note immediately released
- sustain leftovers
- duplicate note-on
- disconnect mid-chord
- reconnect before next trial
- fast repeated chord

Each script has an expected normalized `PerformanceAttempt`.

## 6. Timing tests

Never rely only on wall-clock sleeps in tests. Inject a monotonic clock/test scheduler into evaluators and session engines.

## 7. Content validation

Add a build-time validator that fails on:

- duplicate gate IDs
- unknown prerequisites
- campaign cycles
- unknown skill IDs
- invalid chord formula references
- impossible completion rules
- exercise policy with no generatable candidates

## 8. Performance budgets

Initial targets on the real tablet:

- MIDI event -> updated key visualization: perceptually immediate; target under ~1 display frame plus scheduling where possible
- candidate chord evaluation: <10 ms for normal chord tasks
- exercise generation: <50 ms typical
- no audio glitches during simple ear-training playback
- no dropped Compose frames during normal MIDI bursts

Measure rather than assume.

## 9. Definition of done for every feature

A feature is not done until:

- behavior implemented
- unit tests added
- fake MIDI path works if relevant
- process death/state restoration considered
- accessibility semantics added
- error state implemented
- no hard-coded user-facing theory strings where structured data should be used
- documentation updated
