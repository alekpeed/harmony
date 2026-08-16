# Handoff: Android tablet full-screen artwork app

Paste everything below the line into ChatGPT. It is self-contained.

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
