# VuraVision 1.14.0

Version code 23. The application ID, document schema, Gradle Wrapper, dependency versions and four production signing secrets are preserved.

1. Main menu, files, undo, redo and share now occupy a physical bottom-right toolbar beside the focus toggle in both languages. Page controls move to another bottom row on narrow viewports. Selection actions are near the bottom.
2. Fullscreen native lab/game catalogs and activities have bottom Close, Back, snapshot and game actions. Native simulations keep sliders and playback controls near the bottom. The packaged WebView fallback bar is also at the bottom; arcade exit is bottom-aligned.
3. Image/PDF selection omits Color and handwriting Smart actions. Mixed selections recolor only eligible objects. A direct Crop action is available for a selected image/PDF object.
4. ImageCropView provides dimmed surroundings, eight side/corner handles, a movable crop rectangle, reset and confirmation. Cropping preserves object identity, rotation, centre placement and Undo; embedded PDF crop becomes an image of the selected page. Rendering/loading and saving run on the existing worker.
5. Normal menu navigation remembers its parent and provides a Back button and Android Back route. Nested open panels also have Back. Catalog/detail navigation has explicit Back actions; voice/search settings and the guide retain their parent route.
6. Selection settings are available by tapping the active Select icon again or in Settings. Free is the default, rendered as a path only. Box is rendered as a dashed rectangle only. Both select eligible object centres and exclude locked content; saved preference transfers between board and PDF surfaces.
7. Object Copy/Paste actions and pie shortcuts are removed. Duplicate has an overlapping-page plus icon. URL/diagnostic clipboard copy and PDF-to-board import remain independent of object clipboard editing.
8. All one-to-four splits use full-height vertical columns. Existing pane assignments and content are retained; reducing the count merges removed-pane objects into pane 0 as before.
9. Uncalibrated measurement uses ten document coordinates per board unit. Default ruler labels and dimension overlays follow the same scale. Physical cm still requires display-specific pixels/cm calibration.
10. Guided strokes show live length, with angles for set square/protractor and arc degrees for compass. Guide handle interaction shows live dimensions/angle; annotations are temporary and are not saved as ink.
11. On first magnetic guide entry, free samples already drawn during that same contact are discarded. On leaving that guide's snap range, later points are ignored until lift, including re-entry. Compass handle arcs start on the exact circumference and stop on radial exit. The protractor has separate inner angle-setting and orange pencil handles; the pencil creates an undoable radius ray at the chosen angle, including zero degrees.
12. A visible LTR current/total page label is part of the bottom page strip and updates on navigation and page operations.
13. Pie menu settings are in normal Settings, including the existing six persisted assignments. A toolbar button opens it directly after enabling; the five-finger double tap and long-press shortcut editing remain. The assistant keeps its hidden/off defaults.

## Verification

The 12 new regression cases are in Studio114Test. Structural/source-resource checks and JavaScript syntax checks run locally. Full Android compilation, Robolectric tests, lint, device tests and production APK are pending GitHub Actions because the delivery environment cannot download Gradle or Android dependencies. No prior test pass is used to claim a pass for modified source. See VERIFICATION_1.14.0.json and DEVICE_ACCEPTANCE_1.14.md.
