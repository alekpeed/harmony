# Implementation Phases

Each phase must end with a compiling main branch, passing tests, and a commit/PR boundary. Do not build all screens first and attempt to connect MIDI/theory at the end.

## Phase 0 — Repository and toolchain

Deliver:

- Android project
- Kotlin DSL Gradle
- version catalog
- modules skeleton
- CI build/test workflow
- lint/static analysis
- base README/AGENTS

Acceptance:

- clean checkout builds
- unit test job passes
- debug APK can be produced

## Phase 1 — Pure music domain

Deliver:

- pitch/spelling primitives
- chord degrees
- formulas
- parser
- transposition
- inversion
- voicing policies
- tests/fixtures

Do not build campaign UI beyond a test harness.

Acceptance:

- golden fixtures pass
- common jazz chord symbols parse
- spelling tests pass

## Phase 2 — MIDI foundation

Deliver:

- `MidiInputSource`
- Android source
- fake source
- device discovery
- connection state
- event parser
- active-note tracker
- sustain handling
- diagnostic screen

Acceptance:

- real keyboard note-on/off appears correctly
- hotplug works
- CI uses fake source

## Phase 3 — Performance capture/evaluator

Deliver:

- chord onset aggregator
- `PerformanceAttempt`
- exact/pitch-class/policy evaluators
- semantic errors
- configurable timing policies

Acceptance:

- scripted MIDI tests pass
- rolled-chord threshold behaves predictably

## Phase 4 — Minimal playable vertical slice

Deliver one intentionally plain exercise screen:

```text
show Cmaj7 -> play Cmaj7 -> evaluate -> feedback -> next
```

Include several qualities, roots, inversions.

Acceptance:

- 20-exercise session works on tablet with real keyboard
- no false submissions from ordinary chord spread

## Phase 5 — Persistence and mastery

Deliver:

- Room schema
- attempt storage
- skill mastery
- gate progress
- DataStore preferences
- migrations/tests

Acceptance:

- app restart preserves progress
- historical attempts are inspectable

## Phase 6 — Campaign engine

Deliver:

- content schema
- graph validator
- gate generator
- completion rules
- unlock logic
- simple campaign UI

Acceptance:

- prerequisites/unlocks deterministic
- no unreachable/cyclic content

## Phase 7 — Assistance system

Deliver:

- `AssistanceProfile`
- chord symbol/note names/piano target/staff slots
- difficulty presets
- hint evidence tracking

Acceptance:

- same exercise can be presented at multiple assistance levels without changing answer logic

## Phase 8 — Ear training + sampler v1

Deliver:

- Kotlin AudioTrack sampler
- one high-quality legal piano bank integration path
- ear stimulus engine
- replay rules
- reproduce-by-ear gates

Acceptance:

- deterministic stimulus
- no blocking decode during timed playback
- MIDI answer evaluation reuses core evaluator

## Phase 9 — Sight reading core

Deliver:

- score domain
- Compose notation renderer
- single notes/intervals/triads
- count-in/metronome
- timed evaluator

Acceptance:

- generated phrase displays and grades against injected clock

## Phase 10 — Jazz voicing and progression systems

Deliver:

- shells/guide tones
- rootless policies
- progression domain
- ii–V–I generators
- voice-leading metrics
- progression exercise UI

Acceptance:

- multiple valid voicings accepted where policy allows
- wrong inversion/bass diagnosed correctly

## Phase 11 — Advanced harmony

Deliver:

- extensions/alterations
- sus/slash
- drop voicings
- quartal structures
- diminished submodule
- substitution curriculum

Acceptance:

- tests cover all formulas and representative contexts

## Phase 12 — Figma design system

Now apply the polished UI.

Deliver:

- final Figma screens/components
- design tokens
- Compose design-system components
- mapped states
- screenshot comparisons

Do not change core music semantics during visual implementation.

## Phase 13 — Full campaign content

Populate gates in curriculum order, with human review.

Deliver:

- gate definitions
- instruction copy
- exercise policies
- completion rules
- review mappings

Acceptance:

- content validator clean
- complete path from onboarding through advanced region

## Phase 14 — Quality pass

Deliver:

- actual tablet profiling
- latency tuning
- MIDI compatibility testing
- audio tuning
- accessibility pass
- process/state robustness
- data export/import

## Phase 15 — Release build

Deliver:

- signed APK/AAB path
- install instructions
- GitHub Actions artifact
- release notes
- versioned content schema
- known limitations
