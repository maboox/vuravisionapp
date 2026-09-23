# VuraVision 1.6 — build and device checks

The supplied GitHub Actions log for 1.5 compiled both debug and release Kotlin code, then ran 87 JVM tests with four failures. The rotated ruler and compass tests used Android `PointF` in a plain JVM runner; they now run under Robolectric. The open-circle threshold was adjusted so a near-complete hand-drawn circle is accepted. The palette test now expects the 21 visible colors. None of these fixes, nor the new games and labs changes, have run in Actions yet.

## Checks in this source

- Focused Robolectric tests cover tapping the entire reaction panel, touching Pong without ending the round, mirrored hit areas in face-to-face mode, distinct high/low cards, and visible lab output changes.
- The existing `UpgradeTest` opens every active native game and renders each lab at its control limits. Native graphics tests also capture workspace and panel screenshots to the Actions `verification-<run>` artifact.
- XML, resource references, source syntax balance and `git diff --check` are checked before packaging. Those checks do not replace Kotlin compilation, Android lint or physical device testing.
- Gradle and Android SDK 35 are unavailable in the source packaging environment. A fresh GitHub Actions run is required to establish a green build.

## Run after uploading the source

The `Android APK` workflow runs `testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest`, verifies the APK and uploads `VuraVision-1.6-<run>`. The manual workflow also runs connected device tests. If the workflow fails, inspect `verification-<run>` and share the full failure report.

On a device, open Games and inspect both landscape and portrait modes. Try reaction by tapping outside its option button, move Pong paddles without ending the round, choose an answer from the upside-down player panel, and check the quiz answer layout on a narrow screen. In Labs, move each slider in gas, electrical circuit, buoyancy, dilution, pressure, osmosis, light clock and orbit; check that the illustration follows and that pause stops motion. Resize or rotate the device and check that controls stay reachable and game shapes remain round.

The application ID stays `com.vuravision.classroom.beta`; version code is 9. Updating an installed copy requires the same signing key. Export lessons before uninstalling any previous version.
