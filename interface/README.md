# Harmony Interface

This directory is the handoff point for approved visual interface assets for the Harmony Android tablet app.

The app implementation is already underway. Do not treat this directory as an instruction to restart or redesign the application. Its purpose is to tell the implementation agent where approved visual assets live, how dynamic visual layers are constructed, and how they connect to existing app behavior.

For the mandatory workflow used to create a brand-new screen, especially when no JSON exists yet, read root `SCREEN_DESIGN_WORKFLOW.md`.

## Figma source

Figma file:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5

Native design size for the current tablet screens:
`1536 × 1024` landscape

## Screen asset + JSON rule

Every approved screen must have a machine-readable JSON handoff under `interface/maps/`.

A missing JSON for a **new** screen is not a blocker. The JSON is created as part of the screen-design process:

1. derive the required functionality from existing product/spec/code sources
2. create a draft pre-Figma contract containing semantic controls/features but no fabricated Figma IDs or coordinates
3. build and approve the Figma screen
4. finalize the JSON using the actual approved Figma frame, layer names, hit regions, dynamic-rendering rules, and semantic actions

Do not reply that a new screen cannot be designed merely because its JSON does not yet exist. See `SCREEN_DESIGN_WORKFLOW.md` for the full procedure.

Depending on the screen, a map may describe:
- the approved Figma frame and node ID
- the 1536 × 1024 design coordinate system
- the associated visual/background asset
- named interaction regions when those regions have been finalized
- semantic actions for wiring into existing app behavior
- dynamic rendering rules for screens that cannot be represented by one static image
- motion/state contracts where Figma is being used as a visual reference
- a requirements inventory and requirement status for controls during draft design work

Do not invent or reuse stale hit coordinates. If a screen has been structurally rebuilt, old interaction-region coordinates are invalid until remapped against the new approved frame.

### Canonization rule

When a screen is approved or materially rewired, finish the handoff in the same task: update its JSON map and all repository reference/instruction documents that identify canonical screens. A Figma frame is not considered fully handed off until future sessions can discover it from the repo without relying on conversation history.

### Draft JSON status

For a screen that has not yet been structurally approved in Figma, use a draft status such as:

`"status": "draft-pre-figma"`

At that stage, semantic requirements may be recorded, but Figma node IDs and `boundsPx` that do not yet exist must remain null/unset. Do not fabricate them.

After Figma approval, replace the placeholders with the actual frame/layer information and current measurements.

Current maps:
- `interface/maps/home.json`
- `interface/maps/progression-run.json`
- `interface/maps/chord-gates.json`
- `interface/maps/sight-reading.json`

## Home screen

Approved frame:
`Harmony Gates / FINAL Approved Home`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=28-2

Interaction map:
`interface/maps/home.json`

## Chord Gates

Canonical wired frame:
`Harmony Gates / Chord Gates / CANON / WIRED 01`

Figma node:
`101:76`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=101-76

Interaction/runtime map:
`interface/maps/chord-gates.json`

Production background asset path:
`interface/assets/chord-gates-canon.png`

Chord Gates uses the same layered implementation rule as the rest of Harmony Gates:

1. static environment plate
2. runtime-changing UI
3. semantic hit/interaction layer
4. Kotlin/Compose behavior and state

Runtime values include the center completion percentage, gates unlocked, lessons completed, mastery, streak, current focus, current gate/selected state, gate lock/completion state, Enter Gate label, MIDI connection state, and MIDI device name. These values must not be baked into the production background image.

Interactive regions include Home, Gates, Practice, Journal, Profile, Settings, View Progress, all eight Gate cards, Current Focus, Enter Current Gate, and MIDI Status. The exact current bounds and Figma node IDs are authoritative in `interface/maps/chord-gates.json`.

The eight Gate cards are laid out symmetrically at the 1536 × 1024 reference size: 140 px wide, 230 px high, 12 px gaps, and 166 px left/right margins. Preserve that alignment unless an explicit redesign replaces it and the JSON is remapped.

## Sight Reading

Canonical wired frame:
`Harmony Gates / Sight Reading / CANON / WIRED 01`

Figma node:
`102:2`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=102-2

Interaction/runtime map:
`interface/maps/sight-reading.json`

Detailed handoff:
`interface/SIGHT_READING_HANDOFF.md`

Production background asset path:
`interface/assets/sight-reading-landing.png`

Sight Reading uses the approved Cinque Terre loft/music-study environment as its canonical landing-page visual. This is intentionally a distinct room from Home and Chord Gates and should not be replaced with a copy of another Harmony Gates environment.

Its production stack is:

1. static environment plate
2. runtime progress/path/status UI
3. semantic hit/interaction layer
4. Kotlin/Compose navigation, state, curriculum, exercise, and MIDI behavior

Runtime values include completion percentage, lessons completed, mastery, best accuracy, streak, current focus, current path, each path's completion/current/locked state, current-path highlight, Continue Path label, MIDI connection state, and MIDI device name. These values must not be treated as permanent raster content.

Interactive regions include Home, Gates, Practice, Journal, Profile, Settings, View Progress, all eight Sight Reading path cards, Current Focus, Continue Current Path, and MIDI Status. Exact current bounds and node IDs are authoritative in `interface/maps/sight-reading.json`.

The eight path cards are laid out symmetrically at the reference size: 140 px wide, 270 px high, 16 px gaps, and equal 152 px left/right margins. Preserve that alignment unless an explicit redesign is approved and the JSON is remapped.

The canonical path sequence is Rhythm Foundation, Interval Awareness, Treble Staff Fluency, Bass Staff Fluency, Hands Together, Key Navigation, Dynamics & Expression, and Advanced Repertoire. Current/locked/completed state is runtime-driven.

## Progression Run

Approved Figma motion-reference frame:
`Harmony Gates / Progression Run / FINAL / DEMO 01`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=77-2

Interaction/runtime map:
`interface/maps/progression-run.json`

Detailed handoff:
`interface/PROGRESSION_RUN_HANDOFF.md`

### Required layering model

Progression Run is no longer a single baked image. The production visual stack is:

1. clean static background plate
2. independent progression path
3. reusable orb/pedestal renderer
4. runtime chord/function labels and visual state
5. interaction layer

The background must never contain static progression orbs, pedestal rings, chord labels, Roman numerals, or a baked glowing progression line.

The purpose of this separation is functional, not merely cosmetic. The track must be able to move continuously while the jazz-room environment remains fixed.

### Figma animation versus production behavior

Figma currently contains four demo frames showing successive track positions. These four frames are only visual animation snapshots.

They do **not** mean:
- the exercise is limited to four chords
- only four chords may appear on screen
- production should contain four duplicate screens
- progression data should be hard-coded from the Figma example

Production uses one parameterized Compose renderer driven by progression data and `activeChordIndex`.

The current landscape target shows approximately eight perspective positions at once: one previous chord, one active chord, and six upcoming chords. The underlying progression may be any practical length, including complete standards and generated/custom exercises. Orbs are recycled as the active index advances.

### Motion reference

The current Figma reference uses approximately 650 ms for one advancement, with a smooth ease-out style curve. On a valid advance:
- every visible chord moves one track position toward the player
- the newly active chord receives the strongest gold emphasis
- the completed chord becomes visually subordinate history
- a new upcoming event enters at the far end
- the room/background remains fixed

Kotlin/Compose may be micro-tuned during device testing, but the approved Figma motion should remain the visual target.

### MIDI contract

The production trigger is not a Figma click. The primary gameplay trigger is successful MIDI recognition of the `ChordEvent` at `activeChordIndex`.

A correct qualifying chord advances the track. Wrong or incomplete input does not advance it. Held notes or sustain must not generate duplicate advances. Manual Next may invoke the same advancement function when the mode allows it.

## Intended workflow

For an already-approved screen:

1. Continue building the existing app architecture normally.
2. Treat approved Figma screens as visual/motion targets, not alternate application architectures.
3. Read the corresponding JSON map before implementing the screen.
4. Build dynamic elements parametrically in Compose rather than copying Figma demo states literally.
5. Connect semantic actions to the existing MIDI, curriculum, game, state and navigation layers.
6. Compare Android tablet screenshots and motion against the approved Figma reference.
7. Preserve existing app architecture unless visual integration requires a small local adjustment.

For a new/unmapped screen:

1. Follow root `SCREEN_DESIGN_WORKFLOW.md`.
2. Derive the full functional inventory from project sources.
3. Create the draft JSON contract.
4. Design/build the Figma screen without losing required functionality.
5. Name interaction and dynamic layers semantically.
6. After structural approval, measure/map the current frame and finalize the JSON.
7. Update canonical-screen references in the repo in the same task.
8. Then hand the approved Figma + finalized JSON to the Kotlin/Compose implementation.

In short: Figma defines the approved appearance and motion language; JSON defines the semantic/layout/runtime contract; the existing Kotlin/Compose app supplies the real behavior.
