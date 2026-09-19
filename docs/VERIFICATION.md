# Beta verification — 19 September 2026

## Executed successfully

- Clean build: `clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
- **38 automated host tests passed**, 0 failures, 0 errors, 0 skipped.
- Android lint: **0 errors**, 229 warnings. Full reports are included; warnings remain in this beta (including dynamic resource lookup/unused-resource analysis, localization formatting and dependency maintenance suggestions).
- Installable debug APK assembled and signature verified by Android `apksigner`.
- APK ZIP alignment verified with `zipalign -c -P 16 4`.
- ARM64 and x86_64 ML Kit native LOAD segments have 16 KiB alignment. 32-bit ARM/x86 libraries have 4 KiB alignment. This is a static packaging check, not a run on a 16 KiB device.
- Instrumentation test APK compiled successfully.
- GitHub workflow YAML parsed and required trigger/job structure checked. Its actual hosted run will occur after you push; no GitHub run on your account is claimed here.

Host coverage includes deep undo/redo, object transforms/hit testing, multi-pointer stroke commit/undo, document validation, archive round-trips with image assets, unsafe/missing asset rejection, atomic save backup, bounded streams, image export, typed math/physics formulas, game timing, scoring and turn rules, input-dialog insertion/undo, answer locking, English/Persian interface rendering and all eight lab snapshots.

Rendered English/Persian board, game and lab views were inspected for layout. The screenshots in `../screenshots/` come from native Android view rendering under Robolectric, not a mocked HTML design. Example lesson content is for verification and is not preloaded into the blank first-run board.

## Not executed / still requires a device

This environment did not provide a usable Android emulator/device for instrumented tests. The PDF-to-PDF round-trip and language-identifier instrumentation tests are included and compiled, **not marked passed**. A manual GitHub **Run workflow** runs them on an API 35 emulator. Robolectric cannot execute the PdfDocument native path used by this app.

Real VuraVision touch latency, maximum simultaneous contacts, palm calibration, model downloads/recognition accuracy, actual cross-device LAN transfers and physical 16 KiB device behavior were not measured. Follow `DEVICE_ACCEPTANCE.md` before deploying a classroom fleet. Release-key signing needs your own key; the supplied artifact is debug-signed.

## Delivered APK

- Application ID: `com.vuravision.classroom.beta`
- Version: `0.2.0-beta` (code 2)
- Minimum Android: 8.0 / API 26; target API 35
- Size: 34,652,992 bytes
- SHA-256: `6d9bd5d5d2e00f93087ab6f751a032d5e6dc35f8cfa9983c5913676863bcf45f`

Raw build, test, lint and APK inspection evidence is in `verification/`. The beta has a defined scope and is not a guarantee that every device or input is bug-free.
