# Integrating an approved interface asset

`interface/` is where approved visual assets and their placement references are supplied. This
document is the other half: what the app does with them.

The design goal is that landing a new screen touches artwork and a table of numbers, and
nothing else. Navigation, state handling, MIDI and game logic stay where they are — which is
what `interface/README.md` asks for.

## Current status

**Integrated.** The home screen is the approved artwork, with all twenty controls live.

| Item | State |
| --- | --- |
| `interface/harmony_home_approved.jpg` | JPEG 1536 × 1024, verified at build time |
| `interface/maps/home.json` | 20 regions, bounds and semantic actions |
| Wiring | All 20 bound to `HomeAction`; Theory Lab reaches the Phase 1 harness |
| Verified | Overlay render confirms every region sits on its control |

A note on file names: an earlier `interface/harmony-home-approved.jpg` (hyphens) was a
malformed upload — 14,997 bytes containing no JPEG markers at all. It has been removed, since
leaving it would fail `checkInterfaceAssets` on every build. The valid asset is the
underscored one, which is what `syncInterfaceArtwork` reads.

`interface/maps/home.json` still points its `visualAsset` field at the removed hyphenated
name. Nothing reads that field — the build takes the artwork path from the Gradle task — but
it is worth correcting in the next map export.

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

## Updating the artwork or the map

Replace the file in `interface/` and rebuild. That is the whole workflow — there is no second
copy to keep in step.

`syncInterfaceArtwork` runs per variant and generates:

| Generated | From |
| --- | --- |
| `R.drawable.home_approved` | the artwork, or a blank placeholder if none is usable |
| `R.bool.home_approved_available` | whether it is the real export |
| `R.integer.home_approved_native_width` / `_height` | the artwork's true pixel size |
| `R.raw.home_interaction_map` | `interface/maps/home.json` |

Because the drawable is generated either way, `R.drawable` always resolves and the app needs
no reflection and no conditional compilation. `HomeScreen` shows the artwork only when both
halves are present, falling back to its action list otherwise — artwork with no regions is a
screen of dead pixels, and regions with no artwork is invisible buttons over nothing.

The map is parsed at runtime rather than transcribed into Kotlin, so a re-export updates the
app by replacing one file. Regions are matched on their semantic `action` id first and their
`figmaLayer` name second: the action id is the more stable of the two, since a designer may
rename a layer for tidiness but `navigate_ear_trainer` says what it is for.

An unknown layer is skipped rather than fatal — a design file may gain a control before the app
has a destination for it — but `HomeActionTest` fails the build if the map and the app drift
apart, so a skip is never silent.

### Adding a region

Add it to the map with `figmaLayer`, `action` and `boundsNormalized`, then add a matching
`HomeAction` entry. The test asserts a two-way match, so a half-done addition fails loudly.

### Nested regions

`HIT / Continue` sits inside `HIT / Next Gate Card`. Laid out in declaration order the card
would cover the button and swallow every tap on it, so `ArtworkSpec.regionsInHitTestOrder`
places larger regions first and smaller ones on top. Nesting needs no special handling in a
map; it is handled generally.

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
