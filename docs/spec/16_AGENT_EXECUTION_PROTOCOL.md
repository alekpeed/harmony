# Coding Agent Execution Protocol

## 1. Agent behavior

The coding agent should treat this pack as the source of truth. When documents disagree, precedence is:

1. explicit newer user instruction
2. `AGENTS.md`
3. `18_ACCEPTANCE_CRITERIA.md`
4. subsystem specification
5. general product specification

## 2. Before each phase

Agent must:

1. inspect repository status
2. read relevant spec files
3. inspect existing tests and architecture
4. identify exact files/modules to change
5. state phase acceptance criteria internally/in task log
6. avoid unrelated refactors

## 3. During implementation

Rules:

- maintain buildability
- write tests with domain code
- prefer small composable types over giant manager classes
- do not copy theory rules into UI
- do not hard-code campaign unlock logic in composables
- do not bypass MIDI abstraction to make one screen work
- do not introduce network dependency without explicit scope change
- do not introduce C++/NDK under the “full Kotlin” project constraint

## 4. Phase completion check

Run at minimum:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Add module-specific/instrumented checks as available.

Then verify:

- acceptance criteria
- no TODO that blocks phase behavior
- no known failing tests hidden/disabled
- docs changed if contracts changed

## 5. Git protocol

Recommended:

```text
phase/<number>-<short-name>
```

Commit in coherent units. Each phase finishes with a PR/merge boundary.

Do not keep 10 phases unmerged in parallel branches. Later phases are allowed to depend only on merged earlier work.

## 6. When uncertain about music theory

Do not invent behavior. Add a failing/pending fixture and surface the exact ambiguity for review. Music-theory assumptions must be encoded explicitly in policy/content, not guessed in evaluator code.

## 7. When a dependency breaks

Prefer:

1. stable official API
2. minimal wrapper
3. documented workaround

Do not replace core architecture with a large third-party framework to fix a narrow issue.

## 8. Figma phase

Before final UI implementation, retrieve the actual Figma design context and component/tokens. Do not approximate from prose once a Figma source of truth exists.

## 9. Handoff after every phase

Write/update `docs/IMPLEMENTATION_STATUS.md` with:

```text
Phase:
Commit:
Implemented:
Tests:
Manual verification:
Known limitations:
Next phase prerequisites:
```

This prevents context-window loss between coding-agent sessions.
