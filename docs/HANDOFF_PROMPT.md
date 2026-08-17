# Handoff: Android tablet full-screen artwork app — resolved

This was written to be pasted into ChatGPT as a stuck problem. It did not end up needing that:
all four symptoms below were root-caused and fixed in this repository the same day, most of it
before this file was even committed. Kept as the record of what was actually wrong — "already
tried and it didn't work" is worth less once the problem is gone, but "here is what it actually
was" is worth keeping.

## What was wrong, and what fixed it

**1. Status bar and taskbar drawn over the full-bleed artwork on a normal launch.**
`goFullScreen()` was reapplied in `onResume` and `onWindowFocusChanged` only. Returning from the
notification shade or a permission dialog goes through neither, so a hidden bar could come back
with nothing re-asking for it to hide — "it does it sometimes." Fixed by re-hiding on every
relevant lifecycle callback (`5a15d02`), and made non-fatal regardless: `ArtworkScreen` no longer
assumes the hide succeeded. It measures live `WindowInsets.safeDrawing` on every pass (`26b03ff`),
so if a bar is visible anyway, the artwork contracts around it instead of letting the bar draw
over a painted control.

**2. Identical APK, different window depending on launch source.**
`android:configChanges="orientation|screenSize|screenLayout|..."` told Android to never recreate
the activity when its window changed shape, and `android:launchMode="singleTask"` let a new
launch reuse a task whose bounds belonged to a previous, differently sized invocation. Together,
an activity opened by a package installer or a file-transfer app could end up laid out for a
window it was no longer in. Both attributes are gone from the manifest (`ff039e9`). Separately,
`ArtworkScreen` stopped sizing its frame with `Modifier.aspectRatio` against a container that
could report a stale size, and now derives the frame directly from the `BoxWithConstraints`
constraints reaching that exact composition (`26b03ff`) — a resize produces a correct frame on
the next measure pass whether or not the activity itself was recreated.

**3. Portrait left a large dead band under a letterboxed landscape frame.**
`HarmonyLandscape`, which used to rotate the whole interface 90° to compensate, is deleted — it
could not tell a portrait tablet from a small portrait-shaped window and made the second case
worse, exactly as flagged in "already tried" below. `RequireLandscape` (now `RotateToLandscape.kt`,
`5a15d02`) measures its own live constraints and, when the window is taller than it is wide, shows
a "Turn the tablet" message instead of fitting the 1536×1024 frame into whatever space is left.

**4. Tapping into Campaign or Progress crashed with no trace.**
Root cause found: `CampaignViewModel` and `ProgressViewModel` declared `Application` plus Kotlin
default constructor parameters (e.g.
`private val progress: ProgressRepository = HarmonyGraph.progress(application)`). Android's
default `ViewModelProvider` factory builds an `AndroidViewModel` by reflecting for a constructor
whose only parameter is `Application`; a Kotlin default parameter does not generate that overload,
so the factory found no matching constructor and every entry into either Room/DataStore-backed
screen threw before a line of the screen's own code ran. Fixed by moving the dependency lookups
into the constructor body instead of the signature (`6655135`, `de68ca5`). Separately, `CrashLog`
(`5a15d02`) now writes the stack trace to disk as it happens, and `AppRoot` shows it, selectable,
on the next launch — so whatever crashes next says which line.

## Original handoff text

Kept verbatim below for the record.

---

I have an Android tablet app in Kotlin + Jetpack Compose that will not display correctly, and
several rounds of fixes have not worked. I need you to reason about it from first principles
rather than trusting the reasoning that has already failed.

## What the app is

A jazz-harmony game for Android tablets, played with a MIDI keyboard. The home screen is a
single full-bleed piece of artwork — a 1536 × 1024 PNG showing a room with a menu of eight tiles
painted into it — with invisible clickable boxes positioned over the painted controls. The
artwork is the UI. There is no separate portrait design.

## Environment

- Device: Android tablet (landscape use), system navigation bar on one side, persistent taskbar
- AGP 9.3.1, Kotlin 2.2.10, Compose BOM 2026.08.00, activity-compose 1.13.0, core-ktx 1.19.0
- compileSdk 37, targetSdk 36, minSdk 26
- Single activity, no Fragments, no XML layouts, Compose only

## Symptoms, in the order they appear

1. **Launched from the app launcher:** the artwork displays at the correct size, but the Android
   status bar (clock, notification icons, battery) is drawn on top of it, and the taskbar /
   navigation bar remains visible over the bottom or side.
2. **Launched by tapping "Open" from a package installer, or opened from a file-transfer app:**
   the whole app content is displaced and cut off — the artwork is clipped on the right, and
   content anchored to the bottom-left of the screen is off-screen entirely. The rest of the
   display is dark. Same APK, same device; only the launch source differs.
3. **In portrait:** the landscape artwork is fitted into the portrait window, leaving a very
   large empty dark band below it.
4. **Tapping certain hit regions crashes the app.** The regions in question navigate to screens
   that read from a Room database and a DataStore. No stack trace has been captured yet.

## What has already been tried, and did not fix it

Please do not re-propose these:

- `ContentScale.Fit` on the `Image`, then replaced with explicit measurement of the container
  and manual placement, then replaced again with `Modifier.aspectRatio` on a wrapper box.
- `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`, then narrowed to
  `WindowInsets.systemBars.union(WindowInsets.displayCutout)`.
- `android:windowSoftInputMode="adjustNothing"`.
- `android:launchMode="singleTask"`.
- `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` with
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, called from `onCreate`, `onResume` and
  `onWindowFocusChanged`.
- A wrapper that detected a portrait window and rotated the whole UI 90° with
  `Modifier.requiredSize(width = maxHeight, height = maxWidth).rotate(90f)`. This made symptom 2
  significantly worse and has been removed.

## Current code

`MainActivity`:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this, System.currentTimeMillis())
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        goFullScreen()
        setContent {
            HarmonyTheme(reducedMotion = animationsAreOff()) {
                RequireLandscape {          // shows a "turn the tablet" message if portrait
                    AppRoot()               // Scaffold { ... HomeScreen(...) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        goFullScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()
    }

    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
```

Manifest activity:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:screenOrientation="sensorLandscape"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|density"
    android:resizeableActivity="true"
    android:windowSoftInputMode="adjustNothing"
    android:launchMode="singleTask"
    android:theme="@style/Theme.HarmonyGates">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

The artwork composable, called from inside a `Scaffold` content lambda (the `Scaffold` is not
given the insets padding for this screen — the composable insets itself):

```kotlin
@Composable
fun ArtworkScreen(
    artwork: Painter,
    spec: ArtworkSpec,                 // spec.nativeWidth = 1536, spec.nativeHeight = 1024
    onRegionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    background: Color = Color.Black,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(spec.nativeWidth.toFloat() / spec.nativeHeight.toFloat()),
        ) {
            val frameWidth = maxWidth
            val frameHeight = maxHeight

            Image(
                painter = artwork,                       // 1536x1024 PNG in drawable-nodpi
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            for (region in spec.regionsInHitTestOrder) { // normalized 0..1 bounds
                Box(
                    modifier = Modifier
                        .offset(x = frameWidth * region.bounds.left, y = frameHeight * region.bounds.top)
                        .size(width = frameWidth * region.bounds.width, height = frameHeight * region.bounds.height)
                        .clickable { onRegionClick(region.id) },
                )
            }
        }
    }
}
```

## What I want from you

Work through these in order, and say when you are inferring rather than certain.

1. **Symptom 2 is the strangest: identical APK, identical device, different result depending on
   which app launched it.** What determines the window an activity is given in that situation on
   a modern Android tablet, and what in the code above would react badly to it? Consider the
   interaction of `launchMode`, task affinity, freeform/multi-window, and `configChanges`
   suppressing activity recreation on a size change.
2. **`configChanges` includes `screenSize|screenLayout|smallestScreenSize|orientation|density`,**
   so the activity is never recreated when the window changes shape. Is anything in a Compose
   hierarchy at risk of caching a stale window size or stale insets under that configuration,
   and if so what is the correct way to observe the live value?
3. **On targetSdk 36 / Android 15+, can an app actually hide the tablet taskbar,** and does
   `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` behave differently in a multi-window or freeform
   window? If hiding it is not reliably possible, what is the correct way to lay out a full-bleed
   design so the system UI does not sit on it?
4. **Give me a diagnostic** I can add — as a composable overlay or a log — that prints the values
   that would distinguish these causes: the activity's window metrics, the constraints reaching
   the top-level composable, every inset type separately, the display size, and whether the
   activity is in multi-window mode. I would rather read numbers than keep interpreting
   photographs of a screen.
5. Only after the above: propose the fix, and tell me which of the numbers in (4) would confirm
   it worked.

Please do not give me a rewritten file until we agree on what the cause is.
