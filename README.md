# VuraVision 1.11.0

Full Android source, Gradle Wrapper and GitHub Actions for interactive touch panels.

## Release 1.11.0

The experimental voice assistant is **off and hidden by default**, including after upgrade. Hidden Engineering → Assistant behavior enables the button and normal connection settings. Disabling cancels startup, microphone, playback, capture and network work; keys remain. No conversation starts automatically.

Connection settings support Gemini Live, OpenAI Realtime and a turn-based OpenRouter audio/image-understanding plus speech pipeline. Each provider retains its own encrypted key. Defaults use the educational-assistant identity without a brand/model/company name; unclear speech requests repetition without echoing guesses. See [voice setup, model recommendations and limitations](docs/VOICE_ASSISTANT.md).

Normal Settings → Google search settings offers Ask each time, Internal floating browser and Default device browser. Fixed destinations skip the chooser for a single recognition result; ambiguity and forced smart review retain editable confirmation. Original writing stays on the board. See [search operation](docs/GOOGLE_SEARCH.md).

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Do not upload the ZIP as the only repository file. Keep these repository secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production .jks/.keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.11.0-<run>`; it contains `VuraVision-1.11.0.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

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

Current executed checks are recorded in [VERIFICATION_1.11.0.json](docs/VERIFICATION_1.11.0.json). Protocol/controller tests use fake services and audio; no authenticated customer API or physical-panel audio/performance test is claimed. Production signing remains in GitHub Actions using the existing four secrets. Earlier reports describe their respective releases.
