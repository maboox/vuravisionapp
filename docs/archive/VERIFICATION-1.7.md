# VuraVision 1.7 — verification status

The latest supplied GitHub Actions report for 1.7 compiled debug/release code and passed 92 of 93 JVM tests. In the remaining `BackgroundMindMapTest.pageBackgroundKeepsPatternAndCustomColorTogether` failure, the custom color was still white when asserted. The picker now uses the dialog's standard Apply callback and disables Apply for invalid hex; the test processes the UI queue before asserting. These last changes have not run in Actions. Lint and APK verification did not complete in the failed workflow.

This source adds focused graphics tests for the combined background panel, custom hex color, page-specific colors, and connector ordering behind both mind-map boxes. Static source checks and archive CRC verification do not establish a successful Android build. Run the repository's `Android APK` workflow and inspect `verification-<run>` for any test or lint failures; the APK artifact is `VuraVision-1.7-<run>` after a successful run.

On a device, open Pages → Background, choose a preset or custom color and alternate between plain, dots, grid, ruled and hatch; save and reopen the lesson, then export PDF/PNG to check persistence. Repeat on a second page and in a split panel. Select a mind-map node and change its color; check that branches stay beneath its parent and child. Choose a custom pen color and draw a line, hold for one second and adjust it. Compare ordinary writing and selection drags with the previous version; the input path was shortened but no device latency measurements are available from the source packaging environment.

Application ID remains `com.vuravision.classroom.beta`; version code is 10. Keep the same signing key for an in-place update.
