# Verification — 1.4.0 source revision

Date: 2026-09-23. Base commit: `1a31802` from the supplied archive.

## Follow-up from supplied GitHub Actions log

The supplied run compiled debug/release Kotlin and unit-test Kotlin successfully, then ran 83 tests: 81 passed and 2 failed. It stopped at `testDebugUnitTest`; APK assembly and release lint completion are not established by this log. Deprecation warnings and native-library stripping messages were not the reported failure.

Corrections in this archive:

- `AndroidTest.insertTextThroughInterfaceAndUndo` now locates the new text `WorkspaceIcon` by its accessibility label, instead of searching for the removed `Button` caption. Text insertion and Undo assertions remain.
- `UpgradeTest.compactPersianScreenKeepsMenuReachable` measures/layouts the complete decor window at the capture dimensions. Previously only the content child was resized, allowing unchanged ancestor bounds to clip an RTL menu positioned on the right. The test retains the visibility assertion and now also checks that the whole menu fits inside the window.

These follow-up corrections have not been rerun in CI here. Run Actions again; no tests are skipped or disabled.

## Executed locally

- Source review of document snapshots, archive import/atomic save, shared drawing/export renderer, model download API and token-scoped LAN sharing.
- `git diff --check`.
- Parsed all 49 Android XML files; resolved all 35 Kotlin drawable references.
- Checked lexical delimiter balance in all 37 Kotlin files. This does not establish compilation or type correctness.
- Attempted `./gradlew --offline testDebugUnitTest`; wrapper could not download Gradle 8.11.1 (`java.net.SocketException: Network is unreachable`). Tests, Android compilation, lint, APK assembly and screenshot generation were **not run** locally. This is a source delivery awaiting CI/device validation.

## New regression coverage to run in Actions

`UnitConversionTest`: inch/cm, foot/inch, kg/gram, Persian digits, affine temperature conversion, area, volume, speed/time and invalid dimensions/units.

`Version14Test`: public QR control, hide/restore workspace, side-layer controls, independent opacity undo state, visible color swatches, duplicate mode switch removal, dash/layer/split archive roundtrip, renderer path, conversion preserving original writing and actionable connection-refused diagnostics. Produces `workspace-v14.png` and `layers-v14.png` in the verification artifact.

Updated older UI tests to find icon controls using their accessibility labels / stable menu ID. Existing tests cover archive assets, unsafe archive paths, atomic backups, document picker output, PDF navigation and the NanoHTTPD landing/PDF/bad-token paths.

## Required acceptance after CI passes

1. Install with the same signing identity as the previous app; open existing `.vura` lessons.
2. Test toolbar scrolling on a small screen and Persian RTL; inspect palette, side layer panel and all focus-mode exit paths.
3. Draw fine/broad, vary dash length/gap, export/reopen, compare resulting marks.
4. Add/reorder/lock/hide/rename layers and undo; drag layer handles; move selection; test 0% and 100% opacity. Layer panel closes when Undo replaces its bound page to avoid stale edits.
5. Split into 2/3/4 panes, adjust each pen/background, draw concurrently, save/reopen/export, then merge to one pane without losing items.
6. Try conversions on recognized ink and typed selected text; correct recognition before applying if needed.
7. Download English/Persian models on an accessible network. The supplied trace proves failed TCP connections, not that a model is missing on Google's server. Test actual handwriting accuracy separately.
8. Scan QR from another device on the same LAN, fetch/inspect the PDF, try alternate address if needed, confirm the URL stops after closing the dialog. Guest/client isolation or firewall rules may prevent device-to-device access.
9. Save/open a lesson with images and multipage PDFs; exercise all-page/current-page PDF, PNG and JPG via Android's document picker. Simulate a failed save and verify the prior backup remains readable.

## Sources consulted

- https://developers.google.com/ml-kit/vision/digital-ink-recognition/android — supported model manager/download and recognition API.
- https://feathericons.com/ — icon family; bundled MIT license retained.

No claim of real-device OCR, panel latency, LAN connectivity or visual screenshot verification is made for this source revision.
