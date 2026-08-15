# Harmony Interface

This directory is the handoff point for visual interface assets for the Harmony Android tablet app.

The app implementation is already underway. Do not treat this directory as an instruction to restart or redesign the application. Its purpose is to tell the implementation agent where approved visual assets live and where they should be used.

## Approved home-screen visual

Figma file:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5

Approved frame:
`Harmony Gates / FINAL Approved Home`

Direct node:
https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5?node-id=28-2

Native artwork size:
`1536 × 1024` landscape

Use this approved frame as the home-screen visual asset/reference when the current app reaches the UI integration phase. Preserve existing application architecture, navigation, state handling, MIDI logic, game logic, and other work already completed unless a visual integration requires a small local adjustment.

## Interaction placement reference

The Figma frame also contains transparent named regions showing where interactive controls correspond to the artwork. These are placement references for wiring the existing app behavior to the visual interface, not a request to rebuild the app around absolute coordinates.

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

## Intended workflow

1. Continue building the app normally.
2. When implementing or replacing the home-screen presentation layer, use the approved Figma screen from this directory as the visual target.
3. Use the named Figma hit regions to understand where the existing actions belong visually.
4. Keep interaction/layout logic responsive in Jetpack Compose rather than baking in one-device pixel coordinates.
5. Additional approved screens, backgrounds, illustrations, exported assets, and placement notes will be added under `interface/` as they are created.

In short: this directory is where the finished visual assets and their placement references will live. The coder should integrate them into the app already being built, not start the project over.