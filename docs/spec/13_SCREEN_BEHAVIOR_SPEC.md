# Screen Behavior Specification

## 1. First Launch / Onboarding

Content:

- app premise
- connect USB MIDI keyboard
- detected device selector
- test key visualizer
- keyboard range setup
- sustain pedal test
- audio output test
- optional placement assessment

Exit condition: at minimum MIDI has been validated or user deliberately chooses demo mode.

## 2. Home

Primary actions:

- Continue Campaign
- Quick Practice
- Ear Training
- Sight Reading
- Voicing Lab

Secondary:

- Review Due
- Progress
- MIDI status
- Settings

The home screen should not expose every configuration option.

## 3. Campaign Map

Displays:

- regions
- gate nodes
- dependency paths
- locked/available/in-progress/mastered states
- current recommended gate

Selecting a gate opens Gate Intro, not the exercise immediately.

## 4. Gate Intro

Shows:

- concept name
- what player must do
- examples
- mastery requirement
- current mastery evidence
- assistance preset
- start

Keep explanation concise; deeper theory can be expanded.

## 5. Chord Exercise

Dynamic channels:

- chord symbol
- note names
- inversion/voicing instruction
- staff
- visual piano target
- countdown
- performed keys
- feedback

States:

```text
Preparing
Presenting
Armed
CollectingInput
Evaluating
FeedbackCorrect
FeedbackIncorrect
PausedMidiLost
Complete
```

Every state has explicit accepted UI actions.

## 6. Ear Exercise

Controls:

- play/replay stimulus subject to rule
- optional reference tonic
- answer through MIDI
- optional identify controls if gate requires naming

Do not show hidden chord information in accessibility semantics before answer submission.

## 7. Sight Reading

Displays:

- notation pane
- tempo
- count-in
- playhead/current beat
- optional keyboard feedback
- pitch/rhythm score after phrase

During timed reading, minimize controls and avoid popups.

## 8. Progression Exercise

Display current and upcoming chord symbols. Optional previous chord remains visible.

Advanced mode can show voice-leading traces after the attempt, not before.

## 9. Gate Result

Show:

- pass/not yet passed
- accuracy
- response-time trend
- concept coverage
- exact recurring error classes
- skills improved
- newly unlocked gates

Actions:

- continue
- retry weak subset
- free practice this concept
- campaign map

## 10. Quick Practice Setup

Use progressive disclosure. Default panel should expose only:

- material
- difficulty
- assistance
- session length

Advanced filters open separately.

## 11. Voicing Lab

Interactive:

- type/select chord
- choose voicing family
- display valid realizations
- transpose
- invert
- set top note
- set bass
- compare two voicings
- play reference audio
- play keyboard and see match analysis

This is a study tool, not a scored gate.

## 12. Progress Dashboard

Views:

- skill map
- chord-family mastery
- roots/keys coverage matrix
- ear vs construction vs reading
- error trends
- review due

Avoid vanity engagement statistics.

## 13. MIDI Setup

Show raw key activity in a diagnostic mode. Include:

- device
- input port
- active notes
- velocity
- sustain state
- last message time

This screen is essential for debugging user hardware.

## 14. Settings

Categories:

- MIDI
- keyboard range
- audio/instruments
- notation
- learning defaults
- accessibility
- diagnostics
- data export/import

Keep settings out of active exercise screens except immediate session controls.
