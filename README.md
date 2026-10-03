# VuraVision 1.10.0

Offline-first Android interactive whiteboard, with Persian/English UI, editable lesson projects, native classroom experiments and games.

## Release 1.10.0

See [voice-assistant release notes](docs/RELEASE_1.10.md), [assistant operation and limits](docs/VOICE_ASSISTANT.md), [previous release notes](docs/RELEASE_1.9.md) and [Persian instructions](README.fa.md).

The new independent Voice assistant button starts a Persian audio conversation with Gemini Live about the visible board/PDF. Settings accepts a Google AI Studio API key; hidden Engineering settings configure tone, response length, custom instructions and known panel features. Defaults are warm, polite and brief, with hints before a requested final answer. The assistant has no editing tools. The core whiteboard remains offline; this optional assistant uses the internet.

Previous releases added a scrollable PDF workspace beside the board, per-page editable notes, a draggable divider, PDF original/new-file saving, independent undo, region-to-board copying, ordered guide chapters and classified native experiments. Rendering changes prioritize interaction speed, image transforms and distant zoom. PDF notes are a fixed overlay in the exported PDF; keep the .vura project for separate editing.

Settings → Fonts independently selects Persian and English with previews. The selected pair is stored with new text, sticky notes and smart-conversion results, and used by PDF exports. Optionally apply it to editable text on the active page with Undo. Bundled families and source notices are documented in [FONTS.md](docs/FONTS.md).

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Do not upload the ZIP as the only repository file. Keep these repository secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production .jks/.keystore file |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.10.0-<run>`; it contains `VuraVision-1.10.0.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

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

For 1.10.0, Android compilation, all 130 unit tests across 16 suites, debug/release lint (zero errors), debug APK assembly and device-test APK assembly passed locally. The debug APK passed signature verification and ZIP alignment. Lint reports 700 debug warnings and 699 release warnings. Native voice-control screens were rendered in English/Persian, with toolbar-overlap and hidden-settings checks. Protocol/controller tests use fake transport/audio ports. No authenticated Gemini conversation was run because no customer API key was supplied. Device/Keystore tests were compiled but not executed; real panel audio/echo, latency, screen readability and performance remain to be verified. Production signing uses the existing GitHub Actions secrets. See [VERIFICATION_1.10.0.json](docs/VERIFICATION_1.10.0.json).

Verification records for 1.9.4 and earlier are historical; current verification is recorded separately for 1.10.0.
