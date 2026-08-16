# Build, CI, Install, and Release

## 1. Build variants

At minimum:

- `debug`
- `release`

Optional later:

- `benchmark`

Debug can expose MIDI diagnostics and seeded exercise controls that release hides behind Diagnostics.

## 2. GitHub Actions

CI on pull request and main:

1. checkout
2. set up JDK required by chosen AGP
3. cache Gradle
4. run unit tests
5. lint
6. assemble debug
7. upload APK artifact

Add instrumented emulator jobs after the base pipeline is stable.

## 3. Secrets

No secrets are required for normal debug builds.

Release signing secrets belong in GitHub encrypted secrets or a secure local signing setup, never committed.

## 4. Tablet installation during development

Enable Developer Options + USB debugging on the tablet.

Typical flow:

```text
adb devices
./gradlew installDebug
```

or install the CI-produced debug APK manually.

Note: the MIDI keyboard occupies a USB port. A powered USB-C hub may be useful when simultaneous debugging/charging/MIDI is required. Wireless ADB is another option for development.

## 5. Release packaging

For personal sideloading, a signed APK is sufficient.

For Play distribution, produce an Android App Bundle and comply with the target API requirement current at submission time.

## 6. Versioning

Use semantic app versions and an independent content schema version.

Example:

```text
app version: 1.3.0
content version: 2.1.0
content schema: 4
```

## 7. Crash/analytics policy

No analytics backend is required for v1. If diagnostics are later added, keep them optional and avoid transmitting raw performance history by default.
