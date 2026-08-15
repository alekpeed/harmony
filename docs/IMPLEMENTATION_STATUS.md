# Implementation Status

Handoff record required by 16_AGENT_EXECUTION_PROTOCOL.md §9. Updated at the end of every
phase so a new session can pick up without re-deriving context.

---

## Phase: 4 — Minimal playable vertical slice

**Commit:** see `git log` for `phase/4-vertical-slice`

### Implemented

- `ExercisePolicy` — root pool, formula pool, inversion pool, answer mode, presentation
  channels, register, onset policy, session length. The authoring checklist from
  21_CONTENT_AUTHORING_GUIDE.md §4 as data.
- `DefaultExerciseGenerator` — deterministic from a seed. Prefers untested roots (the coverage
  term from 02 §9), avoids an immediate repeat, and never emits a chord standard notation
  cannot write.
- `ExerciseSessionEngine` and `DefaultExerciseSessionEngine` — the loop from 01 §2, once:
  present → arm → capture → evaluate → feedback → next, with pause, resume, skip and hints.
- `ExercisePresentationModel` — the mapper from 10 §5. A channel the exercise did not enable is
  absent from the model, not merely hidden, so a screen cannot leak the answer.
- Chord gate screen: chord symbol, live keyboard, verdict with a worded musical diagnosis,
  session summary. Reached from the home screen's Chord Gates and Quick Practice controls.
- `CapturePolicy`, `CaptureState` and the `PerformanceCapture` contract moved from `core:midi`
  into `core:music`. They are domain concepts, not transport ones, and the move lets the session
  engine live in pure Kotlin with no Android dependency.

### Tests

268 passing, 0 failing. 25 of them are new.

| Suite | Covers |
| --- | --- |
| `ExerciseSessionEngineTest` | The loop end to end, seed reproducibility, pause/resume re-arm, device loss, hint recording |
| `ExerciseGeneratorTest` | Determinism, root coverage across a session, inversion requirements, writability over 200 seeds |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done, and this is the phase's whole acceptance criterion.** 15_IMPLEMENTATION_PHASES.md
  requires "20-exercise session works on tablet with real keyboard" and "no false submissions
  from ordinary chord spread". Neither can be demonstrated here: no tablet and no MIDI keyboard
  are attached to this environment. The loop is exercised end to end against a fake capture, and
  the capture itself against scripted MIDI, but a person has not played it.

### Known limitations

1. **Phase 4's acceptance is unmet.** See above. Everything up to it has been verified; this one
   genuinely cannot be, from a build server.
2. **The capture thresholds are still unmeasured.** The "no false submissions" criterion is
   precisely a test of those numbers, so the two open items are the same item.
3. **Arming is automatic.** Once a target is on screen and a keyboard is connected, capture arms
   without a tap. That is right for a practice loop and may be wrong for a timed gate; Phase 6's
   gate rules will decide per exercise rather than globally.
4. **The exercise policy lives in the view model.** It is data, but it is data in Kotlin. Phase 6
   moves it into `content/` where a curriculum author owns it.
5. **No mastery, no progress, no persistence.** A session summary is shown and then discarded.
   Phase 5 stores it.
6. **Only one answer mode is used in the app.** The generator and evaluator support exact
   voicings and degree-aware policy matching; the Phase 4 policy asks for pitch classes, which
   is what a first chord gate should ask for.

### Next phase prerequisites

Phase 5 — persistence and mastery. Needs:

- `core:data` as an Android library: Room schema, attempt storage, migrations
- `ProgressRepository` and `ContentRepository` per 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §5
- The `SkillMastery` evidence model from 06 §9, with its weighting: independent correct 1.0,
  correct with reduced hint 0.8, correct after hint 0.5, incorrect 0.0
- DataStore preferences

`AttemptRecord` already carries the instance, the attempt, the result and the hint count, which
is everything the mastery update needs.

---

## Phase: 3 — Performance capture and evaluator

**Commit:** see `git log` for `phase/3-capture-and-evaluation`

### Implemented

**Capture (`core:midi`)**

- `OnsetAggregator` — the state machine from 05_MIDI_INPUT_ENGINE.md §6. Never submits on the
  first note; completes on a quiet window, on every key being lifted, on the roll window running
  out, or on an explicit submit.
- `CapturePolicy` — quiet window, roll window, stabilisation, accidental-note grace, sustain
  policy, accepted range. The spec's values are defaults, not constants, because §6 says so.
- `MidiPerformanceCapture` — the `PerformanceCapture` contract over a `MidiInputSource`. The
  heartbeat only runs while a capture is armed.

**Evaluation (`core:music`)**

- `PerformanceAttempt` and `NormalizedNoteEvent` — the reduction *and* the full history, because
  accidental-note diagnosis needs the note that was dropped
- `OnsetPolicy` — simultaneous, rolled-allowed, unrestricted
- `DefaultPerformanceEvaluator` — three matching modes: pitch-class, exact voicing (multiset,
  so a doubled voice counts), and degree-aware policy matching
- `PerformanceError` with an educational ranking, so a player reads the useful thing first
- `Verdict`, `TimingEvaluation`, `PerformanceMetrics`, `FeedbackModel`
- `ChordRealizer` gained `analyze`, `spelledDegrees` and `degreeOf` — the evaluator needs them
  and they belong on the interface rather than only on the implementation

### Tests

243 passing, 0 failing. 49 of them are new.

| Suite | Covers |
| --- | --- |
| `OnsetAggregatorTest` | All nine scripted scenarios from 14_TESTING_AND_QUALITY.md §5, plus the roll threshold at its edges |
| `PerformanceEvaluatorTest` | The three matching modes, error ordering, device loss, register and bass constraints |
| `MidiPerformanceCaptureTest` | Source-to-aggregator wiring on virtual time |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done:** anything with a real keyboard. The capture thresholds in `CapturePolicy` are the
  specification's starting values and 05_MIDI_INPUT_ENGINE.md §6 explicitly calls them
  "starting default values for user testing, not permanent constants". They have never been
  played on. Instrument-testing them is a hardware task.

### Known limitations

1. **The capture thresholds are unvalidated.** 80 ms quiet window, 300 ms roll, 40 ms
   stabilisation, 70 ms accidental grace. All plausible, none measured against fingers.
2. **No exercise UI yet.** Capture and evaluation exist and are tested, but nothing on screen
   drives them — that is Phase 4's vertical slice.
3. **Timed sequences and sight-reading phrases return `NO_ATTEMPT`.** Those requirement types
   are graded step by step by a session engine, which arrives with Phases 9 and 10.
4. **Voice-leading targets are evaluated only for harmonic validity.** The movement scoring in
   06_PERFORMANCE_EVALUATION_AND_SCORING.md §11 belongs to Phase 10; today a voice-leading
   requirement is judged as a chord-policy match against its destination.
5. **No mastery weighting.** §9's evidence model is Phase 5 and reads these results rather than
   replacing them.

### Design notes worth carrying forward

Two bugs found by the scripted scenarios, both of which would have marked correct answers wrong:

- The accidental-note grace period initially discarded *every* note of a short chord, because
  letting go is how a player finishes. The filter now asks whether the key was released while
  the chord was still arriving, not merely whether it was released quickly.
- Lifting every key completed the attempt even with the pedal down, which ended capture on a
  note that was only a sustained remnant. Capture now waits for the quiet window when the pedal
  is held.

### Next phase prerequisites

Phase 4 — the minimal playable vertical slice. Needs:

- An exercise session engine per 10_ANDROID_ARCHITECTURE.md §4, wrapping arm → capture →
  evaluate → feedback → next
- A deliberately plain exercise screen: show `Cmaj7`, play it, see the verdict
- Exercise generation from a seed, using the `SeededRandomFactory` already in `core:music`
- Several qualities, roots and inversions, per the phase's acceptance criteria

Everything Phase 4 needs from capture and evaluation now exists.

---

## Phase: 2 — MIDI foundation

**Commit:** see `git log` for `phase/2-midi-foundation`

### Implemented

- `core:midi`, an Android library. Everything that decides meaning is plain Kotlin inside it;
  only discovery and transport touch the platform.
- `MidiInputSource` — the single seam the rest of the app sees, per AGENTS.md
- `AndroidMidiInputSource` — `android.media.midi`, transport-specific enumeration on API 33+
  with the older path below it, a dedicated callback thread, hotplug via `DeviceCallback`
- `FakeMidiInputSource` — the simulator. Same interface, injected clock, scriptable notes,
  pedal, raw bytes, disconnect and reconnect. CI never needs hardware.
- `MidiMessageParser` — running status, real-time bytes interleaved mid-message, messages split
  across reads, SysEx skipping, note-on-at-velocity-zero, pitch bend centred at zero
- `ActiveNoteTracker` — the three sets 05_MIDI_INPUT_ENGINE.md §5 requires kept apart:
  physically held, pedal-sustained, and what a listener actually hears
- `MidiConnectionState` and typed `MidiError`, exposed as `StateFlow`
- `PianoKeyboard` in `core:designsystem` — Compose Canvas, held and sustained drawn differently
- MIDI diagnostics screen: connection guidance, device picker, live keyboard, observed range,
  capped event log, and an on-device simulator including a "pull the cable" control
- Reached from the home screen's Settings control; `HomeDestination.Settings` is now live

### Tests

194 passing, 0 failing. 65 of them are new.

| Suite | Covers |
| --- | --- |
| `MidiMessageParserTest` | 24 cases: running status, interleaved clock bytes, split reads, SysEx, orphan bytes |
| `ActiveNoteTrackerTest` | 18 cases: sustain across a chord change, duplicate note-on, remnant clearing |
| `FakeMidiInputSourceTest` | 15 cases: hotplug, multi-device selection, timestamps from an injected clock |
| `KeyboardLayoutTest` | 8 cases: black-key placement, octave repetition, out-of-range anchors |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done:** anything involving a real keyboard. No MIDI device is attached to this build
  environment, so the Phase 2 acceptance criteria "real keyboard note-on/off appears correctly"
  and "hotplug works" are **unverified**. The simulator exercises the same code paths, which is
  not the same thing as a physical test. 22_MANUAL_DEVICE_TEST_PLAN.md remains outstanding.

### Known limitations

1. **Phase 2 acceptance is only partly demonstrable here.** "CI uses fake source" is met and
   tested. The two hardware criteria need a tablet and a keyboard.
2. **No onset aggregation yet.** 05_MIDI_INPUT_ENGINE.md §6 and §7 — the chord capture window,
   the roll tolerance, `OnsetPolicy` — belong to Phase 3 with the evaluator, and are not built.
   Today the tracker reports what is sounding; nothing decides when a chord is "submitted".
3. **Keyboard range discovery is partial.** The diagnostics screen reports the observed low and
   high notes (§10's raw material), but the calibration flow and the 25/49/61/76/88 presets are
   not built. Nothing depends on them yet.
4. **Latency telemetry is not instrumented.** §11 asks for four timestamps through the pipeline;
   only the arrival timestamp exists. The others need the evaluator and the UI hop, so this
   lands with Phase 3 and Phase 4.
5. **The event log is capped at 60 lines.** A diagnostics screen left open for a practice
   session would otherwise grow without bound.
6. **Under extreme backpressure an event may be dropped** from the `events` flow — `tryEmit`
   rather than a suspending emit, because the MIDI callback must never block. Active-note state
   is updated regardless, so what is sounding stays correct even if a log line is missed.

### Next phase prerequisites

Phase 3 — performance capture and evaluator. Needs:

- The onset aggregation state machine from §6, driven by `TestMonotonicClock`
- `OnsetPolicy` — simultaneous, rolled-allowed, unrestricted
- `PerformanceAttempt` per 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §2, keeping the full event
  history and not only the reduced note set
- `PerformanceEvaluator` over the `ExerciseRequirement` types already defined in `core:music`
- The scripted MIDI scenarios in 14_TESTING_AND_QUALITY.md §5, each with an expected attempt

Everything Phase 3 needs from MIDI now exists.

---

## Phase: 0 — Repository and toolchain, and 1 — Pure music domain

**Commit:** see `git log` for `phase/0-1-toolchain-and-music-domain`

### Implemented

**Phase 0**

- Gradle 9.7.0 wrapper, Kotlin DSL throughout, version catalog at `gradle/libs.versions.toml`
- Modules: `app`, `core:music`, `core:designsystem`, `core:testing`
- Android baseline: compile SDK 37, target SDK 36, min SDK 26, AGP 9.3.1
- GitHub Actions CI: domain tests, unit tests, lint, debug APK, artifact upload
- Android Lint with `warningsAsErrors` in both Android modules, shared config at
  `config/lint/lint.xml`
- Placeholder design system: colour/spacing/typography tokens, theme, four components
- Adaptive launcher icon (vector, placeholder)

**Phase 1**

- Pitch primitives: `MidiNote`, `PitchClass`, `ScaleDegree`, `Semitones`, `VoiceIndex`,
  `LetterName`, `Accidental`, `SpelledPitchClass`, `SpelledPitch`, `SpelledInterval`
- `ChordDegree` as (diatonic number, chromatic alteration), so `#9` and `b3` are distinct values
- 30 chord formulas with required/optional/forbidden/written-stack sets
- `JazzChordParser` covering every symbol in 04_HARMONY_DOMAIN_ENGINE.md §5 plus typed variants
- `DegreeAwarePitchSpeller`: letter from the degree number, accidental from the arithmetic
- `DefaultChordRealizer`: chord tones, deterministic voicing generation against a policy,
  performance analysis
- `VoicingPolicy` with bass/top/register/doubling/rootless constraints
- `VoicingTransforms`: invert, octave-displace, drop-2, drop-3, drop-2&4, spread, close,
  constrain-to-range, bass/top anchoring
- `VoiceLeadingAnalyzer` with optimal voice assignment (Hungarian algorithm) and explicit
  add/drop penalties
- `KeyContext`, `Mode`, `RomanNumeral`, `FunctionalChord`, `Functions` progression vocabulary
- `Transposition`: chromatic (spelled), diatonic, voicing-level
- `ExerciseRequirement` sealed hierarchy
- Injected `MonotonicClock`, `WallClock`, `RandomSource`, `SeededRandomFactory`
- `core:testing`: advanceable clocks, scripted random source, readable music assertions
- Phase 1 harness screen in `app`: type a chord symbol, see degrees, spelling and a voicing

**Approved home screen (added mid-phase at the user's request)**

- `ArtworkSpec` / `HitRegion` / `NormalizedRect` / `ArtworkGeometry` / `ArtworkScreen` in
  `core:designsystem`; regions stored as fractions of the artwork, artwork fitted not cropped
- `HomeAction` binding all twenty regions to app destinations, keyed on both the Figma layer
  name and the map's semantic action id, with `HomeDestination` recording the phase each opens in
- `interface/harmony_home_approved.jpg` (1536 × 1024) is the home screen; all twenty controls
  are live, and Theory Lab reaches the Phase 1 harness
- `interface/maps/home.json` parsed at runtime, so a re-export updates the app by replacing one file
- `syncInterfaceArtwork` (buildSrc, per variant) copies artwork and map into generated resources
- `checkInterfaceAssets` validates handed-over assets at build time — added after the first
  upload of the artwork arrived as 14,997 bytes carrying no JPEG markers
- System back returns from the harness to home
- See `docs/INTERFACE_INTEGRATION.md`

### Tests

129 passing, 0 failing.

| Suite | Covers |
| --- | --- |
| `GoldenChordFixtureTest` | 74 hand-derived chord fixtures incl. Bbb, Dbb, E#, B#, Gx |
| `ChordCoverageTest` | 30 formulas × 12 roots: writability, letters, sounding distances, symbol round trips |
| `ChordParserTest` | Every specified symbol, alias equivalence, failure positions, `M7` vs `m7` |
| `VoicingTest` | Inversion by bass, rootless policies, omissions, slash bass, register, determinism |
| `VoicingTransformsTest` | Drop/spread/close/invert, pitch-content preservation |
| `TranspositionTest` | Round trips over 360 chords × 12 intervals, 1000 seeded property cases |
| `VoiceLeadingTest` | Motion metrics, optimal pairing, add/drop, motion-type classification |
| `FunctionalHarmonyTest` | The four specified roman-numeral cases, twelve keys, key signatures |
| `PitchAndDeterminismTest` | MIDI bounds, enharmonic octaves, seeded reproducibility |
| `ArtworkGeometryTest` | Region placement across five container sizes and both letterbox axes |
| `HomeActionTest` | Two-way mapping against the supplied map, nested-region hit order, edge clamping |
| `HarmonyLabAnalyzerTest` | Harness state mapping, unwritable-chord handling |

### Manual verification

- `./gradlew verifyHarmony` — passes
- `./gradlew assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk`
- Home-screen hit regions verified by rendering the artwork with every mapped region drawn over
  it; all twenty sit on their intended control
- **Not done:** installation on the target Samsung tablet, and any MIDI keyboard testing. No
  device is attached to this environment. 22_MANUAL_DEVICE_TEST_PLAN.md is still entirely
  outstanding and remains a release blocker.

### Known limitations

1. **`Cb` diminished sevenths are unwritable.** A `Cbdim7` needs a B triple-flat. The engine
   reports this rather than respelling; content must use `Bdim7`. The Phase 6 content
   validator should reject such roots. Pinned by
   `TranspositionTest.a chord that standard notation cannot write is reported rather than respelled`.
2. **Only the home screen has approved artwork.** Every other destination reports the phase it
   arrives in. `interface/maps/home.json` also points its `visualAsset` field at the malformed
   hyphenated file that has since been removed; nothing reads that field, but it should be
   corrected in the next export.
3. **Quartal and So What voicings are not in the formula registry.** Region 9 material arrives
   with Phase 11, where the chord label is deliberately allowed to be ambiguous.
4. **`targetSdk` is 36 against `compileSdk` 37**, per the planning baseline. The API 37
   validation and upgrade is a Phase 15 release task; `OldTargetApi` is set to informational in
   `config/lint/lint.xml` and must be re-enabled when that task is done.
5. **Kotlin is pinned to 2.2.10**, the version AGP 9.3.1 supplies through built-in Kotlin
   support. Raising it independently would produce `core:music` metadata the Android modules
   cannot read. Raise it with AGP.
6. **`NewerVersionAvailable` and `GradleDependency` are informational.** They compare against
   what was published today and would fail CI on a day nobody touched the repository.
   Dependency bumps are deliberate acts recorded in the version catalog.
7. **No navigation library yet.** Two destinations are switched by saved state in `AppRoot`.
   Navigation 3 and typed routes arrive in Phase 6 with the campaign back stack.

### Next phase prerequisites

Phase 2 — MIDI foundation. Needs:

- `core:midi` as an Android library module (production source uses `android.media.midi`)
- `MidiInputSource` and `PerformanceCapture` per 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §4
- A fake source so CI never needs hardware — 18_ACCEPTANCE_CRITERIA.md requires this
- The scripted MIDI test scenarios in 14_TESTING_AND_QUALITY.md §5, driven by
  `TestMonotonicClock` (already available in `core:testing`)
- A diagnostic screen; the `HomeDestination` table already reserves its place

Nothing in Phase 2 requires the interface artwork.
