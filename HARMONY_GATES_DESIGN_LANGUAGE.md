# Harmony Gates Design Language

This file is the visual and structural design constitution for Harmony Gates. Read it before creating, revising, or implementing any major screen.

Also read `SCREEN_DESIGN_WORKFLOW.md` for the required new-screen process, including how to create a screen JSON and wire the approved design in Figma.

## Project source of truth

- GitHub: `alekpeed/harmony`
- Figma: `https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5`
- Approved Home: Figma node `28:2`
- Approved Progression Run: Figma node `77:2`
- Canonical Chord Gates: Figma node `101:76`
- Chord Gates map: `interface/maps/chord-gates.json`
- Chord Gates production background: `interface/assets/chord-gates-canon.png`
- Interface documentation: `interface/README.md`
- Screen maps: `interface/maps/`
- New-screen workflow: `SCREEN_DESIGN_WORKFLOW.md`

## Visual language

Harmony Gates is a premium native Android landscape-tablet jazz-training app. It should feel like a coherent physical world rather than a collection of generic app dashboards.

The visual language is:

- sophisticated, cinematic, premium jazz atmosphere
- warm, atmospheric interiors rather than generic digital dashboards
- dark walnut, charcoal, stone, brass/gold, cream typography, and warm amber practical lighting
- realistic architecture, instruments, furniture, materials, and spatial depth
- elegant, restrained, tactile surfaces rather than flat or synthetic UI decoration
- landscape-tablet composition intended to sit above a MIDI keyboard like a music stand or control surface
- each major section may inhabit a different room, studio, stage, library, rehearsal space, listening room, or other location, but all locations must clearly belong to the same Harmony Gates universe
- controls may be integrated creatively into the environment rather than appearing only as floating SaaS-style cards
- strong hierarchy, readable typography, and clear touch targets remain mandatory even when controls are visually integrated into the scene

Avoid:

- neon or RGB gamer lighting
- cyberpunk styling
- sci-fi HUDs
- generic mobile-game visuals
- childish gamification
- glassmorphism overload
- generic white or gray SaaS dashboards
- random decorative complexity that obscures interaction
- visual novelty that changes or removes required product functionality

## Structural rule

A designer or coding agent may be creative about the environment, composition, lighting, architecture, materials, and visual integration of controls.

A designer or coding agent may not silently invent, remove, rename, replace, or omit required controls or product behavior.

Before creating a screen, inspect the relevant repository documentation, Figma references, implementation/spec sources, and screen map when one exists, and determine every required:

- control
- state
- navigation item
- displayed value
- interaction
- exercise behavior
- MIDI-dependent behavior

The required feature inventory must survive the visual design process intact.

### Missing-map rule

A missing `interface/maps/<screen>.json` is **not a blocker** and is not evidence that the screen cannot be designed.

For a new screen, the map is created as part of the design workflow. Follow `SCREEN_DESIGN_WORKFLOW.md`:

1. derive the functional inventory from existing product/spec/code sources
2. create a draft JSON contract without invented Figma IDs or coordinates
3. create the Figma design while preserving the inventory
4. after structural approval, finalize the JSON using the actual Figma frame, layers, interaction regions, runtime rules, and semantic actions

If a genuine product decision remains unresolved after source inspection, identify that specific choice. Do not stop merely because the JSON did not already exist.

### Canonization rule

Whenever a major screen is approved or materially rewired, complete the full handoff in the same task. Update its `interface/maps/<screen>.json` and every relevant canonical-screen/reference document. Future sessions must be able to identify the approved Figma frame, asset path, runtime/static split, and interaction contract from the repository alone. Do not rely on conversation history to establish which design is canonical.

## Source-of-truth priority

When sources differ or a new screen is being designed, use this order:

1. Existing product/functionality documentation and implemented behavior determine what the product must do.
2. A current approved screen JSON, when one exists, is the machine-readable handoff for that approved screen.
3. Approved Figma screens determine the established Harmony Gates visual quality, atmosphere, materials, density, typography, and world-building language.
4. New creative design ideas determine how a new environment expresses those requirements.

For a screen without an existing JSON, do not treat the absent file as higher authority than the product sources. Create the draft/final map as part of the design process.

Do not use a new visual concept as justification for changing product behavior.

## Approved visual references

### Home

Figma node `28:2`

Use it as a reference for:

- overall quality bar
- cinematic warmth
- premium materials
- environmental integration
- cream/gold typography
- navigation language
- visual density and polish

Do not assume future screens must reuse its exact layout.

### Chord Gates

Canonical wired frame:
`Harmony Gates / Chord Gates / CANON / WIRED 01`

Figma node:
`101:76`

Machine-readable contract:
`interface/maps/chord-gates.json`

Production background asset path:
`interface/assets/chord-gates-canon.png`

Chord Gates is the canonical landing screen for the Chord Gates module. Preserve its room/environment concept, gate-path hierarchy, and bottom action/status structure unless the user explicitly approves a redesign.

Its production implementation must remain layered. The static environment is separate from runtime state and semantic hit regions. The center completion percentage, gates unlocked, lessons completed, mastery, streak, current focus, selected/current gate, individual gate completion/lock state, Enter Gate label, MIDI connection state, and MIDI device name are runtime-driven and must not be permanently baked into the production background.

All navigation, View Progress, each of the eight Gate cards, Current Focus, Enter Current Gate, and MIDI Status are semantic interaction targets. Use the current bounds and Figma node references from `interface/maps/chord-gates.json` rather than estimating them from the image.

The eight Gate cards are intentionally aligned as a precise row at the 1536 × 1024 reference size: 140 px card width, 230 px card height, 12 px gaps, and equal 166 px left/right margins.

### Progression Run

Figma node `77:2`

Use it as a reference for:

- standalone room/environment treatment
- dark, warm jazz-studio atmosphere
- realistic instruments and architecture
- independent functional overlays integrated with the room
- motion and interaction that preserve the environmental scene

The Progression Run background is a clean static plate. The track and chord orbs are independent runtime UI and must never be baked back into the background asset.

## Screen-design preflight

Before generating or building a new screen:

1. Read this file.
2. Read `SCREEN_DESIGN_WORKFLOW.md`.
3. Read `interface/README.md`.
4. Inspect the relevant file under `interface/maps/` if it exists.
5. If it does not exist, derive the functional inventory and create a draft screen contract rather than stopping.
6. Inspect the relevant product/spec/code sources.
7. Inspect the approved Home, Chord Gates, and Progression Run Figma screens for visual reference where relevant.
8. Identify the complete required control and feature inventory for the target screen.
9. Propose the environment and composition without changing that inventory.
10. Verify that no required control or state has been omitted before considering the screen complete.
11. If the screen becomes approved/canonical, update the JSON and canonical repository references before ending the task.

For exploratory design work, creative freedom is encouraged after these constraints are satisfied.

## Implementation principle

`Figma / approved artwork = what it looks like`

`interface/maps/*.json = semantic/layout/runtime handoff for approved screens`

`existing Kotlin / Compose architecture = what the app actually does`

Do not restart or replace existing application architecture simply to match a visual mockup. Integrate approved visuals into the existing app and preserve MIDI, game, navigation, state, and curriculum logic unless a documented design change explicitly requires otherwise.

## New-session instruction

Any new design session, coding agent, or AI working on Harmony Gates should read this file and `SCREEN_DESIGN_WORKFLOW.md` before proposing a major screen.

If a prompt asks for a new screen without an existing screen JSON, inspect the repository and create the draft contract as part of the task. Do not reply that design must stop because the map is missing.

If a screen has already been canonized, use the canonical Figma node and corresponding JSON map as the starting point rather than generating a replacement from scratch.
