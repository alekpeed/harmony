# Harmony Gates — Coder Document Pack

**Project:** Native Android tablet jazz-harmony and sight-reading game  
**Language:** Kotlin only for application code  
**UI:** Jetpack Compose  
**Primary controller:** USB MIDI keyboard  
**Primary form factor:** Android tablet, landscape-first  
**Working title:** Harmony Gates

## Product statement

Harmony Gates is a jazz-piano learning game in which a real MIDI keyboard is the controller. The app presents a musical task visually, aurally, or in notation; the player responds on the keyboard; the app evaluates the actual MIDI performance; and mastery opens gates into progressively more advanced material.

The progression begins with fully guided chord construction and ends with independent recognition, sight reading, voice leading, altered harmony, rootless voicings, reharmonization, and jazz-functional fluency.

## Non-negotiable design rules

1. MIDI performance is the source of truth for played answers. Do not use microphone transcription for correctness checking.
2. Music-theory logic must live in one reusable, testable harmony domain layer. UI code must never decide whether a chord is musically correct.
3. Chord identity, chord spelling, bass note, inversion, voicing, register, omissions, doubling, extensions, and alterations are separate concepts.
4. Harmonic difficulty and assistance difficulty are separate axes.
5. A gate unlocks from demonstrated mastery, not arbitrary play time.
6. Generated exercises must be deterministic when supplied the same seed.
7. The app works offline after installation and local asset setup.
8. The first release is tablet-first but must remain adaptive instead of hard-locking one pixel size.
9. The application layer remains Kotlin. Do not introduce C++/NDK just to solve MIDI or audio.
10. Final visual styling is designed in Figma after this functional specification. Code should use tokens/components so the final Figma system can be applied without architectural rewrites.

## Android baseline for the first implementation

Use a version catalog and the latest stable mutually compatible libraries at implementation time. Current planning baseline, August 2026:

- compile SDK: API 37
- initial target SDK: API 36, with an explicit API 37 validation/upgrade task before public distribution if appropriate
- minimum SDK: API 26
- current stable Jetpack Compose line
- Material 3 and Material 3 Adaptive
- Navigation 3
- Android Architecture Components / ViewModel
- Kotlin Coroutines and Flow
- Room for durable structured learning data
- DataStore for preferences
- `android.media.midi` for MIDI discovery and transport
- `AudioTrack`-based Kotlin sampler for internal ear-training playback
- Compose Canvas for piano/staff/game visualizations

The current Compose August 2026 line requires compile SDK 37 and a recent Android Gradle Plugin. Do not pin dependency numbers in source files outside `libs.versions.toml`.

## Read order

1. `01_PRODUCT_AND_FUNCTIONAL_SCOPE.md`
2. `02_GAME_LOOP_AND_PROGRESSION.md`
3. `03_JAZZ_CURRICULUM.md`
4. `04_HARMONY_DOMAIN_ENGINE.md`
5. `05_MIDI_INPUT_ENGINE.md`
6. `06_PERFORMANCE_EVALUATION_AND_SCORING.md`
7. `07_EAR_TRAINING_ENGINE.md`
8. `08_SIGHT_READING_ENGINE.md`
9. `09_AUDIO_SAMPLER_ENGINE.md`
10. `10_ANDROID_ARCHITECTURE.md`
11. `11_DATA_MODEL_AND_PERSISTENCE.md`
12. `12_UI_UX_AND_FIGMA_HANDOFF.md`
13. `13_SCREEN_BEHAVIOR_SPEC.md`
14. `14_TESTING_AND_QUALITY.md`
15. `15_IMPLEMENTATION_PHASES.md`
16. `16_AGENT_EXECUTION_PROTOCOL.md`
17. `17_BUILD_CI_INSTALL_RELEASE.md`
18. `18_ACCEPTANCE_CRITERIA.md`
19. `19_BACKLOG_AFTER_V1.md`
20. `PROMPT_TO_CODING_AGENT.md`
21. `AGENTS.md`
22. `REFERENCES.md`

## Proposed repository structure

```text
/
├── app/
├── core/
│   ├── music/
│   ├── midi/
│   ├── audio/
│   ├── data/
│   ├── designsystem/
│   └── testing/
├── feature/
│   ├── home/
│   ├── campaign/
│   ├── chordgate/
│   ├── eartraining/
│   ├── sightreading/
│   ├── progression/
│   ├── voicelab/
│   ├── curriculum/
│   ├── profile/
│   └── settings/
├── content/
│   ├── curriculum/
│   ├── exercises/
│   └── examples/
├── docs/
├── .github/workflows/
├── gradle/libs.versions.toml
├── AGENTS.md
└── README.md
```

## Definition of project completion

The project is complete when a player can connect a class-compliant USB MIDI keyboard to the tablet, complete onboarding, enter the campaign, receive chord/ear/sight-reading tasks, answer on the real keyboard, receive immediate musically correct feedback, accumulate skill-specific mastery, unlock later gates, resume exactly where they left off, and use free-practice modes independently of campaign progression.
