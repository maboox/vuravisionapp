# VuraVision 1.9.3

Offline-first Android interactive whiteboard, with Persian/English UI, editable lesson projects, native classroom experiments and games.

## Release 1.9

See [the complete release notes and verification status](docs/RELEASE_1.9.md) and [Persian instructions](README.fa.md).

This release adds a scrollable PDF workspace beside the board, per-page editable notes, a draggable divider, PDF original/new-file saving, independent undo, region-to-board copying, ordered guide chapters and classified native experiments. Rendering changes prioritize interaction speed, image transforms and distant zoom. PDF notes are a fixed overlay in the exported PDF; keep the .vura project for separate editing.

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Do not upload the ZIP as the only repository file. Keep these repository secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production .jks/.keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.9.3-<run>`; it contains `VuraVision-1.9.3.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

Manual workflow runs also execute Android device PDF checks on an emulator. Reports are separate artifacts. Android SDK 35 and JDK 17 are used. The Gradle Wrapper is included and invoked as `bash ./gradlew`.

## Local checks

```bash
python3 scripts/check-source.py
npm ci
npm test
bash ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Release builds additionally require the production signing environment variables and keystore path. Do not commit a keystore or passwords.

## Verification status

Web simulations, catalog checks and offline structural checks passed in this session. Android compilation, lint and the newly added unit/device tests could not be executed here because Gradle download was unavailable and the Android SDK was absent. A successful GitHub Actions build and actual-panel testing are required before distribution. Structural checks are not compilation, and no measured frame-rate improvement is claimed.

Documents labelled 1.8 or earlier are historical verification records, not results for 1.9.
