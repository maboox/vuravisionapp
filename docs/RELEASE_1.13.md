# VuraVision 1.13.0

Version code 22. Complete source project, Gradle Wrapper and GitHub Actions included. The package ID and four production signing secrets are unchanged.

- Bucket fill accepts closed hand-drawn loops and detects bounded regions formed by multiple strokes, including loops within intersecting ink. Open space, erased gaps and locked outlines do not fill. Detection is bounded to 640 pixels on its longest side and limited to 256 strokes / 100,000 samples on the active layer; exceptionally large or thin scenes are rejected rather than freezing the board. Filled regions use persistent vector runs, support transforms, masks, undo and export.
- The pen menu offers Ink, Smooth pen, Marker, Dashed and Highlighter. Smooth pen follows the tip with a 65 ms filter, including display-frame updates during a pause, and finishes at the released tip. Pressure is unnecessary. Previously saved pencil/chalk/fineliner ink still renders.
- Double tap with five fingers opens the experimental pie menu, which stays off by default. Configured shortcuts refresh immediately and the menu shows only assigned slots; an empty menu initially shows six plus buttons. Configure additional slots in hidden settings.
- Double tap with two nearby fingers for one Undo. Hold the second tap, or slide horizontally while holding it, to browse history. A bottom HUD shows the current position, previews undo/redo live, and keeps the chosen position when fingers are lifted. Each step normally takes 24 display pixels, compressed near an edge so the history remains reachable; history retains the existing 30-step limit.
- The compass has a separate start-angle handle (rotation symbol on its arm) and a drawing handle (pencil symbol at the circumference). Reposition the start without creating ink, then turn the pencil handle to draw an arc.
- Shape measurements appear temporarily beside edges and angles, with at most one decimal. Point labels and geometry angles use the same concise formatting. The math solver keeps its computational precision. Ruler tick and label density adapts to calibration and zoom.
- Element cards include a colored periodic square above the sticky-note properties, with initial text size 15.
- Open About VuraVision: double tap the logo, pause 1–3 seconds, then double tap again to enter hidden settings. The old guide/title triggers have been removed.
- Fresh font defaults are Persian Kahroba and English Rubik; explicit saved choices and legacy document fonts remain intact.
- Page management uses compact rows, previews, active-page marking and related action icons. ListView recycles off-screen rows and avoids rendering all pages together.

Physical touchscreen gesture recognition, perceived stylus delay, and performance on the target display require hardware verification. Authenticated AI providers and production release signing are not exercised locally; the existing Actions signing workflow is preserved.

## Executed verification

All 201 tests in 24 suites passed, with no skips. Debug and release lint passed with no errors; existing/new warnings are recorded in VERIFICATION_1.13.0.json. Debug APK and device-test APK assembled; APK signature, package/version and 16 KB alignment verified. Web scenarios passed: 204 lab cases and 49 games. Native graphics were inspected in English/Persian.
