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
