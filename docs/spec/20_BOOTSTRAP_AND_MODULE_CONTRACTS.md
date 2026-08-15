# Bootstrap and Module Contracts

## 1. Bootstrap order

Create the repository in this order. Do not create all feature implementations at once.

### Step 1 — Root Gradle files

Create:

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
```

Declare modules only after their directories exist.

### Step 2 — `app`

Responsibilities:

- Android application entry point
- application theme wiring
- top-level dependency graph
- Navigation 3 host
- global lifecycle coordination

The app module must not contain chord-theory implementation.

### Step 3 — `core:music`

Pure Kotlin library. No Android dependency unless an unavoidable platform type leaks in; prefer not to allow any.

Public surface:

```kotlin
interface ChordParser {
    fun parse(text: String, context: ParseContext = ParseContext()): ParseResult<ChordSpec>
}

interface ChordRealizer {
    fun chordTones(spec: ChordSpec): List<SpelledPitchClass>
    fun generateVoicings(spec: ChordSpec, policy: VoicingPolicy): List<Voicing>
}

interface PerformanceEvaluator {
    fun evaluate(requirement: ExerciseRequirement, attempt: PerformanceAttempt): EvaluationResult
}

interface ExerciseGenerator {
    fun generate(policy: ExercisePolicy, seed: Long, context: GenerationContext): ExerciseInstance
}
```

### Step 4 — `core:midi`

Android library because production MIDI source uses Android platform APIs.

Public surface:

```kotlin
interface MidiInputSource {
    val connectionState: StateFlow<MidiConnectionState>
    val events: Flow<MidiEvent>
    suspend fun start()
    suspend fun stop()
}

interface PerformanceCapture {
    val state: StateFlow<CaptureState>
    val attempts: Flow<PerformanceAttempt>
    fun arm(policy: CapturePolicy)
    fun cancel()
}
```

### Step 5 — `core:data`

Repositories:

```kotlin
interface ProgressRepository {
    fun observeGateProgress(): Flow<List<GateProgress>>
    fun observeSkillMastery(skillId: SkillId): Flow<SkillMastery?>
    suspend fun recordAttempt(record: AttemptRecord)
    suspend fun updateMastery(update: MasteryUpdate)
}

interface ContentRepository {
    suspend fun curriculum(): Curriculum
    suspend fun gate(id: GateId): GateDefinition
    suspend fun exercisePolicy(id: ExercisePolicyId): ExercisePolicy
}
```

### Step 6 — `core:audio`

Public surface:

```kotlin
interface InstrumentPlayer {
    suspend fun load(preset: InstrumentId)
    fun noteOn(note: MidiNote, velocity: Int)
    fun noteOff(note: MidiNote)
    fun sustain(down: Boolean)
    fun allNotesOff()
}

interface StimulusPlayer {
    suspend fun play(stimulus: AudioStimulus)
    fun stop()
}
```

### Step 7 — `core:designsystem`

Before Figma:

- token types
- placeholder theme
- reusable component contracts

After Figma:

- replace token values
- refine component implementations
- preserve feature APIs

### Step 8 — features

Add only as phases require them.

## 2. Domain/session contracts

### `ExerciseInstance`

```kotlin
data class ExerciseInstance(
    val id: ExerciseInstanceId,
    val definitionId: ExerciseDefinitionId,
    val seed: Long,
    val skillIds: Set<SkillId>,
    val requirement: ExerciseRequirement,
    val presentation: PresentationSpec,
    val stimulus: AudioStimulus? = null,
    val timing: ExerciseTiming? = null,
    val explanation: ExplanationContent? = null
)
```

### `PerformanceAttempt`

```kotlin
data class PerformanceAttempt(
    val startedAtNanos: Long,
    val completedAtNanos: Long,
    val noteEvents: List<NormalizedNoteEvent>,
    val finalEffectiveNotes: List<MidiNote>,
    val onsetSpreadNanos: Long?,
    val sustainUsed: Boolean
)
```

Do not throw away event history after reducing to final notes. Rhythm and accidental-note diagnosis need it.

### `ExerciseSessionState`

```kotlin
sealed interface ExerciseSessionState {
    data object Idle : ExerciseSessionState
    data class Loading(val gateId: GateId?) : ExerciseSessionState
    data class Presenting(val exercise: ExercisePresentationModel) : ExerciseSessionState
    data class Armed(val exercise: ExercisePresentationModel) : ExerciseSessionState
    data class Capturing(val live: LivePerformanceState) : ExerciseSessionState
    data class Feedback(val result: EvaluationResult, val nextAvailable: Boolean) : ExerciseSessionState
    data class Paused(val reason: PauseReason) : ExerciseSessionState
    data class Completed(val summary: SessionSummary) : ExerciseSessionState
}
```

## 3. Clock and randomness injection

Never call `System.currentTimeMillis()` or `Random.Default` deep inside domain logic.

```kotlin
interface MonotonicClock { fun nowNanos(): Long }
interface WallClock { fun now(): Instant }
interface SeededRandomFactory { fun create(seed: Long): RandomSource }
```

Tests supply deterministic implementations.

## 4. Serialization boundary

Persist IDs and versioned DTOs, not arbitrary polymorphic runtime objects without a migration strategy.

Keep separate:

```text
Domain model
Persistence entity
Content DTO
UI model
```

Use explicit mappers.

## 5. Compose contracts

Feature routes should expose one state and intents rather than a large list of callbacks.

Conceptual pattern:

```kotlin
@Composable
fun ChordGateRoute(viewModel: ChordGateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChordGateScreen(state = state, onIntent = viewModel::onIntent)
}
```

`ChordGateScreen` must be preview/testable with fake state and no actual MIDI device.
