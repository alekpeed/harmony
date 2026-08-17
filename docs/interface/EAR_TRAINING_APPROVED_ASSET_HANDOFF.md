# Harmony Gates Ear Training — Approved Layered Asset Handoff

This package contains ONLY the approved visual chain from the conversation plus runtime pieces extracted from the approved moving-parts sheet.

## Authoritative approved visual chain

1. `background/ear_training_background_cinque_terre.png`
   - Exact Cinque Terre baseline supplied/approved in the conversation.

2. `setup/ear_training_setup_shell.png`
   - The later clean reusable empty shell.
   - This is NOT the rejected ornate initial mock-up.

3. `setup/ear_training_section_layout.png`
   - Ear Training-specific internal layout generation.

4. `source/ear_training_control_faces_master.png`
   - The exact control-face generation created after the section layout.
   - Source/reference sheet. Runtime should use the individual assets under `runtime/`.

5. `source/ear_training_active_states_master.png`
   - The exact active-state generation from the approved sequence.
   - Source/reference sheet. Runtime active states are separated under `runtime/active_states/`.

6. `source/ear_training_moving_parts_master.png`
   - The exact moving-parts image later shown again in the conversation.
   - Source/reference sheet only. Do NOT render the whole sheet in the app.

7. `training/ear_training_training_bar_shell.png`
   - CLEAN blank training-bar shell.
   - It is extracted from the approved clean section-layout generation.
   - It contains NO baked chord names, question counts, accuracy, streak, MIDI text, or other runtime data.

## Files deliberately NOT included

The earlier ornate mock-ups and conventional overlay concepts are NOT part of this package.

The rejected training bar containing baked runtime values such as `ii7`, `D-7`, `V7`, accuracy indicators, streak, etc. is NOT included.

## Runtime rule

Use image assets for the rich visual hardware. Use Compose/ViewModel state for anything that can change.

Dynamic Compose content includes:
- pitch labels
- chord/interval names
- current/next exercise
- MIDI text/state
- question number
- accuracy
- streak
- timers/counters
- selected options
- changing numeric values

## Pitch buttons

Use:
- `runtime/selectors/note_button_idle_blank.png`
- `runtime/selectors/note_button_active_blank.png`

Then render note names in Compose.

Support:
C, C♯/D♭, D, D♯/E♭, E, F, F♯/G♭, G, G♯/A♭, A, A♯/B♭, B.

## Design coordinates

Full-screen design coordinate system: 1536 × 1024.

The full-screen background/setup layers must use the same transform. The training bar is a standalone transparent strip and should be anchored to its approved bottom position.

## Coder instruction

Inspect the existing Ear Training implementation before wiring controls. Preserve its actual ViewModel/StateFlow/MIDI/exercise behavior. Do not invent product behavior merely because a visual component exists in this library.

Create/update `interface/maps/ear_training.json` using actual final coordinates and the repo's existing map convention.

Do not substitute rejected mock-ups or generic Material/Compose controls for the supplied visual system.
