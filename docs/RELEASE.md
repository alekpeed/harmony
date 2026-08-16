# Building, Signing and Installing

17_BUILD_CI_INSTALL_RELEASE.md, as the commands actually used.

---

## 1. What you need

- JDK 21 (must match `javaToolchain` in `gradle/libs.versions.toml`)
- The Android SDK, with `compileSdk 37`
- A tablet running Android 8.0 or later (`minSdk 26`), and a USB MIDI keyboard

No secrets are needed for a debug build. Nothing about the release path requires an account.

---

## 2. Build and verify

```bash
./gradlew verifyHarmony     # music tests first, then everything else, then lint
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

`verifyHarmony` is the gate CI runs and the one a release runs. It puts `:core:music:test`
first on purpose: 14_TESTING_AND_QUALITY.md §1 says the costliest failures are musically
incorrect judgments, so a theory regression should fail before anything spends time on the
Android toolchain.

---

## 3. Install on the tablet

```bash
adb devices                 # confirm the tablet is listed
./gradlew installDebug
```

The MIDI keyboard occupies a USB port. A powered USB-C hub is useful when charging, debugging
and MIDI are all needed at once; wireless ADB avoids the problem entirely:

```bash
adb tcpip 5555
adb connect <tablet-ip>:5555
```

Or download the `harmony-gates-debug` artifact from any CI run and install the APK by hand.

---

## 4. Release signing

Signing material is never committed. The build finds it in either of two places, and needs all
four values:

**Locally** — `keystore.properties` in the repository root, which `.gitignore` excludes:

```properties
storeFile=/absolute/path/to/harmony.jks
storePassword=…
keyAlias=harmony
keyPassword=…
```

**In CI** — environment variables, which the release workflow fills from GitHub encrypted
secrets:

| Variable | Secret |
| --- | --- |
| `HARMONY_KEYSTORE_FILE` | decoded from `HARMONY_KEYSTORE_BASE64` |
| `HARMONY_KEYSTORE_PASSWORD` | `HARMONY_KEYSTORE_PASSWORD` |
| `HARMONY_KEY_ALIAS` | `HARMONY_KEY_ALIAS` |
| `HARMONY_KEY_PASSWORD` | `HARMONY_KEY_PASSWORD` |

Creating a keystore, once:

```bash
keytool -genkeypair -v -keystore harmony.jks -alias harmony \
        -keyalg RSA -keysize 4096 -validity 10000
```

**When the values are absent the release build still runs, and is unsigned.** That is
deliberate: an unsigned release APK still proves the shrinker and the resource stripper are
happy, which is worth knowing on a machine or a fork with no keystore. The artifact is labelled
`unsigned` so it cannot be mistaken for a shippable one.

---

## 5. Release build

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/  — sideloading
./gradlew bundleRelease     # app/build/outputs/bundle/release/ — Play
```

Both are minified and resource-shrunk. For personal sideloading the APK is sufficient
(17 §5); the bundle exists for Play distribution, which also requires meeting the target API
level current at submission.

In CI, pushing a `v*` tag runs `.github/workflows/release.yml`: verify, decode the keystore,
assemble both, upload both as artifacts.

```bash
git tag v0.1.0 && git push origin v0.1.0
```

---

## 6. Three version numbers

17 §6 keeps them apart, and they move independently:

| Number | Where it lives | Now |
| --- | --- | --- |
| App version | `app/build.gradle.kts` → `versionName` | `0.1.0` |
| Content version | `content/curriculum/curriculum.json` → `contentVersion` | `0.2.0` |
| Content schema | the same file → `schemaVersion` | `1` |

The content version and schema are read out of the authored file at build time and exposed as
`BuildConfig.CONTENT_VERSION` and `BuildConfig.CONTENT_SCHEMA`, so the version stamped on a
stored attempt is the version of the content that produced it. A constant in Kotlin would have
gone stale the first time content changed and nobody would have noticed until a bug report
disagreed with itself.

Bump the content version when the curriculum changes; bump the schema only when the *shape* of
the files changes, which is what the loader refuses to read across.

---

## 7. Crash reporting

There is none, by design. 17 §7: no analytics backend is required for v1, and if diagnostics
are added later they stay optional and do not transmit raw performance history by default. A
practice history is a record of somebody being bad at something in private.
