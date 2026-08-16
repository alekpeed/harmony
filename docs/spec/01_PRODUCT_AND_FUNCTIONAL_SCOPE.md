# Product and Functional Scope

## 1. Primary use case

The player sits at a real keyboard connected by USB to an Android tablet. The tablet becomes the exercise generator, visual guide, evaluator, progression map, and ear-training sound source.

The app must support two kinds of learning at the same time:

- **declarative knowledge:** knowing what notes and functions form a chord
- **motor/aural fluency:** being able to hear, locate, voice, and play that harmony in real time

The application therefore cannot be designed as a conventional multiple-choice music quiz.

## 2. Core activity loop

```text
Select activity
   ↓
Create deterministic ExerciseInstance
   ↓
Present target using enabled assistance channels
   ↓
Arm MIDI capture
   ↓
Player performs
   ↓
Normalize raw MIDI into PerformanceAttempt
   ↓
Evaluator compares attempt to ExerciseRequirement
   ↓
Feedback + explanation
   ↓
Mastery update
   ↓
Next exercise / gate result
```

## 3. Required top-level modes

### 3.1 Campaign

A progression map composed of worlds/regions and gates. Each gate corresponds to a narrowly defined musical competency. Gate completion unlocks dependent gates.

### 3.2 Quick Practice

Choose a domain directly without affecting unlock requirements unless the player explicitly enables “count practice toward mastery.”

Filters must include:

- root pool
- key signatures
- chord families
- extensions
- alterations
- inversions
- voicing families
- hand/range constraints
- tempo
- assistance level
- answer tolerance

### 3.3 Ear Training

App plays target material. Player identifies or reproduces it at the MIDI keyboard.

### 3.4 Sight Reading

Player reads generated notation and performs it against time.

### 3.5 Progression Lab

Practice functional sequences such as ii–V–I, turnarounds, rhythm changes fragments, blues movements, backdoor dominants, tritone substitutions, secondary dominants, and diminished passing structures.

### 3.6 Voicing Lab

A non-campaign sandbox for inspecting and comparing valid voicings. Display chord tones, tensions, omissions, interval structure, voice-leading distance, and keyboard locations.

### 3.7 Review Queue

A queue created from weak skills and recent errors. This is not a streak mechanic. Its purpose is targeted retrieval practice.

## 4. Two independent difficulty axes

### Harmonic Complexity `H`

Controls what musical material may appear.

Example scale:

- H1: major/minor triads in root position
- H2: all triads + inversions
- H3: basic seventh chords
- H4: seventh inversions + diatonic functional context
- H5: 9ths/11ths/13ths and common omissions
- H6: altered dominants, sus, slash chords
- H7: rootless A/B voicings, shells, guide tones
- H8: drop voicings, quartal/So What structures, upper structures
- H9: advanced substitutions and diminished systems
- H10: context-dependent jazz voicing/voice-leading problems

### Assistance `A`

Controls how much information is exposed before the answer.

- A0: chord symbol + spelled notes + highlighted piano keys + notation + optional fingering + audio
- A1: chord symbol + notes + highlighted keys
- A2: chord symbol + keyboard range, notes hidden
- A3: chord symbol only
- A4: notation only
- A5: function/roman numeral only
- A6: audio + limited contextual hint
- A7: audio only

Difficulty sliders in the UI may present friendly labels, but the internal model must preserve the separate `H` and `A` values.

## 5. Exercise presentation channels

Any exercise can independently enable:

- chord symbol
- roman numeral/function
- spelled note names
- staff notation
- piano keyboard highlighting
- target bass note
- inversion label
- voicing name
- fingering suggestion
- reference audio
- metronome/count-in
- contextual preceding/following chord

Do not build each combination as a separate screen. The exercise screen is compositional.

## 6. Correctness modes

Every exercise explicitly declares how it is evaluated.

Examples:

- exact pitch classes, any octave
- exact MIDI pitches
- chord tones only, doubling allowed
- exact bass pitch class + allowed upper pitch classes
- exact inversion
- exact named voicing
- minimum required chord tones + permitted omissions
- target melody top note required
- rootless allowed/not allowed
- extra tensions allowed/not allowed
- rhythmic onset window required
- sequence with voice-leading objective

No global “chord equals set of pitch classes” shortcut is sufficient.

## 7. Feedback model

Feedback should identify the musical cause of failure. Examples:

- “Correct C7 quality, but E is required in the bass for first inversion.”
- “You played C9; this gate requires the altered dominant C7(b9).”
- “The voicing is harmonically valid, but the target requires G as the melody note.”
- “Pitch content is correct; the third entered 180 ms outside the chord-onset window.”

Do not merely show red/green.

## 8. Out of scope for v1

- microphone polyphonic chord transcription
- online multiplayer
- social feeds
- subscription backend
- cloud account requirement
- full DAW functionality
- full commercial notation engraving
- automatic transcription from songs
- arbitrary AI-generated curriculum
- scoring users against other users

These can be reconsidered later without contaminating the v1 architecture.
