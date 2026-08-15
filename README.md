# Harmony Gates

A native Android tablet jazz-harmony and sight-reading game. A real USB MIDI keyboard is the
controller: the app presents a musical task, the player answers on the keys, and the app judges
the actual MIDI performance. Mastery opens gates into progressively harder material.

Kotlin, Jetpack Compose, offline, tablet-first.

## Status

**Phases 0 and 1 complete.** The toolchain and the music-theory domain are in place; MIDI is
next. See `docs/IMPLEMENTATION_STATUS.md` for the detailed handoff and known limitations.

| | |
| --- | --- |
| Tests | 129 passing |
| Debug APK | builds |
| MIDI | Phase 2 |
| Verified on hardware | not yet — no device attached to the build environment |

## Quick start

```bash
./gradlew verifyHarmony     # every module's tests and lint
./gradlew :core:music:test  # the theory engine on its own — fast
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew installDebug      # with a tablet attached over adb
```

Requires JDK 21 and an Android SDK with platform 37. Point `local.properties` at it:

```properties
sdk.dir=/path/to/android-sdk
```

## What exists today

The app opens on the approved home screen from `interface/`, with all twenty of its controls
live. One of them — **Theory Lab** — leads somewhere real: type a chord symbol and see its
degrees, its spelling and a generated voicing, all answered by `core:music`. The rest report
which phase they arrive in rather than opening an empty room.

The theory engine handles 30 chord formulas across all twelve roots, jazz chord-symbol parsing
(`Cm7b5`, `CΔ7`, `C7alt`, `Dbmaj9/F`), degree-aware enharmonic spelling, inversions, voicing
policies including rootless families, drop and spread transformations, spelled transposition,
roman-numeral functional harmony, and voice-leading analysis with optimal voice assignment.

## Layout

```text
app/                    Android application, Phase 1 harness, home screen
core/music/             Pure Kotlin/JVM: all music theory. No Android dependency.
core/designsystem/      Tokens, placeholder theme, components, artwork hit-region layer
core/testing/           Test doubles and readable music assertions
content/                Curriculum and exercise policies (Phase 6 onwards)
docs/spec/              The full specification — the source of truth
interface/              Approved visual assets and placement references
```

`core:midi`, `core:audio` and `core:data` arrive with Phases 2, 8 and 5.

## Design rules that shape the code

- **MIDI performance is the source of truth.** No microphone transcription for correctness.
- **Theory lives in one place.** `core:music` is a plain JVM library, so an Android dependency
  cannot leak in. UI never decides whether a chord is correct — and `core:designsystem` does not
  depend on `core:music`, so it never gets the chance.
- **Spelling is preserved.** `Db7` spells Cb, not B. A chord that standard notation cannot write
  is reported, never quietly respelled.
- **Ambiguity is data, not code.** A dominant thirteenth permits a natural eleventh without
  writing one; which alterations an altered dominant shows is an exercise policy. Neither is
  hard-coded as a universal rule.
- **Generation is deterministic.** Same seed, same exercise. Clocks and randomness are injected.
- **Visuals come last.** Everything visual goes through a design token, so the approved design
  system replaces values rather than screens. See `docs/INTERFACE_INTEGRATION.md`.

## Contributing

Read `AGENTS.md` first, then the numbered documents in `docs/spec/` for the phase you are
working on. Every phase ends with passing tests, clean lint, a building debug APK, and an
updated `docs/IMPLEMENTATION_STATUS.md`.
