# Sight Reading Landing Screen Handoff

## Canonical reference

Figma file: `https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5`

Canonical wired frame: `Harmony Gates / Sight Reading / CANON / WIRED 01`

Figma node: `102:2`

Machine-readable contract: `interface/maps/sight-reading.json`

Production asset path: `interface/assets/sight-reading-landing.png`

Reference size: `1536 × 1024` landscape.

## Visual direction

The canonical Sight Reading landing environment is the approved loft/music-study room overlooking Cinque Terre. It is intentionally different from the Home and Chord Gates rooms while preserving Harmony Gates materials, typography, polish, and cinematic music-study atmosphere.

The image is a visual environment reference, not application state.

## Required production layering

1. static environment plate
2. runtime-changing progress/path/status UI
3. semantic hit/interaction layer
4. Kotlin/Compose navigation, state, curriculum and MIDI behavior

The production background must not permanently encode current user progress or current-path selection.

## Runtime fields

Runtime-driven values include:

- completion percentage
- lessons completed
- mastery
- best accuracy
- streak
- current focus
- current sight-reading path
- path completed/current/locked states
- current-path highlight
- Continue Path label
- MIDI connected/disconnected state
- MIDI device name

Example values in Figma are mock state only.

## Canonical path row

The landing page currently presents eight sight-reading curriculum areas:

1. Rhythm Foundation
2. Interval Awareness
3. Treble Staff Fluency
4. Bass Staff Fluency
5. Hands Together
6. Key Navigation
7. Dynamics & Expression
8. Advanced Repertoire

The row is deliberately symmetric at the 1536 × 1024 reference size:

- card width: 140 px
- card height: 270 px
- gap: 16 px
- left margin: 152 px
- right margin: 152 px
- y position: 591 px

If the layout changes, remap the JSON rather than reusing stale coordinates.

## Semantic actions

The following regions are interactive and are defined precisely in `interface/maps/sight-reading.json`:

- Home
- Gates
- Practice
- Journal
- Profile
- Settings
- View Progress
- Path 01 through Path 08
- Current Focus
- Continue Current Path
- MIDI Status

Locked path cards remain visible but must not enter lesson flow until curriculum state unlocks them.

## Navigation behavior

`Continue Current Path` enters the user's current unfinished Sight Reading lesson/path.

Selecting an available path opens that path's landing/lesson sequence. Selecting a completed path permits review. Selecting a locked path does not bypass curriculum unlock rules.

`View Progress` opens detailed Sight Reading progress/history.

`Current Focus` opens the current recommended focus or its relevant practice sequence.

`MIDI Status` opens connection/status controls and reflects connect, disconnect, and reconnect state.

## Implementation note

Figma defines the approved visual target and semantic layer positions. `interface/maps/sight-reading.json` is the implementation contract. The existing Kotlin/Compose application remains responsible for real navigation, persistence, curriculum state, exercise generation, progress calculation, and MIDI behavior.
