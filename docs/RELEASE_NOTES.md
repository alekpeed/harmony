# Release Notes

## 0.1.0 — first build

App version 0.1.0 · content version 0.2.0 · content schema 1

A jazz-harmony game for Android tablets, played on a real MIDI keyboard. This is the first
build that contains the whole campaign. It has never run on hardware.

### What is in it

**The music engine.** Thirty chord formulas in all twelve keys, spelled the way a musician
writes them rather than by pitch class — a `Cb` diminished seventh is refused instead of being
silently respelled as an `A`. Voicings, voice leading, functional harmony, transposition that
round-trips exactly. Altered dominants, diminished systems, substitutions and quartal voicings.

**The keyboard.** A MIDI parser that survives running status, interleaved clock bytes and split
reads; sustain-pedal semantics kept separate from fingers; hotplug; and a simulator so that all
of it is tested without hardware.

**Deciding what you played.** Onset aggregation turns a rolled chord into one attempt, then one
evaluator — used by every game mode — decides what was right and names what was wrong in
musical terms: a wrong quality, a missing guide tone, the wrong note in the bass.

**The campaign.** Seventeen regions and forty-three gates, from a first note on a keyboard
through sevenths, functional harmony, ear training, reading, jazz voicings, drops, extensions,
sus and slash chords, quartal structures, altered dominants, diminished systems, substitutions,
the progression vocabulary, voice leading, and comping a chart. A gate opens because the stored
evidence satisfies a rule an author wrote — never because a counter reached a number.

**Progress that survives.** Room at schema 1, with every attempt kept in enough detail to
reproduce it: the instance, the seed, and the full event history. Skill mastery is derived from
that evidence and re-derived whenever it is needed. Progress exports to a JSON file and imports
back.

**Two ways to practise, playable now.** A chord gate: a symbol appears, you play it, the app
tells you what was wrong. And Progression Run: a progression comes towards you along a track,
and each chord you get right pulls the next one closer.

**Ear training and reading.** A pure-Kotlin mixer and synthesised piano, four ear-exercise
families, and a staff that grades pitch and rhythm separately — playing the right notes late is
a timing problem, and playing wrong notes exactly on the beat is not.

### Known limitations

These are the honest list. The status doc has the full version.

1. **Nothing has run on hardware.** No tablet and no MIDI keyboard have ever been attached. The
   manual device test plan is entirely outstanding and remains the release blocker.
2. **The capture thresholds have never been played on.** An 80 ms quiet window, a 300 ms roll,
   a 70 ms grace for a brushed key — all plausible, none measured against fingers.
3. **No audio has been heard.** The mixer is verified by rendering buffers and reading the
   samples back. That proves the arithmetic, not that it sounds like a piano.
4. **The database has never been closed and reopened.** The encoding is tested and the exported
   schema runs against a real SQLite, but Room's own round trip needs an emulator.
5. **Only chord gates are reachable from the app.** Ear, reading and progression gates have
   content, policies and engines, and no screen launches them yet.
6. **The visual design is half done.** Two screens are approved as painted plates; the design
   system's tokens and components exist and are tested, and the remaining screens have not been
   designed.
7. **Export and import have no button.** The service exists and is tested; no settings screen
   calls it.
8. **Accessibility has been passed over one screen.** The exercise screen. The others have not
   been audited.
9. **Region 0's calibration is a chord exercise,** not the note-on/off, sustain and latency
   check the curriculum describes.
10. **No crash reporting, deliberately.** See `docs/RELEASE.md` §7.

### Verified by

611 unit tests, 0 failing, on every commit. Notable coverage: 360 chord/key combinations
checked for spelling, letter and interval invariants; 74 hand-derived golden fixtures; 1,000
seeded transposition round trips; every authored progression placed in all twelve keys; and all
48 exercise policies generating 100 playable exercises each.

Chord evaluation and exercise generation are measured against the budgets in
14_TESTING_AND_QUALITY.md §8 on every run. Latency, audio and frame budgets need the tablet.
