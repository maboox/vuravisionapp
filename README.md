# VuraVision 3.1

Android whiteboard and classroom suite for VuraVision 65–115 inch interactive displays. Persian and English, offline-first, built around a low-latency native ink canvas.

[فارسی](README.fa.md)

## What is in the app

- **Board:** dual-tip pens and highlighter, area/stroke eraser, shapes with recognition, text and sticky notes, geometry tools, graphs, mind maps, layers, pages, split panels, Undo/Redo with history scrubbing.
- **Files:** versioned `.vura` projects (ZIP + JSON + assets) with atomic autosave; image import with crop; PDF as a board object or as a scrollable workspace beside the board, with annotation export.
- **Smart tools:** ML Kit handwriting and Latin OCR, shape conversion, arithmetic/equation solving, function plotting, unit conversion.
- **Smart assistant:** voice conversation about the visible board through Google Gemini Live, OpenAI Realtime, OpenRouter, or AvalAI / any OpenAI-compatible HTTPS gateway. Off by default; each service keeps its own encrypted key. See [voice setup](docs/VOICE_ASSISTANT.md).
- **Labs and games:** native simulations and two-player games, plus the bundled HTML catalogs.
- **Shared rooms (experimental, hidden):** LAN collaboration with View / Control / Collaborate roles.

## Build

GitHub Actions (`.github/workflows/android.yml`) runs unit tests, lint, a production-signed release build and APK checks on every push. Pull requests build a debug APK without signing secrets. The manual workflow run also executes instrumented tests on an Android 35 emulator.

Required repository secrets for release signing: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

Locally, with JDK 17 and Android SDK 35:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The app version lives in `app/build.gradle.kts`; CI names the APK artifact from it.

## Repository layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/com/vuravision/classroom` | All app code |
| `app/src/test`, `app/src/androidTest` | Robolectric unit tests, device tests |
| `app/src/main/assets` | Fonts, guide, periodic table, HTML labs/games |
| `docs/` | Current design, dependency, font, voice and device-acceptance notes |
| `docs/archive/` | Release notes and verification records up to 1.17 |
| `scripts/` | Source checks, APK alignment check, web smoke test |

Physical-panel behavior (pen latency, palm detection, two-device rooms) still needs on-device acceptance: see [device checklist](docs/DEVICE_ACCEPTANCE_1.17.md).
