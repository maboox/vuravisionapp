# VuraVision 1.12.0 — classroom tools and compact controls

Version code 21. The package ID, Gradle Wrapper, production signing and four GitHub Actions secrets are unchanged. This is a complete source project; extract it into the repository root, including `.github`.

## Educational tools

- Offline interactive periodic table with 118 elements, Persian names, family colors, properties, search and two-element comparison. Chemistry → Atom → Interactive periodic table opens it; insert a table or an element card on the board. Select a board table and tap a cell to open the element. Main and f-block positions are separate, and unknown values are shown without invented values.
- The compass has separate center, radius and turning handles. Turning creates an arc automatically, updates the angle and commits one undoable edit. The full-circle construction action remains available. Legacy compasses are migrated when edited while preserving center and radius.
- The protractor has an angle handle. Ruler and set square construct parallel or perpendicular lines and display angles. Physical centimetres require display calibration in normal Settings → Calibration; otherwise tools use board units `u`.
- Supported shapes report lengths, perimeter, area and interior angles. Ellipse perimeter is approximate. Calibrated centimetres refer to the displayed size at the current zoom.
- Coordinate-plane objects support up to three functions, touch/numeric point placement, point removal and numerical intersections. The scan can miss tangencies or narrow features. This does not add a two-variable equation solver.
- Native lab animations stop scheduling frames when detached, hidden, outside the visible window or without window focus. The user’s run/pause choice is retained.

## Drawing and interface

- Fixed-width ink, marker, pencil, fineliner, chalk, dashed and highlighter styles; no pressure-sensitive hardware is required. Fine and broad contacts retain separate style choices.
- Bucket fill applies to a closed vector shape or a nearly closed single ink contour. It preserves the outline, supports opacity and removal, and participates in Undo. Open contours, cut ink, the page background, locked objects/layers and an object covered by another hit object are excluded. It does not flood arbitrary image pixels or join separate strokes.
- Small pen preview, one-row circular color palettes and a + color picker. Up to 40 custom colors are stored with the lesson and follow save/reopen. Text has bold, italic and alignment icons.
- Native menu rows include relevant vector icons and localized labels, including Rename. Long instructional text uses a touchable information icon and temporary popup. Small/medium/large scaling changes icons, touch controls and text together. Calibration is in normal settings; User guide has one normal menu entry.
- New-page background defaults include color and pattern. Optional inheritance copies only the current background style; the first page of a new lesson uses defaults.
- Search destination and Review text before search are independent. With review off, the first nonempty candidate opens directly even when recognition is ambiguous or Smart review is on. Ask each time still chooses a destination; empty recognition sends nothing and overlong text requires correction.
- A two-finger double tap undoes one edit when contacts are near, stationary and within the timing limits. Movement navigates, palm-size contacts are excluded, and Input controls can disable the gesture. Devices must report distinguishable finger contacts; unknown widths in two-tip mode remain available to the existing two-tip drawing logic.
- An experimental stationary five-finger one-second pie menu remains disabled by default and can be enabled only from hidden settings. Six empty + slots can be assigned to existing actions, including new layer, Undo, pen, eraser, page, thickness and an immediate color palette. Long-press changes an assigned slot; assignments persist.
- Voice microphone mute stops capture and input upload while response playback continues. A silence-only packet finishes pending live-provider voice detection. The assistant remains hidden/off by default; all three existing providers and encrypted key storage remain.

## Performance and compatibility

Arcs grow an incremental path instead of rebuilding it each frame. Filled contours use a 16 MiB bounded path cache; hit-testing clones the path before scaling, so cached drawing geometry stays intact. Fill is object-local and never allocates a page-sized flood buffer. Existing bounded image/PDF preview caching, image-drag overlays, adaptive background grids and low-zoom ink detail remain. File schema remains 3, with backward-compatible optional metadata. New capabilities are saved in `.vura` and rendered in PDF export through the same renderer.

Executed checks and limits are recorded in `VERIFICATION_1.12.0.json`. Automated graphics and gestures are not a substitute for latency, palm rejection and multi-contact checks on the actual display.

## Periodic-table data

`app/src/main/assets/science/elements.json` contains the 118-element dataset retrieved on 2026-10-05 from the official PubChem / NCBI Periodic Table endpoint:

https://pubchem.ncbi.nlm.nih.gov/rest/pug/periodictable/JSON

Explorer: https://pubchem.ncbi.nlm.nih.gov/periodic-table/

Fields are preserved from that dataset. Persian names, classroom example text and table layout are bundled locally. Element properties are read-only. No internet is needed to use the table.
