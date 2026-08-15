# Harmony Interface

This directory is the handoff point for visual interface assets for the Harmony Android tablet app.

The app implementation is already underway. Do not treat this directory as an instruction to restart or redesign the application. Its purpose is to tell the implementation agent where approved visual assets live and how their visible controls map to existing app actions.

## Figma source

Figma file:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5

Native design size for the current tablet screens:
`1536 × 1024` landscape

## Screen asset + JSON rule

Every approved screen must have a machine-readable JSON interaction map under `interface/maps/`.

Each map contains:
- the Figma frame name and node ID
- the 1536 × 1024 design coordinate system
- the associated visual asset when one exists
- every named `HIT / ...` Figma interaction region
- exact pixel bounds from Figma
- normalized 0–1 bounds for responsive Compose placement
- a semantic action ID for wiring into the app

The coder should use the semantic action and the normalized bounds. The pixel bounds are the Figma source measurements, not fixed Android device coordinates.

When an approved screen is rendered with aspect-fit or another transform, calculate the actual rendered artwork rectangle first, then scale and offset the normalized regions into that rectangle. Do not position hit targets against the physical device screen independently of the artwork.

Current maps:
- `interface/maps/home.json`
- `interface/maps/progression-run.json`

## Home screen

Approved frame:
`Harmony Gates / FINAL Approved Home`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=28-2

Visual asset location:
`interface/harmony-home-approved.jpg`

Interaction map:
`interface/maps/home.json`

## Progression Run

Approved frame:
`Harmony Gates / Progression Run`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=45-2

Interaction map:
`interface/maps/progression-run.json`

The Progression Run map includes the builder/presets/history tabs, three route chord cells, all 12 key controls, tempo, bars per chord, voicing, assistance, bass, metronome, and Start Run.

## Intended workflow

1. Continue building the app normally.
2. When a screen receives approved artwork/design, use that approved visual as its presentation target.
3. Read that screen's JSON map and connect each semantic action to the existing navigation/state/game logic.
4. Scale the normalized interaction regions with the rendered artwork/screen rectangle.
5. Preserve existing app architecture, MIDI logic, game logic, state handling, and navigation unless visual integration requires a small local adjustment.
6. Every new approved screen added to Figma should receive a corresponding `interface/maps/<screen>.json` file before implementation handoff.

In short: the image/design says what the screen looks like; the JSON says where the interactive regions are and what each one means; the existing Kotlin/Compose code supplies the behavior.
