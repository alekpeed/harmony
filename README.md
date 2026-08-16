# Harmony Gates

Before designing, implementing, or revising a major screen, read:

- [`HARMONY_GATES_DESIGN_LANGUAGE.md`](HARMONY_GATES_DESIGN_LANGUAGE.md) — visual language, structural rules, approved Figma references, source-of-truth priority, and screen-design preflight.
- [`SCREEN_DESIGN_WORKFLOW.md`](SCREEN_DESIGN_WORKFLOW.md) — mandatory workflow for new screens, including how to derive controls, create a draft JSON when none exists, build/wire Figma, and finalize the JSON handoff.
- [`interface/README.md`](interface/README.md) — interface handoff conventions and implementation workflow.
- [`interface/maps/`](interface/maps/) — machine-readable screen maps and interaction semantics.

Important: the absence of `interface/maps/<screen>.json` is **not** a reason to stop a new-screen design task. For a new screen, follow `SCREEN_DESIGN_WORKFLOW.md`: derive the functional inventory from existing project sources, create the draft contract, build the Figma screen, then finalize its JSON from the approved Figma structure.

## Canonization rule

Whenever a major screen is approved or materially rewired, complete the full handoff in the same task. Do not leave the design only in Figma or only in JSON. Update the corresponding `interface/maps/<screen>.json` and the relevant repository instruction/reference documents so future sessions know which frame is canonical, which values are runtime-driven, which regions are interactive, and which asset path is authoritative.

Primary Figma file:
`https://www.figma.com/design/cD5gqV8A5gk94NVuGeJXg5`

Approved visual references:
- Home: node `28:2`
- Progression Run: node `77:2`
- Chord Gates: node `101:76` (`Harmony Gates / Chord Gates / CANON / WIRED 01`)

Chord Gates machine-readable contract:
`interface/maps/chord-gates.json`

Chord Gates production background asset path:
`interface/assets/chord-gates-canon.png`
