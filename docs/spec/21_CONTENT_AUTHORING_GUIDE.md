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
