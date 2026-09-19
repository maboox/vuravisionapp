# VuraVision Classroom Suite — 0.2.0 beta

Native Android whiteboard, editable lessons, English/Persian interface, eight interactive labs and eight two-player games. Android 8.0+; designed primarily for landscape tablets and interactive displays.

## Install now

The delivery ZIP includes `install/VuraVision-beta.apk`. Open it on Android and, if prompted, allow installation from the app used to open it. This is a debug-signed testing build. Export lessons as `.vura` files before uninstalling.

## Build an APK on GitHub

**Updated workflow:** if an earlier run failed with `Failed to find package 'tools'`, replace `.github/workflows/android.yml` from this ZIP and push a new commit. See [CI fix](docs/CI_FIX.md).

1. Extract this ZIP.
2. Create a GitHub repository. Push **the contents inside `VuraVision-beta`** to its root. The root must contain `gradlew`, `app/`, `gradle/`, `settings.gradle.kts` and **`.github/workflows/android.yml`**. Uploading the ZIP itself does not trigger a build. Git or GitHub Desktop preserves the `.github` folder.
3. Open **Actions → Android beta APK** after pushing. The workflow runs automatically.
4. Download the **VuraVision-beta-N** artifact from the successful run. Extract and install `VuraVision-beta.apk`.

No secrets are needed for a beta APK. The workflow installs Java 17 / SDK 35, validates the real Gradle wrapper, runs host tests and lint, builds and verifies the APK, and uploads it. A manual **Run workflow** also starts an Android emulator job for instrumented PDF tests. GitHub Actions must be enabled for the repository.

An APK built on a different machine may have a different debug signing certificate. Android cannot update across certificates. Export lessons and uninstall the previous beta first, or configure your own stable release signing key.

## Included features

- Whiteboard opens directly. Multi-pointer pen input, highlighter, whole-stroke eraser, undo/redo, pan/pinch zoom and fit-to-content.
- Rectangular multi-selection, move/resize/rotate, duplicate/copy/paste, lock/unlock and object layer ordering.
- Text, sticky notes, shapes, images and numeric image crop.
- PDF objects with page navigation and PDF/image exports through a shared renderer.
- Up to three typed function curves, numerical expressions and linear equations in the supported `ax+b=c` form.
- Multiple lesson pages and backgrounds, reorder/duplicate/delete.
- Local autosave with one backup, recent lessons, portable `.vura` archives containing media assets.
- All-page/current-page PDF and current-page PNG/JPEG export.
- Temporary PDF sharing by QR on the same local network, expiring after ten minutes or when closed.
- Explicit English/Persian handwriting model installation, real ML Kit text recognition, result review/edit, keep-or-replace ink.
- Labs: projectile, pendulum, spring, standing waves, quadratic, trigonometry, pH, coin probability. Adjustable controls and board snapshots.
- Games: reaction, tap race, arithmetic, bigger number, even/odd, timing, tic-tac-toe, rock-paper-scissors. Two-player scores, restart, side-by-side/face-to-face layouts.
- Countdown, stopwatch, dice, scoreboard and screen curtain.
- Engineering diagnostics: device touch metrics, opt-in contact thresholds, cache reset, device report and undoable stroke stress sample.

## Quick guide

**Pen** draws. **Select** moves objects: drag the orange corner to resize or the handle above to rotate. Drag empty space with Select for multi-selection. **Navigate** enables pan/pinch; **Fit** restores the overview. **Insert** adds objects, **Edit selection** changes them, **Pages** manages the lesson. Tap the title to rename it. **☰** opens all other areas.

Install handwriting models under **Settings → Models**. Initial downloads need Google's service; recognition then runs on-device. Select a small group of written strokes and choose **Smart ink**, then review the proposed text. Text recognition does not recognize two-dimensional equation layouts.

In **Settings → About**, tap the description seven times to unlock Engineering. Contact-size classification is disabled until explicitly calibrated. Hardware that reports identical pen/finger metrics cannot reliably distinguish them in software.

## Beta scope

This is the working beta requested in the later instruction, not completion of the entire original specification. [BETA_SCOPE.md](docs/BETA_SCOPE.md) identifies remaining work, and [VERIFICATION.md](docs/VERIFICATION.md) records what was actually tested.

Not included: annotation over other apps, image/camera OCR, full handwritten mathematics OCR, symbolic CAS, cloud collaboration, the full 30–40 game catalog, lasso/segment erasing or persistent object groups. The beta delivers eight labs and eight games. PDF/image objects are rasterized at bounded resolution. Crop uses numeric percentages.

Actual panel latency, vendor touch calibration, recognition accuracy and LAN transfers still need acceptance testing on your VuraVision display. No claimed 2 ms latency, 40-touch performance or recognition accuracy is inferred from desktop tests.

## Local build

JDK 17, Android SDK platform 35 and build-tools 35.0.0 are required. Set `ANDROID_HOME` or use a local `local.properties` containing `sdk.dir`.

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows: use `gradlew.bat`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Optional release signing

Add these GitHub repository secrets to build a release APK with your own stable signing key:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded keystore bytes.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Non-PR runs will then also upload **VuraVision-signed-release-N**. Retain the same key for updates. No release private key or password is included. Application ID: `com.vuravision.classroom.beta`, versionCode 2. Decide your production ID/version policy before release.

## Files and data

Lessons and media stay in private local app storage. Autosave keeps one previous backup; exported `.vura` files are the portable backup. Assets are retained to preserve undo and recent lessons; uninstalling clears private data. Imports are bounded to 64 MiB per asset and 128 MiB expanded per lesson. Earlier prototype lesson schemas are rejected rather than silently misread.

See [architecture](docs/ARCHITECTURE.md), [dependencies and privacy](docs/DEPENDENCIES.md), [third-party notices](THIRD_PARTY_NOTICES.md), and [original specification](docs/ORIGINAL_SPEC.md).
