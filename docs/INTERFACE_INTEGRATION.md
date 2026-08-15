# Integrating an approved interface asset

`interface/` is where approved visual assets and their placement references are supplied. This
document is the other half: what the app does with them.

The design goal is that landing a new screen touches artwork and a table of numbers, and
nothing else. Navigation, state handling, MIDI and game logic stay where they are — which is
what `interface/README.md` asks for.

## Current status

| Item | State |
| --- | --- |
| Approved home frame (`Harmony Gates / FINAL Approved Home`, 1536 × 1024) | **Not yet in the repository.** `interface/` currently contains `README.md` only. |
| Hit-region names | Supplied, all twenty wired (`HomeAction`) |
| Hit-region coordinates | Not yet supplied |
| Integration seam | Built and tested |

The Figma file itself is not reachable from a build agent: `figma.com` returns HTTP 403 without
an authenticated session, and no API token is configured. The artwork therefore has to arrive
as a committed export, or via a Figma access token in the environment.

Until it does, `HomeScreen` shows a plainly-styled list of the same actions. That fallback is
deliberately not a mock-up of the approved design — 16_AGENT_EXECUTION_PROTOCOL.md §8 forbids
approximating from prose once a Figma source of truth exists.

## How the seam works

Three pieces:

| Piece | Where | Job |
| --- | --- | --- |
| `ArtworkSpec` / `HitRegion` / `NormalizedRect` | `core:designsystem` | Describe an artwork and the named regions on it, in fractions of the artwork |
| `ArtworkGeometry` | `core:designsystem` | Map those fractions onto whatever size the artwork actually drew at |
| `HomeAction` / `HomeArtwork` | `app` | Bind each Figma layer name to an app destination |

Regions are stored as fractions of the artwork, never device pixels. A region measured against
the 1536 × 1024 frame then lands correctly on a 2560 × 1600 tablet, a 1920 × 1200 one, and
inside a split-screen window, with no per-device table. `ArtworkGeometryTest` asserts exactly
that across five container sizes.

The artwork is fitted, not cropped. Cropping would push controls off-screen on an unusual
aspect ratio, and a control that has quietly left the screen is invisible in review and very
obvious to a player.

## Adding the approved home screen

1. **Commit the export.** Put the 1536 × 1024 PNG or WebP at
   `app/src/main/res/drawable-nodpi/home_approved.webp`. Use `drawable-nodpi` so Android does
   not rescale it per density — the fitting maths already handles size.

2. **Point the app at it.** In `HomeArtwork`:

   ```kotlin
   val drawableResId: Int? = R.drawable.home_approved
   ```

3. **Fill in the regions.** Measure each `HIT / ...` layer in the frame's own 1536 × 1024
   space and convert once:

   ```kotlin
   val regionBounds: Map<HomeAction, NormalizedRect> = mapOf(
       HomeAction.Continue to ArtworkGeometry.normalize(spec, 384f, 256f, 768f, 512f),
       // ...
   )
   ```

   Partial tables are fine. `HomeArtwork.spec` builds from whatever is present, so regions can
   be filled in a few at a time, and `HomeArtwork.isAvailable` stays false until both the
   artwork and at least one region exist — which prevents the half-integrated states of a
   screen full of dead pixels, or invisible buttons over nothing.

Nothing else changes. `HomeScreen` switches presentation on `drawableResId`; every action
already resolves through `HomeAction.forRegion`.

## Keeping the contract honest

`HomeActionTest` copies the twenty region names out of `interface/README.md` verbatim and
asserts a one-to-one mapping in both directions. If the design file renames a layer, the test
fails and names it, instead of a control silently going dead.

When a new approved screen arrives, add its region names to that screen's own action enum and
mirror the same test. The duplication between README and test is the mechanism, not an
oversight.

## Adding other approved screens

Reuse `ArtworkScreen` with a new `ArtworkSpec`. Screens with meaningful text, live data or
notation should not be delivered as flat artwork — those keep Compose components drawing from
design tokens, with the artwork supplying background and framing only. The Phase 12 design
system pass replaces the token *values* in `core:designsystem`; it does not replace the
components or the feature APIs.
