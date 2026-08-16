# AGENTS.md — Harmony Gates

## Mission

Build a native Android tablet jazz-harmony game in Kotlin/Jetpack Compose with a USB MIDI
keyboard as the primary controller.

The full specification lives in `docs/spec/`. Read the numbered documents relevant to your
phase before changing code; they are the source of truth, and this file only summarises the
rules that are easiest to break by accident.

## Hard constraints

- Kotlin application code.
- Compose UI.
- Music correctness lives in `core:music`.
- MIDI access goes through `MidiInputSource`.
- No microphone chord recognition for scoring.
- No network requirement for core play.
- No C++/NDK in the baseline implementation.
- Do not change musical behaviour merely to simplify UI.
- Do not implement final aesthetics ahead of approved design; use the placeholder design
  system until an approved asset exists in `interface/`.

## Required workflow

For every phase:

1. inspect repo and current status
2. implement the smallest coherent phase slice
3. add/update tests
4. run `./gradlew test`
5. run `./gradlew lint`
6. run `./gradlew assembleDebug`
7. update `docs/IMPLEMENTATION_STATUS.md`
8. commit/PR before starting dependent phase

`./gradlew verifyHarmony` runs every module's checks in one go.

## Architecture boundaries

`core:music` is a plain Kotlin/JVM library. It is not an Android module, so a platform
dependency cannot leak in by accident.

UI may ask the domain layer:

```text
What should I display?
Was this attempt valid?
What error occurred?
What skill evidence changed?
```

UI must not answer these itself. `core:designsystem` deliberately does not depend on
`core:music`, so it never gets the chance to make a theory decision.

## No silent theory assumptions

If a chord/voicing rule is ambiguous, represent the ambiguity in `VoicingPolicy` or content.
Do not add a global universal rule unless the specification explicitly requires it.

Two worked examples already in the codebase, both in `ChordFormulas`:

- a dominant thirteenth *permits* a natural eleventh and does not *write* one, so a player who
  voices it is not marked wrong and a printed symbol is not littered with avoid notes
- an altered dominant requires only its guide tones; which alterations appear is an exercise
  policy decision, not a property of the chord

## No silent enharmonic substitution

A chord that standard notation cannot write — `Cbdim7` would need a B triple-flat — is
reported, never respelled. `ChordRealizer.trySpell` returns the failure; `chordTones` throws.
Anything driven by text input or authored content asks first.

## Test rule

Any fixed bug involving chord identity, spelling, MIDI capture, or scoring requires a
regression test reproducing the failure.

Golden theory fixtures live in `core/music/src/test/resources/fixtures/`. They were derived
from theory by hand, not captured from the engine; if the engine and a fixture disagree, the
fixture is the one to trust until a human says otherwise.

## Interface assets

`interface/` is the handoff point for approved visual assets. See
`docs/INTERFACE_INTEGRATION.md` for how an approved screen is wired in. Hit regions are stored
as fractions of the artwork, never as device pixels.
