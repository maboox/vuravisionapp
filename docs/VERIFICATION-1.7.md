# VuraVision 1.7 — verification status

The supplied GitHub Actions report for 1.6 compiled the app and passed 90 of 91 JVM tests. The remaining face-to-face touch test exposed a normalized coordinate error, corrected in the previous source delivery. The new 1.7 changes have not yet run through Android compilation, lint or device tests in Actions.

This source adds focused graphics tests for the combined background panel, custom hex color, page-specific colors, and connector ordering behind both mind-map boxes. Static source checks and archive CRC verification do not establish a successful Android build. Run the repository's `Android APK` workflow and inspect `verification-<run>` for any test or lint failures; the APK artifact is `VuraVision-1.7-<run>` after a successful run.

On a device, open Pages → Background, choose a preset or custom color and alternate between plain, dots, grid, ruled and hatch; save and reopen the lesson, then export PDF/PNG to check persistence. Repeat on a second page and in a split panel. Select a mind-map node and change its color; check that branches stay beneath its parent and child. Choose a custom pen color and draw a line, hold for one second and adjust it. Compare ordinary writing and selection drags with the previous version; the input path was shortened but no device latency measurements are available from the source packaging environment.

Application ID remains `com.vuravision.classroom.beta`; version code is 10. Keep the same signing key for an in-place update.
