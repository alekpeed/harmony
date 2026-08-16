# Implementation Status

Handoff record required by 16_AGENT_EXECUTION_PROTOCOL.md §9. Updated at the end of every
phase so a new session can pick up without re-deriving context.

---

## Phase: 13 — Full campaign content

**Commit:** see `git log` for `phase/13-campaign-content`

### Implemented

- **`RomanNumeralParser`** — reads `ii7`, `bII7`, `#ivo7`, `V7/ii`, `i(maj7)` the way a chart
  writes them, so a progression can be authored instead of compiled (D43).
- **`content/progressions/progressions.json`** — eleven progressions covering Region 12's
  mandatory vocabulary: both ii-V-Is, I-vi-ii-V, iii-VI-ii-V, secondary and descending dominants,
  backdoor, tritone sub, twelve-bar jazz blues, minor blues, and the rhythm-changes A section.
- **`ExercisePolicy` activities** — `earTaskFamily`, `progressionId` + `voicingStyle`, and
  `readingMaterial`. The phase 8 and 9 engines finally have content that reaches them (D44).
- **Drop voicings authorable** — a transform family is named on the policy rather than built as
  a recipe, which is what Region 8's gates needed (D45).
- **Eight new regions**: Setting Up (calibration, now the campaign's entry point), Functional
  Harmony, Ear Training, Reading, Drop Voicings, Progressions, Voice Leading, Integrated
  Reading. Twenty-five new gates, twenty-six new policies, all with instruction copy, completion
  rules and unlocks.
- The campaign is now 17 regions and 43 gates, in curriculum order, from a first note on a
  keyboard through to comping a chart.

### Acceptance

- *content validator clean* — `ContentPackTest` passes: no cycles, no unreachable gates, no
  dangling references, no warnings, and all 48 policies generate 100 playable exercises each.
- *complete path from onboarding through advanced region* — a test walks the prerequisite graph
  from `gate.calibration.keys` and asserts every one of the 43 gates is on it, and that the path
  reaches `region.integrated`.

### Tests

587 passing, 0 failing. 19 new: `RomanNumeralParserTest` (12) and seven in `ContentPackTest`.

### What the tests caught

- **`I6/9` was being read as a secondary target.** The slash in a six-nine chord is part of the
  quality; a slash only separates a target when what follows it is a numeral.
- **`i(maj7)` resolved to a major seventh.** A lowercase numeral has already said "minor", so the
  `m`-prefixed alias has to be tried first there.
- **A test expectation was wrong, not the code.** `ii7/V` in C is `Am7` — the ii *of G* — and the
  test had asserted `Em7`.
- **The drop-voicing gates could not be authored at all,** which is how the recipe/transform
  distinction surfaced (D45).

### Known limitations

1. **No screen runs a progression gate, an ear gate or a reading gate yet.** The content, the
   policies and the engines all exist and are tested; the app still only launches chord gates.
   Wiring them is UI work, which the user has asked to hold.
2. **The calibration gate is a chord exercise, not a calibration screen.** Region 0 asks for
   note-on/off, sustain and latency checks. Those live on the MIDI diagnostics screen, which is
   not yet part of the campaign path.
3. **Voice-leading gates are progression gates.** Region 13 wants scoring on total motion, leaps
   and retained common tones. `VoiceLeadingTarget` exists as a requirement type and no policy
   field reaches it, so the two gates practise the right material with the wrong evaluator.
4. **Integrated reading is a progression with staff notation switched on.** Region 14's two-hand
   coordination — melody in one hand, comping in the other — is not modelled.
5. **Human review has not happened.** The phase asks for gates populated "with human review".
   Every exercise is machine-checked as generable, spellable and playable; nobody has read the
   43 previews or played a session.

---

## Phase: 12 — Design system

**Commit:** see `git log` for `phase/12-design-system`

### Implemented

- **Tokens.** `Tokens.kt` now covers every group 12 §4 names: background and surface levels,
  primary and secondary accents, feedback, text hierarchy, notation colours, piano key states,
  gate states, spacing, corner radii, elevation, typography and motion. The colours are sampled
  from the approved artwork in `interface/` rather than chosen — see D41.
- **One palette.** The approved screens are painted plates in a dark world, so there is no light
  theme to switch to. Contrast is met inside the dark palette and measured by `TokenTest`.
- **Reduced motion.** `HarmonyMotionTokens.reduced()` collapses every duration; `MainActivity`
  reads `Settings.Global.ANIMATOR_DURATION_SCALE` and passes it in, so the system preference is
  honoured rather than duplicated as an in-app switch.
- **Components.** All 27 names in 12 §5 now exist in Compose: `Shell.kt` (shell, status bar,
  MIDI chip, rail, countdown, sheet, dialog), `Controls.kt` (buttons, icon button, segmented
  control, filter chip, difficulty slider), `Campaign.kt` (gate card, gate node, progress meter,
  skill badge, result card), `Exercise.kt` (header, chord symbol, roman numeral, note names,
  feedback panel, metronome, assistance indicator). Three are renamed to avoid colliding with a
  Material 3 symbol; `docs/DESIGN_SYSTEM.md` §3 is the mapping table.
- **Mapped states.** `PianoKeyState` carries 12 §7's nine states as independent layered facts,
  and `KeyPaintings.of()` maps them to a fill, a marker and an outline without touching the
  palette. `MidiPresentation`, `GatePresentation` and `FeedbackPresentation` each carry a glyph,
  a label and a screen-reader description.
- **`docs/DESIGN_SYSTEM.md`** — the token export and component mapping 12 §11 asks for.

### Acceptance

- *design tokens* — done, structurally complete against §4.
- *Compose design-system components* — done, all 27 of §5.
- *mapped states* — done: piano keys (§7), MIDI (§8), gates and verdicts.
- *final Figma screens/components* — **not done.** Two screens are approved as painted plates;
  the rest have not been drawn. This is design work, not code work.
- *screenshot comparisons* — **not done.** There are no Figma baselines to compare against, and
  a JVM Compose renderer (Robolectric or Paparazzi) is not set up because there is no emulator
  in this environment.

### Tests

568 passing, 0 failing. 28 new: `TokenTest` (14), `KeyPaintingTest` (9),
`StatusPresentationTest` (5).

### What the tests caught

- Nothing failed on the first run, which is worth saying plainly rather than dressing up: the
  contrast and distinctness tests were written against a palette that had already been sampled
  from approved artwork, so they confirmed a choice rather than correcting one. Their value is
  forward-looking — they are what will tell the Figma pass whether a new value is still legible.

### Known limitations

1. **The Figma file is still the missing half.** Screens beyond Home and Progression Run are not
   designed, node IDs cannot be mapped, and screenshot baselines do not exist.
2. **Feedback and piano palettes are provisional.** Neither approved plate contains a verdict or
   a keyboard. Nothing depends on the exact values — every state carries a glyph or an outline —
   but they are the first thing the design pass should replace.
3. **No typefaces.** The type scale is set; the families are not identified or licensed.
4. **The components are not yet used by the screens.** The existing screens still compose their
   own layouts from panels and chips. Rewiring them onto the new components is visual work, and
   the user has asked for UI work to be held.

---

## Phase: 11 — Advanced harmony

**Commit:** see `git log` for `phase/11-advanced-harmony`

### Implemented

- `AlteredDominants` — Region 10 as transformations rather than a table. `alter()` builds any
  altered form from a plain dominant, `singleAlterations` and `combinedAlterations` enumerate the
  rungs a gate climbs, `altered()` is the `alt` chord, and `retainsGuideTones` is the required
  task "retain guide tones while changing color tones" stated as a check.
- `DiminishedSystems` — 03 §13's "dedicated submodule rather than silently mixing pedagogy
  systems". Symmetry (`isSameChord`, `equivalentSpellings`, three `distinctFamilies`), the
  dominant b9 relationship both ways (`asDominantSubstitute`, `fromDominant`), passing and
  common-tone use.
- `Substitutions` — tritone, secondary dominant, related ii, backdoor and diminished-for-dominant,
  each carrying the tones the two chords share and a sentence saying why the ear accepts it.
  `applyTo` and `insertBefore` rewrite a progression, because a substitution is only itself in
  context.
- `VoicingFamily.QUARTAL` — stacked fourths with no bass or top constraint, since an inverted
  fourth stack is still a fourth stack. 03 §11 declines to force a conventional chord symbol here
  and so does the recipe.
- `ExercisePolicy.alterationPool` — authored as `[["b9"], ["#9", "b13"]]`, one chord per entry.
  Lets Region 10 be content rather than code.
- Content: five new regions — Sus and Slash, Quartal and Modal, Altered Dominants, Diminished
  Systems, Substitutions — nine new gates and ten new exercise policies, chained onto
  `gate.extensions.ninths`, which now unlocks what follows it.

### Acceptance

- *tests cover all formulas and representative contexts* — `AdvancedHarmonyTest` holds a
  hand-written interval table for all thirty formulas, asserted both against the formula
  definitions and against the realised tones from all twelve roots. The table is written out
  rather than derived: deriving it from `ChordDegree` would have made it a test of nothing.
  Contexts are covered by example — a tritone substitution inside `Dm7 G7 Cmaj7`, a passing
  diminished chord between `Cmaj7` and `Dm7`, a So What voicing and its rotation, `C/E` against
  `C/A`.

### Tests

540 passing, 0 failing. 42 new: `AdvancedHarmonyTest` (38) and four in `ExerciseGeneratorTest`.

### What the tests caught

- **A tritone substitution could be unspellable.** Transposing by a diminished fifth gave `Eb7` a
  substitute of `Bbb7`, which then had no substitute of its own because its fifth needed a triple
  accidental. Roots now fall back to the chart spelling past one accidental (D39).
- **The common-tone diminished chord was on the wrong side of the root.** It was built a semitone
  below, which is the leading-tone chord and shares nothing with a major triad. A semitone above
  — `C - C#dim7 - C` — shares the third and the fifth, which is what the name means.
- **A wrong note is `PARTIAL`, not `INCORRECT`.** Two tests asserted the harsher verdict; the
  evaluator is right and the more useful assertion is the one now made, that the diagnosis names
  the fifth by degree.

### Known limitations

1. **No substitution exercises yet.** `Substitutions` can rewrite a progression, but no answer
   mode asks a player to *choose* a substitution — the two substitution gates practise the chords
   themselves. A progression-level answer mode is Phase 13 work.
2. **Quartal planing is not implemented.** 03 §11 lists it; moving a fourth-stack in parallel
   needs a progression-shaped exercise rather than a chord-shaped one.
3. **No Barry Harris material.** §13 marks it optional and asks for it to stay separate; the
   submodule it would live in exists, and nothing has been put in it.
4. **Pedal points and stepwise bass are unbuilt.** Region 6's two-hand tasks need an exercise that
   can ask for one hand to hold while the other moves.

---

## Phase: 9 — Sight reading core

**Commit:** see `git log` for `phase/9-sight-reading`

### Implemented

- `RationalBeat` — exact musical duration. 08 §3 forbids floating-point durations, and the test
  says why: three triplet eighths are exactly one quarter here and 0.9999999999999999 in doubles.
- `TimeSignature`, `ScoreEvent`, `Measure`, `ScorePhrase` — 08 §3's model, with `validate()`
  reporting a bar that does not add up rather than letting it reach a renderer.
- `StaffPlacement` — vertical position from letter and octave, not from MIDI, so F and F# share
  a line and G# and Ab do not.
- `DefaultPhraseGenerator` — single notes, intervals and triads, drawn from the key's own scale
  so a phrase in Eb needs no sharps. Every bar holds exactly its meter, for every seed tested.
- `ReadingEvaluator` — pitch and rhythm scored separately, notes claimed by nearest written time
  so one missed note does not derail the rest, and tolerance windows as content.
- `CountIn` — clicks in the meter's own beat unit, so 6/8 counts in eighths.
- `StaffGeometry` and `NotationStaff` — Compose Canvas rendering with the layout arithmetic in
  plain Kotlin, per 08 §2, and no WebView.

### Acceptance

- *generated phrase displays and grades against injected clock* — the evaluator is handed
  timestamps rather than reading a clock, so `a perfect performance grades perfectly`,
  `right notes played late are a timing problem, not a pitch one` and
  `grading is deterministic and needs no clock` all run instantly and identically.

### Tests

498 passing, 0 failing. 47 new: `SightReadingTest` (30) and `StaffGeometryTest` (17).

### What the tests caught

- **Pitch and rhythm were coupled.** The evaluator initially refused to count a note as in time
  unless it was also the right note, which made both scores move together and destroyed the
  distinction 08 §5 exists for. A reader hitting wrong notes exactly on the beat has good time
  and a pitch problem, and now hears that.
- **A ledger-line test was wrong, not the code.** D4 below a treble staff is written in the space
  with no ledger line; only C4 sits on one. The test now states the rule properly.

### Known limitations

1. **No beams, ties or key signatures are drawn.** The model carries ties and key context; the
   renderer draws heads, stems, ledger lines, accidentals, dots, rests and barlines. 08 §1's
   full v1 list is not finished.
2. **Rests are a plain mark.** Legible, not engraved, and not distinguished by duration.
3. **No sight-reading screen.** The domain and the renderer exist; nothing has put them together
   with a clock and a MIDI input yet.
4. **Two reading modes are unbuilt.** Continuous stream and lead-sheet reading from §4 need a
   scrolling renderer and a comping evaluator respectively.

---

## Phase: 8 — Ear training and sampler

**Commit:** see `git log` for `phase/8-ear-training`

### Implemented

- `core:audio`, a new module. `Mixer` is plain Kotlin: voice pool, resampling with linear
  interpolation, envelope, per-voice gain, summed float buffer, soft limiter, PCM conversion —
  and no allocation in the render loop, which 09 §6 asks for by name.
- Voice stealing takes the oldest note rather than refusing the new one, and the pedal marks a
  voice sustained rather than released, mirroring `ActiveNoteTracker` on the input side.
- `InstrumentPreset`, `SampleZone`, `PcmSample`, `SampleBank` — 09 §3's model, with licence text
  on every bank because §4 requires it.
- `AudioTrackPlayer` — the Android sink and the render thread, and nothing else.
- `SynthesisedBanks` generates a decaying harmonic tone per zone, so ear training works before a
  sample licence is chosen. Honest about what it is rather than dressed up.
- `EarStimulus`, `StimulusSpec`, `ReplayRule`, `ListeningRecord` — 07 §3's reproducible record
  and §5's replay evidence.
- `DefaultEarExerciseGenerator` — reproduce, identify-then-play, difference detection and
  function hearing, every one producing an ordinary `ExerciseRequirement`.

### Acceptance

- *deterministic stimulus* — `the same seed produces the same stimulus` compares the whole
  `StimulusSpec`, instrument and velocities included; `rendering is deterministic` does the same
  for the mixer's output buffer.
- *no blocking decode during timed playback* — `PcmSample` is decoded audio by construction, a
  `SampleBank` refuses zones whose audio was never decoded, and `load` decodes on the IO
  dispatcher before the render loop starts.
- *MIDI answer evaluation reuses core evaluator* — `playing what you heard is correct, judged by
  the ordinary evaluator` runs an ear answer through `DefaultPerformanceEvaluator`, and
  `an ear exercise uses the same requirement type as a chord gate` pins 07 §1.

### Tests

451 passing, 0 failing. 37 new: `MixerTest` (21) and `EarTrainingTest` (16).

### Known limitations

1. **Nothing has been heard.** No audio device here. The mixer is verified by reading rendered
   buffers — pitch by counting zero crossings, the limiter by counting full-scale frames — which
   proves the arithmetic and not the sound.
2. **No licensed samples.** The synthesised tone is a placeholder with a plausible envelope and
   is not a piano. §4's three v1 instruments need a bank chosen and its licence recorded.
3. **Two ear families return null.** Bass hearing and voice-leading hearing need a moving line,
   and a line is Phase 9's score domain. Declining is deliberate; a half-version would be worse.
4. **No ear-training screen.** The domain and the sampler exist; nothing plays them yet.

---

## Phase: 7 — Assistance system

**Commit:** see `git log` for `phase/7-assistance`

### Implemented

- `AssistanceChannel` — the twelve independent switches of 01 §5, with `revealsTheAnswer`
  marking the ones that hand over the notes rather than pose the question.
- `AssistanceProfile` — a set of channels, converting both ways with `PresentationSpec`, so the
  whole exercise pipeline gained a hint ladder without changing shape.
- `AssistanceLevel` A0–A7 and `DifficultyPreset` Learn/Guided/Practice/Challenge/Blind. Both
  resolve to profiles; neither is stored, which keeps the assistance and harmonic-content
  sliders separate as 02 §8 requires.
- `HintLadder` — one channel per request, structure before notes, and it runs out rather than
  repeating. `forError` targets the mistake actually made: a wrong bass reveals the bass, not
  the metronome.
- `RecoveryPolicy` — 02 §6's loop. Two of the same mistake offers a scaffolded retry; three
  isolates the failing component. Three *different* mistakes is exploring, not being stuck.
- Engine: `requestHint()` reveals and re-presents, `acceptRecovery()` takes the offer without
  counting it as a hint the player went looking for.
- Content: a policy can name `"assistanceLevel": "A1"` instead of listing switches. Setting both
  is refused, because which one won would not be visible in the file.

### Acceptance

- *same exercise can be presented at multiple assistance levels without changing answer logic* —
  `the same exercise at every assistance level asks the same question` generates the same seed
  at all eight levels and asserts one distinct `ExerciseRequirement`, and the chord test does
  the same for the material. Re-presenting after a hint copies the instance with a new
  presentation, so the requirement is the same object it always was.

### Tests

414 passing, 0 failing. 17 of them are new (`AssistanceTest`).

### Known limitations

1. **Four channels have no renderer.** Fingering, reference audio, metronome and surrounding
   chords are modelled and selectable but nothing draws or plays them; audio arrives in Phase 8,
   notation in Phase 9. The round-trip test states which channels this affects rather than
   quietly dropping them.
2. **Remediation still does not route.** `Recovery.Isolate` names the error class and the gate
   maps it to a policy; nothing yet launches that policy and returns.
3. **The UI is minimal.** A Hint button and the recovery offer, in the plain Phase 4 screen.

---

## Phase: 6 — Campaign engine

**Commit:** see `git log` for `phase/5-6-persistence-and-campaign`

### Implemented

- `Curriculum`, `CurriculumRegion`, `GateDefinition`, `CompletionRule`, `Unlock` — the campaign
  as a directed acyclic graph, with no level numbers anywhere in it.
- `CurriculumValidator` — duplicate ids, dangling references, prerequisite cycles, gates nothing
  can reach, and a set of warnings for content that is legal and probably not meant.
- `CampaignEvaluator` — gate status and unlocks derived from stored evidence, every time.
- `GateSessionPlanner` — 02 §3's session phases, shaped by what the player already knows, and
  02 §9's weighted sampling deciding which roots come first.
- Content schema and decoder: `content/curriculum/curriculum.json` and
  `content/exercises/exercise_policies.json`, eleven policies across four regions and nine gates.
- `ContentRepository` reading them from assets, validating on load and refusing bad packs.
- `./gradlew validateHarmonyContent`, wired into `verifyHarmony` and therefore into CI.
- Campaign screen: regions, gate status, what is still missing per gate, and a Play button that
  starts the gate's own session.

### Acceptance

- *prerequisites/unlocks deterministic* — `the same evidence always produces the same map`,
  plus the rewards and prerequisite tests around it. Nothing about a gate's status is stored;
  it is recomputed, so it cannot drift.
- *no unreachable/cyclic content* — the validator catches three-gate cycles, two-gate deadlocks,
  campaigns with no entry point and gates blocked forever, and `ContentPackTest` runs the same
  checks against the curriculum that actually ships.

---

## Phase: 5 — Persistence and mastery

**Commit:** see `git log` for `phase/5-6-persistence-and-campaign`

### Implemented

- `core:data`, a new module: Room database at version 1, seven entities, seven DAOs, schema
  exported to `core/data/schemas` and committed.
- `SkillMastery` and `MasteryUpdater` in `core:music` — 06 §9's four evidence weights, a
  recency-weighted estimate, an error histogram, root coverage and spaced review.
- `MasteryEvidence` — reads an attempt as evidence, and declines to score an inconclusive or
  skipped one against the player.
- `ProgressRepository` and `RoomProgressRepository` — attempts stored as they are judged, in one
  transaction with the mastery they update.
- `HarmonyPreferences` on DataStore — MIDI device, keyboard range, assistance defaults, volumes,
  accidental preference and UI settings.
- Progress screen: mastery per skill weakest-first, recurring errors named, and the attempt
  history with what was asked and what was played.

### Acceptance

- *app restart preserves progress* — every attempt is written as it happens rather than at the
  end of a session, and `MappersTest` proves the encoding round-trips without loss, which is
  where this criterion is actually decided. **Not demonstrated on a device.**
- *historical attempts are inspectable* — the Progress screen reads the attempt table back,
  including the expected and performed snapshots and the semantic diagnosis.

### Tests

397 passing, 0 failing. 77 of them are new.

| Suite | Covers |
| --- | --- |
| `MasteryTest` | The four weights, recency, the histogram, coverage, review scheduling |
| `CampaignTest` | Cycles, unreachable gates, dangling references, unlocks, completion rules |
| `GateSessionPlannerTest` | Session shape against player state, deterministic root ordering |
| `RoomSchemaTest` | The exported schema creates, cascades and constrains; the migration chain |
| `MappersTest` | Mastery and attempts round-trip; a rebuild matches the running total |
| `ContentPackTest` | The shipped curriculum validates; 100 exercises from every policy |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done:** no device, so the database has never actually been closed and reopened. The
  encoding is tested, the schema is executed against real SQLite, and the round trip through
  Room itself remains unproven here.

### Known limitations

1. **Room's own round trip is untested.** `MigrationTestHelper` needs an emulator. The schema is
   exercised as DDL against JVM SQLite instead, which covers the tables but not Room's
   generated DAO code.
2. **One profile.** The tables are keyed by profile and the app only ever makes one.
3. **No export or import.** 11 §7 asks for local JSON export of progress "if practical"; the
   attempt snapshots are designed for it, but nothing writes the file yet.
4. **Assistance is inferred, not chosen.** Whether an answer counted as assisted is read from
   the exercise's presentation switches. Phase 7's assistance profiles will set those switches
   deliberately, and the evidence grades will get sharper for free.
5. **Remediation is authored but unused.** Gates map their common errors to a remedial policy;
   nothing routes a struggling player there until Phase 7's recovery loop.
6. **The campaign screen is a list.** 02 §10 leaves the metaphor to Figma, and no map artwork
   has been supplied, so it is deliberately plain.

---

## Phase: 10 — Jazz voicing and progression systems

**Commit:** see `git log` for `phase/10-progression-run`

Taken out of order, at the user's request, so that the approved home screen and the Progression
Run track can be seen working together. Phases 5 to 9 are untouched and still to come; nothing
here depends on them, because the run holds its results in memory exactly as the exercise
session already does.

### Implemented

- `VoicingFamilies` — shells (`1-3-7`, `1-7-3`), guide tones, and rootless A and B, each
  resolved for a particular chord as a `VoicingPolicy` rather than as a fixed voicing. The two
  shell inversions differ by top note; A and B differ by which tone is lowest.
- `Progression`, `ChordEvent`, `VisibleChord` — an ordered run of arbitrary length, and the
  window the track draws around the play point. Nothing in the domain knows there are eight
  slots.
- `ProgressionTemplates` and `DefaultProgressionGenerator` — ii-V-I major and minor, the
  I-vi-ii-V turnaround, the tritone substitution and the backdoor cadence, placed in any key or
  run through all twelve. Deterministic, including the seeded key shuffle.
- `DefaultProgressionRunEngine` — evaluate the event at `activeChordIndex`, advance on
  acceptance, never on wrong or incomplete input, and never twice from one held chord.
- `ProgressionTrack` and `TrackGeometry` in `core:designsystem` — one parameterised renderer,
  generic orbs, perspective sampled continuously so an orb can be between slots.
- `TrackMapReader` — the slot path, advance duration and easing read from
  `interface/maps/progression-run.json` at runtime.
- Progression Run screen, reached from the home artwork's `HIT / Progression Run` region.

### Tests

320 passing, 0 failing. 52 of them are new.

| Suite | Covers |
| --- | --- |
| `VoicingFamiliesTest` | Shell, guide-tone and rootless shapes; wrong rotation diagnosed as bass or top note |
| `ProgressionTest` | Templates placed in keys, the twelve-key run, windowing, loop identity, several renderings accepted |
| `ProgressionRunEngineTest` | Advance on the right chord only, the held-chord gate, loop wrap, manual next, pause and resume |
| `TrackGeometryTest` | Slot interpolation, extrapolation past both ends, perspective ordering |
| `TrackMapTest` | The supplied map parses to the approved eight-slot track and its timing |

### Acceptance

15_IMPLEMENTATION_PHASES.md asks for two things of this phase, and both are covered by tests:

- *multiple valid voicings accepted where policy allows* — `an any-voicing run accepts
  inversions, omissions and doublings of the same chord` plays a `Dm7` five different ways, in
  root position, in inversion, with the seventh in the bass, without the fifth, and doubled
  across two hands.
- *wrong inversion/bass diagnosed correctly* — `a root-position run diagnoses an inversion as a
  wrong bass` and `playing the B rotation when A was asked for is diagnosed as a wrong bass`.

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- Track geometry rendered from the supplied slot path and inspected as a drawing; the eight orbs
  sit on a receding path with the play point nearest and largest.
- **Not done:** nobody has played the run on a tablet with a keyboard. Same gap as Phase 4, and
  now the larger of the two, because the held-chord gate and the 650 ms advance are precisely
  the things that can only be judged under the hands.

### Known limitations

1. **No approved background plate yet.** `interface/progression_run_background.jpg` has not been
   supplied, so the track draws over the theme background. The layering is already the one the
   handoff requires, so the plate drops in with no code change when it arrives.
2. **The run's own controls are not mapped.** `interface/maps/progression-run.json` marks its
   non-track hit regions as pending remapping from frame `77:2`, so the template, voicing and
   Next controls are ordinary Compose buttons rather than regions on the artwork.
3. **Orb colours are placeholder tokens.** The approved treatment is dark orbs with cream and
   gold labels; the values come with the Phase 12 palette, and the components already read them
   from tokens.
4. **A rejected chord needs a fresh strike.** After a wrong answer the run re-arms, but the
   notes still held are not part of the next attempt — adding the missing tone without lifting
   will not be seen. That is Phase 3's capture model, not new here.
5. **Nothing is persisted.** A finished run reports how it went and then forgets. Phase 5.

---

## Phase: 4 — Minimal playable vertical slice

**Commit:** see `git log` for `phase/4-vertical-slice`

### Implemented

- `ExercisePolicy` — root pool, formula pool, inversion pool, answer mode, presentation
  channels, register, onset policy, session length. The authoring checklist from
  21_CONTENT_AUTHORING_GUIDE.md §4 as data.
- `DefaultExerciseGenerator` — deterministic from a seed. Prefers untested roots (the coverage
  term from 02 §9), avoids an immediate repeat, and never emits a chord standard notation
  cannot write.
- `ExerciseSessionEngine` and `DefaultExerciseSessionEngine` — the loop from 01 §2, once:
  present → arm → capture → evaluate → feedback → next, with pause, resume, skip and hints.
- `ExercisePresentationModel` — the mapper from 10 §5. A channel the exercise did not enable is
  absent from the model, not merely hidden, so a screen cannot leak the answer.
- Chord gate screen: chord symbol, live keyboard, verdict with a worded musical diagnosis,
  session summary. Reached from the home screen's Chord Gates and Quick Practice controls.
- `CapturePolicy`, `CaptureState` and the `PerformanceCapture` contract moved from `core:midi`
  into `core:music`. They are domain concepts, not transport ones, and the move lets the session
  engine live in pure Kotlin with no Android dependency.

### Tests

268 passing, 0 failing. 25 of them are new.

| Suite | Covers |
| --- | --- |
| `ExerciseSessionEngineTest` | The loop end to end, seed reproducibility, pause/resume re-arm, device loss, hint recording |
| `ExerciseGeneratorTest` | Determinism, root coverage across a session, inversion requirements, writability over 200 seeds |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done, and this is the phase's whole acceptance criterion.** 15_IMPLEMENTATION_PHASES.md
  requires "20-exercise session works on tablet with real keyboard" and "no false submissions
  from ordinary chord spread". Neither can be demonstrated here: no tablet and no MIDI keyboard
  are attached to this environment. The loop is exercised end to end against a fake capture, and
  the capture itself against scripted MIDI, but a person has not played it.

### Known limitations

1. **Phase 4's acceptance is unmet.** See above. Everything up to it has been verified; this one
   genuinely cannot be, from a build server.
2. **The capture thresholds are still unmeasured.** The "no false submissions" criterion is
   precisely a test of those numbers, so the two open items are the same item.
3. **Arming is automatic.** Once a target is on screen and a keyboard is connected, capture arms
   without a tap. That is right for a practice loop and may be wrong for a timed gate; Phase 6's
   gate rules will decide per exercise rather than globally.
4. **The exercise policy lives in the view model.** It is data, but it is data in Kotlin. Phase 6
   moves it into `content/` where a curriculum author owns it.
5. **No mastery, no progress, no persistence.** A session summary is shown and then discarded.
   Phase 5 stores it.
6. **Only one answer mode is used in the app.** The generator and evaluator support exact
   voicings and degree-aware policy matching; the Phase 4 policy asks for pitch classes, which
   is what a first chord gate should ask for.

### Next phase prerequisites

Phase 5 — persistence and mastery. Needs:

- `core:data` as an Android library: Room schema, attempt storage, migrations
- `ProgressRepository` and `ContentRepository` per 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §5
- The `SkillMastery` evidence model from 06 §9, with its weighting: independent correct 1.0,
  correct with reduced hint 0.8, correct after hint 0.5, incorrect 0.0
- DataStore preferences

`AttemptRecord` already carries the instance, the attempt, the result and the hint count, which
is everything the mastery update needs.

---

## Phase: 3 — Performance capture and evaluator

**Commit:** see `git log` for `phase/3-capture-and-evaluation`

### Implemented

**Capture (`core:midi`)**

- `OnsetAggregator` — the state machine from 05_MIDI_INPUT_ENGINE.md §6. Never submits on the
  first note; completes on a quiet window, on every key being lifted, on the roll window running
  out, or on an explicit submit.
- `CapturePolicy` — quiet window, roll window, stabilisation, accidental-note grace, sustain
  policy, accepted range. The spec's values are defaults, not constants, because §6 says so.
- `MidiPerformanceCapture` — the `PerformanceCapture` contract over a `MidiInputSource`. The
  heartbeat only runs while a capture is armed.

**Evaluation (`core:music`)**

- `PerformanceAttempt` and `NormalizedNoteEvent` — the reduction *and* the full history, because
  accidental-note diagnosis needs the note that was dropped
- `OnsetPolicy` — simultaneous, rolled-allowed, unrestricted
- `DefaultPerformanceEvaluator` — three matching modes: pitch-class, exact voicing (multiset,
  so a doubled voice counts), and degree-aware policy matching
- `PerformanceError` with an educational ranking, so a player reads the useful thing first
- `Verdict`, `TimingEvaluation`, `PerformanceMetrics`, `FeedbackModel`
- `ChordRealizer` gained `analyze`, `spelledDegrees` and `degreeOf` — the evaluator needs them
  and they belong on the interface rather than only on the implementation

### Tests

243 passing, 0 failing. 49 of them are new.

| Suite | Covers |
| --- | --- |
| `OnsetAggregatorTest` | All nine scripted scenarios from 14_TESTING_AND_QUALITY.md §5, plus the roll threshold at its edges |
| `PerformanceEvaluatorTest` | The three matching modes, error ordering, device loss, register and bass constraints |
| `MidiPerformanceCaptureTest` | Source-to-aggregator wiring on virtual time |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done:** anything with a real keyboard. The capture thresholds in `CapturePolicy` are the
  specification's starting values and 05_MIDI_INPUT_ENGINE.md §6 explicitly calls them
  "starting default values for user testing, not permanent constants". They have never been
  played on. Instrument-testing them is a hardware task.

### Known limitations

1. **The capture thresholds are unvalidated.** 80 ms quiet window, 300 ms roll, 40 ms
   stabilisation, 70 ms accidental grace. All plausible, none measured against fingers.
2. **No exercise UI yet.** Capture and evaluation exist and are tested, but nothing on screen
   drives them — that is Phase 4's vertical slice.
3. **Timed sequences and sight-reading phrases return `NO_ATTEMPT`.** Those requirement types
   are graded step by step by a session engine, which arrives with Phases 9 and 10.
4. **Voice-leading targets are evaluated only for harmonic validity.** The movement scoring in
   06_PERFORMANCE_EVALUATION_AND_SCORING.md §11 belongs to Phase 10; today a voice-leading
   requirement is judged as a chord-policy match against its destination.
5. **No mastery weighting.** §9's evidence model is Phase 5 and reads these results rather than
   replacing them.

### Design notes worth carrying forward

Two bugs found by the scripted scenarios, both of which would have marked correct answers wrong:

- The accidental-note grace period initially discarded *every* note of a short chord, because
  letting go is how a player finishes. The filter now asks whether the key was released while
  the chord was still arriving, not merely whether it was released quickly.
- Lifting every key completed the attempt even with the pedal down, which ended capture on a
  note that was only a sustained remnant. Capture now waits for the quiet window when the pedal
  is held.

### Next phase prerequisites

Phase 4 — the minimal playable vertical slice. Needs:

- An exercise session engine per 10_ANDROID_ARCHITECTURE.md §4, wrapping arm → capture →
  evaluate → feedback → next
- A deliberately plain exercise screen: show `Cmaj7`, play it, see the verdict
- Exercise generation from a seed, using the `SeededRandomFactory` already in `core:music`
- Several qualities, roots and inversions, per the phase's acceptance criteria

Everything Phase 4 needs from capture and evaluation now exists.

---

## Phase: 2 — MIDI foundation

**Commit:** see `git log` for `phase/2-midi-foundation`

### Implemented

- `core:midi`, an Android library. Everything that decides meaning is plain Kotlin inside it;
  only discovery and transport touch the platform.
- `MidiInputSource` — the single seam the rest of the app sees, per AGENTS.md
- `AndroidMidiInputSource` — `android.media.midi`, transport-specific enumeration on API 33+
  with the older path below it, a dedicated callback thread, hotplug via `DeviceCallback`
- `FakeMidiInputSource` — the simulator. Same interface, injected clock, scriptable notes,
  pedal, raw bytes, disconnect and reconnect. CI never needs hardware.
- `MidiMessageParser` — running status, real-time bytes interleaved mid-message, messages split
  across reads, SysEx skipping, note-on-at-velocity-zero, pitch bend centred at zero
- `ActiveNoteTracker` — the three sets 05_MIDI_INPUT_ENGINE.md §5 requires kept apart:
  physically held, pedal-sustained, and what a listener actually hears
- `MidiConnectionState` and typed `MidiError`, exposed as `StateFlow`
- `PianoKeyboard` in `core:designsystem` — Compose Canvas, held and sustained drawn differently
- MIDI diagnostics screen: connection guidance, device picker, live keyboard, observed range,
  capped event log, and an on-device simulator including a "pull the cable" control
- Reached from the home screen's Settings control; `HomeDestination.Settings` is now live

### Tests

194 passing, 0 failing. 65 of them are new.

| Suite | Covers |
| --- | --- |
| `MidiMessageParserTest` | 24 cases: running status, interleaved clock bytes, split reads, SysEx, orphan bytes |
| `ActiveNoteTrackerTest` | 18 cases: sustain across a chord change, duplicate note-on, remnant clearing |
| `FakeMidiInputSourceTest` | 15 cases: hotplug, multi-device selection, timestamps from an injected clock |
| `KeyboardLayoutTest` | 8 cases: black-key placement, octave repetition, out-of-range anchors |

### Manual verification

- `./gradlew verifyHarmony assembleDebug` from a clean checkout — passes
- **Not done:** anything involving a real keyboard. No MIDI device is attached to this build
  environment, so the Phase 2 acceptance criteria "real keyboard note-on/off appears correctly"
  and "hotplug works" are **unverified**. The simulator exercises the same code paths, which is
  not the same thing as a physical test. 22_MANUAL_DEVICE_TEST_PLAN.md remains outstanding.

### Known limitations

1. **Phase 2 acceptance is only partly demonstrable here.** "CI uses fake source" is met and
   tested. The two hardware criteria need a tablet and a keyboard.
2. **No onset aggregation yet.** 05_MIDI_INPUT_ENGINE.md §6 and §7 — the chord capture window,
   the roll tolerance, `OnsetPolicy` — belong to Phase 3 with the evaluator, and are not built.
   Today the tracker reports what is sounding; nothing decides when a chord is "submitted".
3. **Keyboard range discovery is partial.** The diagnostics screen reports the observed low and
   high notes (§10's raw material), but the calibration flow and the 25/49/61/76/88 presets are
   not built. Nothing depends on them yet.
4. **Latency telemetry is not instrumented.** §11 asks for four timestamps through the pipeline;
   only the arrival timestamp exists. The others need the evaluator and the UI hop, so this
   lands with Phase 3 and Phase 4.
5. **The event log is capped at 60 lines.** A diagnostics screen left open for a practice
   session would otherwise grow without bound.
6. **Under extreme backpressure an event may be dropped** from the `events` flow — `tryEmit`
   rather than a suspending emit, because the MIDI callback must never block. Active-note state
   is updated regardless, so what is sounding stays correct even if a log line is missed.

### Next phase prerequisites

Phase 3 — performance capture and evaluator. Needs:

- The onset aggregation state machine from §6, driven by `TestMonotonicClock`
- `OnsetPolicy` — simultaneous, rolled-allowed, unrestricted
- `PerformanceAttempt` per 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §2, keeping the full event
  history and not only the reduced note set
- `PerformanceEvaluator` over the `ExerciseRequirement` types already defined in `core:music`
- The scripted MIDI scenarios in 14_TESTING_AND_QUALITY.md §5, each with an expected attempt

Everything Phase 3 needs from MIDI now exists.

---

## Phase: 0 — Repository and toolchain, and 1 — Pure music domain

**Commit:** see `git log` for `phase/0-1-toolchain-and-music-domain`

### Implemented

**Phase 0**

- Gradle 9.7.0 wrapper, Kotlin DSL throughout, version catalog at `gradle/libs.versions.toml`
- Modules: `app`, `core:music`, `core:designsystem`, `core:testing`
- Android baseline: compile SDK 37, target SDK 36, min SDK 26, AGP 9.3.1
- GitHub Actions CI: domain tests, unit tests, lint, debug APK, artifact upload
- Android Lint with `warningsAsErrors` in both Android modules, shared config at
  `config/lint/lint.xml`
- Placeholder design system: colour/spacing/typography tokens, theme, four components
- Adaptive launcher icon (vector, placeholder)

**Phase 1**

- Pitch primitives: `MidiNote`, `PitchClass`, `ScaleDegree`, `Semitones`, `VoiceIndex`,
  `LetterName`, `Accidental`, `SpelledPitchClass`, `SpelledPitch`, `SpelledInterval`
- `ChordDegree` as (diatonic number, chromatic alteration), so `#9` and `b3` are distinct values
- 30 chord formulas with required/optional/forbidden/written-stack sets
- `JazzChordParser` covering every symbol in 04_HARMONY_DOMAIN_ENGINE.md §5 plus typed variants
- `DegreeAwarePitchSpeller`: letter from the degree number, accidental from the arithmetic
- `DefaultChordRealizer`: chord tones, deterministic voicing generation against a policy,
  performance analysis
- `VoicingPolicy` with bass/top/register/doubling/rootless constraints
- `VoicingTransforms`: invert, octave-displace, drop-2, drop-3, drop-2&4, spread, close,
  constrain-to-range, bass/top anchoring
- `VoiceLeadingAnalyzer` with optimal voice assignment (Hungarian algorithm) and explicit
  add/drop penalties
- `KeyContext`, `Mode`, `RomanNumeral`, `FunctionalChord`, `Functions` progression vocabulary
- `Transposition`: chromatic (spelled), diatonic, voicing-level
- `ExerciseRequirement` sealed hierarchy
- Injected `MonotonicClock`, `WallClock`, `RandomSource`, `SeededRandomFactory`
- `core:testing`: advanceable clocks, scripted random source, readable music assertions
- Phase 1 harness screen in `app`: type a chord symbol, see degrees, spelling and a voicing

**Approved home screen (added mid-phase at the user's request)**

- `ArtworkSpec` / `HitRegion` / `NormalizedRect` / `ArtworkGeometry` / `ArtworkScreen` in
  `core:designsystem`; regions stored as fractions of the artwork, artwork fitted not cropped
- `HomeAction` binding all twenty regions to app destinations, keyed on both the Figma layer
  name and the map's semantic action id, with `HomeDestination` recording the phase each opens in
- `interface/harmony_home_approved.jpg` (1536 × 1024) is the home screen; all twenty controls
  are live, and Theory Lab reaches the Phase 1 harness
- `interface/maps/home.json` parsed at runtime, so a re-export updates the app by replacing one file
- `syncInterfaceArtwork` (buildSrc, per variant) copies artwork and map into generated resources
- `checkInterfaceAssets` validates handed-over assets at build time — added after the first
  upload of the artwork arrived as 14,997 bytes carrying no JPEG markers
- System back returns from the harness to home
- See `docs/INTERFACE_INTEGRATION.md`

### Tests

129 passing, 0 failing.

| Suite | Covers |
| --- | --- |
| `GoldenChordFixtureTest` | 74 hand-derived chord fixtures incl. Bbb, Dbb, E#, B#, Gx |
| `ChordCoverageTest` | 30 formulas × 12 roots: writability, letters, sounding distances, symbol round trips |
| `ChordParserTest` | Every specified symbol, alias equivalence, failure positions, `M7` vs `m7` |
| `VoicingTest` | Inversion by bass, rootless policies, omissions, slash bass, register, determinism |
| `VoicingTransformsTest` | Drop/spread/close/invert, pitch-content preservation |
| `TranspositionTest` | Round trips over 360 chords × 12 intervals, 1000 seeded property cases |
| `VoiceLeadingTest` | Motion metrics, optimal pairing, add/drop, motion-type classification |
| `FunctionalHarmonyTest` | The four specified roman-numeral cases, twelve keys, key signatures |
| `PitchAndDeterminismTest` | MIDI bounds, enharmonic octaves, seeded reproducibility |
| `ArtworkGeometryTest` | Region placement across five container sizes and both letterbox axes |
| `HomeActionTest` | Two-way mapping against the supplied map, nested-region hit order, edge clamping |
| `HarmonyLabAnalyzerTest` | Harness state mapping, unwritable-chord handling |

### Manual verification

- `./gradlew verifyHarmony` — passes
- `./gradlew assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk`
- Home-screen hit regions verified by rendering the artwork with every mapped region drawn over
  it; all twenty sit on their intended control
- **Not done:** installation on the target Samsung tablet, and any MIDI keyboard testing. No
  device is attached to this environment. 22_MANUAL_DEVICE_TEST_PLAN.md is still entirely
  outstanding and remains a release blocker.

### Known limitations

1. **`Cb` diminished sevenths are unwritable.** A `Cbdim7` needs a B triple-flat. The engine
   reports this rather than respelling; content must use `Bdim7`. The Phase 6 content
   validator should reject such roots. Pinned by
   `TranspositionTest.a chord that standard notation cannot write is reported rather than respelled`.
2. **Only the home screen has approved artwork.** Every other destination reports the phase it
   arrives in. `interface/maps/home.json` also points its `visualAsset` field at the malformed
   hyphenated file that has since been removed; nothing reads that field, but it should be
   corrected in the next export.
3. **Quartal and So What voicings are not in the formula registry.** Region 9 material arrives
   with Phase 11, where the chord label is deliberately allowed to be ambiguous.
4. **`targetSdk` is 36 against `compileSdk` 37**, per the planning baseline. The API 37
   validation and upgrade is a Phase 15 release task; `OldTargetApi` is set to informational in
   `config/lint/lint.xml` and must be re-enabled when that task is done.
5. **Kotlin is pinned to 2.2.10**, the version AGP 9.3.1 supplies through built-in Kotlin
   support. Raising it independently would produce `core:music` metadata the Android modules
   cannot read. Raise it with AGP.
6. **`NewerVersionAvailable` and `GradleDependency` are informational.** They compare against
   what was published today and would fail CI on a day nobody touched the repository.
   Dependency bumps are deliberate acts recorded in the version catalog.
7. **No navigation library yet.** Two destinations are switched by saved state in `AppRoot`.
   Navigation 3 and typed routes arrive in Phase 6 with the campaign back stack.

### Next phase prerequisites

Phase 2 — MIDI foundation. Needs:

- `core:midi` as an Android library module (production source uses `android.media.midi`)
- `MidiInputSource` and `PerformanceCapture` per 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §4
- A fake source so CI never needs hardware — 18_ACCEPTANCE_CRITERIA.md requires this
- The scripted MIDI test scenarios in 14_TESTING_AND_QUALITY.md §5, driven by
  `TestMonotonicClock` (already available in `core:testing`)
- A diagnostic screen; the `HomeDestination` table already reserves its place

Nothing in Phase 2 requires the interface artwork.
