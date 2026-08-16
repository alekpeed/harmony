# Design System

The Compose side of 12_UI_UX_AND_FIGMA_HANDOFF.md. This is the document §11 asks for — token
export, component mapping, and what still needs the Figma file — written from the code so it can
be checked against it.

Everything visual lives in `core:designsystem`. That module deliberately does **not** depend on
`core:music`: a drawing routine that could reach a chord could make a theory decision, and
non-negotiable rule 3 puts those in one place.

---

## 1. Where the colours came from

The palette is **sampled from the approved artwork**, not chosen in code:

- `interface/harmony_home_approved.jpg`
- `interface/assets/progression-run-background.png`

Both plates are one world: warm near-black grounds around `#0A0908`, brass and amber accents
from `#B07020` up to `#F0C090`, warm off-white text near `#F8F0E8`. A Compose layer drawn over a
painted plate has to be in the plate's palette or it sits beside the artwork rather than on it.

Two groups are **not** in the artwork and are marked provisional in `Tokens.kt`:

| Group | Why it is provisional |
| --- | --- |
| Feedback (success / warning / error / neutral) | Neither approved screen shows a verdict. |
| Piano key surfaces | Neither approved screen shows a keyboard. |

Nothing depends on their exact value. 18_ACCEPTANCE_CRITERIA.md forbids carrying correctness by
colour alone, so every state that uses them also carries a glyph or an outline — which is what
`KeyPaintingTest` checks.

**One palette, not two.** There is no light theme. The approved screens are painted plates in a
dark world; a Compose layer that turned pale in light mode would be drawn on artwork that did
not turn with it. Contrast is met inside the dark palette instead, and `TokenTest` measures it.

---

## 2. Token export

`core/designsystem/.../theme/Tokens.kt`. Read through `HarmonyTheme.colors`, `.spacing`,
`.shapes`, `.motion`, `.typography` — never as a literal at a call site.

### Colour

| Token | Value | Role |
| --- | --- | --- |
| `backgroundBase` | `#0A0908` | The floor. What a painted plate composites onto. |
| `backgroundSunken` | `#050403` | Wells and insets. |
| `surface` | `#1A1813` | Where content sits. |
| `surfaceRaised` | `#23201B` | A panel lifted off the surface. |
| `surfaceOverlay` | `#2C2823` | Sheets and dialogs. |
| `scrim` | `#CC050403` | Under an overlay. |
| `accentPrimary` | `#C98A3C` | Brass. Actionable things. |
| `accentSecondary` | `#F0C090` | Lit amber. Emphasis, and the current position. |
| `onAccent` | `#120E08` | Text on an accent fill. |
| `textPrimary` | `#F8F0E8` | |
| `textSecondary` | `#C0BCB2` | |
| `textTertiary` | `#8F887D` | Large text and disabled states only. |
| `outline` | `#4A4238` | |
| `feedbackSuccess` | `#7FCB9B` | Provisional. |
| `feedbackWarning` | `#E8C25A` | Provisional. |
| `feedbackError` | `#E8837B` | Provisional. |
| `feedbackNeutral` | `#A9B0B6` | Provisional. |
| `staffLine`, `staffLedger` | `#8F887D` | |
| `noteHead`, `clef` | `#F8F0E8` | |
| `accidental` | `#F0C090` | |
| `keyWhite` / `keyWhitePressed` | `#EDE7DC` / `#C9B189` | Provisional. |
| `keyBlack` / `keyBlackPressed` | `#17150F` / `#4A3A22` | Provisional. |
| `keyBorder`, `keyLabel`, `keyTarget` | `#0A0908`, `#2A2620`, `#C98A3C` | Provisional. |
| `gateLocked` | `#6A6156` | |
| `gateAvailable` | `#C98A3C` | |
| `gateInProgress` | `#F0C090` | |
| `gateMastered` | `#7FCB9B` | |

### Spacing

`hairline 1` · `tight 4` · `small 8` · `medium 16` · `large 24` · `section 40` dp.
`minimumTouchTarget 56.dp` — above the platform's 48, because the player is at arm's length from
a propped-up tablet, not holding a phone.

### Shape

Radii `none 0` · `small 4` · `medium 12` · `large 20` · `pill 999` dp.
Elevation `flat 0` · `raised 2` · `floating 8` · `overlay 16` dp.

### Motion

`instant 0` · `quick 120` · `standard 220` · `deliberate 420` · `celebration 900` ms, with
standard, emphasized and exit easing curves.

`HarmonyMotionTokens.reduced()` returns the same set with every duration at zero, so honouring
the system's reduced-motion preference is one substitution rather than an audit of every
animation. `MainActivity` reads `Settings.Global.ANIMATOR_DURATION_SCALE` and passes it in.

Only `celebration` is long, and 12 §9 allows that only after scoring — never while input is
being taken.

### Typography

`chordSymbol 64` · `display 40` · `heading 28` · `title 20` · `body 16` · `label 14` ·
`caption 13` sp. The chord symbol is the largest thing on any screen because it is read from a
keyboard bench.

---

## 3. Component mapping

12 §5 lists the components Figma and Compose should both define. Every name on that list exists:

| 12 §5 name | Compose | File |
| --- | --- | --- |
| `AppShell` | `AppShell` | `component/Shell.kt` |
| `TopStatusBar` | `TopStatusBar` | `component/Shell.kt` |
| `MidiStatusChip` | `MidiStatusChip` | `component/Shell.kt` |
| `NavigationRail` | `NavigationRail` | `component/Shell.kt` |
| `PrimaryButton` | `PrimaryButton` | `component/Controls.kt` |
| `SecondaryButton` | `SecondaryButton` | `component/Controls.kt` |
| `IconButton` | `HarmonyIconButton` | `component/Controls.kt` |
| `SegmentedControl` | `SegmentedControl` | `component/Controls.kt` |
| `DifficultySlider` | `DifficultySlider` | `component/Controls.kt` |
| `AssistanceIndicator` | `AssistanceIndicator` | `component/Exercise.kt` |
| `GateCard` | `GateCard` | `component/Campaign.kt` |
| `GateNode` | `GateNode` | `component/Campaign.kt` |
| `ProgressMeter` | `ProgressMeter` | `component/Campaign.kt` |
| `SkillBadge` | `SkillBadge` | `component/Campaign.kt` |
| `ExerciseHeader` | `ExerciseHeader` | `component/Exercise.kt` |
| `ChordSymbolDisplay` | `ChordSymbolDisplay` | `component/Exercise.kt` |
| `RomanNumeralDisplay` | `RomanNumeralDisplay` | `component/Exercise.kt` |
| `NoteNameStrip` | `NoteNameStrip` | `component/Exercise.kt` |
| `PianoKeyboard` | `PianoKeyboard` | `component/PianoKeyboard.kt` |
| `StaffView` | `NotationStaff` | `notation/NotationStaff.kt` |
| `FeedbackPanel` | `FeedbackPanel` | `component/Exercise.kt` |
| `CountdownOverlay` | `CountdownOverlay` | `component/Shell.kt` |
| `MetronomeIndicator` | `MetronomeIndicator` | `component/Exercise.kt` |
| `ResultCard` | `ResultCard` | `component/Campaign.kt` |
| `FilterChip` | `FilterChip` | `component/Controls.kt` |
| `BottomSheet` | `BottomSheet` | `component/Shell.kt` |
| `Dialog` | `HarmonyDialog` | `component/Shell.kt` |

Three names differ, in each case to avoid colliding with a Material 3 symbol of the same name in
the same file: `IconButton`, `Dialog` and the staff view.

The Figma node ID column is missing on purpose. `interface/README.md` forbids inventing or
reusing node IDs, and the components have not been drawn yet.

---

## 4. Mapped states

### Piano keys — 12 §7

Nine states, and they layer: a key can be a required tone, held, and judged wrong at once. So
`PianoKeyState` is a record of independent facts, not an enum.

```
PianoKeyState(role, held, sustained, verdict)
    role    = INACTIVE | TARGET | REQUIRED | OPTIONAL
    verdict = NONE | CORRECT | EXTRA | MISSING
```

`KeyPaintings.of(state)` maps it to a `KeyPainting(fill, marker, outline)` — **a pure function
with no access to the palette**, which is how "do not encode these states only by color" becomes
structural rather than aspirational.

| State | Fill | Marker | Outline |
| --- | --- | --- | --- |
| inactive | natural | none | none |
| target tone | target | bar | thin |
| required tone | natural | dot | thin |
| optional tone | natural | ring | thin |
| physically held | pressed | none | thick |
| sustained (pedal) | sustained | none | dashed |
| correct performed tone | pressed | tick | thick |
| incorrect extra tone | error | cross | thick |
| missing after evaluation | natural | diamond | thin |

Precedence: verdict outranks held, which outranks role. A required tone played wrong stops
saying "required" — the player has been told the answer, and what they need now is the marker
about what they did.

`KeyPaintingTest` asserts no two of the nine share both a marker and an outline, so the set is
distinguishable with the colour removed.

### MIDI — 12 §8

`MidiPresentation`: `DISCONNECTED ○` · `CONNECTING ◐` · `CONNECTED ●` · `ERROR ⚠`. Each carries
its own glyph, short label and screen-reader description; the connected state names the device.

### Gates

`GatePresentation`: `LOCKED 🔒` · `AVAILABLE ▶` · `IN_PROGRESS ◧` · `MASTERED ★`. Only locked is
unplayable, and locked gates are still drawn — 02 §2 makes the campaign a map, and a map with
the unvisited parts cut out is not one.

### Verdicts

`FeedbackPresentation`: `CORRECT ✓` · `PARTIAL ~` · `INCORRECT ✕` · `NEUTRAL •`.

---

## 5. What the tests check

| Test | What it protects |
| --- | --- |
| `TokenTest` | WCAG contrast for every text-on-surface pair, accent fills, feedback and notation colours; surface levels ordered by luminance; spacing and type scales ordered; reduced motion collapses durations. |
| `KeyPaintingTest` | All nine §7 states representable, distinguishable without colour, layering precedence, and the mapping total over every combination. |
| `StatusPresentationTest` | Every MIDI, gate and verdict state has its own glyph and its own words. |
| `KeyboardLayoutTest` | Black keys land on the right side of the white keys they straddle. |
| `StaffGeometryTest` | Ledger lines, stems and horizontal layout. |
| `ArtworkGeometryTest`, `TrackGeometryTest` | Approved plates map to the 1536 × 1024 design space. |

---

## 6. Still needed from Figma

Phase 12's remaining deliverables are blocked on the design file itself, not on code:

1. **Final Figma screens and components.** Two screens are approved as painted plates (Home,
   Progression Run). The other screens in 13_SCREEN_BEHAVIOR_SPEC.md have not been drawn.
2. **Screenshot comparisons.** There is nothing to compare against until baselines exist. When
   they do, the harness needs a JVM Compose renderer (Robolectric or Paparazzi) since this
   project has no emulator in CI.
3. **Node ID mapping.** Section 3's table gets a Figma column once the components are drawn.
4. **Feedback and piano palettes.** Provisional above; these are the two groups the approved
   artwork does not contain.
5. **Type faces.** The scale is set; the families are not. Both plates use type that has not
   been identified or licensed.
