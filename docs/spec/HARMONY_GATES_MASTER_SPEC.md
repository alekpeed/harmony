# Harmony Gates — Consolidated Master Specification

This file concatenates the primary coder documents for agents that work better from one context file. The individual files remain authoritative and easier to maintain.


---

<!-- SOURCE: 00_README_START_HERE.md -->

# Harmony Gates — Coder Document Pack

**Project:** Native Android tablet jazz-harmony and sight-reading game  
**Language:** Kotlin only for application code  
**UI:** Jetpack Compose  
**Primary controller:** USB MIDI keyboard  
**Primary form factor:** Android tablet, landscape-first  
**Working title:** Harmony Gates

## Product statement

Harmony Gates is a jazz-piano learning game in which a real MIDI keyboard is the controller. The app presents a musical task visually, aurally, or in notation; the player responds on the keyboard; the app evaluates the actual MIDI performance; and mastery opens gates into progressively more advanced material.

The progression begins with fully guided chord construction and ends with independent recognition, sight reading, voice leading, altered harmony, rootless voicings, reharmonization, and jazz-functional fluency.

## Non-negotiable design rules

1. MIDI performance is the source of truth for played answers. Do not use microphone transcription for correctness checking.
2. Music-theory logic must live in one reusable, testable harmony domain layer. UI code must never decide whether a chord is musically correct.
3. Chord identity, chord spelling, bass note, inversion, voicing, register, omissions, doubling, extensions, and alterations are separate concepts.
4. Harmonic difficulty and assistance difficulty are separate axes.
5. A gate unlocks from demonstrated mastery, not arbitrary play time.
6. Generated exercises must be deterministic when supplied the same seed.
7. The app works offline after installation and local asset setup.
8. The first release is tablet-first but must remain adaptive instead of hard-locking one pixel size.
9. The application layer remains Kotlin. Do not introduce C++/NDK just to solve MIDI or audio.
10. Final visual styling is designed in Figma after this functional specification. Code should use tokens/components so the final Figma system can be applied without architectural rewrites.

## Android baseline for the first implementation

Use a version catalog and the latest stable mutually compatible libraries at implementation time. Current planning baseline, August 2026:

- compile SDK: API 37
- initial target SDK: API 36, with an explicit API 37 validation/upgrade task before public distribution if appropriate
- minimum SDK: API 26
- current stable Jetpack Compose line
- Material 3 and Material 3 Adaptive
- Navigation 3
- Android Architecture Components / ViewModel
- Kotlin Coroutines and Flow
- Room for durable structured learning data
- DataStore for preferences
- `android.media.midi` for MIDI discovery and transport
- `AudioTrack`-based Kotlin sampler for internal ear-training playback
- Compose Canvas for piano/staff/game visualizations

The current Compose August 2026 line requires compile SDK 37 and a recent Android Gradle Plugin. Do not pin dependency numbers in source files outside `libs.versions.toml`.

## Read order

1. `01_PRODUCT_AND_FUNCTIONAL_SCOPE.md`
2. `02_GAME_LOOP_AND_PROGRESSION.md`
3. `03_JAZZ_CURRICULUM.md`
4. `04_HARMONY_DOMAIN_ENGINE.md`
5. `05_MIDI_INPUT_ENGINE.md`
6. `06_PERFORMANCE_EVALUATION_AND_SCORING.md`
7. `07_EAR_TRAINING_ENGINE.md`
8. `08_SIGHT_READING_ENGINE.md`
9. `09_AUDIO_SAMPLER_ENGINE.md`
10. `10_ANDROID_ARCHITECTURE.md`
11. `11_DATA_MODEL_AND_PERSISTENCE.md`
12. `12_UI_UX_AND_FIGMA_HANDOFF.md`
13. `13_SCREEN_BEHAVIOR_SPEC.md`
14. `14_TESTING_AND_QUALITY.md`
15. `15_IMPLEMENTATION_PHASES.md`
16. `16_AGENT_EXECUTION_PROTOCOL.md`
17. `17_BUILD_CI_INSTALL_RELEASE.md`
18. `18_ACCEPTANCE_CRITERIA.md`
19. `19_BACKLOG_AFTER_V1.md`
20. `PROMPT_TO_CODING_AGENT.md`
21. `AGENTS.md`
22. `REFERENCES.md`

## Proposed repository structure

```text
/
├── app/
├── core/
│   ├── music/
│   ├── midi/
│   ├── audio/
│   ├── data/
│   ├── designsystem/
│   └── testing/
├── feature/
│   ├── home/
│   ├── campaign/
│   ├── chordgate/
│   ├── eartraining/
│   ├── sightreading/
│   ├── progression/
│   ├── voicelab/
│   ├── curriculum/
│   ├── profile/
│   └── settings/
├── content/
│   ├── curriculum/
│   ├── exercises/
│   └── examples/
├── docs/
├── .github/workflows/
├── gradle/libs.versions.toml
├── AGENTS.md
└── README.md
```

## Definition of project completion

The project is complete when a player can connect a class-compliant USB MIDI keyboard to the tablet, complete onboarding, enter the campaign, receive chord/ear/sight-reading tasks, answer on the real keyboard, receive immediate musically correct feedback, accumulate skill-specific mastery, unlock later gates, resume exactly where they left off, and use free-practice modes independently of campaign progression.


---

<!-- SOURCE: 01_PRODUCT_AND_FUNCTIONAL_SCOPE.md -->

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


---

<!-- SOURCE: 02_GAME_LOOP_AND_PROGRESSION.md -->

# Game Loop and Progression

## 1. Design premise

The game layer exists to organize repetition and mastery. It must never distort the musical objective. Advancement is competency-gated.

## 2. Campaign topology

Represent the campaign as a directed acyclic graph rather than a single linear level number.

```kotlin
data class GateDefinition(
    val id: GateId,
    val title: String,
    val skillIds: Set<SkillId>,
    val prerequisites: Set<GateId>,
    val exercisePolicyId: ExercisePolicyId,
    val completionRule: CompletionRule,
    val rewards: List<Unlock>
)
```

This permits branches such as:

```text
Triads
├── Inversions
├── Diatonic Function
└── Ear Recognition
       ↓
Seventh Chords
├── Voicing
├── Sight Reading
└── Progressions
       ↓
Extensions / Altered Harmony / Voice Leading
```

## 3. Gate session anatomy

A normal gate session has 8–24 exercises depending on the skill and mastery state.

Phases:

1. **Preview** — one concise explanation and optional demonstration.
2. **Guided trials** — high assistance.
3. **Independent trials** — reduced assistance.
4. **Challenge trials** — randomized roots/register/context.
5. **Gate check** — fixed number of scored attempts with no tutorial interruption.
6. **Result** — pass, partial mastery, or retry recommendation.

Do not force a player who has already demonstrated mastery to repeat tutorial trials.

## 4. Gate completion

Use a mastery rule such as:

```text
minimum attempts: 12
minimum recent weighted accuracy: 0.90
maximum critical-theory errors in last 8: 1
required root coverage: >= 8 of 12 roots where applicable
required median response time: skill-specific threshold
```

Exact thresholds are content data, not hard-coded UI constants.

## 5. Error classes

Store errors semantically:

- wrong root
- wrong quality
- missing required chord tone
- wrong alteration
- wrong bass/inversion
- wrong voicing
- extra disallowed note
- register violation
- timing/onset violation
- rhythm duration violation
- melody/top-note violation
- incomplete sequence

This enables targeted review.

## 6. Recovery loop

Failure does not simply restart the same set.

After repeated errors:

1. lower assistance by only one dimension at a time
2. show a concise explanation
3. isolate the failing component
4. provide one scaffolded retry
5. return to the original task

Example: if `G7b9` repeatedly fails because the player omits B, temporarily present the guide tones B/F and then rebuild the full chord.

## 7. Mastery graph

Mastery belongs to individual skills, not levels.

Example skill IDs:

```text
triad.major.build
triad.major.inversion.1
seventh.dom7.build
seventh.dom7.inversion.3
tension.b9.recognize
tension.sharp11.build
voicing.shell.3_7
voicing.rootless.a
progression.ii_v_i.major
voiceleading.common_tone
reading.grand_staff.chordal
hearing.dom7_vs_maj7
```

A gate aggregates these skills; it does not replace them.

## 8. Difficulty slider

The user-facing slider can expose presets:

- Learn
- Guided
- Practice
- Challenge
- Blind

Each preset maps to an `AssistanceProfile`. An “Advanced” control exposes the individual channels.

The harmonic-content slider is independent:

- Foundations
- Seventh Harmony
- Extensions
- Jazz Voicings
- Altered Harmony
- Advanced

## 9. Session generator

The generator must use weighted sampling from:

- gate-required coverage
- untested roots
- recent mistakes
- spaced review due items
- desired novelty
- avoidance of immediate duplicates

Pseudocode:

```text
candidate exercises = policy.generate(seed)
score each candidate:
    + coverageNeed
    + weaknessWeight
    + reviewDueWeight
    + noveltyWeight
    - recentDuplicatePenalty
sample deterministically from weighted set
```

## 10. Game presentation

The visual metaphor can be a map, gates, chambers, constellations, clubs, transit lines, or another theme chosen in Figma. The progression API must not depend on the final art metaphor.

Model concepts generically as `Region`, `Gate`, `Path`, `Unlock`, and `MasteryState`.

## 11. Rewards

Useful rewards are educational access rather than currencies:

- new voicing family
- new harmonic region
- new visual theme
- new instrument sound
- new practice preset
- challenge gate

Avoid artificial energy systems, timers, streak pressure, or monetization mechanics in v1.


---

<!-- SOURCE: 03_JAZZ_CURRICULUM.md -->

# Jazz Curriculum

## 1. Curriculum philosophy

The curriculum should move through four simultaneous competencies:

1. **construct** — know the notes
2. **locate** — find them physically
3. **hear** — recognize sonority/function
4. **connect** — move efficiently to the next harmony

A player should not be considered fluent because they can calculate a chord slowly from a symbol.

## 2. Region 0 — MIDI and keyboard calibration

Skills:

- connect device
- identify lowest/highest available key
- verify sustain pedal
- play single-note calibration
- learn app feedback conventions

No musical prerequisite.

## 3. Region 1 — Intervals and triad foundations

Content:

- semitone/whole tone
- major/minor thirds
- perfect/diminished/augmented fifth
- major triad
- minor triad
- diminished triad
- augmented triad
- root position
- first inversion
- second inversion
- chord spelling by thirds

Gates:

- build from symbol
- build from root + quality
- identify from audio
- identify inversion
- sight-read block triads

## 4. Region 2 — Seventh harmony

Content:

- maj7
- 7 / dominant seventh
- min7
- minMaj7
- half-diminished / m7b5
- diminished seventh
- major 6
- minor 6
- all inversions

Functions:

- tonic major
- tonic minor
- predominant
- dominant
- leading-tone diminished

Required fluency:

- all 12 roots
- common enharmonic spellings
- chord-symbol aliases

## 5. Region 3 — Diatonic functional harmony

Major-key seventh chords:

```text
Imaj7 ii7 iii7 IVmaj7 V7 vi7 viiø7
```

Minor-key practical forms:

- natural/minor tonic structures
- iiø7–V7–i
- raised leading-tone dominant function
- tonic minor 6 / minMaj7 alternatives

Exercises:

- roman numeral -> chord
- chord -> function
- function -> play in key
- diatonic sequence reading

## 6. Region 4 — Guide tones, shells, and economical comping

Content:

- thirds and sevenths
- 1–3–7 shells
- 1–7–3 shells
- two-note guide-tone shells
- omitted fifth as a normal jazz practice where appropriate
- voice-leading 3rd ↔ 7th through ii–V–I

This region should arrive before extremely dense extensions. It teaches the harmonic skeleton.

## 7. Region 5 — Extensions

Content:

- 9
- b9
- #9
- 11
- #11
- 13
- b13
- add9/add11 distinctions
- major 9, minor 9, dominant 9
- maj13, min11, 13

Teach tension availability contextually rather than as a flat list.

Evaluation must distinguish “required”, “allowed”, and “avoid/disallowed for this exercise”; the theory engine itself should not assert that a context-dependent tension is universally illegal.

## 8. Region 6 — Sus, slash chords, and moving bass

Content:

- sus2/sus4
- 7sus4
- slash notation
- inversion slash chords versus independent upper-structure/bass combinations
- pedal points
- stepwise bass under static harmony
- common bass-line harmonization

Tasks may require one hand to maintain a chord while the other executes bass motion.

## 9. Region 7 — Rootless jazz voicings

Start with practical dominant/major/minor ii–V–I families.

Concepts:

- A and B position families
- 3rd/7th as structural anchors
- 9/13 or related color tones
- melody-note constraints
- left-hand register limits
- root intentionally omitted

Do not evaluate a rootless voicing using a generic “root missing = incorrect” rule.

## 10. Region 8 — Drop and spread voicings

Content:

- close position
- drop 2
- drop 3
- selected drop 2&4 structures
- spread voicings
- top-note control

The harmony engine should generate these by voice transformation, not static lookup alone.

## 11. Region 9 — Quartal and modal voicings

Content:

- stacked fourths
- So What-type structures
- sus/modal sonorities
- quartal planing

The chord label may be ambiguous. Exercises here can specify structure/function rather than force a single conventional chord symbol.

## 12. Region 10 — Altered dominant vocabulary

Content:

- V7b9
- V7#9
- V7b5/#11
- V7#5/b13
- combined alterations
- altered scale-compatible collections
- tritone-sub dominant relationship

Required tasks:

- build alteration from base dominant
- hear base vs altered
- retain guide tones while changing color tones
- resolve altered tensions by voice leading

## 13. Region 11 — Diminished systems

Content:

- fully diminished seventh symmetry
- dominant b9 relationship
- passing diminished chords
- common-tone diminished usage
- diminished-derived dominant movement

Optional Barry Harris-specific material should be a dedicated submodule rather than silently mixing pedagogy systems.

## 14. Region 12 — Progressions

Mandatory vocabulary:

- major ii–V–I
- minor iiø–V–i
- I–vi–ii–V
- iii–VI–ii–V
- secondary dominants
- backdoor dominant
- tritone substitution
- descending dominants
- turnaround variants
- basic 12-bar jazz blues
- rhythm-changes A-section functional skeleton

Each progression exists as abstract function and as transposed concrete instances.

## 15. Region 13 — Voice-leading challenge

The app presents a starting voicing and a destination harmony.

Scoring considers:

- harmonic validity
- total semitone movement
- maximum individual voice leap
- retained common tones
- crossing/overlap rules
- requested top note
- hand-range constraints

There may be several equally valid high-scoring answers. The evaluator must support sets/ranges of valid outcomes.

## 16. Region 14 — Integrated jazz reading

Combine:

- melody notation
- chord symbols
- rhythmic comping prompts
- left-hand shells/rootless voicings
- two-hand coordination

The end state is not “memorized chord flash cards.” It is reading and hearing harmonic information and immediately turning it into physical keyboard language.


---

<!-- SOURCE: 04_HARMONY_DOMAIN_ENGINE.md -->

# Harmony Domain Engine

## 1. Rule

`core:music` is a pure Kotlin module with no Android UI dependency. Almost all classes should be unit-testable on the JVM.

## 2. Primitive types

Do not use raw integers and strings throughout the codebase.

```kotlin
@JvmInline value class MidiNote(val value: Int)
@JvmInline value class PitchClass(val value: Int) // normalized 0..11
@JvmInline value class ScaleDegree(val value: Int)
@JvmInline value class Semitones(val value: Int)
@JvmInline value class VoiceIndex(val value: Int)

enum class LetterName { C, D, E, F, G, A, B }
enum class Accidental { DOUBLE_FLAT, FLAT, NATURAL, SHARP, DOUBLE_SHARP }

data class SpelledPitchClass(val letter: LetterName, val accidental: Accidental)
data class SpelledPitch(val pitchClass: SpelledPitchClass, val octave: Int)
```

`PitchClass(1)` is not sufficient to decide whether the music should display C# or Db. Preserve spelling.

## 3. Chord representation

Separate symbol intent from realized voicing.

```kotlin
data class ChordFormula(
    val quality: ChordQuality,
    val requiredDegrees: Set<ChordDegree>,
    val optionalDegrees: Set<ChordDegree>,
    val forbiddenDegrees: Set<ChordDegree> = emptySet(),
    val aliases: Set<String> = emptySet()
)

data class ChordSpec(
    val root: SpelledPitchClass,
    val formulaId: ChordFormulaId,
    val alterations: Set<DegreeAlteration> = emptySet(),
    val additions: Set<ChordDegree> = emptySet(),
    val omissions: Set<ChordDegree> = emptySet(),
    val explicitBass: SpelledPitchClass? = null
)

data class Voicing(
    val chord: ChordSpec,
    val pitches: List<MidiNote>,
    val spelledPitches: List<SpelledPitch>,
    val metadata: VoicingMetadata
)
```

## 4. Chord degrees

Represent degrees functionally, e.g. `ROOT`, `b3`, `3`, `5`, `b7`, `7`, `b9`, `9`, `#9`, `11`, `#11`, `b13`, `13`.

Do not collapse `#9` into `b3` just because they share a pitch class. Their harmonic roles differ.

## 5. Chord parser

Required examples:

```text
C
Cm
Cmaj7
CΔ7
C7
Cm7
Cm(maj7)
Cø7
Cm7b5
Cdim7
C°7
C6
Cm6
C9
Cmaj9
Cm9
C11
C13
C7b9
C7#9
C7#11
C7b13
C7alt
C7sus4
C13sus
C/E
Dbmaj9/F
```

Parser output should be canonical `ChordSpec`; display formatting can preserve a preferred symbol style separately.

## 6. Enharmonic spelling

Create a `PitchSpeller` aware of:

- explicit root spelling
- key signature
- chord degree role
- preferred accidental policy

Examples:

- Db7 should spell Db–F–Ab–Cb, not C#–F–G#–B.
- F#maj7 should contain E#, not F.
- C7#9 uses D#, not Eb, in analytical display even though MIDI pitch is the same.

## 7. Inversion

An inversion is determined by bass relationship to the intended chord, not list order after sorting alone.

```kotlin
enum class Inversion { ROOT, FIRST, SECOND, THIRD, OTHER }
```

Extended chords may use `OTHER` plus explicit bass degree because “fifth inversion of a 13th chord” is not a useful training abstraction for this product.

## 8. Voicing policy

```kotlin
data class VoicingPolicy(
    val requiredDegrees: Set<ChordDegree>,
    val optionalDegrees: Set<ChordDegree>,
    val allowedOmissions: Set<ChordDegree>,
    val allowDoubling: Boolean,
    val requireRoot: Boolean,
    val bassRequirement: BassRequirement,
    val topNoteRequirement: TopNoteRequirement?,
    val pitchRange: IntRange?,
    val maxVoices: Int?,
    val namedFamily: VoicingFamily? = null
)
```

This policy is the bridge between harmony and exercise evaluation.

## 9. Voicing transformations

Provide pure transformations:

- invert
- octave-displace voice
- drop-2
- drop-3
- spread
- transpose chromatically
- transpose diatonically where appropriate
- constrain into range
- preserve top note
- preserve bass

## 10. Functional harmony

```kotlin
data class KeyContext(
    val tonic: SpelledPitchClass,
    val mode: Mode
)

data class FunctionalChord(
    val romanNumeral: RomanNumeral,
    val alterations: Set<DegreeAlteration>,
    val secondaryTarget: ScaleDegree? = null
)
```

Examples that must be expressible:

```text
ii7 in C major
V7/ii in Eb major
bII7 tritone substitute in C
ivm7 -> bVII7 backdoor movement
```

## 11. Voice-leading metrics

For two ordered voicings, calculate:

- summed absolute semitone motion
- per-voice motion
- maximum leap
- common-tone count
- pitch-class common-tone count
- contrary/similar/oblique motion metadata where defined

For voicings with unequal voice counts, use an assignment algorithm with explicit add/drop penalties rather than assuming index-to-index correspondence.

## 12. Exercise requirement

All games converge on this domain object:

```kotlin
sealed interface ExerciseRequirement {
    data class PitchSet(...): ExerciseRequirement
    data class ExactVoicing(...): ExerciseRequirement
    data class ChordPolicyMatch(...): ExerciseRequirement
    data class TimedSequence(...): ExerciseRequirement
    data class VoiceLeadingTarget(...): ExerciseRequirement
    data class SightReadingPhrase(...): ExerciseRequirement
}
```

## 13. Required domain tests

At minimum:

- all 12 roots for every supported formula
- all inversions
- enharmonic spelling fixtures
- parser alias round trips
- transposition invariants
- drop voicing transformations
- allowed/forbidden omission behavior
- rootless voicing behavior
- slash chord bass behavior
- voice-leading metric fixtures
- randomized property tests: transpose + inverse transpose returns equivalent structure

The music engine should reach very high unit-test coverage because incorrect theory silently ruins the training product.


---

<!-- SOURCE: 05_MIDI_INPUT_ENGINE.md -->

# MIDI Input Engine

## 1. Goal

Turn raw Android MIDI messages into stable, timestamped musical performance state with minimal latency and no false chord submissions caused by normal human finger spread.

## 2. Android API

Use `android.media.midi.MidiManager` as the normal transport abstraction.

Check `PackageManager.FEATURE_MIDI` at startup. On modern Android, enumerate MIDI 1.0 byte-stream devices using the transport-specific API where available; retain an API-compatible path for older supported versions.

USB class-compliant keyboards normally appear through Android's MIDI service. Do not manually implement the USB-MIDI class protocol unless a specific target keyboard proves incompatible.

## 3. MIDI connection states

```kotlin
sealed interface MidiConnectionState {
    data object Unsupported : MidiConnectionState
    data object NoDevice : MidiConnectionState
    data class DevicesAvailable(val devices: List<MidiEndpoint>) : MidiConnectionState
    data class Connecting(val endpoint: MidiEndpoint) : MidiConnectionState
    data class Connected(val endpoint: MidiEndpoint) : MidiConnectionState
    data class Error(val reason: MidiError) : MidiConnectionState
}
```

Expose this as `StateFlow`.

## 4. Message normalization

Normalize input into:

```kotlin
sealed interface MidiEvent {
    val timestampNanos: Long

    data class NoteOn(
        val note: MidiNote,
        val velocity: Int,
        val channel: Int,
        override val timestampNanos: Long
    ) : MidiEvent

    data class NoteOff(...) : MidiEvent
    data class ControlChange(...) : MidiEvent
    data class PitchBend(...) : MidiEvent
}
```

Treat Note On with velocity 0 as Note Off.

## 5. Sustain pedal

CC64 changes semantic held state.

Maintain separately:

- physically depressed notes
- pedal-sustained notes
- effective sounding notes

For chord-answer capture, configurable policies:

- ignore sustained remnants from the previous attempt
- include pedal-held notes
- require pedal release between trials

Default guided chord mode should avoid marking a correct new chord wrong because of stale sustain. Sequence/sight-reading modes may require literal sustain behavior.

## 6. Chord capture window

Humans do not depress simultaneous chord notes on the exact same millisecond.

Use an onset aggregation state machine:

```text
IDLE
  first qualifying NoteOn -> COLLECTING
COLLECTING
  accumulate NoteOns
  reset short quiet timer on each new onset
  when quiet interval elapsed OR max collection duration reached -> CANDIDATE
CANDIDATE
  wait optional stabilization period
  evaluate
```

Starting default values for user testing, not permanent constants:

- inter-note quiet window: ~60–100 ms
- maximum chord roll window: ~250–350 ms
- stabilization: ~30–60 ms

Expose them as internal tuning parameters and instrument-test them.

## 7. Rolled-chord tolerance

Some exercises intentionally require simultaneity; others should accept a slightly rolled jazz voicing.

`OnsetPolicy`:

```kotlin
sealed interface OnsetPolicy {
    data class Simultaneous(val maxSpreadMs: Int): OnsetPolicy
    data class RolledAllowed(val maxSpreadMs: Int): OnsetPolicy
    data object Unrestricted: OnsetPolicy
}
```

## 8. Debounce and noise

Ignore/handle:

- duplicate NoteOn caused by device quirks
- repeated NoteOff
- active sensing
- unsupported system messages
- channel messages not needed by the current exercise

Never solve MIDI noise by adding arbitrary multi-hundred-millisecond delays to the UI.

## 9. Device hotplug

The app must survive:

- keyboard disconnected while playing
- keyboard reconnected
- switching keyboards
- activity background/foreground
- USB hub disconnect

On disconnection:

1. stop accepting scoreable input
2. clear active-note state
3. preserve the current exercise
4. show non-destructive reconnect UI
5. resume after reconnection

## 10. Keyboard range discovery

MIDI device metadata usually does not guarantee physical key count/range. During onboarding allow:

- auto-detect from observed minimum/maximum over calibration
- manual choices: 25/49/61/73/76/88 key common ranges
- custom low/high MIDI note

Exercise generation must respect the configured playable range.

## 11. Latency telemetry

In debug builds log:

- raw MIDI timestamp
- normalization timestamp
- evaluator receipt timestamp
- UI feedback timestamp

This allows measurement of app processing latency separately from human response time.

## 12. MIDI simulator

Implement a fake `MidiInputSource` that can feed event scripts into the app and instrument tests. The entire app must be testable without a physical keyboard in CI.

```kotlin
interface MidiInputSource {
    val connectionState: StateFlow<MidiConnectionState>
    val events: Flow<MidiEvent>
}
```

Production and fake sources implement the same interface.


---

<!-- SOURCE: 06_PERFORMANCE_EVALUATION_AND_SCORING.md -->

# Performance Evaluation and Scoring

## 1. Separate validity from score

The evaluator first determines whether an answer satisfies the musical requirement. Scoring is secondary.

```kotlin
data class EvaluationResult(
    val verdict: Verdict,
    val matched: Set<MidiNote>,
    val missing: Set<ExpectedTone>,
    val extra: Set<MidiNote>,
    val semanticErrors: List<PerformanceError>,
    val timing: TimingEvaluation?,
    val metrics: PerformanceMetrics,
    val explanation: FeedbackModel
)
```

## 2. Verdicts

```text
CORRECT
CORRECT_WITH_ACCEPTED_VARIATION
PARTIAL
INCORRECT
NO_ATTEMPT
ABORTED_DEVICE_LOSS
```

## 3. Pitch-class exercises

For “play any Cmaj7”:

- expected pitch classes: C E G B
- any octave accepted
- duplicates optionally accepted
- bass may or may not matter
- extra notes handled by policy

Do not compare raw MIDI lists.

## 4. Exact voicing exercises

For a displayed exact voicing:

- require exact MIDI pitches unless an explicit octave-equivalence policy exists
- compare bass and top note
- preserve duplicate voices

A set loses duplicate information. Use a multiset/count map.

## 5. Chord-policy matching

Example rootless G13 target:

```text
required: B, F, E
optional: A, D
root G: allowed but not required (or explicitly excluded depending on lesson)
fifth D: optional
bass: no G requirement
range: C3..C5
```

Evaluation is degree-aware, not based on a hard-coded note list.

## 6. Temporal evaluation

Measure independently:

- response latency: target armed -> first intended note
- onset spread: first chord note -> last chord note
- beat error: onset vs target beat
- duration error for reading
- sequence transition time

Do not conflate response speed with rhythmic accuracy.

## 7. False-positive prevention

Never auto-submit on the first note of a chord.

A candidate is submitted when one of these occurs:

- onset aggregation stabilizes
- user releases after a coherent chord
- beat boundary requires evaluation
- explicit exercise rule triggers evaluation

A brief grace period may allow correction of an accidental neighboring key before final verdict in practice mode. In challenge/timed mode, accidental notes can count immediately if the policy says so.

## 8. Semantic error classification

```kotlin
sealed interface PerformanceError {
    data class WrongRoot(...): PerformanceError
    data class WrongQuality(...): PerformanceError
    data class MissingDegree(...): PerformanceError
    data class WrongAlteration(...): PerformanceError
    data class WrongBass(...): PerformanceError
    data class WrongTopNote(...): PerformanceError
    data class ExtraTone(...): PerformanceError
    data class RegisterViolation(...): PerformanceError
    data class OnsetSpreadViolation(...): PerformanceError
    data class RhythmViolation(...): PerformanceError
}
```

Order explanations by educational importance, not arbitrary collection order.

## 9. Mastery update

Maintain per-skill evidence.

Suggested state:

```kotlin
data class SkillMastery(
    val skillId: SkillId,
    val estimate: Double,       // 0..1
    val attempts: Int,
    val successfulAttempts: Int,
    val recentWeightedAccuracy: Double,
    val medianResponseMs: Long?,
    val lastPracticedAt: Instant?,
    val nextReviewAt: Instant?,
    val errorHistogram: Map<ErrorClass, Int>
)
```

Use a transparent deterministic update algorithm first. Do not add opaque ML.

Example correctness evidence weight:

```text
independent correct: 1.0
correct with reduced hint: 0.8
correct after hint: 0.5
incorrect: 0.0
```

Recent independent performances should matter more than old guided performances.

## 10. Score display

The player-facing app should emphasize:

- accuracy
- response time trend
- coverage
- concept mastery
- specific recurring errors

A numerical score can exist for game feedback, but it must not obscure the musical diagnosis.

## 11. Voice-leading score

Possible normalized components:

```text
validHarmony          hard requirement
movementCost          lower is better
maxLeapPenalty        lower is better
commonToneReward      higher is better
voiceCrossingPenalty  configurable
melodyConstraint      hard/soft depending exercise
registerPenalty       hard/soft depending exercise
```

Display why one valid solution was more efficient than another.


---

<!-- SOURCE: 07_EAR_TRAINING_ENGINE.md -->

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


---

<!-- SOURCE: 08_SIGHT_READING_ENGINE.md -->

# Sight Reading Engine

## 1. Scope

The app needs a training notation renderer, not a general-purpose publishing engraver.

V1 notation requirements:

- treble clef
- bass clef
- grand staff
- ledger lines
- key signatures
- time signatures
- noteheads/stems
- accidentals
- rests
- dots
- ties
- beams for common subdivisions
- barlines
- chord symbols above staff
- optional fingering numbers
- playhead / current-beat indication

## 2. Full-Kotlin rendering

Render with Compose Canvas and a bundled SMuFL-compatible music font where glyphs materially simplify notation. Keep layout geometry in Kotlin.

Do not embed a WebView notation engine for v1 unless the custom renderer proves inadequate to the defined scope.

## 3. Notation domain model

```kotlin
data class ScorePhrase(
    val key: KeyContext,
    val meter: TimeSignature,
    val tempoBpm: Int,
    val measures: List<Measure>
)

data class Measure(val events: List<ScoreEvent>)

sealed interface ScoreEvent {
    val onset: RationalBeat
    val duration: RationalBeat
    data class Note(...): ScoreEvent
    data class Chord(...): ScoreEvent
    data class Rest(...): ScoreEvent
}
```

Use rational musical duration internally. Do not represent eighth notes as accumulated floating-point seconds.

## 4. Reading modes

### Static flash

Show one note/chord. Player plays it without a moving clock.

### Measure reading

Show 1–4 measures. Count in, then evaluate in time.

### Continuous stream

Notation moves or playhead advances while new material is generated ahead.

### Lead-sheet reading

Melody notation plus chord symbols. Advanced mode assigns melody to right hand and voicing/comping response to left hand.

### Chord-symbol sight reading

No staff notes required. Chord symbols advance on a timeline and the player comps them in time.

## 5. Rhythm evaluation

Use beat-domain expected onsets converted through a monotonic clock.

Configurable tolerance windows by difficulty, for example:

```text
Learn: ±180 ms
Practice: ±100 ms
Challenge: ±60 ms
```

These are initial tuning values, not fixed laws.

Score pitch and rhythm separately so the player can see whether the problem was reading pitch or timing.

## 6. Adaptive generation

Parameters:

- hand
- clef
- pitch range
- interval leap maximum
- rhythmic vocabulary
- accidental density
- key signature pool
- chord density
- polyphony
- tempo
- phrase length

Generation rules must prevent impossible or pedagogically nonsensical passages.

## 7. Chordal sight reading progression

1. single notes
2. two-note intervals
3. root-position triads
4. inverted triads
5. seventh chords
6. mixed block chords
7. independent two-hand voicings
8. melody + chord symbol
9. voice-led chord progression

## 8. Scrolling strategy

Prefer stable notation with a moving playhead for early versions. Constantly scrolling the staff adds motion and layout complexity that may impair reading.

Continuous scrolling can be enabled as a later presentation option after timing/evaluation is reliable.


---

<!-- SOURCE: 09_AUDIO_SAMPLER_ENGINE.md -->

# Audio Sampler Engine

## 1. Requirements

The internal audio engine is needed for ear-training stimuli, demonstrations, metronome cues, and optional playback of target voicings.

It is not the source of truth for the player's answer; MIDI is.

## 2. Full-Kotlin constraint

V1 must not require C++/NDK. Implement the sampler around Android `AudioTrack` with a Kotlin mixer.

If a future measurement proves the Kotlin implementation cannot meet required latency/voice count on the target hardware, document the evidence before proposing a native engine.

## 3. Sample architecture

Instrument preset:

```kotlin
data class InstrumentPreset(
    val id: InstrumentId,
    val zones: List<SampleZone>,
    val gainDb: Float,
    val maxVoices: Int,
    val pedalBehavior: PedalBehavior
)

data class SampleZone(
    val rootMidi: Int,
    val minMidi: Int,
    val maxMidi: Int,
    val velocityMin: Int,
    val velocityMax: Int,
    val sampleAsset: AssetId,
    val loop: LoopSpec? = null
)
```

## 4. Quality tiers

V1 should support at least:

- acoustic piano
- Rhodes-style electric piano
- Wurlitzer-style electric piano

The software architecture must permit additional organs/synths later.

Use legally distributable samples. Store license metadata with each bank.

## 5. Realism features in priority order

1. multiple sampled pitches or intelligently bounded pitch shifting
2. velocity layers
3. natural release tails
4. sustain pedal handling
5. per-note gain normalization
6. round-robin variants where available
7. sympathetic/pedal resonance as a later enhancement
8. user EQ as later enhancement

Do not hide poor samples under heavy reverb.

## 6. Mixer

Mixer loop responsibilities:

- active voice list
- sample interpolation/resampling
- envelope
- per-voice gain
- stereo pan if needed
- summed float buffer
- limiter/clamp
- conversion to target PCM format
- write to `AudioTrack`

Avoid allocation inside the real-time render loop.

## 7. Preloading

Pre-decode frequently used sample zones to PCM before a timed exercise begins. Ear-training playback must not stall on file decode.

Use an LRU memory policy for large banks.

## 8. Humanization

For demonstration audio only, optional small deterministic velocity/onset variation may reduce machine-like playback. The seed must be stored with the exercise.

Never humanize timed reference clicks or anything used as a timing ground truth.

## 9. External keyboard audio

The player may hear their physical keyboard directly. The app should therefore allow:

- internal target audio on/off
- internal echo of incoming MIDI on/off
- metronome volume
- stimulus volume

Default incoming-MIDI echo should be off to avoid doubled notes when the physical keyboard has local sound enabled.


---

<!-- SOURCE: 10_ANDROID_ARCHITECTURE.md -->

# Android Architecture

## 1. Architecture goals

- testable music logic
- replaceable MIDI source
- deterministic exercise generation
- lifecycle-safe sessions
- offline-first
- tablet/adaptive UI
- minimal coupling between game presentation and domain rules

## 2. Module graph

```text
app
 ├─ feature:home
 ├─ feature:campaign
 ├─ feature:chordgate
 ├─ feature:eartraining
 ├─ feature:sightreading
 ├─ feature:progression
 ├─ feature:voicelab
 └─ feature:settings
       ↓
core:designsystem   core:data
       ↓              ↓
core:music   core:midi   core:audio
       ↓
core:testing (test helpers only)
```

Feature modules may depend on core modules. Core modules must not depend on features.

## 3. State flow

Follow unidirectional data flow.

```text
UI event
  -> ViewModel intent
  -> use case/domain operation
  -> repository/session engine
  -> StateFlow<UiState>
  -> Compose
```

One-off events should be modeled carefully; avoid global event buses.

## 4. Exercise session engine

Build a reusable engine independent of screen type.

```kotlin
interface ExerciseSessionEngine {
    val state: StateFlow<ExerciseSessionState>
    suspend fun start(config: SessionConfig)
    suspend fun submit(attempt: PerformanceAttempt)
    suspend fun requestHint()
    suspend fun replayStimulus()
    suspend fun skip(reason: SkipReason)
    suspend fun stop()
}
```

The chord, ear, and reading features may have specialized wrappers, but session lifecycle should not be duplicated.

## 5. Presentation model

`ExerciseInstance` is domain-rich; the UI consumes `ExercisePresentationModel` produced by a mapper.

This mapper determines what assistance channels are visible without altering the underlying answer.

## 6. Dependency injection

Use a lightweight DI approach appropriate to project scale. Hilt is acceptable if used consistently. Manual DI is also acceptable if it remains explicit and testable.

Do not introduce a service locator hidden behind global singletons.

## 7. Navigation

Use Navigation 3 and typed destinations.

Conceptual routes:

```text
Home
Campaign(regionId?)
GateIntro(gateId)
Exercise(sessionId)
GateResult(sessionId)
QuickPractice
EarTrainingSetup
SightReadingSetup
VoiceLab
Progress
Settings
MidiSetup
```

Do not pass large domain objects through navigation. Pass IDs and retrieve state from repositories/session owners.

## 8. Adaptive UI

Use window-size/adaptive APIs. Although the target is a Galaxy tablet, do not assume one exact resolution or orientation.

Landscape target layout can use:

- navigation rail or compact sidebar
- primary task pane
- secondary information pane
- persistent MIDI status

Portrait can collapse secondary information below or into a sheet.

## 9. Lifecycle

A running exercise must survive normal configuration/window changes without losing its current target.

When app goes background:

- pause timed exercise
- persist resumable session snapshot if safe
- keep no stale MIDI note state

On resume:

- verify MIDI device state
- require re-arm/count-in for timed tasks

## 10. Threading

- UI state mutations: Main dispatcher
- database/content IO: IO dispatcher
- exercise/theory calculations: Default when nontrivial
- MIDI callback: normalize quickly and emit without heavy work
- audio render: dedicated high-priority audio thread controlled by sampler

Never parse curriculum JSON or run database queries in a MIDI callback.

## 11. Error handling

Use typed errors:

```text
MidiUnavailable
MidiDisconnected
ContentInvalid
AudioAssetMissing
SessionCorrupt
DatabaseFailure
UnsupportedDevice
```

User messages should be actionable; logs retain technical detail.

## 12. Logging

Debug logs may include MIDI events and exercise IDs. Release logs must not dump massive continuous MIDI streams by default.

Provide an optional diagnostic export containing:

- app/build version
- device info
- MIDI endpoint info
- session seed
- exercise definition ID
- evaluator result

No account or cloud backend is needed for this.


---

<!-- SOURCE: 11_DATA_MODEL_AND_PERSISTENCE.md -->

# Data Model and Persistence

## 1. Storage split

### Bundled/static content

Version-controlled JSON/Kotlin resources:

- chord formula catalog
- curriculum graph
- gate definitions
- exercise policies
- instrument metadata

### Room database

Mutable learning data:

- profiles
- skill mastery
- attempts
- gate completion
- review schedule
- sessions
- unlocked content

### DataStore

Preferences:

- MIDI device preference
- keyboard range
- assistance defaults
- audio levels
- notation preferences
- accidental preference
- UI settings

## 2. Core entities

Suggested Room entities:

```text
ProfileEntity
SkillMasteryEntity
GateProgressEntity
AttemptEntity
SessionEntity
ReviewItemEntity
UnlockEntity
```

Avoid storing every derived metric if it can be reliably recomputed; cache only where useful.

## 3. Attempt record

```kotlin
data class AttemptRecord(
    val id: UUID,
    val sessionId: UUID,
    val exerciseDefinitionId: String,
    val exerciseSeed: Long,
    val skillIds: List<String>,
    val startedAt: Instant,
    val firstInputAt: Instant?,
    val completedAt: Instant?,
    val verdict: Verdict,
    val assistanceUsed: AssistanceSnapshot,
    val expectedSnapshotJson: String,
    val performedSnapshotJson: String,
    val semanticErrorsJson: String
)
```

Keep snapshots sufficiently complete to reproduce bugs even if content definitions later change.

## 4. Content versioning

Every static content pack has a semantic version and schema version.

On app update:

- retain old attempt history
- migrate IDs through explicit alias tables if definitions are renamed
- never silently reinterpret old attempts against changed exercise rules

## 5. Curriculum file example

See `content/examples/curriculum.sample.json`.

## 6. Exercise policy example

See `content/examples/exercise_policy.sample.json`.

## 7. Backup/export

V1 should include local JSON export/import of learning progress if practical. It is useful for a personal app and avoids requiring an account backend.

Export must include schema version.


---

<!-- SOURCE: 12_UI_UX_AND_FIGMA_HANDOFF.md -->

# UI/UX and Figma Handoff

## 1. Purpose of this document

This file defines interface behavior and component requirements without prematurely locking the final visual language. The actual polished interface will be designed in Figma after the coding specification is accepted.

## 2. Design intent

The app should feel like a purpose-built tablet music game, not a settings-heavy educational utility.

Primary visual priorities:

1. target chord/task is immediately legible
2. keyboard and notation remain large enough to read at playing distance
3. feedback is visible without covering the keyboard/staff
4. MIDI connection state is always discoverable
5. game progression feels spatial and consequential
6. touch controls are large and sparse

## 3. Figma-to-code pipeline

After this pack:

1. create Figma design file
2. establish Android tablet frame(s)
3. create design tokens
4. build reusable components
5. design Home + Campaign first
6. design core Exercise shell
7. design chord variants
8. design Ear Training variants
9. design Sight Reading variants
10. design setup/result/progress/settings screens
11. review on actual tablet aspect ratio
12. map Figma components to Compose components
13. implement through shared design-system module
14. compare screenshots against Figma

## 4. Required design tokens

Do not scatter literal values across Compose.

Tokens:

- background levels
- surface levels
- primary/secondary accents
- success/warning/error/neutral feedback
- text hierarchy
- staff/notation colors
- piano white/black/highlight states
- gate locked/available/mastered states
- spacing scale
- corner radii
- elevation/shadow treatment
- typography scale
- animation durations/easing

## 5. Core reusable components

Figma and Compose should both define:

```text
AppShell
TopStatusBar
MidiStatusChip
NavigationRail
PrimaryButton
SecondaryButton
IconButton
SegmentedControl
DifficultySlider
AssistanceIndicator
GateCard
GateNode
ProgressMeter
SkillBadge
ExerciseHeader
ChordSymbolDisplay
RomanNumeralDisplay
NoteNameStrip
PianoKeyboard
StaffView
FeedbackPanel
CountdownOverlay
MetronomeIndicator
ResultCard
FilterChip
BottomSheet
Dialog
```

## 6. Exercise screen composition

The exercise screen is a shell with slots:

```text
┌──────────────────────────────────────────────────────────┐
│ gate/session info                     MIDI / tempo / menu │
├──────────────────────┬───────────────────────────────────┤
│                      │ optional theory/info              │
│ PRIMARY TASK AREA    │ panel                             │
│ chord / staff / ear  │                                   │
│                      │                                   │
├──────────────────────┴───────────────────────────────────┤
│                 PIANO / PERFORMANCE VIEW                 │
├──────────────────────────────────────────────────────────┤
│ feedback / controls / next                               │
└──────────────────────────────────────────────────────────┘
```

Actual proportions should be designed in Figma for the target tablet.

## 7. Piano component states

Each key must support independent layered state:

- inactive
- target tone
- required tone
- optional tone
- currently physically held
- sustained
- correct performed tone
- incorrect extra tone
- missing tone after evaluation

Do not encode these states only by color. Shape/outline/marker differences should be available.

## 8. MIDI status

States:

- disconnected
- connecting
- connected + device name
- connection error

During a gate, disconnect should produce a clear overlay/banner without destroying session state.

## 9. Feedback motion

Feedback should be fast and restrained:

- correct: short confirmation pulse/opening gate motion
- incorrect: identify exact keys/notes; no long blocking animation
- gate completion: richer animation may occur after scoring is complete

Input must never be ignored just because a decorative animation is running unless the session is explicitly between attempts.

## 10. Accessibility

- scalable text
- sufficient contrast
- non-color correctness cues
- TalkBack labels for controls and progress
- haptics optional
- reduced-motion mode
- left/right-handed layout preferences where useful

## 11. Figma implementation rule

Once the final Figma design exists, update this pack with:

- Figma file key
- component/node mapping
- screenshot baselines
- design token export
- Compose component mapping

Do not rebuild domain architecture to fit a visual mockup. The design layer must consume existing state contracts.


---

<!-- SOURCE: 13_SCREEN_BEHAVIOR_SPEC.md -->

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


---

<!-- SOURCE: 14_TESTING_AND_QUALITY.md -->

# Testing and Quality

## 1. Testing priorities

The costliest failures are not crashes; they are musically incorrect judgments. Testing therefore prioritizes domain correctness and MIDI behavior.

## 2. Test pyramid

### Pure JVM unit tests

Target heavily:

- chord parser
- spelling
- chord formula generation
- inversion
- transposition
- voicing policies
- progression generation
- voice-leading metrics
- scoring
- mastery update
- deterministic random generation

### Android unit/instrumented tests

- Room migrations
- DataStore settings
- lifecycle state retention
- fake MIDI integration
- audio engine startup/stop

### Compose UI tests

- state-to-screen mapping
- MIDI disconnect banner
- exercise transitions
- accessibility labels
- gate state display

### Physical-device tests

Mandatory before release:

- actual target Samsung tablet
- at least one class-compliant USB MIDI keyboard
- sustain pedal
- USB unplug/replug
- screen resize/multi-window if supported
- Bluetooth/headphone audio optional path

## 3. Golden theory fixtures

Create a human-reviewed fixture set with hundreds of known cases.

Example:

```json
{
  "symbol": "Db7b9",
  "expectedDegrees": ["1","3","5","b7","b9"],
  "expectedSpellings": ["Db","F","Ab","Cb","Ebb"]
}
```

Fixtures should intentionally contain awkward spellings to catch pitch-class-only logic.

## 4. Property tests

Examples:

- transposing a pitch-class collection by 12 semitones preserves it
- transpose by `n`, then `-n`, returns equivalent structure
- inversion preserves chord pitch-class multiset
- generated voicing stays inside requested range
- required tones are never omitted by generator

## 5. MIDI scripted tests

Scripts should model:

- perfectly simultaneous chord
- 80 ms hand spread
- 250 ms rolled chord
- accidental neighbor note immediately released
- sustain leftovers
- duplicate note-on
- disconnect mid-chord
- reconnect before next trial
- fast repeated chord

Each script has an expected normalized `PerformanceAttempt`.

## 6. Timing tests

Never rely only on wall-clock sleeps in tests. Inject a monotonic clock/test scheduler into evaluators and session engines.

## 7. Content validation

Add a build-time validator that fails on:

- duplicate gate IDs
- unknown prerequisites
- campaign cycles
- unknown skill IDs
- invalid chord formula references
- impossible completion rules
- exercise policy with no generatable candidates

## 8. Performance budgets

Initial targets on the real tablet:

- MIDI event -> updated key visualization: perceptually immediate; target under ~1 display frame plus scheduling where possible
- candidate chord evaluation: <10 ms for normal chord tasks
- exercise generation: <50 ms typical
- no audio glitches during simple ear-training playback
- no dropped Compose frames during normal MIDI bursts

Measure rather than assume.

## 9. Definition of done for every feature

A feature is not done until:

- behavior implemented
- unit tests added
- fake MIDI path works if relevant
- process death/state restoration considered
- accessibility semantics added
- error state implemented
- no hard-coded user-facing theory strings where structured data should be used
- documentation updated


---

<!-- SOURCE: 15_IMPLEMENTATION_PHASES.md -->

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


---

<!-- SOURCE: 16_AGENT_EXECUTION_PROTOCOL.md -->

# Coding Agent Execution Protocol

## 1. Agent behavior

The coding agent should treat this pack as the source of truth. When documents disagree, precedence is:

1. explicit newer user instruction
2. `AGENTS.md`
3. `18_ACCEPTANCE_CRITERIA.md`
4. subsystem specification
5. general product specification

## 2. Before each phase

Agent must:

1. inspect repository status
2. read relevant spec files
3. inspect existing tests and architecture
4. identify exact files/modules to change
5. state phase acceptance criteria internally/in task log
6. avoid unrelated refactors

## 3. During implementation

Rules:

- maintain buildability
- write tests with domain code
- prefer small composable types over giant manager classes
- do not copy theory rules into UI
- do not hard-code campaign unlock logic in composables
- do not bypass MIDI abstraction to make one screen work
- do not introduce network dependency without explicit scope change
- do not introduce C++/NDK under the “full Kotlin” project constraint

## 4. Phase completion check

Run at minimum:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Add module-specific/instrumented checks as available.

Then verify:

- acceptance criteria
- no TODO that blocks phase behavior
- no known failing tests hidden/disabled
- docs changed if contracts changed

## 5. Git protocol

Recommended:

```text
phase/<number>-<short-name>
```

Commit in coherent units. Each phase finishes with a PR/merge boundary.

Do not keep 10 phases unmerged in parallel branches. Later phases are allowed to depend only on merged earlier work.

## 6. When uncertain about music theory

Do not invent behavior. Add a failing/pending fixture and surface the exact ambiguity for review. Music-theory assumptions must be encoded explicitly in policy/content, not guessed in evaluator code.

## 7. When a dependency breaks

Prefer:

1. stable official API
2. minimal wrapper
3. documented workaround

Do not replace core architecture with a large third-party framework to fix a narrow issue.

## 8. Figma phase

Before final UI implementation, retrieve the actual Figma design context and component/tokens. Do not approximate from prose once a Figma source of truth exists.

## 9. Handoff after every phase

Write/update `docs/IMPLEMENTATION_STATUS.md` with:

```text
Phase:
Commit:
Implemented:
Tests:
Manual verification:
Known limitations:
Next phase prerequisites:
```

This prevents context-window loss between coding-agent sessions.


---

<!-- SOURCE: 17_BUILD_CI_INSTALL_RELEASE.md -->

# Build, CI, Install, and Release

## 1. Build variants

At minimum:

- `debug`
- `release`

Optional later:

- `benchmark`

Debug can expose MIDI diagnostics and seeded exercise controls that release hides behind Diagnostics.

## 2. GitHub Actions

CI on pull request and main:

1. checkout
2. set up JDK required by chosen AGP
3. cache Gradle
4. run unit tests
5. lint
6. assemble debug
7. upload APK artifact

Add instrumented emulator jobs after the base pipeline is stable.

## 3. Secrets

No secrets are required for normal debug builds.

Release signing secrets belong in GitHub encrypted secrets or a secure local signing setup, never committed.

## 4. Tablet installation during development

Enable Developer Options + USB debugging on the tablet.

Typical flow:

```text
adb devices
./gradlew installDebug
```

or install the CI-produced debug APK manually.

Note: the MIDI keyboard occupies a USB port. A powered USB-C hub may be useful when simultaneous debugging/charging/MIDI is required. Wireless ADB is another option for development.

## 5. Release packaging

For personal sideloading, a signed APK is sufficient.

For Play distribution, produce an Android App Bundle and comply with the target API requirement current at submission time.

## 6. Versioning

Use semantic app versions and an independent content schema version.

Example:

```text
app version: 1.3.0
content version: 2.1.0
content schema: 4
```

## 7. Crash/analytics policy

No analytics backend is required for v1. If diagnostics are later added, keep them optional and avoid transmitting raw performance history by default.


---

<!-- SOURCE: 18_ACCEPTANCE_CRITERIA.md -->

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


---

<!-- SOURCE: 19_BACKLOG_AFTER_V1.md -->

# Backlog After V1

These ideas are intentionally excluded from the critical path but the architecture should not make them impossible.

## Music

- standards-specific practice packs
- user-imported lead sheets
- reharmonization challenges
- melody harmonization
- comping rhythm vocabulary
- transcription notebook
- MIDI file import
- custom chord dictionaries
- Barry Harris dedicated world
- bebop scale/diminished-sixth relationships

## Input

- Bluetooth MIDI
- MIDI 2.0/UMP-specific improvements
- multi-device input
- external sustain/expression mapping

## Audio

- richer Rhodes/Wurlitzer banks
- organ engine
- amp/cab effects
- EQ
- room/reverb presets
- sympathetic resonance

## Game

- procedural challenge runs
- daily generated drill without streak pressure
- boss gates combining multiple skills
- alternate campaign themes

## Data

- optional cloud backup
- multi-device sync
- shareable practice presets

## Intelligence

If adaptive algorithms are later added, begin with explainable statistical selection from mastery/error data. Do not put an LLM in the real-time correctness path.


---

<!-- SOURCE: 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md -->

# Bootstrap and Module Contracts

## 1. Bootstrap order

Create the repository in this order. Do not create all feature implementations at once.

### Step 1 — Root Gradle files

Create:

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
```

Declare modules only after their directories exist.

### Step 2 — `app`

Responsibilities:

- Android application entry point
- application theme wiring
- top-level dependency graph
- Navigation 3 host
- global lifecycle coordination

The app module must not contain chord-theory implementation.

### Step 3 — `core:music`

Pure Kotlin library. No Android dependency unless an unavoidable platform type leaks in; prefer not to allow any.

Public surface:

```kotlin
interface ChordParser {
    fun parse(text: String, context: ParseContext = ParseContext()): ParseResult<ChordSpec>
}

interface ChordRealizer {
    fun chordTones(spec: ChordSpec): List<SpelledPitchClass>
    fun generateVoicings(spec: ChordSpec, policy: VoicingPolicy): List<Voicing>
}

interface PerformanceEvaluator {
    fun evaluate(requirement: ExerciseRequirement, attempt: PerformanceAttempt): EvaluationResult
}

interface ExerciseGenerator {
    fun generate(policy: ExercisePolicy, seed: Long, context: GenerationContext): ExerciseInstance
}
```

### Step 4 — `core:midi`

Android library because production MIDI source uses Android platform APIs.

Public surface:

```kotlin
interface MidiInputSource {
    val connectionState: StateFlow<MidiConnectionState>
    val events: Flow<MidiEvent>
    suspend fun start()
    suspend fun stop()
}

interface PerformanceCapture {
    val state: StateFlow<CaptureState>
    val attempts: Flow<PerformanceAttempt>
    fun arm(policy: CapturePolicy)
    fun cancel()
}
```

### Step 5 — `core:data`

Repositories:

```kotlin
interface ProgressRepository {
    fun observeGateProgress(): Flow<List<GateProgress>>
    fun observeSkillMastery(skillId: SkillId): Flow<SkillMastery?>
    suspend fun recordAttempt(record: AttemptRecord)
    suspend fun updateMastery(update: MasteryUpdate)
}

interface ContentRepository {
    suspend fun curriculum(): Curriculum
    suspend fun gate(id: GateId): GateDefinition
    suspend fun exercisePolicy(id: ExercisePolicyId): ExercisePolicy
}
```

### Step 6 — `core:audio`

Public surface:

```kotlin
interface InstrumentPlayer {
    suspend fun load(preset: InstrumentId)
    fun noteOn(note: MidiNote, velocity: Int)
    fun noteOff(note: MidiNote)
    fun sustain(down: Boolean)
    fun allNotesOff()
}

interface StimulusPlayer {
    suspend fun play(stimulus: AudioStimulus)
    fun stop()
}
```

### Step 7 — `core:designsystem`

Before Figma:

- token types
- placeholder theme
- reusable component contracts

After Figma:

- replace token values
- refine component implementations
- preserve feature APIs

### Step 8 — features

Add only as phases require them.

## 2. Domain/session contracts

### `ExerciseInstance`

```kotlin
data class ExerciseInstance(
    val id: ExerciseInstanceId,
    val definitionId: ExerciseDefinitionId,
    val seed: Long,
    val skillIds: Set<SkillId>,
    val requirement: ExerciseRequirement,
    val presentation: PresentationSpec,
    val stimulus: AudioStimulus? = null,
    val timing: ExerciseTiming? = null,
    val explanation: ExplanationContent? = null
)
```

### `PerformanceAttempt`

```kotlin
data class PerformanceAttempt(
    val startedAtNanos: Long,
    val completedAtNanos: Long,
    val noteEvents: List<NormalizedNoteEvent>,
    val finalEffectiveNotes: List<MidiNote>,
    val onsetSpreadNanos: Long?,
    val sustainUsed: Boolean
)
```

Do not throw away event history after reducing to final notes. Rhythm and accidental-note diagnosis need it.

### `ExerciseSessionState`

```kotlin
sealed interface ExerciseSessionState {
    data object Idle : ExerciseSessionState
    data class Loading(val gateId: GateId?) : ExerciseSessionState
    data class Presenting(val exercise: ExercisePresentationModel) : ExerciseSessionState
    data class Armed(val exercise: ExercisePresentationModel) : ExerciseSessionState
    data class Capturing(val live: LivePerformanceState) : ExerciseSessionState
    data class Feedback(val result: EvaluationResult, val nextAvailable: Boolean) : ExerciseSessionState
    data class Paused(val reason: PauseReason) : ExerciseSessionState
    data class Completed(val summary: SessionSummary) : ExerciseSessionState
}
```

## 3. Clock and randomness injection

Never call `System.currentTimeMillis()` or `Random.Default` deep inside domain logic.

```kotlin
interface MonotonicClock { fun nowNanos(): Long }
interface WallClock { fun now(): Instant }
interface SeededRandomFactory { fun create(seed: Long): RandomSource }
```

Tests supply deterministic implementations.

## 4. Serialization boundary

Persist IDs and versioned DTOs, not arbitrary polymorphic runtime objects without a migration strategy.

Keep separate:

```text
Domain model
Persistence entity
Content DTO
UI model
```

Use explicit mappers.

## 5. Compose contracts

Feature routes should expose one state and intents rather than a large list of callbacks.

Conceptual pattern:

```kotlin
@Composable
fun ChordGateRoute(viewModel: ChordGateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChordGateScreen(state = state, onIntent = viewModel::onIntent)
}
```

`ChordGateScreen` must be preview/testable with fake state and no actual MIDI device.


---

<!-- SOURCE: 21_CONTENT_AUTHORING_GUIDE.md -->

# Content Authoring Guide

## 1. Rule

Curriculum content should be data-driven wherever the musical behavior can be expressed using existing domain types. Adding a new lesson must not normally require a new screen or evaluator.

## 2. Separate concepts

A gate definition answers:

- what competency is being tested?
- what prerequisites exist?
- how is mastery determined?

An exercise policy answers:

- what material may be generated?
- what presentation is shown?
- what answer policy is applied?

A chord formula answers:

- what harmonic degrees define the sonority?

Do not merge these three concerns into one giant JSON object.

## 3. Gate authoring checklist

For every gate define:

1. stable ID
2. player-facing title
3. learning objective
4. prerequisite gates
5. skill IDs
6. exercise policy
7. preview/demo content
8. mastery rule
9. remediation mapping
10. unlocks

## 4. Exercise policy checklist

Define:

- root/key pool
- chord formula pool
- inversion pool
- voicing family
- register constraints
- harmonic context
- assistance profile
- onset policy
- answer policy
- session sampling weights

## 5. Difficulty progression pattern

Prefer staged variation:

```text
Stage 1: fixed root + visible notes
Stage 2: several roots + visible chord symbol
Stage 3: all roots + symbol only
Stage 4: mixed inversions
Stage 5: timed/contextual
Stage 6: ear or functional cue
```

Do not introduce five new variables simultaneously unless the gate is explicitly an integration challenge.

## 6. Naming IDs

Use stable semantic IDs:

```text
region.seventh_harmony
skill.dom7.build
skill.dom7.inversion
policy.dom7.inversions.all_roots
gate.dom7.inversions
```

Do not use presentation order such as `level_17` as the only identity.

## 7. Theory text

Instruction copy should explain the exact lesson and avoid declaring context-dependent jazz practices as universal laws.

Bad:

> Never play the fifth in a dominant chord.

Better:

> In this rootless voicing exercise, omit the fifth so the guide tones and tensions fit the assigned register.

## 8. Human review

Before shipping a curriculum region:

- generate at least 100 sample exercises from each policy
- inspect root/key coverage
- inspect enharmonic spellings
- inspect register feasibility
- play representative tasks on the actual keyboard
- verify remediation messages

## 9. Content validation command

Create a Gradle task such as:

```text
./gradlew validateHarmonyContent
```

It must run in CI and fail on invalid references or impossible content.


---

<!-- SOURCE: 22_MANUAL_DEVICE_TEST_PLAN.md -->

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


---

<!-- SOURCE: AGENTS.md -->

# AGENTS.md — Harmony Gates

## Mission

Build a native Android tablet jazz-harmony game in Kotlin/Jetpack Compose with a USB MIDI keyboard as the primary controller.

## Hard constraints

- Kotlin application code.
- Compose UI.
- Music correctness lives in `core:music`.
- MIDI access goes through `MidiInputSource`.
- No microphone chord recognition for scoring.
- No network requirement for core play.
- No C++/NDK in the baseline implementation.
- Do not change musical behavior merely to simplify UI.
- Do not implement final aesthetics before the approved Figma phase; use a clean placeholder design system first.

## Required workflow

Before changing code, read the relevant numbered documents in this pack.

For every phase:

1. inspect repo and current status
2. implement the smallest coherent phase slice
3. add/update tests
4. run `./gradlew test`
5. run `./gradlew lint`
6. run `./gradlew assembleDebug`
7. update `docs/IMPLEMENTATION_STATUS.md`
8. commit/PR before starting dependent phase

## Architecture boundaries

`core:music` must be pure Kotlin/JVM where possible.

UI may ask the domain layer:

```text
What should I display?
Was this attempt valid?
What error occurred?
What skill evidence changed?
```

UI must not answer these itself.

## No silent theory assumptions

If a chord/voicing rule is ambiguous, represent the ambiguity in `VoicingPolicy` or content. Do not add a global universal rule unless the specification explicitly requires it.

## Test rule

Any fixed bug involving chord identity, spelling, MIDI capture, or scoring requires a regression test reproducing the failure.


---

<!-- SOURCE: PROMPT_TO_CODING_AGENT.md -->

# Prompt to Coding Agent

You are implementing **Harmony Gates**, a native Android tablet jazz-harmony learning game. The complete specification is in this repository's coder document pack.

Read `00_README_START_HERE.md`, `AGENTS.md`, and all numbered documents before making architectural changes. Treat those documents as the product specification.

The app is Kotlin + Jetpack Compose. A USB MIDI keyboard is the primary controller. Do not use microphone transcription for performance correctness. Keep the music-theory domain independent of Android UI. Keep MIDI behind an interface with a fake source for tests. The project must work offline. The baseline implementation must remain full Kotlin and must not introduce C++/NDK.

Work strictly in the phases defined by `15_IMPLEMENTATION_PHASES.md`. Do not skip directly to polished UI. The required order is music-domain correctness, MIDI, performance capture/evaluation, a minimal real-keyboard vertical slice, persistence/mastery, campaign, assistance, audio/ear training, sight reading, jazz voicing/progression systems, advanced harmony, then final Figma-driven UI implementation.

For each phase:

1. inspect the existing repository and preserve working code
2. state/record the phase acceptance criteria
3. implement only what is needed for that phase plus unavoidable foundations
4. write tests as functionality is added
5. run tests, lint, and debug assembly
6. update `docs/IMPLEMENTATION_STATUS.md`
7. commit the phase in coherent commits
8. merge/land the phase before beginning work that depends on it

Do not invent unsupported music-theory behavior. When a rule is context-dependent, encode it as an exercise/voicing policy. Preserve enharmonic spelling and degree identity; do not reduce all harmony to pitch-class sets.

The final interface will be designed in Figma. Before that design exists, build clean functional Compose components with design tokens and stable state contracts. Once Figma is supplied, implement the actual visual system from Figma without rewriting the music/MIDI/game architecture.

Start with Phase 0 and Phase 1 only unless explicitly instructed to continue farther.


---

<!-- SOURCE: REFERENCES.md -->

# Technical References Checked for This Plan

These are implementation anchors, not substitutes for the product specification.

## Android platform

- Android 17 SDK setup documentation: API level 37 / Build Tools 37.
- Android Developers Compose August 2026 release notes: current Compose line compiles against API 37 and requires a recent AGP.
- Google Play target API requirements: starting August 31, 2026, new apps/updates generally must target Android 16 / API 36 or higher.

## MIDI

- Android `MidiManager` is available from API 23.
- `PackageManager.FEATURE_MIDI` identifies full `android.media.midi` support.
- Modern `MidiManager` supports transport-specific enumeration and callbacks for MIDI 1.0 byte-stream and Universal MIDI Packet transports.
- Android's USB host APIs exist separately, but class-compliant MIDI should normally be consumed through the MIDI service instead of manually parsing USB MIDI.

## Architecture/UI

- Android recommends Jetpack Compose for modern UI.
- Navigation 3 is Compose-centric and supports adaptive navigation patterns.
- Material 3 Adaptive provides window-size/posture-aware layout building blocks.
- Room is the recommended abstraction over raw SQLite for structured local app data.
- DataStore is appropriate for small preference/typed settings data.

Before pinning dependency versions, check the current official AndroidX stable releases and keep all versions in the version catalog.
