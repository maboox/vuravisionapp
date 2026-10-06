# VuraVision 1.14.1

Full Android source, Gradle Wrapper and GitHub Actions for interactive touch panels.

## Release 1.14.1

Build verification repair: menu Back callbacks are awaited in tests, lab/game footer checks use a consistent panel viewport, and the older settings test expects the now-public Pie menu. All 213 unit tests remain enabled. See [build fix details](docs/RELEASE_1.14.1.md).

## Whiteboard features

Bottom-right main controls, bottom lab/game actions, separate free and box selection, graphical image cropping, contextual image actions, Duplicate with a plus icon, four vertical board columns, live guide dimensions and angles, clean guide entry/exit, a protractor drawing handle, a visible page counter and a normal Settings entry for the pie menu.

The pie menu can be enabled and configured in **Settings → Pie menu**, and opened from the bottom toolbar or by a five-finger double tap. The voice assistant keeps its existing hidden/off defaults. See [release details](docs/RELEASE_1.14.md), [voice setup](docs/VOICE_ASSISTANT.md), and [Google search](docs/GOOGLE_SEARCH.md).

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Do not upload the ZIP as the only repository file. Keep these repository secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production .jks/.keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.14.1-<run>`; it contains `VuraVision-1.14.1.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

Manual workflow runs also execute Android device PDF and Keystore checks on an emulator. Reports are separate artifacts. Android SDK 35 and JDK 17 are used. The Gradle Wrapper is included and invoked as `bash ./gradlew`.

## Local checks

```bash
python3 scripts/check-source.py
npm ci
npm test
bash ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Release builds additionally require the production signing environment variables and keystore path. Do not commit a keystore or passwords.

## Verification status

Current checks and limitations are in [VERIFICATION_1.14.1.json](docs/VERIFICATION_1.14.1.json). The supplied 1.14.0 Actions log confirms debug/release Kotlin and Java compilation and 210 passing tests out of 213. This patch addresses the three reported failures. Local source/XML/font/data and JavaScript syntax checks passed. Gradle cannot download its distribution here, so a fresh Android test, lint and APK build is **pending GitHub Actions**, not claimed as locally passed. Earlier reports are historical.

`Studio114Test.kt` adds 12 regression tests for selection, crop handles, clean guide strokes, compass/protractor drawing, vertical panes, calibration, menu Back routes, contextual image actions and bottom controls. Production signing uses the original four secrets.
