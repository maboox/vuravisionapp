# VuraVision 1.15.0

Full Android source, Gradle Wrapper and GitHub Actions for interactive touch panels.

## Release 1.15.0

Placement fixes: no Pie button on the toolbar (Pie menu stays in Settings and the five-finger
gesture), menus open centred again, the selected object's actions float beside the object, and
Back is a top-left icon in nested menus and full-screen screens. The bottom toolbar and the
lab/game action rows stay at the bottom.

New preview in the hidden engineering section: **Connect displays**. Each display has a local
profile (name, color, emoji or photo). One display creates a room; others join from nearby rooms,
a QR code or the address. The owner sets each person to View, Control (works on the owner's
board with shared page/view/Undo) or Collaborate (independent tools/page/Undo, live cursors,
fading one-minute halos, colored selection ownership). See [release notes](docs/RELEASE_1.15.md)
and [design notes](docs/COLLABORATION.md).

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Do not upload the ZIP as the only repository file. Keep these repository secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production .jks/.keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.15.0-<run>`; it contains `VuraVision-1.15.0.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

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

See [VERIFICATION_1.15.0.json](docs/VERIFICATION_1.15.0.json). For this release all main Kotlin
sources were type-checked locally with Kotlin 2.1.20 against the Android API 35 platform classes,
real Gson 2.12.1 and small signature stubs for the other libraries; the new and changed tests were
type-checked the same way, and the sync rules were exercised in a three-device simulation.
Android unit tests, lint and the signed APK build still run in GitHub Actions; they are not
claimed as passed here. Behaviour across two physical devices needs the checks in
[DEVICE_ACCEPTANCE_1.15.md](docs/DEVICE_ACCEPTANCE_1.15.md).
