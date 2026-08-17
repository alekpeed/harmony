# Session Handoff — 2026-08-17

Written at the end of a session that merged two branches of work — this session's engine/content
phases and a separate coding agent's Progression Run screen fixes — verified the merge builds
clean, and fixed one bug found by hand-testing on a tablet. Read this before starting new work;
it says what is actually true of the tree right now, not what an older doc assumed.

## State of the repository

- Branch `claude/build-main-documents-ywuh58` is pushed to `origin` and is **fully caught up
  with `origin/main`** — zero commits behind, two ahead (the merge commit and a bug fix).
- Working tree is clean. `verifyHarmony` (music tests first, then everything, then lint) and
  `assembleDebug` both pass. 45 test classes, 611 tests, 0 failures.
- The debug APK builds as `app/build/outputs/harmony/harmony-gates-<version>-debug.apk`, where
  `<version>` is `0.1.0.<commit-count>-<short-sha>` (see `app/build.gradle.kts`, the
  `harmonyVersionName` block). Every build gets a distinct name and the app prints its own
  version in the bottom-left corner in debug builds — this exists because three
  identically-named APKs reached a tablet in one afternoon and nobody could tell them apart.

## What this session did

1. **Merged `origin/main` into this branch.** Main had 21 commits of Progression Run background
   work from a separate coding session (iterating toward a real clean plate — no baked track,
   orbs, or controls — after several broken intermediate attempts). One binary conflict, in
   `interface/assets/progression-run-background.png`: this branch had an old plate I'd restored
   as a stopgap after an earlier merge brought in a corrupted file; main had since landed a
   genuinely clean one. Took main's version and confirmed by decoding it (`1536×1024 PNG`,
   visually a bare room — no HUD baked in) and by extracting it from the built APK afterward.
2. **Fixed RESTART RUN doing nothing** (`app/src/main/kotlin/com/harmonygates/progression/`).
   Three compounding faults: the button didn't close the setup drawer, so a working restart was
   invisible behind it; closing the drawer resumed playback by reading a stale composed snapshot
   of run status rather than the live engine state, which could re-pause a run that had just been
   restarted; and a restart left a stale "resume pending" flag set. Fixed with a new
   `ResumeIfPaused` intent that reads live state, and by having the restart button close the
   drawer and clear that flag. See commit `b04d1e1`.
3. Verified the merged tree end-to-end by unzipping the built APK and independently decoding the
   packaged artwork, the progression-run interaction map, and the curriculum JSON rather than
   trusting the build log.

## What the other coding agent's branch changed that affects this one

Read these before touching windowing or the Progression Run screen again — this session's own
earlier attempts at some of these were superseded:

- **`AndroidManifest.xml`**: no longer sets `android:configChanges` (so the activity *does*
  recreate on orientation/size/density changes — deliberately, per the comment in the manifest)
  and no longer sets `android:launchMode="singleTask"` (reasoning: a reused task can carry
  stale freeform/multi-window bounds forward, which was making things worse, not better).
- **`MainActivity.kt`**: hides system bars via `WindowInsetsControllerCompat` on create, resume,
  and focus-change, but explicitly does **not** depend on that succeeding — the real fix for
  content sitting under system bars is that `ArtworkScreen` insets itself with
  `WindowInsets.safeDrawing` regardless of whether the bars are actually hidden.
- **`RequireLandscape`** (`app/src/main/kotlin/com/harmonygates/RotateToLandscape.kt`) is
  unchanged from this session's version: a portrait window gets a "Turn the tablet" message
  rather than a rotated or letterboxed interface. The old rotate-the-UI approach
  (`HarmonyLandscape`) is gone for good; it broke small windows (e.g. launched from a
  file-transfer app) worse than doing nothing.
- **Progression Run background pipeline**: canonical source is now
  `interface/assets/progression-run-background.png`, synced by `SyncInterfaceArtwork` in
  `app/build.gradle.kts` (`sourceFileName.set("assets/progression-run-background.png")`). A
  `checkInterfaceAssets` Gradle task (added on `main`, not by this session) now fails the build
  if an interface asset doesn't decode as a real image — this is what caught the corrupted file
  during today's merge, and it's worth keeping.
- Note: `docs/PROGRESSION_BACKGROUND_RUNTIME_VALIDATION.md` (landed via the merge) describes the
  canonical source as a `.jpg`; the tree actually has a `.png` and the build points at the
  `.png`. The build is correct and verified working; the doc is just stale on that one line.

## What is still true and unresolved from before this session

- No tablet or MIDI hardware has ever been attached to this build environment (repo-wide
  constant since Phase 4). Everything reported as "working" is verified by decoding artifacts,
  running the JVM/Robolectric-free test suite, and reading photos the user has sent — not by
  the author of this session directly operating a device.
- `docs/HANDOFF_PROMPT.md` is a **separate document** written earlier today for pasting into an
  external LLM (ChatGPT) to get a second opinion on a windowing bug. That bug's diagnosis in that
  file is now superseded by the fixes described above — don't treat it as current status, and
  don't delete it either; it's a dated artifact of that troubleshooting session, not living docs.
- Fourteen other remote branches exist from the other agent's iterative troubleshooting (mostly
  `fix/progression-*` and a few scratch names like `noop-test`, `temp-do-not-use`,
  `ignore-branch`). Not touched, not evaluated, not mine to clean up — they belong to that
  agent's workflow.
- Full phase status (all 16 phases, test coverage, known limitations) is tracked in
  `docs/IMPLEMENTATION_STATUS.md` and the published build report
  (`docs/build-report.html`, live at the artifact URL recorded in earlier session history).
  Neither was updated this session since no phase work happened — only a merge and one bug fix.

## Suggested next step

Ask the user whether they've tested the merged build (APK sent this session:
`harmony-gates-0.1.0.125-6471b1b-debug.apk`) before making further changes to the Progression
Run screen or windowing — two sessions have now independently touched that surface today, and a
third round of guessing without device feedback is exactly what went wrong earlier in this
session's own history (see the CrashLog / build-stamp additions, which exist specifically to
stop that pattern).
