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
