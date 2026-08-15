# Progression Run Implementation Handoff

Status: visual/motion concept approved for implementation.

Approved Figma start frame:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=77-2

Runtime map:
`interface/maps/progression-run.json`

## 1. Core architecture

Progression Run must be implemented as a layered dynamic screen, not as a static screenshot with fake interaction.

Layer order:

1. Clean static jazz-room/background plate
2. Independent progression path
3. Reusable chord orb + pedestal components
4. Runtime text/state treatment inside those orb components
5. Touch/accessibility/interaction layer

The clean background is intentionally free of the progression track. Never reintroduce baked orbs, baked chord labels, baked pedestal rings, or a baked glowing path into the background image.

## 2. Figma is a motion reference, not the state model

The four current Figma demo frames are successive visual snapshots used to demonstrate movement with Smart Animate.

Do not reproduce them as four Compose screens or four fixed progression states.

Production must use a single screen and a single reusable track renderer driven by runtime progression data.

The relevant state is conceptually:

```kotlin
ProgressionRunState(
    progression = Progression(...),
    activeChordIndex = Int,
    isAnimatingAdvance = Boolean,
    queuedAdvance = Boolean
)
```

Exact naming may follow the existing architecture.

## 3. Progression length

There is no four-chord limit and no visible-slot-based exercise limit.

The runtime progression is an ordered `ChordEvent` list of arbitrary practical length. It can represent, among other sources:
- curriculum exercises
- ii-V-I and other functional drills
- complete standards/forms
- Barry Harris/diminished material
- substitutions and reharmonizations
- generated exercises
- custom user sequences

The current landscape composition targets eight visible perspective slots at once:
- 1 previous/history slot
- 1 active play-point slot
- 6 upcoming slots

Events outside the visible window remain in the progression model. Visual orb instances are recycled as the window advances.

## 4. Orb component

An orb is a generic reusable renderer. It is never permanently associated with a specific chord.

Minimum runtime inputs should support:

```kotlin
ChordOrbUiModel(
    eventId = String,
    chordSymbol = String,
    functionLabel = String?,
    state = Previous | Active | Upcoming | Correct | IncorrectOrIncomplete,
    relativeSlot = Int,
    assistance = ...
)
```

The app may add inversion, voicing, guide-tone, root/bass, or assistance indicators without changing the component architecture.

The renderer should derive size, position, pedestal treatment, glow and text scale from the assigned perspective slot rather than from the identity of the chord.

## 5. Track geometry

The approved 1536 × 1024 reference currently uses these approximate slot centers and diameters:

| Relative index | Role | Center | Diameter |
|---|---|---:|---:|
| -1 | previous | 380,570 | 100 |
| 0 | active/play point | 565,548 | 102 |
| +1 | upcoming | 758,501 | 86 |
| +2 | upcoming | 914,457 | 80 |
| +3 | upcoming | 1027,404 | 72 |
| +4 | upcoming | 1138,373 | 66 |
| +5 | upcoming | 1245,351 | 66 |
| +6 | upcoming | 1348,335 | 60 |

These are design-space reference measurements, not hard-coded physical-device coordinates.

Compose should place the track responsively relative to the approved content area/aspect ratio. Preserve the visual perspective and path relationship when adapting to the Galaxy tablet resolution.

## 6. Advance behavior

One accepted progression advance performs one logical increment of `activeChordIndex` and one visual movement cycle.

During that cycle:
- the previous chord exits/recedes from the near side
- the active chord becomes previous/history
- each upcoming chord moves one perspective position toward the player
- the next chord reaches the active play point
- active emphasis transfers to that chord
- the next off-screen `ChordEvent`, if one exists, is assigned to/recycled into the far-upcoming orb
- the room/background does not move

Current visual timing reference: approximately 650 ms.

Current easing reference:
`cubic-bezier(0.22, 1, 0.36, 1)`

Do not permit overlapping advance animations. If a valid new advance arrives during an active transition, queue at most one advance unless the implementation's state machine provides an equivalent safe solution.

## 7. MIDI advancement

Primary gameplay flow:

`MIDI note state -> chord recognition/matcher -> evaluate active ChordEvent -> accepted -> advanceTrack()`

Rules:
- evaluate the actual `ChordEvent` at `activeChordIndex`
- do not judge correctness from the string displayed inside the orb
- wrong or incomplete input does not advance
- a sustained/held accepted voicing must not produce repeated advances
- require a new qualifying note state before the next acceptance
- respect root, bass, inversion, omission and allowed-voicing policies
- manual Next, when enabled, should call the same progression-advance path rather than maintaining separate track state

## 8. ChordEvent contract

The current handoff expects support for at least:
- chord symbol
- harmonic/function label
- root pitch class
- quality
- extensions
- alterations
- optional slash/bass pitch class
- duration
- root policy
- bass policy
- inversion policy and allowed inversions
- required and optional pitch classes
- allowed omissions
- acceptable voicing-family IDs
- exact-mode flag

Use existing domain models where equivalent types already exist. Do not create duplicate theory models merely to match these property names.

## 9. End-of-progression behavior

Non-looping run:
- completing the final event ends the musical run
- perform the final visual completion transition
- show results/next-step UI according to the existing game flow

Looping run:
- continue assigning beginning events to the far-upcoming slot and maintain seamless movement

## 10. Visual validation

Implementation is not complete merely because the logic works.

Validate on the intended landscape Android tablet target against the Figma reference for:
- orb centers and perspective
- relative orb scaling
- pedestal alignment
- active glow intensity
- line/path continuity
- text readability
- transition duration and easing
- absence of any duplicated/static track beneath the animated layer

The clean-background separation exists specifically so the track can animate without double imagery or alignment ghosts.

## 11. Source-of-truth hierarchy

For Progression Run:

`Figma = approved appearance and motion language`

`interface/maps/progression-run.json = current runtime/rendering contract`

`existing Kotlin/Compose/domain code = production behavior and architecture`

If these conflict because implementation evolved, preserve correct domain behavior and update the visual integration locally rather than rebuilding the app around the Figma prototype.
