# Prompt to Coding Agent

You are implementing **Harmony Gates**, a native Android tablet jazz-harmony learning game. The complete specification is in this repository's coder document pack.

Read `00_README_START_HERE.md`, `AGENTS.md`, and all numbered documents before making architectural changes. Treat those documents as the product specification.

The app is Kotlin + Jetpack Compose. A USB MIDI keyboard is the primary controller. Do not use microphone transcription for performance correctness. Keep the music-theory domain independent of Android UI. Keep MIDI behind an interface with a fake source for tests. The project must work offline. The baseline implementation must remain full Kotlin and must not introduce C++/NDK.

Work strictly in the phases defined by `15_IMPLEMENTATION_PHASES.md`. Do not skip directly to polished UI. The required order is music-domain correctness, MIDI, performance capture/evaluation, a minimal real-keyboard vertical slice, persistence/mastery, campaign, assistance, audio/ear training, sight reading, jazz voicing/progression systems, advanced harmony, then final Figma-driven UI implementation.

For each phase:

1. inspect the existing repository and preserve working code
2. state/record the phase acceptance criteria
3. implement only what is needed for that phase plus unavoidable foundations
4. write tests as functionality is added
5. run tests, lint, and debug assembly
6. update `docs/IMPLEMENTATION_STATUS.md`
7. commit the phase in coherent commits
8. merge/land the phase before beginning work that depends on it

Do not invent unsupported music-theory behavior. When a rule is context-dependent, encode it as an exercise/voicing policy. Preserve enharmonic spelling and degree identity; do not reduce all harmony to pitch-class sets.

The final interface will be designed in Figma. Before that design exists, build clean functional Compose components with design tokens and stable state contracts. Once Figma is supplied, implement the actual visual system from Figma without rewriting the music/MIDI/game architecture.

Start with Phase 0 and Phase 1 only unless explicitly instructed to continue farther.
