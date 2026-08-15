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
