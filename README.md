# VuraVision 1.2 — unverified source checkpoint

**No 1.2 APK is included.** The supplied GitHub run compiled debug/release Kotlin and ran 67 tests, with one failure in the compact Persian menu test. This checkpoint corrects that test's obsolete text-based icon lookup. The correction and full release workflow still require a fresh CI run.

Source changes include independent fine/broad pen styles (default fine-tip contact threshold 5 raw pixels), layer controls and persistence, cached eraser masks, PDF movement without swipe pagination, Smart lasso-only text/formula/graph gestures, hidden default-off QR, revised icons/panels and native implementations of 68 imported lab topics and 49 arcade topics with educational extras.

These are source changes, not verified functional-parity claims. Several simulations are simplified. Game rules, edge cases, device behavior and rendering require further review. Older screenshots and 1.1 documents are historical references only. Legacy HTML/reference code remains, but native catalogs are now the primary route.

## Build

Requires full JDK 17, Android SDK 35 and Gradle 8.11.1 (wrapper included):

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest
```

Resource and manifest processing completed here, but compilation stopped because the installed Java runtime lacks `JAVA_COMPILER`. System package installation was denied. See `docs/HANDOFF-1.2.md`.

Application ID: `com.vuravision.classroom.beta`; version name: `1.2.0`; version code: `5`. The included GitHub Actions build workflow has not been run for this checkpoint.

`docs/UPDATED_SPEC.md` is the target mega prompt, **not a completion checklist**. `docs/ORIGINAL_SPEC.md` preserves the original requirements.

Release builds use development signing unless production signing environment variables are configured. No private key is included. A different signing certificate cannot update an existing installation. Export important `.vura` lessons before considering any uninstall.

Google handwriting models remain an external dependency. Bundled Latin OCR is not general Persian handwriting/formula recognition. Contact thresholds and latency require testing on the target hardware.
