# Implementation Status

Handoff record required by 16_AGENT_EXECUTION_PROTOCOL.md §9. Updated at the end of every
phase so a new session can pick up without re-deriving context.

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

**Interface handoff (added mid-phase at the user's request)**

- `ArtworkSpec` / `HitRegion` / `NormalizedRect` / `ArtworkGeometry` / `ArtworkScreen` in
  `core:designsystem`
- `HomeAction` binding all twenty `HIT / ...` regions from `interface/README.md` to app
  destinations, with `HomeDestination` recording which phase each opens in
- `HomeScreen` with an artwork presentation and a plain fallback
- See `docs/INTERFACE_INTEGRATION.md`

### Tests

123 passing, 0 failing.

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
| `HomeActionTest` | One-to-one mapping between actions and the twenty approved region names |
| `HarmonyLabAnalyzerTest` | Harness state mapping, unwritable-chord handling |

### Manual verification

- `./gradlew verifyHarmony` — passes
- `./gradlew assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk`
- **Not done:** installation on the target Samsung tablet, and any MIDI keyboard testing. No
  device is attached to this environment. 22_MANUAL_DEVICE_TEST_PLAN.md is still entirely
  outstanding and remains a release blocker.

### Known limitations

1. **`Cb` diminished sevenths are unwritable.** A `Cbdim7` needs a B triple-flat. The engine
   reports this rather than respelling; content must use `Bdim7`. The Phase 6 content
   validator should reject such roots. Pinned by
   `TranspositionTest.a chord that standard notation cannot write is reported rather than respelled`.
2. **Approved home artwork is absent.** `interface/` contains only `README.md`; the Figma file
   is not reachable without an access token. The seam is built and tested; the artwork and its
   region coordinates are outstanding. See `docs/INTERFACE_INTEGRATION.md`.
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
