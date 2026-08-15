# Data Model and Persistence

## 1. Storage split

### Bundled/static content

Version-controlled JSON/Kotlin resources:

- chord formula catalog
- curriculum graph
- gate definitions
- exercise policies
- instrument metadata

### Room database

Mutable learning data:

- profiles
- skill mastery
- attempts
- gate completion
- review schedule
- sessions
- unlocked content

### DataStore

Preferences:

- MIDI device preference
- keyboard range
- assistance defaults
- audio levels
- notation preferences
- accidental preference
- UI settings

## 2. Core entities

Suggested Room entities:

```text
ProfileEntity
SkillMasteryEntity
GateProgressEntity
AttemptEntity
SessionEntity
ReviewItemEntity
UnlockEntity
```

Avoid storing every derived metric if it can be reliably recomputed; cache only where useful.

## 3. Attempt record

```kotlin
data class AttemptRecord(
    val id: UUID,
    val sessionId: UUID,
    val exerciseDefinitionId: String,
    val exerciseSeed: Long,
    val skillIds: List<String>,
    val startedAt: Instant,
    val firstInputAt: Instant?,
    val completedAt: Instant?,
    val verdict: Verdict,
    val assistanceUsed: AssistanceSnapshot,
    val expectedSnapshotJson: String,
    val performedSnapshotJson: String,
    val semanticErrorsJson: String
)
```

Keep snapshots sufficiently complete to reproduce bugs even if content definitions later change.

## 4. Content versioning

Every static content pack has a semantic version and schema version.

On app update:

- retain old attempt history
- migrate IDs through explicit alias tables if definitions are renamed
- never silently reinterpret old attempts against changed exercise rules

## 5. Curriculum file example

See `content/examples/curriculum.sample.json`.

## 6. Exercise policy example

See `content/examples/exercise_policy.sample.json`.

## 7. Backup/export

V1 should include local JSON export/import of learning progress if practical. It is useful for a personal app and avoids requiring an account backend.

Export must include schema version.
