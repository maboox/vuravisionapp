# VuraVision 1.10.2

Offline-first Android interactive whiteboard, with Persian/English UI, editable lesson projects, native classroom experiments and games.

## Release 1.10.2

See [voice-assistant release notes](docs/RELEASE_1.10.md), [assistant operation and limits](docs/VOICE_ASSISTANT.md), [previous release notes](docs/RELEASE_1.9.md) and [Persian instructions](README.fa.md).

Version 1.10.2 fixes the key-field UI regression: password masking is explicitly restored after single-line setup, the field is left-aligned in both languages, and its regression test measures/renders the field before checking actual paragraph direction and password masking. The previous test read the unresolved view direction and stopped CI despite accepting the pasted key.

Version 1.10.1 fixes overly strict local API-key validation: opaque credentials are accepted without assuming a fixed prefix, token alphabet or 200-character limit. Boundary whitespace/direction marks and paired copy quotes are removed; interior credential bytes are preserved. Google still validates access and quota when connecting.

The new independent Voice assistant button starts a Persian audio conversation with Gemini Live about the visible board/PDF. Settings accepts a Google AI Studio API key; hidden Engineering settings configure tone, response length, custom instructions and known panel features. Defaults are warm, polite and brief, with hints before a requested final answer. The assistant has no editing tools. The core whiteboard remains offline; this optional assistant uses the internet.

Select writing or text and choose **Search Google** in the selection toolbar. Review/edit the recognized word or question, then open an interactive, draggable/resizable in-app window or an external browser. The browser can request an adjacent window where the panel supports it. Original writing is retained; internet is required, a Gemini key is not. See [search operation and window limits](docs/GOOGLE_SEARCH.md).

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

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.10.2-<run>`; it contains `VuraVision-1.10.2.apk` and `SHA256SUMS.txt`. Pull-request builds are unsigned-for-production debug builds. Production builds fail if signing settings are absent. The package ID and signing mechanism are unchanged; there is no activation/serial-number feature.

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

For 1.10.2, all 149 Android unit tests across 19 suites passed. Debug/Release lint completed with zero errors and 709 warnings each. Debug APK and device-test APK assembly passed; the development APK signature and ZIP alignment were checked. Native key-field renderings in both languages were inspected, with regression checks for actual paragraph direction, left alignment and password masking. Device tests were compiled but not executed. No authenticated Gemini conversation or physical-panel Google/OEM PiP performance test was run. Production signing remains in GitHub Actions using the existing four secrets. See [VERIFICATION_1.10.2.json](docs/VERIFICATION_1.10.2.json). Earlier verification records describe their respective versions.
