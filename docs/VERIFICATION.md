# Verified build — 1.0.0

The source included in this archive was rebuilt after workspace recovery. The enclosed APK comes from that successful build.

- Gradle tasks: `testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest` — BUILD SUCCESSFUL.
- Host tests: **50 passed, 0 failures, 0 errors, 0 skipped**. JUnit/Robolectric with Android 28 native graphics.
- Release lint: **0 errors; 332 warnings**. Full reports are included, not suppressed by a lint baseline.
- Release APK: 1.0.0 / version code 3, minimum Android 8 (API 26), target 35; non-debuggable. APK signature verification and `zipalign -c -P 16 4` pass. Development signing certificate; configure your production key before distribution.
- SHA-256: `acf6ffdc43067750d06946f95c8e63ea7ef03166b8aa42ad48027cd3eb8de53d`.

Tests cover document/history/import validation, existing Android views, numerical-label crash regression, all 32 game dialogs, 24 quiz categories across 200 random seeds each, all 24 shapes, all 16 labs at minimum/default/maximum controls, scientific reference cases, page navigation, independent pen/highlighter profiles, compact Persian navigation, ten-contact drawing with 500 move events and a completed-scene cache-reuse assertion, and a real loopback HTTP landing page/PDF download/invalid-token rejection.

Screenshots are generated from the real Android view hierarchy using Robolectric native graphics. They are not photos or measurements from a VuraVision panel. Synthetic input verifies correctness and cache behavior; it does not establish a millisecond input-latency guarantee.

## Remaining device checks

The instrumentation APK compiles; local emulator/physical-device tests and the hosted GitHub Actions run have not been executed. Real Google model downloads and handwriting accuracy, QR access from a second device, panel-specific 5/10-touch latency and contact-size calibration, and 16 KB page-size device execution remain unverified. Use DEVICE_ACCEPTANCE.md before a classroom rollout.

Floating widgets work inside this app; full handwritten formula OCR and all other omitted original-spec items are listed in CURRENT_SCOPE.md. These limitations are not hidden by the 1.0 version label.

Detailed test, lint, signature, package metadata and build reports are in `docs/verification/`; UI captures are in `docs/screenshots/`.
