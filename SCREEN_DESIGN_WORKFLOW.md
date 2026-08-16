# Harmony Gates Screen Design Workflow

This is the required workflow for creating a new Harmony Gates screen or substantially rebuilding an existing one. Read this together with `HARMONY_GATES_DESIGN_LANGUAGE.md` and `interface/README.md`.

## Critical rule: a missing JSON file is not a blocker

A screen map under `interface/maps/` is a handoff artifact produced by the design process. New screens may not have one yet.

If `interface/maps/<screen>.json` does not exist, do **not** stop and do **not** conclude that the screen cannot be designed. Instead, create the screen contract as part of the work using the process below.

Do not invent product behavior casually. First derive requirements from existing project sources. If a genuine product choice remains unresolved after inspection, identify only that unresolved choice and ask for it. Do not use the absence of a finalized JSON as a reason to refuse the design task.

## Source inspection order for a new screen

Before designing:

1. Read root `README.md`.
2. Read `HARMONY_GATES_DESIGN_LANGUAGE.md`.
3. Read `interface/README.md`.
4. Inspect `interface/maps/` for any existing map for the target screen.
5. Inspect relevant product/specification documentation in the repository, including coder-pack/spec documents when available.
6. Inspect existing Kotlin/Compose implementation for the target module when available. Existing implemented behavior is strong evidence of required controls and states.
7. Inspect approved Figma Home node `28:2` and Progression Run node `77:2` for visual language, not for copying their layout.
8. Inspect any other approved screen or handoff that shares relevant controls or navigation behavior.

## Phase 1: build the functional inventory

Before drawing the screen, write a concise inventory of everything the screen must support.

At minimum identify, when applicable:

- screen identity/title
- global navigation inherited from Harmony Gates
- primary exercise modes
- selectors and configuration controls
- play/replay/next/previous controls
- MIDI status and MIDI-dependent actions
- answer/response controls
- assistance/hint controls
- difficulty controls
- exercise prompt/state display
- score/progress/accuracy data if the product requires it
- sound/instrument controls if applicable
- settings that affect exercise generation
- empty/loading/error/completed states
- any dynamic visual elements that cannot be baked into a static background

Classify each item as:

- `required`: directly supported by existing product docs/code
- `inherited`: shared Harmony Gates navigation or established system behavior
- `proposed`: a design/UI choice introduced to express already-required behavior
- `unresolved`: a real product decision not supported by current sources

Do not silently drop a required item because it is difficult to fit visually.

## Phase 2: create a draft screen contract when no JSON exists

If no screen map exists, create `interface/maps/<screen>.json` as a **draft contract** before or alongside initial Figma work.

The draft JSON should contain semantic structure and requirements, but it must not fabricate Figma node IDs or pixel hit coordinates that do not exist yet.

Recommended draft fields:

```json
{
  "schemaVersion": 1,
  "screen": "ear-trainer",
  "status": "draft-pre-figma",
  "designSize": {"width": 1536, "height": 1024},
  "requirementsSource": [],
  "requiredFeatures": [],
  "dynamicLayers": [],
  "controls": [
    {
      "id": "example_control",
      "action": "semantic_action_id",
      "requirementStatus": "required | inherited | proposed | unresolved",
      "figmaLayer": null,
      "boundsPx": null
    }
  ],
  "figma": {
    "fileKey": "cD5gqV8A5gk94NVuGeJXg5",
    "nodeId": null,
    "frameName": null
  }
}
```

A draft contract prevents visual design from losing functionality while explicitly distinguishing known product requirements from design proposals.

## Phase 3: propose the environment

After the functional inventory is established, propose a new environment that belongs to the Harmony Gates universe.

The environment may be highly creative. It may change the room, architecture, furniture, instruments, lighting, spatial organization, and visual metaphor. It must not change the required feature inventory.

Before generating/building, state:

1. one short paragraph describing the environment
2. the required controls/features that will appear
3. any genuinely proposed UI treatment
4. any unresolved product decision, if one remains

Do not stall merely because a JSON is still in draft state.

## Phase 4: build the Figma screen

Create the screen in the existing Harmony Gates Figma file:

`https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5`

Rules:

- native design target is `1536 × 1024` landscape
- preserve the Harmony Gates visual constitution
- retain every required/inherited function from the inventory
- use editable Figma layers for controls and dynamic elements
- do not bake interactive/dynamic elements into background art when they must change at runtime
- name interactive layers consistently as `HIT / <Control Name>` when a separate hit region is needed
- name dynamic visual layers semantically so implementation can identify them
- keep touch targets suitable for Android tablet use
- do not use raw visual novelty as a reason to obscure controls

If background artwork is used, treat it as a clean plate wherever runtime UI must move or change independently.

## Phase 5: wire the Figma prototype where useful

Figma prototype behavior is a demonstration/reference, not the production app engine.

Use Figma interactions to demonstrate important transitions, state changes, or motion when useful. Do not imply that Figma clicks represent real MIDI recognition or app runtime logic.

For reusable/dynamic behavior, build enough prototype states to communicate motion and appearance. Do not duplicate production architecture merely because multiple Figma frames are used to demonstrate an animation.

## Phase 6: finalize the JSON after the screen is structurally approved

Once the Figma screen structure is approved, update `interface/maps/<screen>.json` from draft to approved/current handoff status.

Replace draft placeholders with actual Figma data:

- approved frame node ID and frame name
- relevant background/artwork node IDs
- named dynamic layer IDs/names when important
- every finalized `HIT / ...` region
- actual `boundsPx` measured from the approved 1536 × 1024 frame
- normalized bounds if the screen uses coordinate-mapped hit regions
- semantic actions
- dynamic rendering contracts
- motion/state behavior
- production-vs-Figma distinctions

Never reuse hit coordinates from an older/deleted/rebuilt frame.

## Phase 7: implementation handoff

The final relationship is:

`approved Figma = visual and motion target`

`interface/maps/<screen>.json = semantic/layout/runtime handoff contract`

`existing Kotlin/Compose = production behavior`

The implementation agent should build responsive Compose UI and connect semantic actions to existing state, MIDI, curriculum, audio, navigation, and game logic. It should not hard-code device-specific x/y coordinates or duplicate Figma demo frames as separate production screens.

## Phase 8: validation

Before declaring a screen complete:

1. Confirm every required control/feature from the inventory is present.
2. Confirm proposed controls do not replace required behavior.
3. Confirm the approved Figma node is recorded in JSON.
4. Confirm dynamic elements that must move/change are not baked into static artwork.
5. Confirm interaction regions are mapped from the current approved frame only.
6. Confirm the JSON and Figma agree.
7. During implementation, compare a tablet screenshot against Figma and test all mapped actions.

## Practical instruction for AI design sessions

When asked to design a screen that lacks a JSON map, the correct response is not `there is no JSON, so I cannot proceed`.

The correct response is:

1. inspect product/code sources
2. derive the functional inventory
3. create/update the draft JSON contract
4. propose the environment
5. build the Figma screen
6. obtain/establish approval
7. finalize the JSON with the actual Figma structure
8. hand off to Kotlin/Compose

Only stop for user clarification when a real product decision remains unresolved after source inspection and cannot reasonably be treated as a proposed UI expression of already-required behavior.
