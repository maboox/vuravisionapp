# VuraVision Classroom Suite

Android application baseline for VuraVision 65–115 inch interactive displays. It opens directly into the Whiteboard and is designed around large touch targets, offline operation and an object-based document model.

## Implemented in this repository

- Whiteboard-first startup, custom low-latency Android View renderer
- Pen with historical MotionEvent samples and smoothed vector paths
- Pan/zoom, object selection/move, stroke/object eraser, undo/redo for add/remove/clear
- Object model for ink, image and PDF
- Image import
- PDF import as a movable/resizable board object (first-page rendering baseline)
- Versioned `.vura` ZIP+JSON document container with embedded assets
- Autosave and last-session recovery
- PDF export of ink/images
- Local LAN HTTP sharing with cryptographically random token + QR code
- Persian and English resources / RTL support
- Engineering Mode with live pointer count, pressure, size, major/minor, orientation/tool type visualization and hardware summary
- Calibration classifier core with tests
- Offline deterministic math engine core with tests
- VuraVision Lab: 4 functional baseline simulations/calculators
- VuraVision Games: 5 functional baseline games
- GitHub Actions for test/lint/debug APK and optional signed release APK + `apksigner` verification

## Architecture

Single Android application module for the first CI-stable baseline, organized by domain packages:
`document`, `whiteboard`, `pdf`, `sharing`, `input`, `math`, `engineering`, `lab`, `games`, `settings`.

The document model is Android-UI-independent except for geometry (`RectF`) and stores stable object IDs. `.vura` is a versioned ZIP container rather than Java/Kotlin object serialization.

## Build

CI is the intended first build path:
1. Push this repository to GitHub.
2. Open **Actions → Android CI**.
3. The workflow installs JDK 17, Android SDK 36/build-tools 36.0.0 and Gradle 8.13.
4. It runs unit tests, lint and `assembleDebug`.
5. Download the `VuraVision-debug` artifact.

Locally, with Android SDK + Gradle 8.13 installed:
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

### Gradle wrapper note
The source-generation environment used to create this ZIP had no external DNS access, so it could not download the official `gradle-wrapper.jar`. `gradlew` is therefore a deterministic bootstrap that uses the official wrapper JAR if you later add it, otherwise it uses Gradle 8.13 installed by the GitHub `setup-gradle` action. CI remains independent of a preinstalled runner Gradle.

## Release signing
See `docs/SIGNING.md`.

## Engineering Mode
Available from the VuraVision menu in this baseline. Before commercial release, hide it behind the planned About-logo 7-tap flow/admin PIN.

## Offline recognition
No handwriting/math-OCR model is bundled yet. This is intentional: the Mega Prompt forbids fake recognition, and a production choice needs model-level license, Persian accuracy, APK/model size and Android inference validation. The app does not present a fake Smart Recognition button.

## Known V1-baseline limitations
This ZIP is a **buildable product baseline, not completion of every item in the 100-point mega specification**. Remaining major work includes: production handwriting + Persian model manager, math OCR, PDF page controls/snapshot crop, resize/rotate handles and transform undo, multi-page board UI, 3D objects, geometry tools, overlay/MediaProjection capture, 12–15 polished Lab simulations, 15–20 polished games, full calibration wizard/device profiles, advanced diagnostics/log export, and performance/spatial-indexing hardening.

These are deliberately documented rather than represented by dead buttons.
