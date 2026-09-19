# Verification — VuraVision 1.1.0

Build date: 2026-09-19. Release version code 4, application ID `com.vuravision.classroom.beta`, min API 26, target API 35. Universal APK: 76,511,090 bytes.

## Passed locally

- Clean Gradle build: `clean testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest`, JDK 17 / Gradle 8.11.1 / SDK 35.
- **67 unit/Robolectric tests, 0 failures, 0 errors, 0 skipped.** Core 26, Android 12, Upgrade 12, version-1.1 regressions 17.
- Android lint: **0 errors**, 342 warnings (including dynamic resource lookup, hardcoded UI text and deprecated APIs); warnings are retained in the report, not presented as a clean warning-free audit.
- New regression coverage: independent contact-tip styles; area-mask pixels, resizing, undo/redo and PDF page scope; fast swept erasing; text metrics/bold/alignment; all four line directions; constrained shapes/cylinder caps; PDF swipes; second-tap actions; continuous Smart lines; lasso selection; arithmetic/linear/quadratic/rejected equations; new dialogs; three-tap hidden settings; persistence and defaults for old documents.
- Retained ten-pointer test: 500 move frames, 10 independent committed strokes, completed scene rebuilt once during the gesture. This checks the cache invariant, **not measured physical touch latency**.
- HTML DOM/native-Canvas smoke checks: **204 runs (68 labs × default/min/max controls)**, 49 games started, interacted with, timed, ended, restarted and exited, zero captured runtime errors. Snapshot output rendered. The reproducible script is `scripts/web-smoke.cjs` (`npm ci && npm test`).
- APK signature verified, ZIP native-library alignment verified with `zipalign -c -P 16 4`, manifest version inspected. APK is release-mode, development-signed. SHA-256 is in `install/SHA256SUMS.txt`.
- Native pen/text/eraser/Smart dialog renders inspected. Generated view renders and Canvas-only lab/game images are in `docs/screenshots`; they are not photographs of an Android panel.

## Not verified locally

Android instrumentation tests were compiled but not run on a device. The manual GitHub workflow can run the existing PDF-export/model-identifier tests on API 35. A full Chromium launch was blocked by the environment's socket restrictions, so the HTML checks use jsdom 26.1 with native Canvas and do **not** verify CSS layout or Android WebView behavior. Those need device acceptance testing.

No physical panel was connected. Google model downloads/recognition accuracy, bundled OCR accuracy on actual handwriting, dual-tip hardware measurements, real five/ten-touch latency, Wi-Fi QR across devices and target WebView rendering remain unverified. The screenshot's HTTP 404 root cause remains outside the app; added diagnostics/fresh retry and bundled Latin OCR are not a guaranteed repair of Google's model server. The 68 simulations have runtime smoke coverage, not a complete independent scientific audit.

Raw reports are in `docs/verification`. See `docs/DEVICE_ACCEPTANCE.md` before classroom deployment. Passing these checks is not a promise that the application is defect-free.
