Harmony Gates Ear Training Control Asset Package

WHAT THIS IS
The original moving-parts sheet has been split into individual transparent PNG assets that can be positioned and animated independently in the Android/Compose implementation.

IMPORTANT
- moving_parts_master.png is reference/source only.
- Do not render the master sheet directly in the app.
- Use the individual PNGs under buttons/, knobs/, toggles/, sliders/, indicators/, faders/, icons/, waveforms/, and selectors/.
- Pitch names are NOT baked into the reusable note button. Use selectors/note_button_active_blank.png and selectors/note_button_idle_blank.png, then render C, C♯, D♭, etc. as live Compose text.
- selectors/note_labels.json contains the complete chromatic pitch-class labels and enharmonic spellings.
- A control housing that does not move can remain in the control-face/layout artwork. Only independently animated or state-swapped pieces need separate runtime assets.

RECOMMENDED RUNTIME STACK
background
setup shell
ear-training section layout
control-face artwork
individual active-state / moving-part PNGs
dynamic Compose text + values
interaction hit targets

SOURCE COORDINATE SYSTEM
1536 x 1024
