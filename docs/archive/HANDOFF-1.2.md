> Historical document. For the 1.4 source delivery, see `../README.md` and `VERIFICATION-1.4.md`.

# Resume checklist — 1.2

This is a recoverable source checkpoint, not a finished APK.

## Blocker

The installed Java runtime lacks `javac`. System package installation was denied. Obtain user direction before changing installation strategy or moving execution elsewhere; do not bypass restrictions.

## Next tasks

1. Compile with full JDK 17/SDK 35, fix source errors, run unit tests/lint and assemble APKs.
2. Add 1.2 regressions for schema migration, layer snapshots/undo/opacity/locks, Smart gestures leaving no ink, default-off QR, pen styles and eraser mask reuse.
3. Review native arcade rules and exercise answers/scoring, simultaneous input, pause/restart, timeouts and face-to-face orientation, not merely dialog creation.
4. Review lab equations at extrema and simplified visualizations: osmosis arrow direction, periodic-table coverage, collision animation and schematic molecule labels need particular attention. Do not claim exact original parity.
5. Review pattern-memory order, Stroop rules, color-memory similarity and dice/high-low feedback. Add appropriate round timeouts.
6. Review live-stroke layer preview, which may appear above other layers until commit. Profile dense-page erasing and cut-list growth.
7. Remove unused curtain implementation and WebView activity/assets after checking references. Preserve original rules as non-shipped documentation; update legacy web-smoke CI accordingly.
8. Improve native catalog titles/search; inspect English/Persian layouts, panels and vector icon inflation. Generate fresh screenshots.
9. Verify PDF page-button boundaries, QR shutdown after disabling and hidden engineering-entry behavior.
10. Check signing compatibility with the old APK. No original signing key was recovered. Do not advise uninstalling before lesson export.

## Relevant files

Main sources under `app/src/main/java/com/vuravision/classroom/`: `Document.kt`, `Files.kt`, `Board.kt`, `Renderer.kt`, `Erasing.kt`, `MainActivity.kt`, `Design.kt`, `NativeLabs.kt`, `Labs.kt`, `NativeGames.kt`, `Games.kt`.

Existing `UpgradeTest.kt` and `Version11Test.kt` were adapted to new catalog counts/PDF behavior but have not run. The updated mega prompt describes the target, not completed implementation.

Final delivery needs a newly built verified APK, full source ZIP, fresh test report and clear device/model-service limitations. Exclude local SDK paths, caches and private signing keys. Persist deliverables before linking; until verified, call archives checkpoints.
