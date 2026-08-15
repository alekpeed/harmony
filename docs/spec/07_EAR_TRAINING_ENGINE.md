# Ear Training Engine

## 1. Purpose

Ear training must use the same harmonic objects as the chord-construction game. There must not be a separate, inconsistent list of audio chord labels.

## 2. Ear task families

### Reproduce

App plays a chord. Player reproduces it on MIDI.

Difficulty dimensions:

- root known/unknown
- quality known/unknown
- inversion known/unknown
- bass note known/unknown
- extension known/unknown
- contextual key given/hidden

### Identify then play

Player chooses/enters identity and then performs it. The performance step prevents guessing from being treated as complete mastery.

### Difference detection

Play A then B. Player determines what changed:

- major 7 -> dominant 7
- natural 9 -> b9
- 5 -> #5/b13
- root position -> inversion
- closed -> spread voicing

### Function hearing

Provide a key/tonic and ask player to reproduce or identify:

- ii
- V
- I
- secondary dominant
- tritone substitute
- backdoor dominant

### Bass hearing

Play upper chord + moving bass. Ask for bass line, slash identity, or full reproduction.

### Voice-leading hearing

Play two voicings and ask which voice moved or require reproduction of the second from the first.

## 3. Stimulus determinism

Every stimulus record stores:

- exercise seed
- `ChordSpec` / progression spec
- realized voicing
- instrument preset
- velocity pattern
- playback tempo
- optional humanization seed

This makes reported errors reproducible.

## 4. Avoid timbral memorization

Where appropriate, randomize among a small approved set of realistic instrument presets so the player learns harmony rather than one spectral fingerprint.

Do not randomize so aggressively that timbre becomes the difficulty instead of harmony.

## 5. Replay rules

Configurable by gate:

- unlimited replay in Learn mode
- limited replay in Practice
- one playback in Challenge

Store replay count as assistance evidence. A correct answer after five replays is still useful practice but should not equal a one-hearing independent response for gate mastery.

## 6. Reference tonic

Some tasks require a stable tonal reference. Support:

- play tonic chord
- play tonic note
- cadence into key
- no reference

## 7. Audio-to-MIDI evaluation

There is no transcription step. The app already knows what it played, and evaluates the player's MIDI response against that target.

This is a major reliability advantage and should remain architecturally explicit.
