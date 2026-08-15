# Harmony Interface

This directory is the handoff point for the Harmony Android tablet interface.

## Approved Figma source of truth

Figma file:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5

Approved home screen frame:
`Harmony Gates / FINAL Approved Home`

Node URL:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=28-2

Native design size:
`1536 × 1024` landscape

## Implementation rule

Treat the approved Figma frame as the visual source of truth for the tablet home screen. Do not substitute a generic reconstruction or redesign it from scratch. Preserve the composition, spacing, imagery, card positions, typography treatment, and overall appearance.

The Figma frame contains named transparent interaction regions over the approved visual. Important hit-target layers include:

- `HIT / Menu`
- `HIT / Nav Home`
- `HIT / Nav Map`
- `HIT / Nav Practice`
- `HIT / Nav Stats`
- `HIT / Nav Library`
- `HIT / Nav Profile`
- `HIT / Nav Settings`
- `HIT / Profile Summary`
- `HIT / Chord Gates`
- `HIT / Ear Trainer`
- `HIT / Sight Reading`
- `HIT / Progression Run`
- `HIT / Voice Leading`
- `HIT / Theory Lab`
- `HIT / Daily Challenge`
- `HIT / My Journey`
- `HIT / Next Gate Card`
- `HIT / Continue`
- `HIT / Streak Summary`

## Android target

Implement natively in Kotlin with Jetpack Compose, landscape-first for Android tablets. Use responsive Compose layout logic rather than hard-coded screen coordinates where practical. For visually anchored regions that correspond directly to the approved artwork, derive interaction bounds from the Figma geometry and scale them consistently with the rendered design.

This directory can be expanded with exported assets, implementation notes, screenshots, and additional approved screens as the interface is developed.
