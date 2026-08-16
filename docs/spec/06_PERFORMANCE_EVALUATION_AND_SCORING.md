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
