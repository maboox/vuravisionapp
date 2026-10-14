# VuraVision 3.1

Android whiteboard and classroom suite for VuraVision 65–115 inch interactive displays. Persian and English, offline-first, built around a low-latency native ink canvas.

[فارسی](README.fa.md)

## What is in the app

- **Board:** dual-tip pens and highlighter, area/stroke eraser, shapes with recognition, text and sticky notes, geometry tools, graphs, mind maps, layers, pages, split panels, Undo/Redo with history scrubbing.
- **Files:** versioned `.vura` projects (ZIP + JSON + assets) with atomic autosave; image import with crop; PDF as a board object or as a scrollable workspace beside the board, with annotation export.
- **Smart tools:** ML Kit handwriting and Latin OCR, shape conversion, arithmetic/equation solving, function plotting, unit conversion.
- **AI on the board:** on a selection — explain, simplify, translate, find mistakes (circled on the work), step-by-step reveal, clean handwriting, sketch to picture, quiz, mind map, note grouping; plus charts, pictures and an end-of-lesson summary page with a QR. Gemini, OpenAI, OpenRouter or AvalAI; only the selection is sent.
- **Class tools:** laser pen, spotlight, name picker, timers, scoreboard, class quizzes saved in the lesson, page replay, tables, connector arrows, map of Iran, page templates.
- **Smart assistant:** voice conversation about the visible board through Google Gemini Live, OpenAI Realtime, OpenRouter, or AvalAI / any OpenAI-compatible HTTPS gateway. Off by default; each service keeps its own encrypted key. See [voice setup](docs/VOICE_ASSISTANT.md).
- **Labs and games:** native simulations (drag on the picture to change settings, slow motion, AI explanation) and two-player games with sound and celebration.
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
| `app/src/main/java/com/vuravision/classroom` | App code in packages by area: `board`, `ai`, `voice`, `room`, `labs`, `games`, `pdf`, `ui`, `tools` (see [architecture](docs/ARCHITECTURE.md)) |
| `app/src/test`, `app/src/androidTest` | Robolectric unit tests, device tests |
| `app/src/main/assets` | Fonts, guide, periodic table, maps |
| `docs/` | Design rationale (`UI_RATIONALE.md`), design system, dependencies, fonts, voice, device acceptance |
| `docs/archive/` | Release notes and verification records up to 1.17 |
| `scripts/` | Source checks, APK alignment check, icon generation |
| `server/ai-proxy/` | Optional AI gateway for schools (keeps the API key off the panels) |

Physical-panel behavior (pen latency, palm detection, two-device rooms) still needs on-device acceptance: see [device checklist](docs/DEVICE_ACCEPTANCE.md).
