# VuraVision 1.12.0

Full Android source, Gradle Wrapper and GitHub Actions for interactive touch panels.

## Release 1.12.0

Offline interactive periodic table, compass arc handles, useful ruler/protractor construction actions, shape measurement and coordinate-plane points/intersections. Drawing adds fixed-width pressure-free pen styles and closed-object bucket fill. Native controls use compact previews, circular lesson palettes, meaningful menu icons and touch information popups. New-page defaults/inheritance, independent Google query review, two-finger double-tap Undo and voice microphone mute are included.

The voice assistant and experimental five-finger six-slot pie menu are **off and hidden by default**. Hidden settings enable them; the existing Gemini/OpenAI/OpenRouter connections remain. See [complete change details and compatibility](docs/RELEASE_1.12.md), [voice setup](docs/VOICE_ASSISTANT.md), and [Google search](docs/GOOGLE_SEARCH.md).

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Do not upload the ZIP as the only repository file. Keep these repository secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production .jks/.keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.12.0-<run>`; it contains `VuraVision-1.12.0.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

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

Current executed checks are recorded in [VERIFICATION_1.12.0.json](docs/VERIFICATION_1.12.0.json). Protocol/controller tests use fake services and audio; no authenticated customer API or physical-panel audio/performance test is claimed. Production signing remains in GitHub Actions using the existing four secrets. Earlier reports describe their respective releases.
