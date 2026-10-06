# VuraVision 1.14.0

Full Android source, Gradle Wrapper and GitHub Actions for interactive touch panels.

## Release 1.14.0

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

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.14.0-<run>`; it contains `VuraVision-1.14.0.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

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

Current checks and limitations are in [VERIFICATION_1.14.0.json](docs/VERIFICATION_1.14.0.json). Local source/XML/font/data and JavaScript syntax checks passed. Gradle could not download its distribution in the restricted delivery environment, so Android compilation, lint and the new Robolectric tests are **pending GitHub Actions**, not claimed as locally passed. The previous 1.13 report is historical.

`Studio114Test.kt` adds 12 regression tests for selection, crop handles, clean guide strokes, compass/protractor drawing, vertical panes, calibration, menu Back routes, contextual image actions and bottom controls. Production signing uses the original four secrets.
