# Android Architecture

## 1. Architecture goals

- testable music logic
- replaceable MIDI source
- deterministic exercise generation
- lifecycle-safe sessions
- offline-first
- tablet/adaptive UI
- minimal coupling between game presentation and domain rules

## 2. Module graph

```text
app
 ├─ feature:home
 ├─ feature:campaign
 ├─ feature:chordgate
 ├─ feature:eartraining
 ├─ feature:sightreading
 ├─ feature:progression
 ├─ feature:voicelab
 └─ feature:settings
       ↓
core:designsystem   core:data
       ↓              ↓
core:music   core:midi   core:audio
       ↓
core:testing (test helpers only)
```

Feature modules may depend on core modules. Core modules must not depend on features.

## 3. State flow

Follow unidirectional data flow.

```text
UI event
  -> ViewModel intent
  -> use case/domain operation
  -> repository/session engine
  -> StateFlow<UiState>
  -> Compose
```

One-off events should be modeled carefully; avoid global event buses.

## 4. Exercise session engine

Build a reusable engine independent of screen type.

```kotlin
interface ExerciseSessionEngine {
    val state: StateFlow<ExerciseSessionState>
    suspend fun start(config: SessionConfig)
    suspend fun submit(attempt: PerformanceAttempt)
    suspend fun requestHint()
    suspend fun replayStimulus()
    suspend fun skip(reason: SkipReason)
    suspend fun stop()
}
```

The chord, ear, and reading features may have specialized wrappers, but session lifecycle should not be duplicated.

## 5. Presentation model

`ExerciseInstance` is domain-rich; the UI consumes `ExercisePresentationModel` produced by a mapper.

This mapper determines what assistance channels are visible without altering the underlying answer.

## 6. Dependency injection

Use a lightweight DI approach appropriate to project scale. Hilt is acceptable if used consistently. Manual DI is also acceptable if it remains explicit and testable.

Do not introduce a service locator hidden behind global singletons.

## 7. Navigation

Use Navigation 3 and typed destinations.

Conceptual routes:

```text
Home
Campaign(regionId?)
GateIntro(gateId)
Exercise(sessionId)
GateResult(sessionId)
QuickPractice
EarTrainingSetup
SightReadingSetup
VoiceLab
Progress
Settings
MidiSetup
```

Do not pass large domain objects through navigation. Pass IDs and retrieve state from repositories/session owners.

## 8. Adaptive UI

Use window-size/adaptive APIs. Although the target is a Galaxy tablet, do not assume one exact resolution or orientation.

Landscape target layout can use:

- navigation rail or compact sidebar
- primary task pane
- secondary information pane
- persistent MIDI status

Portrait can collapse secondary information below or into a sheet.

## 9. Lifecycle

A running exercise must survive normal configuration/window changes without losing its current target.

When app goes background:

- pause timed exercise
- persist resumable session snapshot if safe
- keep no stale MIDI note state

On resume:

- verify MIDI device state
- require re-arm/count-in for timed tasks

## 10. Threading

- UI state mutations: Main dispatcher
- database/content IO: IO dispatcher
- exercise/theory calculations: Default when nontrivial
- MIDI callback: normalize quickly and emit without heavy work
- audio render: dedicated high-priority audio thread controlled by sampler

Never parse curriculum JSON or run database queries in a MIDI callback.

## 11. Error handling

Use typed errors:

```text
MidiUnavailable
MidiDisconnected
ContentInvalid
AudioAssetMissing
SessionCorrupt
DatabaseFailure
UnsupportedDevice
```

User messages should be actionable; logs retain technical detail.

## 12. Logging

Debug logs may include MIDI events and exercise IDs. Release logs must not dump massive continuous MIDI streams by default.

Provide an optional diagnostic export containing:

- app/build version
- device info
- MIDI endpoint info
- session seed
- exercise definition ID
- evaluator result

No account or cloud backend is needed for this.
