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
