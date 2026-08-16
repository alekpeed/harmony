# UI/UX and Figma Handoff

## 1. Purpose of this document

This file defines interface behavior and component requirements without prematurely locking the final visual language. The actual polished interface will be designed in Figma after the coding specification is accepted.

## 2. Design intent

The app should feel like a purpose-built tablet music game, not a settings-heavy educational utility.

Primary visual priorities:

1. target chord/task is immediately legible
2. keyboard and notation remain large enough to read at playing distance
3. feedback is visible without covering the keyboard/staff
4. MIDI connection state is always discoverable
5. game progression feels spatial and consequential
6. touch controls are large and sparse

## 3. Figma-to-code pipeline

After this pack:

1. create Figma design file
2. establish Android tablet frame(s)
3. create design tokens
4. build reusable components
5. design Home + Campaign first
6. design core Exercise shell
7. design chord variants
8. design Ear Training variants
9. design Sight Reading variants
10. design setup/result/progress/settings screens
11. review on actual tablet aspect ratio
12. map Figma components to Compose components
13. implement through shared design-system module
14. compare screenshots against Figma

## 4. Required design tokens

Do not scatter literal values across Compose.

Tokens:

- background levels
- surface levels
- primary/secondary accents
- success/warning/error/neutral feedback
- text hierarchy
- staff/notation colors
- piano white/black/highlight states
- gate locked/available/mastered states
- spacing scale
- corner radii
- elevation/shadow treatment
- typography scale
- animation durations/easing

## 5. Core reusable components

Figma and Compose should both define:

```text
AppShell
TopStatusBar
MidiStatusChip
NavigationRail
PrimaryButton
SecondaryButton
IconButton
SegmentedControl
DifficultySlider
AssistanceIndicator
GateCard
GateNode
ProgressMeter
SkillBadge
ExerciseHeader
ChordSymbolDisplay
RomanNumeralDisplay
NoteNameStrip
PianoKeyboard
StaffView
FeedbackPanel
CountdownOverlay
MetronomeIndicator
ResultCard
FilterChip
BottomSheet
Dialog
```

## 6. Exercise screen composition

The exercise screen is a shell with slots:

```text
┌──────────────────────────────────────────────────────────┐
│ gate/session info                     MIDI / tempo / menu │
├──────────────────────┬───────────────────────────────────┤
│                      │ optional theory/info              │
│ PRIMARY TASK AREA    │ panel                             │
│ chord / staff / ear  │                                   │
│                      │                                   │
├──────────────────────┴───────────────────────────────────┤
│                 PIANO / PERFORMANCE VIEW                 │
├──────────────────────────────────────────────────────────┤
│ feedback / controls / next                               │
└──────────────────────────────────────────────────────────┘
```

Actual proportions should be designed in Figma for the target tablet.

## 7. Piano component states

Each key must support independent layered state:

- inactive
- target tone
- required tone
- optional tone
- currently physically held
- sustained
- correct performed tone
- incorrect extra tone
- missing tone after evaluation

Do not encode these states only by color. Shape/outline/marker differences should be available.

## 8. MIDI status

States:

- disconnected
- connecting
- connected + device name
- connection error

During a gate, disconnect should produce a clear overlay/banner without destroying session state.

## 9. Feedback motion

Feedback should be fast and restrained:

- correct: short confirmation pulse/opening gate motion
- incorrect: identify exact keys/notes; no long blocking animation
- gate completion: richer animation may occur after scoring is complete

Input must never be ignored just because a decorative animation is running unless the session is explicitly between attempts.

## 10. Accessibility

- scalable text
- sufficient contrast
- non-color correctness cues
- TalkBack labels for controls and progress
- haptics optional
- reduced-motion mode
- left/right-handed layout preferences where useful

## 11. Figma implementation rule

Once the final Figma design exists, update this pack with:

- Figma file key
- component/node mapping
- screenshot baselines
- design token export
- Compose component mapping

Do not rebuild domain architecture to fit a visual mockup. The design layer must consume existing state contracts.
