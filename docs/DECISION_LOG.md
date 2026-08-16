# Decision Log

Choices that were not forced by the specification, and why they were made this way. Music-theory
decisions matter most here: 21_CONTENT_AUTHORING_GUIDE.md §7 warns against declaring
context-dependent jazz practice as universal law, and the only defence is writing down what was
decided and on what grounds.

---

## D1 — A chord degree is a diatonic number plus a chromatic alteration

**Phase 1.** `ChordDegree(number, alteration)` rather than a flat enum of degree names.

`#9` becomes `ChordDegree(9, +1)` and `b3` becomes `ChordDegree(3, -1)` — two values that can
never compare equal, satisfying 04_HARMONY_DOMAIN_ENGINE.md §4 structurally rather than by
convention. The pair also drives spelling for free: the number fixes the letter, the alteration
and root fix the accidental. All three worked examples in §6 (`Db7` → Cb, `F#maj7` → E#,
`C7#9` → D#) fall out of that one rule with no lookup table, and all 74 golden fixtures passed
on the first run.

---

## D2 — Chord formulas separate *permitted* from *written*

**Phase 1.** `ChordFormula` carries `requiredDegrees`, `optionalDegrees`, `forbiddenDegrees` and
an optional `stackOverride`.

Required-versus-optional alone could not express a dominant thirteenth. The natural eleventh is
available to a player — marking it wrong would fail correct answers, and forbidding it would
encode a style preference as theory — but a written C13 does not contain an F. Those are two
different questions, so they get two different fields. `stackOverride` answers "what is
written"; `permittedDegrees` answers "what is allowed".

The altered dominant uses the same split from the other side: only the guide tones are
required, all four alterations are optional, and the natural 5, 9, 11 and 13 are genuinely
forbidden because they contradict what the symbol asserts.

Contentious cases are commented at each formula in `ChordFormulas.kt`.

---

## D3 — Unwritable chords are refused, never respelled

**Phase 1.** `SpellingResult.Overflow` instead of an enharmonic substitution.

A `Cb` diminished seventh needs a B triple-flat. Handing back an `A` would be the same sound and
a different chord on the page, and would be undetectable in testing — exactly the silent failure
the spelling rules exist to prevent. Found by a seeded property test rather than by inspection,
which is itself an argument for keeping the property tests.

`chordTones` throws (a content authoring error); `trySpell` reports (for anything driven by text
input). The harness screen uses the second and shows a message.

---

## D4 — Voice leading uses optimal assignment, not index pairing

**Phase 1.** Hungarian algorithm over a padded cost matrix.

For two equal-length sorted voicings, index pairing happens to be optimal, so the algorithm
earns its place on the unequal case — which 04_HARMONY_DOMAIN_ENGINE.md §11 explicitly calls
out. Padding rows and columns at the add and drop penalties turns "some voices have no partner"
into an ordinary assignment problem, so a four-note chord answering a three-note one reports a
new voice rather than inventing an octave leap. `VoiceLeadingTest` pins a case where index
pairing reads eleven semitones of movement and the musical answer is seven.

The result is deterministic because voice-leading scores are shown to players and must not
wobble between runs.

---

## D5 — Voicing generation is deterministic and bounded

**Phase 1.** Fixed subset enumeration order, a stable comparator, and a hard cap of 64 results.

Non-negotiable design rule 6 requires seeded exercises to reproduce. That only holds if
generation itself is reproducible, so ties break on span, then bass, then note-by-note — never
on set iteration order. The cap and the six-degree enumeration limit stop a deliberately wide
policy from stalling an exercise generator.

---

## D6 — Kotlin is pinned to whatever AGP supplies

**Phase 0.** `kotlin = "2.2.10"`, matching AGP 9.3.1's built-in Kotlin.

AGP 9 removed the separate `org.jetbrains.kotlin.android` plugin and compiles Android modules
with its own bundled Kotlin. A newer Kotlin in the version catalog would compile `core:music`
to metadata the app module could not read. "Latest stable mutually compatible" means AGP sets
the ceiling.

---

## D7 — Two dependency-freshness lint checks are informational

**Phase 0.** `NewerVersionAvailable` and `GradleDependency` report but do not fail.

Both compare against what was published this morning, so with `warningsAsErrors` they fail a
build on a day nobody touched the repository. Version bumps are deliberate acts recorded in the
version catalog. Everything else lint reports remains fatal. `OldTargetApi` is also
informational, but for a different reason — the trailing `targetSdk` is the specified baseline,
and its upgrade is tracked as a Phase 15 task.

---

## D8 — No navigation library until there is a back stack

**Phase 1.** Two destinations switched by `rememberSaveable` state in `AppRoot`.

10_ANDROID_ARCHITECTURE.md §7 calls for Navigation 3 and typed destinations, and Phase 6 is
where that lands — with the campaign, gate intro, exercise and result flow that actually needs
a back stack. Introducing a router for two screens would be scaffolding with no load on it, and
the alpha API would be a dependency held for several phases before doing any work. The one
behaviour a router would add here — surviving rotation and process death — is provided by
saved state.

---

## D9 — Interface hit regions are stored as fractions

**Phase 1, at the user's request.** `NormalizedRect` holds 0..1 fractions of the artwork, never
device pixels.

`interface/README.md` step 4 asks for responsive layout rather than one-device coordinates. A
region measured against the approved 1536 × 1024 frame then lands correctly at any tablet size
and in split-screen, and the artwork is fitted rather than cropped so no control can be pushed
off-screen on an unusual aspect ratio.

The home fallback deliberately does not imitate the approved design.
16_AGENT_EXECUTION_PROTOCOL.md §8 forbids approximating from prose once a Figma source of truth
exists; it is only reached when no usable artwork is present.

---

## D10 — Interface assets are validated by the build

**Phase 1.** `checkInterfaceAssets` fails the build on a file named like an image that is not one.

The first upload of the approved artwork arrived as 14,997 bytes containing no JPEG markers at
all. Nothing in a normal build would have caught it: AAPT does not decode a file it only
copies, so it would have surfaced as a blank home screen on a tablet, a long way from its
cause. The check reads image headers directly rather than using a decoder, because the job is
to say "this is not an image" about files no decoder would accept.

---

## D11 — The interaction map is parsed, not transcribed

**Phase 1.** `interface/maps/home.json` is copied into `R.raw` and read at runtime.

Transcribing twenty rectangles into Kotlin would work once and rot on the first re-export. The
map carries a `schemaVersion`, which says it is meant to be consumed as data. Regions bind on
the semantic `action` id first and the `figmaLayer` name second — a designer may rename a layer
for tidiness, but `navigate_ear_trainer` says what it is for.

An unknown layer is skipped rather than fatal, since a design file may gain a control before
the app has a destination for it. `HomeActionTest` reads the supplied map directly and fails
if the two drift apart, so a skip is never silent.

---

## D12 — Nested hit regions are ordered by area

**Phase 1.** `ArtworkSpec.regionsInHitTestOrder` sorts largest first.

The approved frame puts `HIT / Continue` inside `HIT / Next Gate Card`. Laid out in map order
the card would cover the button and swallow every tap on it. Sorting by descending area fixes
that case and any future nesting without either the map or the app needing to know about it;
ties break on id so the order does not depend on how the map happened to be written.

---

## D13 — Meaning is parsed outside the Android layer

**Phase 2.** `MidiMessageParser` and `ActiveNoteTracker` are plain Kotlin inside `core:midi`;
`AndroidMidiInputSource` only discovers devices, opens a port and forwards bytes.

The parser is where the real difficulty is — running status, real-time bytes landing between
the data bytes of another message, messages split across reads — and none of it needs a device
to test. Keeping it out of the platform class turned the whole problem into 24 ordinary unit
tests. What remains in the Android class is thin enough that its untested state is a much
smaller risk.

---

## D14 — Three note sets, not one

**Phase 2.** `ActiveNotes` carries `physicallyHeld`, `pedalSustained` and a derived `sounding`.

05_MIDI_INPUT_ENGINE.md §5 asks for the distinction, and the reason shows up immediately: with
the pedal down, "did you play a C major seventh?" and "are your fingers on a C major seventh?"
have different answers, and an exercise needs the second while a listener hears the first. A
single "active notes" collection would have to pick one and be silently wrong about the other —
which is exactly how a correct answer gets marked wrong after a pedal is held across a chord
change.

`clearSustainedRemnants` exists for the same reason: arming a new attempt should drop the
previous chord's pedal wash without lifting fingers off keys.

---

## D15 — The MIDI callback never suspends

**Phase 2.** The receive path uses `tryEmit`, not a suspending `emit`.

10_ANDROID_ARCHITECTURE.md §10 requires the MIDI callback to normalise quickly and get out of
the way. A suspending emit could block the transport thread behind a slow collector and skew
the onset timestamps a chord is later judged by. Active-note state is updated before the emit,
so what is sounding stays correct even in the case where a log line is dropped — the state is
authoritative, the event stream is a notification.

---

## D16 — MIDI setup lives behind the Settings control

**Phase 2.** `HIT / Nav Settings` opens the diagnostics screen.

The approved home frame has no MIDI region, and inventing one would mean editing artwork that
has been signed off. MIDI setup genuinely belongs under settings, so the Settings control opens
the one setting that exists today. When the full settings screen arrives in Phase 6 this becomes
a row inside it rather than the whole destination.

---

## D17 — The simulator ships in the app, not only in tests

**Phase 2.** The diagnostics screen can swap the real source for `FakeMidiInputSource`.

The screen has to be checkable on a tablet with nothing plugged in, which has been this
project's situation throughout. It also makes the disconnect path testable by hand: "pull the
cable" exercises the reconnect flow without anyone touching a cable. Both sources implement the
same interface, so the screen cannot tell which it has — which is the same property that makes
CI work.

---

## D18 — A note is a slip only if it was released while the chord was still arriving

**Phase 3.** The accidental-note filter tests *when* a key came up, not only how briefly.

The first version discarded any note released inside the grace period, which discarded every
note of a short chord: letting go is how a player finishes. The scripted scenarios caught it
immediately — a correct Cmaj7 evaluated as an empty attempt.

The rule that works asks whether the release happened before the final onset. Released early and
briefly is a slip, like catching the black key next door. Released after the last note arrived is
the player finishing. The same test governs pedal remnants: released early, under the pedal, is
something ringing rather than something being played.

---

## D19 — Lifting your hands with the pedal down does not end a chord

**Phase 3.** Capture waits for the quiet window while the sustain pedal is held.

Completing on "every key released" ended the attempt on a note that was only a sustained
remnant, before the real chord had been played. It is also just wrong musically: with the pedal
down the notes are still sounding and the player may still be playing. Waiting for silence is
what lets a remnant be recognised as a remnant.

---

## D20 — Three matching modes, not one comparison

**Phase 3.** The evaluator branches on the requirement type rather than reducing everything to
a note comparison.

"Play any Cmaj7" is a question about pitch classes; the octave is not part of the answer. "Play
this voicing" is about exact notes *including doublings*, so it compares multisets — a set would
silently agree that a doubled root is the same as a single one. "Play a rootless G13" is about
degrees, and a generic "root missing means incorrect" rule would fail the whole of Region 7.

One comparison routine would have to pick one of the three and be wrong about the other two.

---

## D21 — Errors are ranked, not listed

**Phase 3.** `PerformanceError.rank` orders feedback by educational importance.

06_PERFORMANCE_EVALUATION_AND_SCORING.md §8 asks for this explicitly. A player who is on the
wrong chord *and* spread it too wide should be told about the chord; mentioning the spread first
would be technically complete and pedagogically useless. Missing a guide tone outranks missing a
fifth for the same reason — one changes what the chord is, the other usually does not.

---

## D22 — The capture contract is domain, the capture implementation is transport

**Phase 4.** `CapturePolicy`, `CaptureState` and the `PerformanceCapture` interface moved from
`core:midi` into `core:music`; `OnsetAggregator` and `MidiPerformanceCapture` stayed.

The session engine needs capture and evaluation, and putting it in `core:midi` would have made
the whole game loop depend on an Android library for no reason. Splitting on "is this about MIDI
or about performance" put the line in the right place: a quiet window is a musical decision, and
a byte stream is not. The engine is now plain Kotlin and fully unit-tested.

---

## D23 — A presentation model omits, rather than hides

**Phase 4.** `ExercisePresentationModel` carries null for a channel the exercise did not enable.

10_ANDROID_ARCHITECTURE.md §5 makes the mapper responsible for which assistance channels are
visible. Handing a screen the note names and trusting it not to draw them would put the
assistance system's correctness in the UI layer, where one careless composable leaks the answer.
Absent is safer than hidden.

---

## D24 — Arming is automatic

**Phase 4.** Capture arms as soon as a target is on screen and a keyboard is connected.

A "ready?" tap before every chord would put a finger-off-the-keys interruption between the
player and the instrument twenty times a session. 06_PERFORMANCE_EVALUATION_AND_SCORING.md §7
already prevents the failure this would guard against — capture never submits on a first note —
so the tap would cost something and buy nothing.

A timed gate may want it back, which is why arming is an engine call rather than a rule inside
the screen.

---

## D25 — Test collectors need an eager dispatcher

**Phase 4.** The session engine's tests run on `UnconfinedTestDispatcher`.

With the default `StandardTestDispatcher`, the engine's `SharedFlow` collectors had not yet
subscribed when the test delivered an attempt, and a replay-free `SharedFlow` drops what nobody
is listening for — so six tests failed against correct production code. Worth recording because
the symptom (state stuck at `Armed`) looks exactly like an engine bug and is not one.

---

## D26 — A named voicing family is a policy, not a voicing

**Phase 10.** `VoicingFamilies.recipe(family, chord)` returns a `VoicingPolicy`, never a fixed
`Voicing`.

Region 7's whole point is that a rootless A on `G7` has many correct renderings — any register,
any spacing, either hand. Storing one canonical voicing per chord would have made all the others
wrong, so the family says what must sound, what must not, and what has to be lowest, and the
evaluator accepts anything that satisfies it. Two shapes with identical pitch content — the two
shell inversions, rootless A against rootless B — are told apart by bass and top note, which is
also how a player hears the difference and how a mistake gets diagnosed.

---

## D27 — A rootless family extends the chord it is asked about

**Phase 10.** The recipe for a rootless voicing of `C7` carries a chord containing the ninth and
thirteenth, while the orb still shows `C7`.

`degreeOf` names a pitch class by looking it up in the chord's own degrees, so a ninth the chord
did not know about would come back as "a note that is not part of the chord" — true of the
symbol and useless to a player who left out a tone the shape requires. Extending the chord makes
the diagnosis "missing the ninth". The display symbol is kept separately, so what is asked for
and what is judged can differ without the screen ever knowing.

---

## D28 — The fifth is droppable, the altered fifth is not

**Phase 10.** An any-voicing progression run accepts a seventh chord with no fifth.

`DegreeRole` already calls the fifth "usually the first tone a jazz voicing drops", and a run
that rejected `Dm7` played as D-F-C would be arguing with every jazz pianist alive. Two limits
keep it honest: only a perfect fifth, because the b5 of a `m7b5` and the #5 of an altered
dominant are what those chords *are*; and only from a chord with a seventh or sixth, because a
triad without its fifth is not a voicing of the triad, it is two notes.

---

## D29 — One accepted chord locks the track until the keyboard is quiet

**Phase 10.** After a correct chord, the run stops evaluating until nothing is sounding.

`interface/PROGRESSION_RUN_HANDOFF.md` §7 requires that a held voicing or a sustain pedal must
not advance twice, and that a new qualifying note state is needed before the next acceptance.
The gate is set only when notes are actually down: most chords finish *by* being released, and
gating on a release that has already happened would leave the run waiting forever.

---

## D30 — The track animates one number, so advances cannot overlap

**Phase 10.** Every orb is drawn one slot further out than it has settled into, closing to zero
over a single animation keyed to the run's advance count.

The handoff asks for no overlapping advance animations, and offers a queue as the remedy. One
shared progress value is the safer equivalent it allows: a second advance arriving mid-flight
restarts the same animation rather than starting a competing one, so there is no queue to drain,
no state to get stuck in, and the domain never has to know how long a transition takes.

---

## D31 — The track geometry and timing are read from the map, not transcribed

**Phase 10.** The slot path, advance duration and easing curve come out of
`interface/maps/progression-run.json` at runtime.

§11 of the handoff names that file "the current runtime/rendering contract". Transcribing eight
coordinate pairs into Kotlin would have made a re-composed track a code change, and would have
put a second copy of the numbers somewhere to drift. The map's non-track hit regions are
deliberately *not* read: the map marks them as pending remapping from the approved `77:2` frame,
so the run's own controls are laid out in Compose instead of against invented coordinates.

---

## D32 — Mastery is domain logic, not storage logic

**Phase 5.** `SkillMastery`, `MasteryUpdater` and `MasteryEvidence` live in `core:music`;
`core:data` only reads a row, hands it to the updater and writes the answer back.

The alternative — computing the estimate inside the repository — would have made the number that
gates a player's progress untestable without a database, and would have put a teaching judgement
(a hint is worth half an independent answer) inside a persistence class. It follows D22's line:
the contract is domain, the implementation is transport.

---

## D33 — The evidence grade is stored, the weight is not

**Phase 5.** An attempt row records `INDEPENDENT_CORRECT`, not `1.0`.

Whether the player was helped is a fact about that attempt and will never change. What that help
is worth is a teaching policy that may. Storing the fact and applying the policy on read is what
lets `rebuildMastery` re-score old history under new weights without breaking
11_DATA_MODEL_AND_PERSISTENCE.md §4's rule against silently reinterpreting old attempts.

---

## D34 — A gate is complete because the evidence says so

**Phase 5/6.** `gate_progress` stores only *when* a gate was first passed, never *that* it was.

A stored "complete" flag can disagree with the attempts behind it — after a rebuild, after a
content edit, after a bug. Deriving status from mastery every time the map is drawn means the two
can never diverge, and makes 15_IMPLEMENTATION_PHASES.md's "prerequisites/unlocks deterministic"
true by construction rather than by careful bookkeeping.

---

## D35 — Room's schema is tested on the JVM, not on a device

**Phase 5.** `RoomSchemaTest` runs the exported DDL against `sqlite-jdbc`.

Room's own `MigrationTestHelper` needs an emulator, and there is not one here. What it needs the
emulator *for* is running SQL — and the exported schema is ordinary DDL. Running it in a JVM
SQLite proves the tables really can be created, that a deleted profile takes its attempts with
it, and that the primary keys hold, none of which should have waited for hardware. The migration
*chain* is checked separately, so raising the version without adding a step fails the build.

---

## D36 — A voicing family is authored intent, resolved per chord

**Phase 6.** `ExercisePolicy` gained `voicingFamily`, alongside the existing `voicingPolicy`.

Found by the content test rather than by design: a policy asking for rootless A voicings could
not be built at all, because `ChordPolicy` mode demanded a concrete `VoicingPolicy` and a
rootless A of `Cmaj7` is a different set of tones from one of `G7`. The family is what an author
means; the concrete policy is what a particular chord makes of it, so resolution moved to
generation time.

---

## D37 — Content validation delegates rather than duplicating

**Phase 6.** `./gradlew validateHarmonyContent` depends on `core:data`'s unit tests.

21_CONTENT_AUTHORING_GUIDE.md §9 asks for the task by name. Implementing the checks a second time
inside a Gradle task would have created two validators, and the one that mattered — the one the
app actually runs on startup — would not have been the one CI checked. The tests load the
authored files through the production decoder and the production validator.

---

## D38 — KSP forced a relaxed AGP check

**Phase 5.** `android.disallowKotlinSourceSets=false` is set in `gradle.properties`.

KSP 2.2.10-2.0.2 — the only KSP built against the Kotlin that AGP 9.3.1 supplies — registers its
generated sources through `kotlin.sourceSets`, which AGP 9 rejects by default. The KSP release
that uses `android.sourceSets` needs a newer Kotlin, and raising Kotlin alone would produce
`core:music` metadata the app module could not read. AGP's own error message suggests this flag;
it comes out when the two line up again.

---

## D39 — Substitution and diminished spellings prefer the interval, then the chart

**Phase 11.** `Substitutions` and `DiminishedSystems` transpose roots by a `SpelledInterval` and
fall back to a practical spelling only when the exact one needs more than one accidental.

A semitone count cannot tell `C#` from `Db`, and both appear in this material for different
reasons: the passing chord between `Cmaj7` and `Dm7` is `C#dim7` because it rose from C, while
the tritone substitute for `G7` is `Db7` because it falls to C. So the interval decides the
letter. But the letter it decides is sometimes unwritable in practice — a diminished fifth above
`Eb` is `Bbb`, and a chart says `A7` — so a root needing a double accidental is respelled. The
line is drawn at one accidental rather than at what the engine *can* spell, because `Bbb7` is
legal notation that no player has ever read.

---

## D40 — An alteration pool holds sets, not alterations

**Phase 11.** `ExercisePolicy.alterationPool` is a `List<Set<DominantAlteration>>`.

Region 10 teaches altered dominants as transformations of a plain dominant, so the pool alters
the formula the policy already names rather than listing `dominant_seventh_flat_nine` as a chord
of its own — which would have meant a new formula per combination, thirty of them, each able to
disagree with the others. Each entry is a set because `C7#9b13` is one exercise: a flat list of
alterations would have read as an instruction to sound all of them at once. A pool applied to a
chord that is not a dominant leaves it alone, since the authored formula is the lesson and
inventing a `Cmaj7b9` to satisfy the pool would be teaching something else.

---

## D41 — The palette is sampled from the approved artwork, and there is one of it

**Phase 12.** `ApprovedColors` in `Tokens.kt`; `HarmonyTheme` no longer switches on system dark
mode.

The two approved screens in `interface/` are painted plates. A Compose layer drawn over one has
to be in the plate's own palette or it sits beside the artwork rather than on it, so the colours
were sampled out of the plates rather than chosen — warm near-blacks, brass and amber accents,
warm off-white text. For the same reason there is no light theme to switch to: a Compose layer
that turned pale in light mode would be drawn on artwork that did not turn with it. Contrast is
met inside the dark palette instead, and `TokenTest` measures every text-on-surface pair against
WCAG AA rather than trusting that it looks fine.

Two token groups are not in the artwork — feedback colours and piano key surfaces, since neither
approved screen contains a verdict or a keyboard. They are marked provisional in the file. That
they can be provisional at all is the point of the next entry.

---

## D42 — Key state maps to shape before it maps to colour

**Phase 12.** `KeyPaintings.of()` is a pure function in a file with no access to the palette.

12 §7 says "do not encode these states only by color" and lists nine layered states. Making the
mapping return a fill *name*, a marker and an outline — rather than a `Color` — turns that rule
into something a test can check: `KeyPaintingTest` asserts that no two of the nine share both a
marker and an outline, so the set stays distinguishable with the colour removed entirely. It also
means the provisional key palette can be replaced by the Figma pass without any state becoming
ambiguous in the meantime.

The precedence — verdict over held over role — is pedagogical rather than technical. A required
tone played wrong must stop saying "required" the moment the player has been told the answer;
what they need then is the marker about what they did.

---

## D43 — A progression is authored as roman numerals, not as Kotlin

**Phase 13.** `RomanNumeralParser`, plus `content/progressions/progressions.json`.

21_CONTENT_AUTHORING_GUIDE.md §1 asks for content to be data wherever existing domain types can
express the musical behaviour. A progression already *was* expressible — it is a list of
`FunctionalChord` — but there was no way for an author to write one down, so every tune would
have been a new entry in `Functions` and a recompile. The parser reads what a chart writes, and
the mandatory vocabulary of Region 12 (the blues, rhythm changes, turnaround variants,
descending dominants) is now eleven entries in a JSON file.

Case carries meaning, exactly as it does on a chart: `ii7` is the two chord and `II7` is the
dominant of the five, and that difference is the only thing the capitals say. It applies only to
the figures that are ambiguous alone — a bare numeral, and plain `7`, `6`, `9`, `11`, `13`. An
explicit suffix overrules it, so `IIm7` and `ii7` are one chord written two ways.

Two things the tests caught. The slash in `I6/9` is part of the quality, not a secondary target,
so a slash only separates a target when what follows it is a numeral — the same distinction the
chord parser draws for a slash bass. And `i(maj7)` has to reach the minor-major seventh even
though `maj7` on its own is a major seventh: a lowercase numeral has already said "minor", so the
`m`-prefixed alias is tried first there and second everywhere else.

---

## D44 — A gate's activity is derived from what its policy names

**Phase 13.** `ExercisePolicy.activity`, over `earTaskFamily`, `progressionId` and
`readingMaterial`.

The ear-training and sight-reading engines were finished in phases 8 and 9 and no content could
reach them, because a policy could only describe a chord exercise. Rather than a `kind` field
that could disagree with the rest of the policy, the activity is derived from which of the three
markers is set — the same reasoning as gate status, which is derived from evidence and never
stored. Setting two markers is refused at construction: a gate is one activity.

`GateActivity` names four *inputs* — hands, ears, a progression, a staff — and not four screens.
01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §5 is explicit that the exercise screen is compositional.

---

## D45 — A drop voicing is named on the policy, not built as a recipe

**Phase 13.** `VoicingFamilies.transformFamilies` and `authorable`.

Authoring the Region 8 gates failed the content validator: `DROP_2` is not in
`VoicingFamilies.supported`, because a drop voicing is not a choice of degrees — it is the same
four notes with one moved down an octave, so there is nothing for `recipe` to return. The
realizer already builds them by transforming a close voicing when a `VoicingPolicy` names the
family. So the generator now passes a transform family through to the policy instead of asking
for a recipe, and the content check accepts either route. The drop gates are authored as
`ExactVoicing`, because a drop voicing is judged on its spacing: the same four tones in close
position are a different answer.

---

## D46 — A backup carries evidence, not conclusions

**Phase 14.** `ProgressBackup` exports sessions, attempts and gate completions. It does not
export skill mastery.

Mastery is derived from the attempts, so a file carrying an estimate could be restored into a
build whose weighting had changed and would then disagree with its own attempt history forever.
The import calls the same `rebuildMastery` the app uses, which means a restored profile is
scored by exactly the code that scores a played one. It is the same rule the campaign already
follows for gate status: store what happened, re-derive what it means.

Attempts whose session is not in the file are dropped rather than imported — the schema makes a
session an attempt's parent — and the count of dropped attempts is returned rather than swallowed.
A truncated import that says nothing is how somebody loses six months quietly.

The decisions live in `ProgressBackupService` behind a narrow `ProgressBackupStore`, so all of
the above is tested on the JVM. Room's own round trip still needs an emulator; these rules do
not, and they are the part that can be wrong.

---

## D47 — Determinism is the whole of the process-death story

**Phase 14.** `SessionRestoration` saves four values: session id, seed, gate id, position.

The exercise generator is deterministic — same seed, same position, same exercises — so a
killed session does not need its exercises saved. It needs the two numbers that regenerate them.
The attempts already answered were written to the database as each one was judged, so they were
never at risk.

A half-written saved state returns null rather than a partly-filled object. Resuming under an
old session id with a lost seed would attach new attempts to a session that was about something
else, and starting fresh is a better outcome than that. The current attempt is deliberately not
restored: a player whose tablet was killed mid-chord gets that exercise again rather than being
marked wrong for it.

---

## D48 — The performance budgets that can be measured here, are

**Phase 14.** `PerformanceBudgetTest` measures chord evaluation and exercise generation against
14 §8's own numbers.

"Measure rather than assume" applies to whatever can be measured. Two of the five budgets are
pure computation and run anywhere; the other three — MIDI-to-pixel latency, audio glitches,
dropped Compose frames — need the tablet, and the status note says so rather than implying
coverage that does not exist.

The measurements warm up and then take a median rather than a mean, because on a shared build
machine one run in twenty is interrupted by something unrelated and a mean lets that run fail a
build that is not broken. They are there to catch an order-of-magnitude regression — a realizer
that started allocating per note — not to certify a number that would only be true of this
machine.
