# Project Acceptance Criteria

The following are release-blocking unless explicitly deferred.

## MIDI

- [ ] App detects MIDI support.
- [ ] USB MIDI keyboard can connect and produce note events.
- [ ] Note On velocity 0 is handled as Note Off.
- [ ] Sustain pedal state is handled.
- [ ] Device disconnect does not destroy the current session.
- [ ] Device reconnect can resume the session safely.
- [ ] Chords are not submitted prematurely from normal human finger spread.

## Harmony

- [ ] Major/minor/diminished/augmented triads supported.
- [ ] Core seventh-chord families supported.
- [ ] Extensions and specified alterations supported.
- [ ] Enharmonic spellings are degree-aware.
- [ ] Inversions are evaluated correctly.
- [ ] Slash bass can be enforced.
- [ ] Rootless voicing policies are supported.
- [ ] Doubling/omission policy is exercise-specific.
- [ ] Voice-leading metrics are deterministic.

## Exercise/game

- [ ] Harmonic difficulty separate from assistance difficulty.
- [ ] Guided and independent presentation of same target works.
- [ ] Semantic feedback identifies why an answer failed.
- [ ] Mastery stored per skill.
- [ ] Gate unlocks use explicit completion rules.
- [ ] Review queue uses recorded weak evidence.
- [ ] Exercise seeds reproduce the same target.

## Ear training

- [ ] App can play deterministic chord stimuli.
- [ ] Player can reproduce stimuli using MIDI.
- [ ] Replay policy affects assistance evidence.
- [ ] Audio can be disabled independently of MIDI input.

## Sight reading

- [ ] Treble/bass/grand staff basic renderer works.
- [ ] Single-note and chordal pitch evaluation works.
- [ ] Timed rhythm evaluation uses monotonic clock.
- [ ] Pitch and rhythm feedback are separate.

## Persistence

- [ ] Progress survives restart.
- [ ] Room migrations are tested.
- [ ] Preferences persist through DataStore.
- [ ] Attempts contain enough snapshot data for debugging.

## UI

- [ ] Usable at tablet playing distance.
- [ ] MIDI state visible/discoverable.
- [ ] Correctness is not represented by color alone.
- [ ] Final Compose UI follows approved Figma design system.
- [ ] Layout survives relevant tablet resizing/window configurations.

## Quality

- [ ] Pure music layer has extensive unit tests.
- [ ] MIDI fake source permits CI testing.
- [ ] Main branch builds from clean checkout.
- [ ] CI produces debug APK artifact.
- [ ] Real target tablet + physical keyboard manual test completed.
