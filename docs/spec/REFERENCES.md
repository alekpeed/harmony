# Technical References Checked for This Plan

These are implementation anchors, not substitutes for the product specification.

## Android platform

- Android 17 SDK setup documentation: API level 37 / Build Tools 37.
- Android Developers Compose August 2026 release notes: current Compose line compiles against API 37 and requires a recent AGP.
- Google Play target API requirements: starting August 31, 2026, new apps/updates generally must target Android 16 / API 36 or higher.

## MIDI

- Android `MidiManager` is available from API 23.
- `PackageManager.FEATURE_MIDI` identifies full `android.media.midi` support.
- Modern `MidiManager` supports transport-specific enumeration and callbacks for MIDI 1.0 byte-stream and Universal MIDI Packet transports.
- Android's USB host APIs exist separately, but class-compliant MIDI should normally be consumed through the MIDI service instead of manually parsing USB MIDI.

## Architecture/UI

- Android recommends Jetpack Compose for modern UI.
- Navigation 3 is Compose-centric and supports adaptive navigation patterns.
- Material 3 Adaptive provides window-size/posture-aware layout building blocks.
- Room is the recommended abstraction over raw SQLite for structured local app data.
- DataStore is appropriate for small preference/typed settings data.

Before pinning dependency versions, check the current official AndroidX stable releases and keep all versions in the version catalog.
