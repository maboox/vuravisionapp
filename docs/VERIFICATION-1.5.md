# VuraVision 1.5 — build and device checks

The supplied GitHub Actions log for the preceding source passed 83 unit tests and assembled the release APK, then stopped at `lintRelease` because `WorkspaceIcon` extended a platform image button. That class now extends `AppCompatImageButton`. None of the 1.5 changes in this archive have run through Actions yet; do not treat an earlier result as a passing 1.5 build.

## Local checks completed

- `git diff --check` passed.
- All 50 Android XML files parsed. Every one of the 35 Kotlin `R.drawable` references resolves to an asset.
- Lexical delimiter/quote balance passed in all 39 main and test Kotlin source files. This does not check Kotlin types or Android APIs.
- New focused JVM tests cover snapping to a rotated ruler, compass radius, a hand-drawn triangle, and node attachment/subtree membership. The prior color palette test was updated for the expanded palette.
- Gradle 8.11.1, the Android SDK and their dependencies are unavailable in this runtime. Compilation, lint, screenshot rendering and APK assembly need the GitHub Actions workflow.

## Run after uploading source

The `Android APK` workflow runs `testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest`, verifies the release APK and uploads `VuraVision-1.5-<run>`. A manually dispatched workflow also runs the existing device test job. Inspect the `verification-<run>` artifact for reports, failures and UI captures. If lint or a test fails, send the full report before installing the APK.

On a device, check these interactions:

1. Draw beside a ruler edge, move/rotate/lengthen it, then draw beside a rotated edge. Repeat with the set square, protractor and compass circle; try writing away from a guide to confirm ordinary pen strokes.
2. Insert the three blank mind nodes, write across an edge and lift the pen. Move the node to check that its ink and branch follow, add a side child and lower sibling, optionally add typed text, and save/reopen the `.vura` lesson.
3. Hold the pen still for two seconds after drawing a line, triangle and circle; adjust the shape before lifting. Quickly drawn handwriting should stay handwriting unless intentionally held and recognized.
4. Change UI size, switch language, inspect the color/background swatches, save and reopen colored dotted/grid pages, then export PDF and PNG/JPG. Test an old `.vura` file too.
5. From Smart settings choose Auto, select English/Persian ink and use contextual Convert to text. Test both with and without installed handwriting models. The supplied `downloaderror.txt` shows a refused connection to `dl.google.com`; no local change can make that remote network route available.
6. Scan the local QR from the same network. For another network, send the PDF to a cloud app, set that app's link permissions, paste its public HTTPS link into the QR tool, and scan it from a different connection. The app does not host the file or manage cloud permissions.

The application ID remains `com.vuravision.classroom.beta`; version code is 8. To install over the existing app, keep the same signing key in GitHub Actions.
