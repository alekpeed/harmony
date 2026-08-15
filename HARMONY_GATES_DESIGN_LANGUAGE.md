# Harmony Gates Design Language

This file is the visual and structural design constitution for Harmony Gates. Read it before creating, revising, or implementing any major screen.

## Project source of truth

- GitHub: `alekpeed/harmony`
- Figma: `https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5`
- Approved Home: Figma node `28:2`
- Approved Progression Run: Figma node `77:2`
- Interface documentation: `interface/README.md`
- Screen maps: `interface/maps/`

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

Before creating a screen, inspect the relevant repository documentation, Figma references, and screen map and determine every required:

- control
- state
- navigation item
- displayed value
- interaction
- exercise behavior
- MIDI-dependent behavior

The required feature inventory must survive the visual design process intact.

## Source-of-truth priority

When sources differ or a new screen is being designed, use this order:

1. Product/functionality documentation and screen JSON determine what must exist and what it does.
2. Approved Figma screens determine the established Harmony Gates visual quality, atmosphere, materials, density, typography, and world-building language.
3. New creative design ideas determine how a new environment expresses those requirements.

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
2. Read `interface/README.md`.
3. Inspect the relevant file under `interface/maps/` if it exists.
4. Inspect the approved Home and Progression Run Figma screens for visual reference.
5. Identify the complete required control and feature inventory for the target screen.
6. Propose the environment and composition without changing that inventory.
7. Verify that no required control or state has been omitted before considering the screen complete.

For exploratory design work, creative freedom is encouraged after these constraints are satisfied.

## Implementation principle

`Figma / approved artwork = what it looks like`

`interface/maps/*.json = where interactive regions are and what they mean`

`existing Kotlin / Compose architecture = what the app actually does`

Do not restart or replace existing application architecture simply to match a visual mockup. Integrate approved visuals into the existing app and preserve MIDI, game, navigation, state, and curriculum logic unless a documented design change explicitly requires otherwise.

## New-session instruction

Any new design session, coding agent, or AI working on Harmony Gates should read this file before proposing a major screen. If a prompt asks for a new screen without enough functional detail, inspect the repository first rather than inventing a feature set from the visual theme alone.
