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
exists, and the artwork is not yet in the repository.
