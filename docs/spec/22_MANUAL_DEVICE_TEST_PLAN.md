# Manual Device Test Plan

Run this plan on the target Android tablet before calling a milestone complete.

## A. USB MIDI connection

1. Launch app with no keyboard.
2. Verify disconnected state.
3. Connect keyboard through USB-C/adapter/hub.
4. Verify device appears without app restart.
5. Open MIDI diagnostic screen.
6. Play lowest, middle, highest keys.
7. Verify note numbers/names.
8. Test soft and hard velocities.
9. Test sustain pedal down/up.
10. Unplug keyboard while notes are held.
11. Verify app clears state and pauses safely.
12. Reconnect and resume.

## B. Chord capture

Test the same four-note chord:

- perfectly together
- natural finger spread
- deliberately rolled slowly
- one accidental neighbor note corrected quickly
- with sustain remnants from previous chord

Record whether the result matches configured capture policy.

## C. Theory evaluation

Manually verify:

- root-position major/minor/7th chords
- each inversion
- slash-bass requirement
- rootless voicing
- doubled chord tone
- omitted fifth when allowed
- omitted third when forbidden
- wrong alteration
- correct pitches in wrong register for exact-voicing gate

## D. Ear training

1. Play stimulus 20 times across roots/registers.
2. Confirm no decode stalls.
3. Confirm replay count policy.
4. Confirm stimulus identity matches evaluator target.
5. Confirm internal MIDI echo can be disabled.

## E. Sight reading

1. Verify staff geometry at normal playing distance.
2. Test accidentals/key signature.
3. Test ledger lines.
4. Play early/on-time/late deliberately.
5. Confirm pitch and rhythm diagnostics remain separate.
6. Change tempo and repeat.

## F. Window/lifecycle

1. Rotate/rescale where device allows.
2. Background app during untimed exercise.
3. Background during timed exercise.
4. Return after several minutes.
5. Confirm no stale held notes.
6. Confirm session progress remains coherent.

## G. Long session

Run at least 30 continuous minutes with MIDI connected. Watch for:

- audio glitches
- delayed MIDI feedback
- memory growth
- stuck notes
- UI slowdown
- database errors

## H. Release candidate

Run the test on the signed release build, not only debug.
