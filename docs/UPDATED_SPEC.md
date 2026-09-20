# VuraVision Classroom Suite — Updated Implementation Mega Prompt

**Specification revision: 2026-09-20. Language: English implementation instructions, with a fully Persian/English product.**

Use this entire document as a self-contained implementation prompt for building the application from scratch. It consolidates the original mega prompt and the user's subsequent product feedback. You do not need the previous chat to understand the intended behavior.

## What this document means

This is a **target product specification**, not a claim that every described capability exists or has passed tests in an earlier package. Build and verify the whole target. Retain the original requested capabilities unless this document explicitly changes or removes them. The current scope includes the complete reference lab and game inventories, not the smaller early MVP counts.

A previous application package was named `VuraVision-1.1.zip`; development toward 1.2 added requirements described here. If that package is supplied, inspect its actual source as a migration aid, not as proof of implementation quality. Historic messages about successful builds/tests are not evidence for your new implementation. Some in-progress source was lost when a temporary workspace was cleared; durable source checkpoints are mandatory.

The desired outcome is an installable, polished commercial Android app, complete source, repeatable GitHub Actions, and evidence from the exact delivered revision. Do not merely change a version label or remove the word beta to call it final.

## Latest decisions, already settled by the user

- Launch directly into Whiteboard; large interactive displays are the primary product.
- Dual-tip contact detection works on the user's panel. Use **5 raw contact pixels** as the new default fine-tip upper threshold, with technician calibration. Preserve intentional saved calibration.
- Both tips independently choose color, thickness and **pen style**, including normal ink, dashed, marker and highlighter. Thin ink / broad highlighter must work without changing tools.
- Open pen settings by tapping the active Pen icon again. Keep contact-threshold calibration exclusively in the hidden engineering area.
- Area erasing must remove only the traversed region, work on ink and other supported objects, and must not leave the page slow afterward.
- In Select mode, a selected PDF shows previous/next buttons and page position. **Dragging moves the PDF in either direction; swiping or scrolling must not change its page.**
- Smart Text / Formula / Function modes only lasso existing writing; they never deposit ink. Automatic Shape mode is the deliberate drawing exception.
- **Remove Screen Curtain / Screen Shade entirely.**
- QR sharing stays implemented behind an engineering flag but is **off and invisible by default**.
- Use a coherent licensed icon family such as Feather, with a documented coherent fallback if necessary. Do not invent random toolbar icons or use emoji as application controls.
- One native, visually consistent Lab catalog and one Games catalog. HTML references supply ideas and rules, not a second embedded application.
- Include all **68 reference lab topics** and all **49 reference games**, preserving additional existing educational challenges in the same catalog. Do not achieve counts through duplicate or empty entries.
- Add proper Photoshop-like document layers: show/hide, opacity, reorder, lock/unlock, select active layer and move objects between layers; persistence, Undo and export must agree.
- Persian recognition/model installation and real-device latency are unresolved engineering requirements, not features you may declare solved without evidence.

## Supplied inputs and independence

Use these inputs if attached: the original/updated prompt, official VuraVision logo, the last source ZIP, `vuravision-lab (1)(1).html` (or equivalent lab reference), `two-player-arcade(1).html` (or equivalent game reference), and the screenshot showing a Google model-download HTTP 404. The full reference inventories and key behavioral requirements are included below so implementation can proceed without those HTML files. If the references are available, inspect their actual rules, controls and formulas and reconcile any differences. Never claim to have inspected unavailable attachments.

Use the official logo when supplied. If it is unavailable, provide a clearly replaceable brand-safe asset and disclose this, without blocking unrelated implementation.

## Role and execution mandate

You are a senior Android architect, graphics engineer, AI/ML integration engineer, UI/UX designer, open-source researcher, and build/release engineer.

Your job is NOT to create a demo, mockup, proof of concept, or a collection of fake screens.

Your job is to design and IMPLEMENT a real, maintainable, production-quality Android application repository called:

# VuraVision Classroom Suite

The final repository must be ready to upload to GitHub and must successfully build an APK using GitHub Actions without requiring Android Studio on my computer.

The application is a commercial educational application intended primarily for VuraVision interactive touch displays, while still remaining installable as an APK on generic Android devices.

Do not stop after architecture or planning. Research, make technical decisions, implement the repository, test the build, fix build problems, and deliver a complete project.

---

# 0. CRITICAL RULE: RESEARCH BEFORE YOU BUILD

Do NOT implement major subsystems from scratch before researching existing high-quality open-source libraries, frameworks, reference implementations, models, components, repositories, and design systems.

For EVERY important subsystem, first search:

- GitHub
- official Android documentation
- Maven Central
- Google Maven
- Jetpack repositories
- Hugging Face when relevant
- ONNX model repositories when relevant
- open-source Android applications
- open-source whiteboards
- drawing applications
- note-taking applications
- PDF annotation applications
- education applications
- graphing/math applications
- open-source UI galleries
- design systems
- academic/open-source handwriting recognition projects

Research current options at the time you are doing this task.

Do not rely only on your pretrained memory because libraries become abandoned, APIs change, Android requirements change, licenses change, and better alternatives appear.

Before selecting a dependency, evaluate at least:

1. License
2. Last meaningful maintenance/activity
3. Android compatibility
4. Kotlin compatibility
5. Jetpack Compose compatibility
6. Android 14 compatibility
7. Current target SDK compatibility
8. ARM64 support
9. 16 KB Android page-size compatibility where native libraries are involved
10. Performance
11. Offline capability
12. Documentation quality
13. Community adoption
14. Open issues
15. Binary size
16. Ability to customize
17. Suitability for a commercial closed-source product

Prefer:

- Apache-2.0
- MIT
- BSD
- similarly permissive licenses

Be VERY careful with:

- GPL
- AGPL
- strong copyleft
- unclear licenses
- abandoned repositories
- proprietary SDKs masquerading as free libraries

This is intended for a commercial product distributed to customers.

Do not introduce a dependency whose license could force the entire VuraVision application to become open-source unless there is a very strong reason and you explicitly document that issue.

Create:

`docs/DEPENDENCY_RESEARCH.md`

For every major category include:

| Capability | Candidates researched | License | Maintenance | Offline | Android fit | Selected solution | Reason |
|---|---|---|---|---|---|---|---|

Also create:

`THIRD_PARTY_NOTICES.md`

with every dependency/model/assets license that requires attribution.

---

# 1. DO NOT REINVENT GOOD TECHNOLOGY

The philosophy is:

RESEARCH → REUSE/ADAPT → EXTEND → BUILD FROM SCRATCH ONLY WHEN NECESSARY.

For example, actively research existing solutions for:

### Drawing / Whiteboard
- low-latency ink engines
- stylus input
- pressure-sensitive strokes
- vector stroke smoothing
- path simplification
- palm rejection
- object selection
- lasso
- zoomable infinite canvas
- undo/redo systems

Examples worth investigating, but NOT blindly choosing:

- Android/Jetpack graphics APIs
- Skia
- Android Canvas
- Compose Canvas
- native Android View drawing engines
- existing open-source Android whiteboards
- open-source note-taking apps
- compose-stylus
- xnotes Android
- ChitraLekhan
- similar current projects

These are references only. Search for better/current alternatives.

If Compose is not the best solution for the high-performance ink surface, do NOT force the drawing engine into Compose.

It is completely acceptable to use:

- Jetpack Compose for UI
- a specialized custom Android View / Surface / graphics engine for whiteboard rendering

if that produces substantially better latency and stability.

---

# 2. RESEARCH UI BEFORE DESIGNING THE APP

Do not create generic Material 3 screens and call them finished.

Research high-quality modern UI libraries, interaction galleries and design systems.

Examples of design inspiration include:

https://ui.watermelon.sh/

Watermelon is mainly a React UI ecosystem, so use it as visual/interaction inspiration where appropriate rather than blindly embedding React code into a native Android app.

Also search for:

- modern Android design systems
- Jetpack Compose design systems
- open-source component galleries
- tablet interfaces
- interactive display interfaces
- spatial toolbars
- professional creative applications
- Figma-like selection UI
- modern presentation/whiteboard software
- premium education software

Build a dedicated internal VuraVision design system.

Do not randomly combine unrelated UI libraries.

The finished application must look as if one professional design team designed all of it.

---

# 3. RESEARCH COMPETING INTERACTIVE DISPLAYS

Before finalizing the Whiteboard UX, research the CURRENT capabilities of products/software from companies such as:

- Promethean
- BenQ
- Samsung interactive displays
- LG CreateBoard
- ViewSonic ViewBoard
- SMART Board
- Newline
- other major interactive-display vendors

Research their current:

- whiteboard functionality
- annotation
- handwriting
- object manipulation
- QR sharing
- PDF workflow
- geometry tools
- classroom tools
- split-screen features
- screen capture
- smart recognition
- templates

Do not clone their protected artwork or proprietary assets.

Use competitive research to identify good interaction patterns and missing opportunities.

Add findings to:

`docs/COMPETITIVE_RESEARCH.md`

---

# 4. TARGET HARDWARE

Primary devices:

VuraVision interactive touch displays:

- 65 inch
- 75 inch
- 86 inch
- 98 inch
- 110 inch
- 115 inch

Current typical hardware:

- Android 14
- 8 GB RAM
- 128 GB internal storage
- 8-core CPU
- 4K display
- multitouch
- passive stylus
- potentially up to ~40 simultaneous touch points depending on hardware

Hardware may become more powerful in future versions.

The application must scale upward without architectural changes.

It should also run on generic Android tablets/displays when technically possible.

---

# 5. UX PRIORITY

This is NOT a mobile-first application.

This is:

# LARGE TOUCH DISPLAY FIRST

while remaining responsive enough for tablets.

Touch controls must be large.

Avoid tiny desktop-style controls.

Optimize for:

- standing teacher
- classroom use
- large physical display
- fast actions
- minimal dialogs
- one/two-handed touch
- multitouch
- stylus + finger
- 4K

Do not waste canvas area.

---

# 6. APPLICATION STARTUP

There must NOT be a traditional home/dashboard screen.

When the user launches VuraVision:

# OPEN DIRECTLY INTO WHITEBOARD.

Whiteboard is the primary environment.

Lab, Games, Files, Share and Settings are secondary destinations accessible through an elegant collapsible menu/launcher from inside the Whiteboard.

Suggested destinations:

- Whiteboard
- Lab
- Games
- Files
- Share
- Settings

The menu should consume minimal canvas space when collapsed.

---

# 7. HIGH-LEVEL PRODUCT MODULES

The application has Whiteboard, Lab, Games, Files, ordinary Settings, and a hidden Engineering/Diagnostics area. All share one VuraVision design system.

Sharing/export remains available through Files. Experimental LAN QR sharing is implemented but hidden by default, with its enabling flag only in Engineering Mode. No ordinary Share/QR shortcut should advertise a disabled feature.

Whiteboard remains the entry point and central document. Lab snapshots, equations, graphs and imported media return to the same board.

---

# 8. WHITEBOARD CANVAS

Implement a professional whiteboard.

Required:

- effectively infinite canvas
- smooth pan
- pinch zoom
- multi-touch navigation
- multiple pages
- page thumbnails
- add page
- duplicate page
- delete page
- reorder pages
- fast page navigation
- page overview
- optional fit-to-content
- reset viewport

Infinite workspace and page-based organization should coexist sensibly.

---

# 9. OBJECT-BASED ARCHITECTURE

The canvas must NOT merely be a giant bitmap.

Create a proper document/object model.

Examples of CanvasObject types:

- InkStrokeObject
- TextObject
- EquationObject
- GraphObject
- ImageObject
- PdfObject
- ShapeObject
- GeometryObject
- ThreeDObject
- StickyNoteObject
- TableObject
- PeriodicElementObject
- LabSnapshotObject
- ScreenshotObject

Every compatible object should support operations such as:

- select
- deselect
- multi-select
- lasso select
- move
- resize
- rotate
- duplicate
- delete
- copy
- paste
- lock
- unlock
- group
- ungroup
- bring forward
- send backward
- bring to front
- send to back

The document architecture must allow new object types later without rewriting the whole whiteboard.

Use stable IDs.

---

# 10. LOW-LATENCY DRAWING ENGINE AND TOOL SEMANTICS

Research native and permissive open-source approaches, then implement a measured low-latency vector ink engine.

Required pen styles include normal ink, pencil, marker, highlighter, brush where technically supported, and dashed ink. At minimum normal ink, marker, dashed and highlighter must have clearly different visible behavior:

- Ink: precise, opaque, round stroke.
- Marker: opaque broad/chisel-like stroke, distinguishable from ink.
- Highlighter: translucent stroke that leaves underlying text legible.
- Dashed: stable dash spacing along the complete path, not restarted at every touch sample.

Each tip independently selects style, color, thickness and supported opacity. Style changes affect subsequent strokes; old strokes retain their own properties. Explain styles with visual previews and localized labels.

Re-tapping the active Pen icon opens Pen Studio. Use labelled Color, Thickness, Style and Preview sections with comfortable spacing and obvious Fine Tip / Broad Tip selectors. Highlighter belongs in this style system; a separate shortcut, if included, is only a preset using the same model and must not create conflicting settings.

Use stable pointer IDs, historical MotionEvent samples, interpolation and pressure only when reliable. Avoid rebuilding all historical paths or the complete scene on every move. Keep live strokes separate from cached committed content. Persist vector data. Bound allocations, caches and memory.

Target stable 60 FPS on the actual panel, benefit from higher refresh rates, and measure 1/2/5/10 simultaneous contacts. If hardware reports more, do not artificially cap it at 10. No seconds-long input backlog. A panel's advertised 2 ms touch specification is not measured app latency and must not be advertised as such.

Document measurement hardware, document load, frame-time distribution and observed input-to-display latency separately from host functional tests.

---

# 11. INDEPENDENT DUAL-TIP APPEARANCE AND CONTACT CLASSIFICATION

VuraVision may use a passive stylus. The fine and broad ends can have no electronic identity; finger and broad tip may overlap in reported contact area. Do not pretend to distinguish physically indistinguishable contacts.

Use available Android toolType, touchMajor, touchMinor, size, pressure and device characteristics. Prefer valid hardware tool information but test real panels rather than assuming every stylus report is accurate. Contact classification must not flicker midway through a stroke; use a stable per-pointer classification or documented hysteresis.

The user's panel performed well with **5 raw touchMajor pixels as the fine-tip upper threshold**, replacing the previous default of 10. This is a reported-contact threshold, not a 5 px drawn stroke width, dp value, or assumed physical millimetres. Display the units accurately. Physical units require verified device calibration.

Provide independent persisted profiles:

| Contact | User-configurable appearance / behavior |
|---|---|
| Fine tip | Normal ink / dashed / marker / highlighter / other supported style; own color, thickness and opacity |
| Broad tip | Same independent choices; can differ from fine tip |
| Finger | Drawing or navigation when distinguishable; disclose ambiguous hardware |
| Palm | Reject or temporary erase, with threshold and enable flag |

Examples that must work: fine blue normal ink + broad yellow highlighter; fine black dashed + broad red normal ink. Switching ends must not require opening a menu.

Ordinary Pen Studio exposes appearance only. Calibration enablement, contact thresholds, palm classification, device profiles and raw contact metrics belong only in hidden Engineering Mode. Preserve existing manually calibrated values during upgrades; a new default must not silently overwrite them.

---

# 12. PALM ERASER

A large contact region such as the side/palm of the hand should optionally act as a temporary eraser.

Important:

It should not permanently switch the selected Pen tool.

When the palm leaves the display, the previous tool remains selected.

Provide:

- enable/disable
- sensitivity
- minimum contact threshold
- calibration
- visual diagnostic display

If reliable distinction is impossible on specific hardware, do not fake it.

Use Engineering Mode to diagnose and calibrate.

---

# 13. STROKE, AREA AND OBJECT ERASING

Provide clearly labelled modes:

1. Whole-stroke eraser: touching a stroke deletes that complete stroke.
2. Area eraser: removes only the swept path, leaving the remainder intact.
3. Object deletion/eraser: explicit whole-object behavior.
4. Optional temporary palm eraser governed by the hidden touch profile.

Area erasing applies to ink and supported text, shapes, images, graphs, sticky notes and PDF content. Use vector fragments or persistent object-local masks as appropriate. A white painted stroke is not an eraser: it fails on non-white backgrounds and layered content.

Erasures move, scale and rotate consistently with the object, survive save/reload and appear identically in PNG/JPEG/PDF exports. PDF masks/annotations that apply to a page are scoped to that page. Offer Restore Erased Content when using nondestructive masks. Undo/Redo must restore exact content and object identity.

Never erase locked layers or locked objects. Clearly define current-layer versus all-visible-unlocked-layers erasing in the UI. Hidden layers stay untouched. Test that an eraser does not expose a misleading opaque white patch over underlying layers.

Fix both observed performance problems: lag during erasing and persistent page lag afterward. Avoid reconstructing a growing mask history on every frame, allocating a path per historical sample, or redrawing all objects on each event. Cache or incrementally consolidate masks, batch gesture work, repaint only affected regions/tiles, and compact history safely. Benchmark after repeated erasing and after resuming drawing, not just on a fresh page.

Retain clear ink / selected objects / page / document commands with Undo and appropriate destructive-action confirmation.

---

# 14. SELECTION AND CONTEXTUAL OBJECT ACTIONS

Support tap, box, lasso, multi-select and Select All, with large resize/rotation handles.

In Select mode: the first tap selects an object; tapping the already selected object again without dragging opens its Object Actions near the selection or finger. A drag moves it and must not accidentally open a menu. Context actions must be reachable without searching a remote menu.

Include relevant edit, duplicate, copy/paste, delete, lock/unlock, group/ungroup, ordering, layer assignment, color and object-specific actions. Respect layer locks. For overlapping objects, hit-test the actual visible stacking order, not a stale insertion order. Provide a route to unlock locked objects/layers even if ordinary selection excludes them.

PDF selection exposes previous/next/page indicator controls. Normal vertical and horizontal drags move the PDF; wheel/scroll events do not implicitly change its page.

---

# 15. UNDO / REDO

Implement command-based or similarly robust Undo/Redo.

Support actions including:

- drawing
- erase
- move
- resize
- rotate
- delete
- paste
- group
- ungroup
- background change
- import
- object property change

Avoid storing a full rendered bitmap for every operation.

---

# 16. SMART HANDWRITING — REQUIRED IN V1

This is NOT a placeholder feature.

Research current open-source/on-device solutions for handwriting recognition.

Research:

- Android-compatible OCR
- ONNX models
- TensorFlow Lite models
- PaddleOCR ecosystem
- handwriting-specific transformers
- stroke-based recognition
- image-based handwriting recognition
- Hugging Face models
- Persian handwriting models
- English handwriting models

Priority:

1. fully offline
2. downloadable once then offline
3. cloud only if unavoidable

Required language targets:

- English
- Persian
- numbers
- common symbols

Architecture must abstract recognition behind an interface so the recognition engine can later be replaced.

For example:

`HandwritingRecognitionEngine`

with implementations such as:

- LocalModelRecognitionEngine
- OptionalOnlineRecognitionEngine

Do not tightly couple UI to one model vendor.

---

# 17. MODEL MANAGER, HONEST FAILURES AND OPTIONAL FILE IMPORT

Recognition must have a provider-neutral interface. Research a genuinely deployable offline engine for English, Persian and mathematical input. Prefer a bundled baseline model where suitable, with downloadable-once larger models.

Model Manager shows truthful installed/available/provider/language/version/size/progress/storage/error states. Include cancel where supported, retry, re-check, remove/reinstall and update. Do not invent byte progress, version identifiers or checksums when a vendor SDK does not expose them; show an indeterminate operation and explicitly mark SDK-managed metadata.

The user observed **HTTP 404** from a Google digital-ink model download. Show the actual provider error and sanitized diagnostics. A 404 means a resource was not found; it is not proof that internet is disconnected or Android Download Manager is disabled. Check supported language identifiers, SDK compatibility, endpoint/model availability, network conditions and real device behavior. A prettier error screen is not a fixed download.

For app-managed open models, support manual model-package import through Android Storage Access Framework, including local storage, USB or Google Drive's document provider. Validate model manifest, supported runtime/version, language, size limits, required files and SHA-256 against a trusted manifest. Stage installation atomically; reject corrupt/incompatible archives without damaging the current model. Include model license attribution and a smoke inference after installation.

**Do not present a file-import button for a vendor engine that cannot load such files.** The documented ML Kit Digital Ink Android API uses DigitalInkRecognitionModel and RemoteModelManager; it does not expose the ordinary app-managed model-file import path requested by the user. Verify current SDK capabilities at implementation time. Do not reverse-engineer private caches or instruct users to place arbitrary Google model files into them. If retaining this SDK, manual import belongs to a separate supported local engine; otherwise explain this limitation and implement a suitable alternative.

A bundled Latin image OCR fallback is useful, but it is not equivalent to Persian handwriting recognition or general math OCR. Expose supported languages and notation honestly. Existing installed models must remain usable offline. Whiteboard remains functional if every model operation fails.

Provider documentation reference to recheck: https://developers.google.com/ml-kit/vision/digital-ink-recognition/android

---

# 18. HANDWRITING UX AND REVIEW

Teacher writes using the ordinary Pen. In Smart Text mode, teacher draws a lasso around existing handwriting. Recognize only the selected eligible strokes, excluding the lasso itself, hidden/locked content and unrelated neighboring items.

Show editable candidate text and a preview. Allow Replace Original Ink, Keep Original + Add Text, or Cancel. The resulting text is a normal movable/editable object. The operation is one Undo step; preserve the intended layer. Handle an asynchronous result safely if the user changes page, closes the dialog or edits/deletes the source.

Do not save failed Smart gestures as ink. Explain why nothing was selected and how to try again. Ordinary selection followed by Smart should also process the current selection without requiring a second lasso.

---

# 19. HANDWRITTEN MATHEMATICS

This feature is REQUIRED.

Research modern open-source handwritten mathematical expression recognition.

Search specifically for:

- handwritten math → LaTeX
- ONNX math OCR
- offline Android mathematical OCR
- CROHME-based models
- pix2tex-like approaches
- LaTeX OCR projects
- Android ONNX Runtime implementations

Do NOT automatically choose an old or proprietary library merely because you remember its name.

Evaluate current alternatives.

Pipeline should conceptually allow:

Ink/Strokes
→ region rendering/preprocessing if necessary
→ Math Recognition
→ normalized expression / LaTeX
→ mathematical parser
→ renderer
→ solver

---

# 20. SMART MATH ACTIONS AND CONCRETE EXAMPLES

Implement recognition → editable normalized expression → mathematical parsing → solve/evaluate/plot → movable result object.

Acceptance examples:

- Handwrite `2*(3+4)` with Pen, circle with Smart Formula, review, calculate → `14`.
- Handwrite `2x+3=11`, circle with Smart Formula, review, solve → `x=4`.
- Recognize `2x+4=12` → `x=4`.
- Recognize/type `y=2x-5`, choose Smart Function/Plot → a correct line graph.
- Recognize/type `y=x^2-4x+3` → a correct quadratic graph with roots 1 and 3.

Support equation conversion, evaluation, solving, simplification and plotting as genuinely implemented by the chosen math engine. Required baseline includes arithmetic, parentheses, implicit multiplication and linear/quadratic equations in x. Preserve the broader original requirement for proper mathematical rendering and research a suitable engine for more advanced notation.

Never return an invented answer for an unsupported equation, malformed expression, ambiguous handwriting, domain error or divide-by-zero. Distinguish recognition uncertainty from a valid mathematical result. Use bounded parsing and computation without evaluating arbitrary code. Keep math direction LTR inside Persian UI. Include both Persian/Arabic and Latin digit normalization where appropriate.

---

# 21. SYMBOLIC MATH

Research open-source options for:

- algebra parsing
- equation solving
- simplification
- expression evaluation

If no good native Android/Kotlin symbolic system exists, research reasonable embeddable alternatives.

Possible approaches MAY include:

- Kotlin/JVM math libraries
- WASM/native symbolic libraries
- carefully embedded Python only if appropriate
- SymPy-based local subsystem only if architecture, APK size, performance and licensing make sense

Do not choose a heavyweight solution without justification.

Implement a clean interface:

`MathEngine`

so the backend can change later.

---

# 22. MATH RENDERING

Research current open-source math rendering solutions.

Need professional rendering of:

- fractions
- roots
- powers
- integrals
- matrices where supported
- Greek symbols
- equations

LaTeX representation is acceptable internally.

Do not display raw LaTeX to ordinary users unless editing.

---

# 23. SMART TOOL MODES AND GESTURE CONTRACT

Expose four clear modes: Text, Formula / Calculate / Solve, Function / Plot, and Automatic Shape.

**Text / Formula / Function** are selection tools, not writing tools. They draw only a temporary lasso/selection overlay. An open gesture, empty region, unsupported content or failed recognition disappears without changing the document. Never commit the gesture as ink, never pass it to the recognizer, and never create an unnecessary Undo entry.

A nearly closed loop should be accepted with a size-aware tolerance. Selection must be robust for rotated/scaled strokes, punctuation, dot strokes and fragmented writing. Use actual transformed geometry where practical rather than only object-center inclusion. Provide visible selection feedback and a concise empty-selection hint. Process only eligible visible/unlocked items, with a documented policy for multiple layers.

**Automatic Shape** is intentionally different: draw normally while this mode remains active. On pointer-up, recognize and replace supported geometric strokes automatically, without tapping Smart after every shape or drawing an enclosing circle. Keep unsupported strokes as ink or offer a clear fallback according to the documented mode. Undo restores the prior state. Repeated shapes and multiple pointers must not mix their samples.

Make the current mode visible and switching back to Pen easy. Do not create a mode that silently alternates between writing and selection depending on whether the gesture happens to be recognized.

---

# 24. FUNCTION PLOTTER AND GRAPH OBJECTS

Provide a real typed and handwriting-assisted function plotter. Explain supported syntax with tappable examples: `y=2x-5`, `y=x^2`, `sin(x)`, `cos(x)`, `sqrt(x)`, `abs(x)` and multiple functions such as `sin(x);cos(x)`.

The parser must normalize optional `y=`, implicit multiplication, supported powers and digits. Validate before creating a graph; show a useful error at the input rather than inserting a blank object.

Required: line graphs, curved functions, axes, readable labels, grid, domain/range controls, multiple curves with distinguishable colors/legend, editing after insertion, pan/zoom/reset as appropriate. Trace/readout is desirable. Support ordinary chart/data-series objects when offered; do not label a static axes illustration as a functioning plotter.

Use equal physical scale for x and y by default so angles and shapes are not distorted; axis range changes are explicit. Resizing a graph changes its viewport with a defined aspect-ratio policy, not a stretched bitmap. Do not connect across undefined values, asymptotes or large discontinuities. Negative square-root domains, singularities and extreme ranges must not crash rendering.

Graphs remain editable independent board objects, participate in layers/selection/erasures/save/export, and work offline. No cloud calls are needed for ordinary plotting.

---

# 25. AUTOMATIC SMART SHAPES

Implement offline recognition for supported lines, circles, rectangles, triangles and other reliably recognized shapes. Use permissive libraries or a measured geometric recognizer after research.

With Automatic Shape mode enabled, each completed drawing is recognized immediately. The mode remains active for subsequent shapes. No repeated button press and no lasso are required.

Preserve color, stroke style, width and layer when replacing ink. Respect uniform/proportional and parametric shape rules. Provide sensible rejection for low-confidence input rather than converting every scribble. Test multiple scales, reversed directions and repeated strokes. This geometric recognizer must not depend on downloading a Google text model.

---

# 26. DIRECT SHAPE GALLERY AND RESIZING RULES

Shapes must be directly accessible from the main board toolbar, with previews and localized names. Do not duplicate Shapes under Insert. Keep Text directly accessible as well; Insert should not duplicate it.

Baseline gallery: rectangle, square, rounded rectangle, circle, ellipse, triangle, right triangle, diamond, pentagon, hexagon, octagon, star, heart, trapezoid, parallelogram, line, arrow, double arrow, cross, speech bubble, cylinder, cube, cone and coordinate axes. Retain the original arc/polygon and further educational geometry requirements where not covered by these.

Define resizing semantics per type:

| Type | Behavior |
|---|---|
| Rectangle, ellipse and other intentionally freeform geometry | Independent width/height where sensible |
| Circle, square, regular polygon, cube and fixed-ratio symbols | Uniform proportions; optional explicit conversion to a freeform variant |
| Line / arrow / double arrow | Exactly from touch-down point to release point; preserve direction in every drag quadrant |
| Axes / function illustration / graph | Consistent coordinate scale; never distort a function by independent bitmap stretching |
| Cylinder, cone, prism and related solids | Parametric geometry: change height/radius/depth, recompute caps/faces, preserve valid geometry |

For a cylinder, dragging height makes the body taller/shorter while top/bottom cross-sections retain a consistent perspective for the chosen radius. Do not stretch ellipses taller merely because cylinder height changed. Handle extreme short/wide cases without inverted or intersecting faces; constrain impossible geometry explicitly.

Gallery previews must obey the same geometry rules—circles/squares must not look stretched in their cards.

---

# 27. GEOMETRY TOOLS

Provide:

- ruler
- protractor
- compass
- set square

They must support touch:

- move
- rotate
- resize where sensible

Drawing against a ruler edge should produce accurate lines.

---

# 28. 3D EDUCATIONAL OBJECTS

Research suitable open-source Android 3D rendering options before choosing.

Candidates may involve:

- Filament
- OpenGL
- Scene libraries
- other maintained open-source Android 3D solutions

Do not use an abandoned 3D library without strong justification.

At minimum provide:

- cube
- cuboid
- sphere
- cylinder
- cone
- pyramid
- prism

Interaction:

- rotate
- zoom
- move
- resize

Keep architecture extensible for chemistry molecules and future 3D teaching models.

---

# 29. TEXT OBJECTS AND TIGHT BOUNDS

Editable TextObject supports Persian/English, proper shaping, RTL/LTR, color, font size, bold/regular, alignment and multiline content. Show these controls before insertion and when editing an existing text object.

Selection bounds should closely fit laid-out glyphs and intentional padding, not an arbitrary large rectangle. Recompute after changing text, font, size, weight, wrapping or alignment. Keep handles usable while avoiding oversized blank hit regions. Mixed Persian, Latin digits and equations must remain legible.

Text is directly available on the main toolbar and must not be duplicated under Insert. Use legally redistributable fonts and preserve styling in save/open/export. Area erasure must not make the underlying editable text model disappear; keep masks or a clearly documented compatible representation.

---

# 30. STICKY NOTES

Provide movable, resizable, editable sticky notes with duplicate/delete and layer integration. Note background color is independently selectable in the note editor and can be changed after creation. Keep text color and note color distinct. Offer readable preset combinations, sensible padding and fitting text layout. Save, Undo and export preserve both note and text styling.

---

# 31. TABLES

Create editable tables.

Support:

- rows
- columns
- cell text
- resize
- move
- delete

---

# 32. BACKGROUNDS

Support:

- white
- black
- dark
- custom color
- grid
- dot grid
- horizontal ruled
- graph paper

Template architecture should allow additional classroom templates.

---

# 33. IMAGE OBJECTS

Import:

- PNG
- JPEG
- WEBP where practical

ImageObject:

- move
- resize
- rotate
- crop
- lock
- duplicate
- annotate around/on top
- delete

Research good OSS cropping/image handling libraries before writing custom code.

---

# 34. PDF AS A CANVAS OBJECT — BUTTON PAGINATION

A PDF is a movable/resizeable/selectable/lockable board object, not merely a separate viewer. Import large documents safely using a maintained Android renderer with bounded page caching, background rendering and correct native ABI/16 KB support where relevant.

When a PDF is selected in Select mode, show large **Previous**, **Next**, **current page / total pages**, and optional page-picker controls beside it or in a clearly contextual toolbar. Disable unavailable directions at the first/last page.

**Vertical dragging, horizontal dragging, wheel scrolling and touch swiping must not turn PDF pages.** Dragging the object moves it freely in both axes. Pagination happens only through explicit controls. Keep the PDF object selected after paging, refresh its content and preserve its position/size.

Retain independent PDF-page erasure/annotation state where applicable. An erase on page 2 must not punch a hole in page 1. Multiple PDF objects must paginate independently. A cached preview from one object/page must not be displayed for another.

Support crop/snapshot to a separate image, readable zoom, offline operation, safe import failures, save/open and export of the displayed page state.

---

# 35. PDF SNAPSHOT / CROP

Teacher can:

1. display a PDF page
2. select a region
3. press Snapshot
4. region becomes independent ImageObject
5. move it elsewhere
6. resize it
7. annotate it

This interaction must be genuinely implemented.

---

# 36. PERIODIC TABLE

Include an interactive periodic table.

Available from:

- Whiteboard quick tools
- Chemistry Lab

Touch element to show:

- element name
- symbol
- atomic number
- atomic mass
- group
- period
- category
- electron configuration

Use a legally usable/open dataset.

Future architecture should support richer data.

---

# 37. FLOATING CLASSROOM TOOLS — NO SCREEN CURTAIN

Provide Timer, Countdown, Stopwatch, Dice, Spinner, Random Number and Scoreboard where applicable. Tools must remain visible while the teacher writes, moves objects, changes board pages or opens a small tool panel.

Use in-app floating PiP-like windows/cards, with drag, collapse/minimize, restore, close and safe placement within screen bounds. They must not turn into blocking dialogs that vanish when drawing resumes. Multiple tools can coexist without stealing all touch input. Prevent timer duplication or leaks across activity/lifecycle changes.

This is primarily in-app floating UI, not a requirement for Android video Picture-in-Picture mode or system-wide overlays. Cross-app annotation is a separate feature with explicit permissions.

**Remove Screen Curtain / Screen Shade.** The user rejected it; do not keep a visible menu item, shortcut, dormant customer toggle or replacement shade feature.

---

# 38. VERSIONED DOCUMENT FORMAT AND LAYER MIGRATION

Use a versioned `.vura` container with manifest, domain-model document data, assets and optional thumbnails/metadata. Never serialize Android UI objects.

Persist all editable objects, styles, transforms, PDF page state, page-scoped erasure masks, page order, document/layer IDs, layer order/visibility/opacity/locks, active layer and object-layer membership. Exported bitmaps are not substitutes for the editable source.

Support migration from supplied older files. A legacy page without layers migrates to one visible, unlocked, fully opaque base layer; every old object belongs to it. Validate unique IDs, references, bounds, finite numeric values, mask limits, asset paths and supported schema versions. Reject malformed archives, path traversal, unreasonable sizes and unknown future schemas safely.

Page duplication must deep-copy layer metadata and masks/objects appropriately; changes on one page must not alter another through shared mutable references. Undo snapshots must preserve layer state without copying a full raster image per action. Save atomically with recovery/backup strategy. Add round-trip and old-file migration tests.

---

# 39. AUTOSAVE / RECOVERY

Implement:

- autosave
- last session restore
- crash recovery
- power-loss tolerance where possible
- recent files

Avoid losing an entire lesson after application termination.

---

# 40. EXPORT THAT MATCHES THE DOCUMENT

Export high-quality PNG/JPEG/PDF: current page, selected pages and all pages as appropriate. Respect current layer order, layer visibility, group opacity, transforms, transparent erased regions and displayed PDF-page content. Hidden layers must not appear in exports or thumbnails.

Render each layer's opacity as group opacity, not separately on every overlapping child, unless an explicitly documented different blending model is intended. Transparent output remains transparent where supported; JPEG uses a chosen background.

Support quality/resolution choices with sensible memory limits. Compare representative exported images against on-screen rendering, including masks over colored backgrounds and lower layers.

---

# 41. EXPERIMENTAL LOCAL QR SHARING — HIDDEN BY DEFAULT

Retain LAN PDF sharing behind a centralized engineering feature flag. Default the flag to **off**, including clean installs and upgrades where no technician explicitly enabled it. Ordinary users see no QR button/menu/settings entry while disabled.

Engineering Mode may enable it for troubleshooting. Only then expose sharing UI. Export a PDF, start a tokenized temporary HTTP server, select a reachable LAN interface, show URL/QR, and allow a same-network phone browser to download. Handle Ethernet/Wi-Fi, multiple addresses, VPN/loopback/link-local addresses and Android network changes explicitly.

Closing the QR dialog must not silently stop a server whose UI promised that sharing remains active. Conversely, leaving the server running must be clear, time-limited and stoppable. Define behavior on backgrounding/process termination using supported Android lifecycle/foreground-service rules if needed.

Serve only the explicit exported file, use a high-entropy unguessable token and expiry, expose no directories/private files, redact tokens from logs and stop after expiry or user action. Surface AP/client isolation and wrong-interface problems with useful diagnostics.

The user still cannot download via QR even on the same network. Generating a QR image or passing a localhost HTTP test does not prove the cross-device problem is fixed. Keep the feature off until technician-enabled testing on the actual display and a separate phone succeeds. The default release UI remains hidden regardless of isolated test success until the product owner changes that decision.

---

# 42. ONE NATIVE LAB — COMPLETE REFERENCE INVENTORY

Implement all **68 reference lab activities** listed in Appendix A, plus useful existing core activities such as energy, dilution, triangle area, descriptive statistics and trigonometry where they add distinct value. Deduplicate genuinely equivalent activities. No empty titles, decorative animations masquerading as experiments, or duplicate cards to inflate counts.

The earlier 12–15-simulation MVP limit is superseded. Stage delivery internally to maintain quality, but do not call the requested whole product complete with only the small early subset.

Use the supplied HTML only as an inventory of topics, interactions and formulas. Rebuild in the shared native VuraVision design system; do not create “Discovery HTML Lab” and “Core Native Lab” as separate styles or destinations. A justified specialized engine may be shared internally, but must not simply wrap the legacy website.

Each card has a clear localized title, subject, preview and short description. Provide searchable/filterable Physics, Chemistry, Mathematics/Geometry and Statistics/Probability categories. A simulation has appropriate touch controls, labelled units/ranges, meaningful visualization, observable values, start/pause/reset, formula and concise teaching explanation. Add to Whiteboard produces a useful snapshot/graph/formula/state object.

Correct science matters more than a pretty moving canvas. Clearly distinguish a schematic/conceptual illustration from a quantitatively scaled simulation. No false physical claims; specify simplifying assumptions. Test defaults and control extremes, repeat resets, pause/resume and multiple entries.

Known regressions to avoid: projectile x/y scaling that falsifies launch angle; converging lens image-sign/real-versus-virtual mistakes; ideal-gas pressure readout inconsistent with PV=nRT; paused simulations becoming blank; old animation callbacks altering a newly opened simulation.

---

# 43. RESEARCH SIMULATION LIBRARIES

Before writing every visualization yourself, investigate:

- physics engines
- chart libraries
- plotting engines
- 3D engines
- molecule rendering
- scientific visualization
- geometry libraries

Use libraries only where they improve the product.

Avoid adding a huge physics engine merely for an animation that needs 30 lines of deterministic math.

Balance dependency weight and usefulness.

---

# 44. LAB → WHITEBOARD

Important integration:

Every useful Lab simulation should support:

# Add to Whiteboard

Possible outputs:

- current snapshot
- diagram
- graph
- formula
- simulation state summary

It becomes an Object on Whiteboard.

Lab is not an isolated app.

---

# 45. ONE NATIVE GAMES CATALOG — COMPLETE REFERENCE INVENTORY

Implement all **49 reference games** listed in Appendix B. Preserve the additional 24 educational challenge types in Appendix C, along with useful existing distinct games. Deduplicate overlaps such as two copies of the same reaction game. Counts must refer to real playable activities, not empty entries or cosmetic variants.

The original 15–20-game first-release limit is superseded. One Games destination, common native catalog, shared visual design, search/filter/categories and consistent match controls. No separate HTML Arcade versus Native Games menu.

Reference HTML is a gameplay/orientation reference. Rebuild the actual mechanics in native Android/common rendering components. Use maintained permissive engines/libraries where beneficial. Do not replace Pong, memory matching, Connect Four, timing or target-hitting games with a generic multiple-choice quiz and keep their old names.

Cards need localized names, previews and concise rules. Matches need clear start/countdown, scores, progress, correct/incorrect feedback, round result, match result, restart and exit. Improve graphics and animation while preserving readability and large hit targets.

Two players must interact concurrently where the rules allow, without one touch blocking the other. Randomized tasks must be equally difficult. Define ties, simultaneous answers, early taps, wrong-answer penalties, timeouts, turn-taking and game completion per game. Numeric strings must never be passed to an Android resource-ID API; this caused the earlier Math Speed crash.

Clean up callbacks/timers/input state on reset, exit and lifecycle transitions. A timer from a previous round must never resolve or mutate the next round. Classic shared-board games are deliberately turn-based; do not falsely market them as simultaneous-answer games.

---

# 46. TWO-PLAYER LAYOUT

Games that support two simultaneous players need:

- Top / Bottom
- Left / Right
- Auto

Top/Bottom allows players facing opposite sides of a display.

Left/Right works for players standing beside each other.

Auto chooses sensible layout based on orientation/aspect ratio.

Remember this application may run on:

65–115 inch displays.

Do not design game hit targets like a phone game.

---

# 47. GAME ENGINE AND RULE FIDELITY

Separate player input, round generation, timing, scoring, win/tie/lose logic and presentation. Reuse a common match/session engine without flattening all games into one mechanic.

Use monotonic time. Maintain per-pointer and per-player state for concurrent interactions; use a generation/session token or cancellable scopes for delayed work. Make deterministic random seeds injectable for tests. Fairness, answer-lock policies and tie handling must be explicit.

Support shared-board turn-based games, simultaneous quizzes, precision/timing contests, memory sequences, drag/swipe speed games and real-time arcade physics. Restart must clear all relevant state, and backgrounding must have a documented pause/resume policy.

The current baseline is the complete 49-reference-game inventory plus additional distinct educational challenges, not a future 40–50-game promise. Future additions should require implementing a clear activity contract rather than changing a monolithic switch everywhere.

---

# 48. SOUND

Research legally safe/open sounds if needed.

Prefer subtle classroom-friendly sounds.

Allow mute.

Do not use copyrighted game sounds.

---

# 49. HIDDEN ENGINEERING MODE — EXACT ENTRY FLOW

The canonical hidden entry is **Quick Guide → tap its title three times**. Use a short reasonable tap window so unrelated taps do not unexpectedly unlock it. Keep this path documented for technicians, not displayed as a normal classroom action.

Do not permanently expose an Engineering item in ordinary Settings after one successful unlock unless an explicitly selected technician-session policy requires it. Release defaults keep it hidden. An optional technician/admin PIN may protect destructive actions; an obscure gesture is not a security boundary.

Engineering contains dual-tip calibration/thresholds, palm behavior, raw pointer diagnostics, device profiles, performance and memory diagnostics, sanitized error/log reports, model/provider status, cache inspection and experimental flags, including **Enable LAN QR sharing**, off by default.

Pen appearance remains easy to access in Pen Studio; calibration remains hidden. No customer-facing calibration button should leak back into Pen Studio. The older About/logo-seven-tap example is superseded by the three-tap Quick Guide flow.

---

# 50. TOUCH VISUALIZER

Engineering Mode should contain a raw touch diagnostics screen.

For every active pointer display:

- pointer ID
- X
- Y
- tool type
- pressure
- size
- touchMajor
- touchMinor
- orientation
- axis values available from MotionEvent
- estimated contact area
- classification result

Draw a visible circle/ellipse representing reported touch size.

Give each simultaneous pointer a distinguishable visualization.

Show:

- current touch count
- maximum observed touch count

---

# 51. MULTITOUCH TEST

Create dedicated multi-touch test.

Support observing:

- 1
- 2
- 5
- 10
- 20
- 40 touches

Do not artificially cap at 10 if Android hardware reports more.

Display live detected maximum.

This is important for hardware QA.

---

# 52. INPUT CALIBRATION WIZARD

Calibration wizard for:

- thin stylus tip
- thick stylus/back
- finger
- palm

Ask installer to touch screen several times.

Collect distributions of:

- major/minor
- pressure
- size
- tool type

Calculate suggested thresholds.

Show raw sample data.

Allow manual override.

Save calibration per Device Profile.

---

# 53. DEVICE PROFILES

Support:

- VuraVision 65
- VuraVision 75
- VuraVision 86
- VuraVision 98
- VuraVision 110
- VuraVision 115
- Generic Android

Do not assume diagonal size by screen resolution alone.

Allow technician to choose profile.

Store calibration and performance preferences.

---

# 54. HARDWARE DIAGNOSTICS

Engineering Mode should display what Android makes available, such as:

- manufacturer
- model
- Android version
- API level
- resolution
- refresh rate
- density
- display metrics
- CPU ABI
- CPU info where legally/API-accessible
- RAM
- free memory
- storage
- GPU renderer/vendor where obtainable
- OpenGL/Vulkan capabilities
- touch devices
- maximum pointer observations
- network information needed for diagnostics

Avoid requiring root.

---

# 55. PERFORMANCE DEBUGGING

Include a debug/performance area showing:

- FPS
- frame duration
- dropped/janky frames if obtainable
- canvas object count
- stroke count
- active pointers
- memory use
- autosave state
- PDF cache state
- ML model state
- last errors
- current document size estimate

Provide toggleable developer overlays.

These overlays must be disabled for normal customers.

---

# 56. INTERNAL LOG VIEWER

Engineering Mode should include a safe internal application log viewer.

Use structured application logging.

Categories:

- Canvas
- Input
- PDF
- Recognition
- Math
- Save
- QR
- Lab
- Games
- Networking

Allow:

- filter
- clear
- export diagnostic report

Never log:

- passwords
- signing secrets
- private tokens beyond temporary redacted IDs

---

# 57. DIAGNOSTIC REPORT

Allow export of a diagnostic ZIP/text report containing:

- application version
- device profile
- hardware info
- calibration values
- performance snapshot
- recent sanitized logs
- installed recognition models
- feature status

Useful for VuraVision technical support.

---

# 58. CENTRALIZED ENGINEERING FEATURE FLAGS

Maintain a centralized feature-flag/settings model for technician-only options. Include LAN QR sharing enabled=false by default, diagnostic overlays off, verbose logs off, alternative renderer/classifier experiments and touch heatmap if implemented.

A disabled capability must be absent from customer navigation and remain guarded at its action entry point. Do not merely hide a button while leaving an ordinary menu route active. Persist intentional technician settings safely. Export non-secret flag state in diagnostics.

Do not include Screen Curtain as a feature flag: it was removed, not temporarily disabled.

---

# 59. ANNOTATION OVERLAY

Research Android's current supported approaches for drawing over other applications.

Desired feature:

Teacher opens:

- browser
- another PDF app
- video
- Android home/application

Then activates floating VuraVision Pen overlay.

Can draw annotations over screen.

Then:

Capture to Whiteboard

Screenshot becomes an ImageObject in VuraVision.

Use officially supported Android mechanisms.

If this requires special permissions such as overlay permission or MediaProjection:

- request permission clearly
- explain why
- degrade gracefully if unavailable

Never require root.

---

# 60. RTL / LTR / LOCALIZATION

Full languages:

- Persian
- English

Persian:

RTL

English:

LTR

Do not hardcode user-facing strings in Kotlin.

Use proper Android resources/localization architecture.

Make sure mathematical formulas remain directionally correct.

Test mixed Persian + Latin + math content.

---

# 61. VURAVISION DESIGN SYSTEM AND VISUAL POLISH

Create a deliberate internal design system using researched native components/libraries. Define brand color roles, typography, spacing, corner radii, borders, elevation, motion, touch targets, toolbar states, sheets/panels/dialogs, cards, sliders, chips, fields and error/loading/empty states.

Use the official supplied VuraVision logo consistently. The brand can use deep navy/purple, warm orange accents and restrained complementary teal; verify against supplied assets rather than inventing a conflicting brand. The result should feel like a professional educational/creative application, not bare Android dialogs or a dry collection of text buttons.

Pen Studio, Shapes, selection actions, Text, Notes, Layers, Files, Smart, Lab, Games and hidden Engineering all use the same components. Label color/thickness/style sections explicitly; add meaningful previews; avoid cramped choices and unlabelled numeric sliders.

Prioritize common classroom tasks: direct Pen/Shapes/Text/Select/Eraser/Smart/Layers, visible page controls, contextual object actions, persistent floating classroom tools. Use progressive disclosure for rare settings. Insert contains meaningful remaining objects such as sticky note, graph, image, PDF and math/table tools, without duplicate Text/Shapes.

Avoid excessive gradients, random neon, tiny desktop handles, mismatched icon families and decoration that harms touch performance. Verify landscape 4K displays, tablets and small screens, including Persian RTL and enlarged fonts.

---

# 62. LICENSED CONSISTENT ICONOGRAPHY

Use one coherent open-source icon family throughout application controls—Feather is the user's explicit example. If its coverage is insufficient, research a similarly coherent family or a carefully documented compatible extension; maintain consistent stroke weight, size and alignment.

Do not hand-invent toolbar glyphs, mix random Unicode/emoji with professional controls, or redraw copied proprietary assets. Convert licensed source SVGs to Android vectors when appropriate and retain attribution/license files and source/version metadata.

Emoji or custom game art may be part of an intentional game mechanic, not a substitute for the application icon system. Pair unfamiliar icons with localized labels/content descriptions and adequate hit targets.

---

# 63. RESPONSIVE LARGE-SCREEN DESIGN

Do not simply multiply all dp values based on resolution.

Use:

- WindowSizeClass-like concepts where appropriate
- physical interaction considerations
- logical density
- aspect ratio
- minimum hit areas
- large-display layout rules

4K does NOT mean buttons should become microscopic because density is unusual.

Engineering Mode should help diagnose unusual DPI configurations.

---

# 64. PERFORMANCE ARCHITECTURE AND ERASER REGRESSION

Keep the live input/rendering path independent from expensive UI updates, serialization, PDF rendering and recognition. Avoid whole-board recomposition, repeated stroke-path construction and per-sample allocations. Use appropriate retained paths, dirty regions/tiles, viewport culling, bounds/spatial indexing and bounded caches.

Layer compositing, partial erase and undo must not destroy the low-latency path. Cache erasure masks by immutable revision or another reliable invalidation key. Invalidate correctly after Undo/Redo, scaling/rotation, page switches, PDF pagination, layer opacity/order changes and media readiness. Do not show stale cached content to improve a performance number.

A stress test includes 1/2/5/10 contacts, many long strokes, imported PDFs/images, layered content, and repeated area erasures. Measure fresh-page drawing, active erasing, drawing after erasing, and document reopen. Record p50/p95/p99 frame times where possible, missed frames, memory and cache rebuild counts. Separate host simulation results from physical panel latency. Do not claim a 10-pointer unit test measured hardware responsiveness.

---

# 65. THREADING

Keep:

- rendering responsive
- file IO off main thread
- PDF rendering off main thread where possible
- ML inference off main thread
- serialization off main thread

Use Kotlin Coroutines/Flow appropriately.

Do not wrap everything in unnecessary abstractions.

---

# 66. ARCHITECTURE

Prefer a maintainable modular architecture.

A possible structure is:

```
app/

core/
  common/
  designsystem/
  input/
  graphics/
  document/
  persistence/
  pdf/
  recognition/
  math/
  sharing/
  diagnostics/

feature/
  whiteboard/
  lab/
  games/
  files/
  settings/
  engineering/
```

This is guidance, not an absolute requirement.

Choose the architecture after research.

Use:

- Kotlin
- modern Android APIs
- Jetpack Compose for normal UI unless justified otherwise
- Coroutines / Flow
- Room/DataStore where appropriate
- dependency injection only if it adds real value

Avoid architecture astronautics.

---

# 67. DEPENDENCY INJECTION

Research current Android best practices.

Hilt/Koin/manual DI are possible.

Choose based on project complexity.

Do not add a massive framework for no reason.

Document the decision.

---

# 68. OFFLINE-FIRST

Core Whiteboard must work with zero internet.

Core Lab must work with zero internet.

Core Games must work with zero internet.

PDF handling must work offline.

Saving/exporting must work offline.

QR LAN sharing must work without internet.

Handwriting/math:

prefer offline or downloaded-once models.

Only truly unavoidable AI/cloud capabilities may require internet.

Any online-only feature must visibly explain:

"Internet connection required"

and must not crash the app when offline.

---

# 69. PRIVACY

Avoid analytics/telemetry by default.

Do not upload classroom content without explicit user action.

On-device recognition is strongly preferred.

Do not send handwriting/PDF content to external servers silently.

---

# 70. NO FAKE FEATURES

Absolutely prohibited in the final V1:

- buttons that do nothing
- screens with fake data presented as real
- TODO handlers
- "Coming Soon" replacing required V1 features
- fake handwriting recognition
- hardcoded math answers
- fake PDF manipulation
- fake QR
- fake simulations

If a requested V1 feature cannot be implemented using the selected approach:

research an alternative and implement a working fallback.

Document unavoidable limitations honestly.

---

# 71. REFERENCE FILES AND INVENTORY MAPPING

Read supplied HTML lab/game files, the source ZIP, official logo and relevant screenshots. Extract every reference activity's rules, controls, ranges, formulas, feedback and orientation behavior.

Create `docs/REFERENCE_MAPPING.md` with one row per reference activity: reference ID, final native activity, retained mechanic, deliberate improvements, tests and status. Include additional existing educational activities. If files are unavailable, use Appendices A–C as the required topic/rule baseline and mark attachment-specific verification pending.

Do not copy old HTML visual design or expose it as a second application. Do not preserve scientific mistakes just to match a prototype. Document corrected equations and assumptions. Do not omit difficult entries silently or replace them with renamed generic content.

---

# 72. TESTING

Create meaningful tests.

At least:

## Unit tests
- document serialization
- undo/redo
- object transforms
- math parsing
- game scoring
- calibration classifier
- model manager logic
- QR session/token logic

## Android/instrumentation tests where appropriate
- document open/save
- localization
- core navigation
- PDF import
- selected critical interactions

Do not create hundreds of useless boilerplate tests.

---

# 73. PERFORMANCE TEST SCREEN

Engineering Mode should have a stress test capable of generating:

- many strokes
- many objects
- large PDF
- many simultaneous animated objects

Use it to evaluate renderer behavior.

---

# 74. CRASH HANDLING

Do not swallow exceptions silently.

Provide controlled error states.

A PDF failing to load must not kill the whole application.

A recognition model failing to initialize must not destroy Whiteboard.

---

# 75. ACCESSIBILITY

Even though this is a specialized interactive display:

- provide semantic labels
- sufficient contrast
- scalable text
- sensible touch targets

Do not break TalkBack unnecessarily.

---

# 76. BUILD SYSTEM

The GitHub repository MUST be independently buildable.

Include:

- `gradlew`
- `gradlew.bat`
- Gradle Wrapper files
- `settings.gradle.kts`
- root Gradle files
- module Gradle files
- version catalog if used
- complete AndroidManifest
- required resources
- ProGuard/R8 rules where necessary

Do not omit Gradle wrapper binaries/configuration.

---

# 77. VERSION COMPATIBILITY

Before implementation, research and choose mutually compatible current stable versions of:

- Gradle
- Android Gradle Plugin
- Kotlin
- Compose
- Compose Compiler/Kotlin integration
- JDK
- Android SDK
- dependencies

Do not combine arbitrary latest versions.

Create:

`docs/BUILD_VERSIONS.md`

explaining selected versions.

Prefer JDK 17 unless current Android tooling has a different well-supported requirement.

---

# 78. GITHUB ACTIONS — REQUIRED

Create:

`.github/workflows/android.yml`

The workflow must support:

```
push:
  branches:
    - main
    - master

pull_request:
  branches:
    - main
    - master

workflow_dispatch:
```

GitHub Actions must:

1. checkout repository
2. configure correct JDK
3. configure Android SDK
4. cache/setup Gradle correctly
5. ensure `gradlew` is executable
6. run appropriate tests
7. run lint or suitable static checks
8. build debug APK
9. upload debug APK artifact

Example artifact naming:

`VuraVision-debug.apk`

Do not assume the `gradle` command exists globally.

Prefer:

`./gradlew`

with the repository Gradle Wrapper.

---

# 79. SIGNED RELEASE APK

Also support production signing using GitHub Secrets.

Use secrets such as:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Do NOT put actual secrets into the repository.

Workflow should safely decode the Base64 keystore into a temporary file.

Release signing config should read values from environment variables.

When signing secrets are correctly configured, build:

`VuraVision-release.apk`

Upload it as a GitHub Actions artifact.

Never print passwords in logs.

Never commit the keystore.

---

# 80. APK SIGNATURE VERIFICATION

After release build, verify the APK signature.

Do NOT assume `apksigner` is globally available.

Locate the installed Android Build Tools version properly, for example through `$ANDROID_HOME/build-tools/...`.

Run signature verification similar to:

`apksigner verify --verbose --print-certs <apk>`

Fail the release workflow if signature verification fails.

This requirement exists because previous Android workflows commonly fail by simply calling `apksigner` without adding the Build Tools binary to PATH.

---

# 81. BUILD ARTIFACTS

Upload:

- debug APK
- signed release APK when signing is configured
- relevant test reports on failure if practical

Give artifacts human-readable names.

---

# 82. OPTIONAL GITHUB RELEASE WORKFLOW

If appropriate, additionally create a tag/release workflow.

For a tag like:

`v1.0.0`

it may:

- build signed APK
- verify signing
- attach APK to GitHub Release

Do not make this necessary for ordinary CI.

---

# 83. README

Create a professional `README.md`.

Include:

- what VuraVision is
- screenshots section placeholder only if screenshots cannot yet be generated
- project architecture
- features
- build instructions
- GitHub Actions
- release signing setup
- required GitHub Secrets
- supported Android versions
- offline models
- model download system
- experimental QR sharing and hidden default-off policy
- Engineering Mode activation
- known limitations

Do not include secrets.

---

# 84. RELEASE SIGNING DOCUMENTATION

Create:

`docs/SIGNING.md`

Explain exactly how to:

1. create/use a keystore
2. Base64 encode it
3. add GitHub Secrets
4. trigger workflow
5. obtain signed artifact
6. verify APK signature

Include Windows, Linux/macOS Base64 examples if practical.

---

# 85. DEVELOPMENT VERSUS RELEASE

Debug builds may expose a clearly developer-only shortcut. Ordinary release builds must keep Engineering behind the three-tap Quick Guide flow. QR remains off by default. Screen Curtain is absent.

Use actual build types, version codes and signing identities. The historical installed app may use `com.vuravision.classroom.beta`; for an upgrade preserve application ID and signing certificate continuity when available. A new production ID requires an explicit migration plan and clear user-facing distinction. Do not change ID or certificate and then imply it will update an existing installation without data migration.

For a from-scratch product use `com.vuravision.classroom` unless compatibility requirements dictate otherwise. Never bundle a previous version's APK as evidence of a newly edited source build.

---

# 86. APPLICATION ID / BRANDING

Use a sensible package/application ID such as:

`com.vuravision.classroom`

unless repository constraints require something else.

App display name:

`VuraVision`

Internal product name may be:

`VuraVision Classroom Suite`

Brand all screens consistently.

---

# 87. APPLICATION ICON

Create a clean adaptive Android icon compatible with VuraVision branding.

Do not use copyrighted third-party assets.

If exact official logo assets are not provided, create a simple brand-safe vector placeholder/wordmark that can easily be replaced.

---

# 88. FIRST-LAUNCH BEHAVIOR

Do not show a long onboarding flow before Whiteboard.

Open Whiteboard.

Small unobtrusive guidance can appear the first time.

If recognition models are not installed:

Whiteboard still works.

When Smart Recognition is first used:

explain required model/download clearly.

---

# 89. ORDINARY SETTINGS VERSUS HIDDEN SETTINGS

Ordinary Settings: language, theme, autosave, default background, model status/management, supported recognition-engine choice, storage/cache, about/license notices and accessible help. Keep useful single-touch/multitouch switching readily available.

Pen Studio: fine/broad tip appearance, style, color, thickness, supported opacity and live preview.

Hidden Engineering: calibration enablement/thresholds, raw contact metrics, palm/finger classification settings, device profiles, performance tuning, logs, diagnostics and QR enablement. QR sharing settings are absent from ordinary UI while disabled. No duplicate public calibration route.

General preferences and technician profiles survive app restarts/upgrades. Saving defaults must not alter existing strokes/documents unexpectedly.

---

# 90. PERFORMANCE PROFILES

If useful create:

- Balanced
- Performance
- Quality

But avoid confusing ordinary users.

Automatic/default profile should work well.

Engineering Mode may expose deeper tuning.

---

# 91. CURRENT IMPLEMENTATION PRIORITIES AND COMPLETION SCOPE

Implement in dependency order while retaining the complete required scope:

1. Buildable foundation, durable checkpoint mechanism, design tokens and core object model including layers.
2. Extremely smooth multitouch Whiteboard, independent tip styles, robust erasing/selection/pages and Undo.
3. Real offline-aware Smart text/math/shapes, graphing and truthful model management.
4. PDF objects with button paging, media, save/open/migration/export and floating tools.
5. Unified native Lab covering all 68 reference topics plus distinct useful core activities.
6. Unified native Games covering all 49 reference games plus distinct existing educational challenges.
7. Hidden engineering diagnostics, default-disabled QR, supported cross-app annotation and the remaining original object/tool requirements.
8. Full visual/localization/accessibility review, meaningful tests, clean build, signature verification and final packaging.

Do not call the product complete because a subset works. Conversely, do not sacrifice scientific correctness or working mechanics just to report counts. Track incomplete items honestly in the requirement matrix while continuing implementation.

---

# 92. FUTURE EXTENSIBILITY WITHOUT DEFERRING CURRENT REQUIREMENTS

Leave clear extension points for additional labs/games beyond the current catalog, better recognition engines, advanced AI, cloud accounts/sync, student-phone participation, polling, collaborative lessons, teacher-generated questions, 4-player/team modes, casting and richer geometry solvers.

These future capabilities are not permission to defer the current 68 lab topics, 49 reference games, additional educational challenges, layers or the original required board/recognition/PDF/tool capabilities. Cloud accounts, a paid AI subscription and a permanent backend must not be prerequisites for core classroom use.

---

# 93. DEVELOPMENT PROCESS YOU MUST FOLLOW

Perform the work in this order.

## Phase A — Inspect
Read all supplied files.

## Phase B — Research
Research all major libraries and technology decisions.

Create:

- `DEPENDENCY_RESEARCH.md`
- `COMPETITIVE_RESEARCH.md`
- `BUILD_VERSIONS.md`

## Phase C — Architecture
Define:

- modules
- object model
- renderer
- input system
- persistence
- ML interfaces
- math interfaces

## Phase D — Skeleton
Create a buildable repository before implementing all features.

Immediately verify:

`./gradlew assembleDebug`

Fix all problems.

## Phase E — Core Whiteboard
Implement foundational document/canvas/object/input systems.

## Phase F — Smart Tools
Implement recognition/math/graphing.

## Phase G — PDF / Files / Sharing

## Phase H — Lab

## Phase I — Games

## Phase J — Engineering Mode

## Phase K — Polish
UI consistency, localization, loading/error/empty states.

## Phase L — Tests

## Phase M — GitHub Actions

## Phase N — Final Build Verification

---

# 94. CONTINUOUS VALIDATION AND DURABLE CHECKPOINTS

Create a buildable skeleton first. Run relevant builds/tests during development and fix failures before piling unrelated changes on top.

At every meaningful completed milestone and before risky environment/build operations:

1. Save the complete current source to durable user-accessible storage or the authorized Git repository.
2. Record revision/hash, feature changes, tests actually run, failures and next steps.
3. Include hidden repository files such as `.github` and the Gradle Wrapper.
4. Verify the saved archive can be opened and contains actual source; a progress message is not a checkpoint.
5. Save even if the build environment is blocked; label that snapshot unbuilt/unverified.

Maintain `docs/PROGRESS.md`, a requirement-status matrix and reproducible build/test instructions. Temporary scratch directories alone are insufficient. Never wait until final APK packaging to save hours of work.

If the environment lacks network access, SDK/compiler/dependencies or signing keys, report the exact limitation, preserve the source and prepare runnable CI. Do not claim compilation/tests/signing that did not run. Do not replace current evidence with historical reports. Resume from the latest saved checkpoint after resets, and identify any unrecoverable code honestly.

---

# 95. IF A LIBRARY FAILS

If a selected library:

- does not compile
- is abandoned
- conflicts with Android 14
- causes unacceptable crashes
- has licensing problems
- has unacceptable performance

DO NOT spend the entire project forcing it to work.

Return to dependency research and choose the next candidate.

Record major change in research documentation.

---

# 96. DO NOT BLINDLY COPY OPEN-SOURCE APPS

Open-source apps can be inspected for architecture and ideas.

But:

- comply with licenses
- do not copy GPL code into a commercial closed-source application unless intentionally accepting the licensing consequences
- do not copy copyrighted graphics
- do not remove attributions
- do not pretend copied code is original

When useful, prefer permissive reusable libraries over copying entire app source.

---

# 97. UI QUALITY GATE

Before considering V1 finished, inspect every screen for:

- alignment
- spacing
- typography
- touch target size
- visual hierarchy
- RTL correctness
- English correctness
- empty states
- loading states
- error states
- consistency

There should not be one beautiful Whiteboard screen and ugly default Android screens elsewhere.

Lab, Games, Settings, Share and Engineering Mode should all belong to the same product.

---

# 98. NATIVE PRODUCT, NOT TWO LEGACY WEB APPS

Native Android is the primary architecture. Use the new shared VuraVision design system and native activity contracts for Whiteboard, Lab and Games.

Do not solve the full-catalog requirement by wrapping the supplied Lab/Arcade HTML in a WebView, adding “Core / Discovery / Arcade” launch choices or maintaining two visual languages. The user explicitly rejected that architecture and appearance.

A narrowly scoped specialized web/WASM component is permissible only after documented research demonstrates a real technical advantage, with coherent UI, offline operation, lifecycle control and explicit tradeoffs. It does not authorize shipping the old websites as the requested rebuilt modules.

---

# 99. ACCEPTANCE TESTS

V1 is only acceptable when these scenarios work:

### Scenario 1
Launch app → immediately see Whiteboard → draw smoothly.

### Scenario 2
Two simultaneous fingers can interact without crashing.

### Scenario 3
Pen/finger/palm metrics can be inspected in Engineering Mode.

### Scenario 4
Draw ink → select → move → resize/delete → undo.

### Scenario 5
Import image → move/resize/crop.

### Scenario 6
Import PDF → select → use visible previous/next buttons → drag vertically/horizontally without changing page → resize/move; verify per-page masks stay on their page.

### Scenario 7
Take a rectangular snapshot from PDF → snapshot becomes independent board object.

### Scenario 8
Write English handwriting → convert to text.

### Scenario 9
Use Persian handwriting recognition when installed/supported by selected local model.

### Scenario 10
Write math → recognize equation → render it properly.

### Scenario 11
Recognize solvable equation → solve it.

### Scenario 12
Write/type function → plot → graph becomes board object.

### Scenario 13
Save `.vura` → close application → reopen document with editable objects preserved.

### Scenario 14
Export lesson to PDF.

### Scenario 15
Fresh install → no QR controls are visible. Hidden Engineering → explicitly enable QR → generate session → separate phone on the same LAN downloads the PDF. Disable again → controls disappear and active sharing is stopped.

### Scenario 16
Open Lab → interact with simulation → send snapshot to Whiteboard.

### Scenario 17
Open a two-player game → both players can interact simultaneously.

### Scenario 18
Switch Top/Bottom ↔ Left/Right.

### Scenario 19
Change language Persian ↔ English and UI direction changes correctly.

### Scenario 20
GitHub Actions builds the exact delivered source, runs tests/lint and uploads the correctly versioned APK with accurate signing status.

---

# 100. FINAL DELIVERY REQUIREMENTS

At the end of the task I want a REAL repository.

Do not give me only snippets.

Do not give me pseudocode.

Do not say:

"Here is an example of how you could implement..."

Implement it.

The repository must contain all necessary source files.

Before final delivery:

1. run tests
2. run debug build
3. fix compilation errors
4. inspect dependency licenses
5. verify Gradle Wrapper
6. verify GitHub Actions YAML
7. verify no secrets are committed
8. verify AndroidManifest permissions
9. verify Persian resources
10. verify release signing configuration
11. verify diagnostics does not leak secrets

Then provide a concise final report including:

- architecture used
- libraries selected
- important rejected libraries and why
- offline recognition approach
- math recognition approach
- PDF engine
- drawing engine
- 3D engine
- QR sharing implementation
- games implemented
- lab simulations implemented
- Engineering Mode features
- build command
- GitHub workflow path
- GitHub Secrets required
- known limitations
- next recommended V2 tasks

---

# 101. FULL DOCUMENT LAYERS

Add Photoshop-like layers to each board page, accessible directly from a Layers control. Keep the panel touch-friendly, with clear active-layer and top-to-bottom visual stacking.

Required operations:

- Add, name/rename, select active layer, duplicate where supported, and delete with appropriate confirmation.
- Show/hide, lock/unlock, change opacity 0–100%, raise/lower and reorder.
- Move selected objects to another editable layer.
- Keep object-local ordering separate from layer ordering.
- Make the destination for newly drawn/imported/generated content unambiguous.

New pages start with one visible, unlocked, fully opaque layer. Prevent accidental drawing into locked/hidden layers and show an actionable hint. Define what happens if the active layer is deleted/hidden/locked. Do not silently redirect content to a different layer without explaining it.

Layer locks prevent drawing, erasing, transforms, deletion and smart replacement through every route, not just the panel. Hidden layers cannot be hit-tested or recognized. Preserve object-level locks as a separate concept.

Compositing must be correct: use layer-group opacity so overlapping strokes on a 50%-opaque layer do not unexpectedly darken merely from per-object opacity. Keep intrinsic highlighter alpha and layer alpha as separate values. Area erasure must reveal the correct underlying content; state clearly whether it affects current layer or all eligible layers.

All layer operations integrate with Undo/Redo, autosave, crash recovery, page duplication, `.vura` save/open, thumbnails and exports. Migrate old documents to a base layer. Test independent duplicated-page layers and consistent hit/render/export ordering.

---

# 102. PAGE NAVIGATION AND EVERYDAY UX

Provide an obvious compact page strip/overview with current index, previous/next, add and thumbnails. Make duplicate/delete/reorder discoverable, with protection against accidental destructive gestures. Restore the selected page and relevant view state on reopening.

Keep direct Shapes and Text actions on the toolbar, without duplicates under Insert. Re-tapping active Pen/Eraser opens their own settings. Selection actions appear near selected content. Classroom floating tools remain visible while writing.

Use meaningful localized titles, examples and previews in Lab/Games, not only internal IDs. Avoid forcing teachers through several menus for common classroom tasks. Check every control on the actual large display and at small-screen sizes; do not infer physical readability from a desktop screenshot alone.

---

# 103. REGRESSION ACCEPTANCE MATRIX

In addition to the original acceptance scenarios, implement meaningful tests and documented device checks for these cases:

| Area | Required evidence |
|---|---|
| Input | 1/2/5/10 simultaneous pointer IDs keep independent strokes; higher reported counts do not hit an artificial limit |
| Latency | Measured target-panel drawing and erasing; no persistent slowdown after repeated erasures |
| Dual tip | Fresh default threshold 5 contact px; boundary cases, unknown metrics and preserved saved calibration |
| Appearance | Independent fine/broad normal/dashed/marker/highlighter, color and width survive restart |
| Hidden settings | Pen Studio exposes no calibration; triple-tap Quick Guide opens it; ordinary Settings stays clean |
| Smart selection | Failed/open/empty lasso commits no ink; lasso is excluded from recognition; source content preserved on cancel |
| Smart shape | Repeated shapes convert on release without repeated mode activation |
| Recognition | Real samples, installed/offline behavior, HTTP 404/corrupt/missing model paths, honest supported-language status |
| Math | `2*(3+4)=14`, `2x+3=11 → x=4`, typed/recognized function plotting and invalid-input handling |
| Shapes | All gallery previews; every line-drag quadrant; uniform geometry; cylinder cap behavior under height changes |
| Area eraser | Partial stroke/object erase, non-white backgrounds, lower layers, masks after transforms and Undo/Redo |
| Text/notes | Tight bounds, mixed RTL/LTR, bold/alignment/colors, independent note background color |
| PDF | Buttons paginate; both-axis drag moves; first/last limits; independent PDFs and page-local erasures |
| Layers | Visibility, group opacity, locks, reorder, active layer, moving selections, migration, clone independence, save/export/Undo |
| Floating tools | Stay visible while drawing and switching pages; lifecycle timing correctness |
| Removed features | Screen Curtain absent from navigation and user-facing resources/routes |
| QR | Hidden/off by default; technician enable/disable; expiry/stop; actual second-device transfer when enabled |
| Labs | Every activity opens, interacts, pauses/resets and exports; control extremes; reference mapping and numerical tests |
| Games | Every game starts/completes/restarts; actual intended mechanics, fairness, multipointer and timer isolation |
| Localization | Persian/English layouts, strings, large text, accessible labels and legible mixed-direction formulas |
| Build | Clean build, tests/lint, resource/manifest checks, valid signature, alignment and source/APK version match |

Visual render/screenshot tests support these checks but do not replace interaction tests, physical latency measurements or scientific validation. A successful “open all cards” test does not prove the activities are correct or playable.

---

# 104. SCIENTIFIC AND GAME QUALITY GATES

For each lab: document equations, assumptions, units, parameter ranges and expected limiting behavior. Use an independent numerical/reference check for critical formulas. Avoid silent division by zero, NaN/Infinity drawing coordinates, unstable integration, energy-sign mistakes and chart scales that misrepresent geometry. Periodic Table means a real data-backed table, not just a slider through the first few elements. A 2D schematic molecule must not be presented as a freely rotatable 3D model.

For each game: document actual rules, player agency, start/end conditions, scoring, penalties and ties. Preserve meaningful differences: Quick Draw includes decoy cues; Mental Sum reveals numbers sequentially; Simon requires sequence recall; Pong has real paddle/ball collision; Connect Four has gravity and four-direction win checks; memory pairs actually conceal and reveal pairs; precision games compare precision rather than only first tap.

Graphical improvements may use licensed sprites, particles, subtle animation and sound, but cannot obscure answers, inflate rendering work or replace core mechanics. Use measured difficulty progression and clear reset/lifecycle behavior.

---

# 105. DELIVERY, SIGNING AND HONEST STATUS

Deliver a versioned ZIP with complete source, Gradle Wrapper, `.github/workflows`, dependency notices, original/reference mapping, updated specification, Persian user guide, build/signing instructions and verification reports from the delivered source revision.

Include a newly generated APK only after actually building it. Verify application ID, versionCode/versionName, signing certificate and ZIP/native alignment. Distinguish a development-signed test APK from a production-signed release. Development signing must not be described as production readiness. Keep signing-key continuity for updates and never commit private keys/passwords.

If only source can be produced, name the archive clearly as a source checkpoint and state which build/tests remain unrun. Do not put a stale APK in the package or carry forward an old report headed “all tests pass.” Mark per-feature status as implemented/tested/device-verified/blocked with evidence, not a single blanket success claim.

Provide a concise final summary and direct download links. Include the exact repository-root upload instructions: upload extracted project contents, including `.github`, not the ZIP file itself or a redundant outer folder.

---

# FINAL MINDSET

Treat VuraVision as a serious commercial interactive-display product, not a coding exercise.

The central philosophy is:

**Whiteboard first.**
**Touch first.**
**Offline first.**
**Object based.**
**Research before reinventing.**
**Open-source where practical.**
**Commercial-license safe.**
**Beautiful throughout.**
**Buildable at every stage.**
**No fake features.**

When an excellent open-source solution exists, use or adapt it.

When several exist, research and select the best one instead of choosing the first Google/GitHub result.

When nothing good exists, implement the subsystem yourself cleanly and document why.

Do not sacrifice product quality simply to avoid writing custom code, and do not write custom code merely to avoid researching existing solutions.

Build a VuraVision application that can realistically ship with a 65–115 inch interactive display.

---

# APPENDIX A — COMPLETE 68-ACTIVITY LAB INVENTORY

These are the reference activities, not 68 interchangeable animations. Subject groups are navigation categories, not separate apps. Controls may be improved, but retain the central phenomenon and learning interaction.

| # | Reference ID | Subject | English title | Persian title | Required interaction / correctness focus |
|---|---|---|---|---|---|
| 1 | `orbit` | physics | Gravity and orbital motion | گرانش و مدار | Gravity/time controls, multiple bodies/trails, meaningful orbit dynamics and reset. |
| 2 | `pendulum` | physics | Pendulum | آونگ | Length, gravity, damping/kick; visible oscillation and measured/theoretical period with small-angle assumptions. |
| 3 | `projectile` | physics | Projectile motion | پرتابه | Speed, launch angle, gravity; launch/clear; true aspect ratio, range and maximum height. |
| 4 | `spring` | physics | Spring and oscillation | فنر و نوسان | Mass, stiffness, damping, pull/release; period/frequency and time-dependent motion. |
| 5 | `collision` | physics | Collision and momentum | برخورد و تکانه | Masses and initial speed; elastic/inelastic modes; momentum and kinetic-energy accounting. |
| 6 | `standing` | physics | Standing waves | موج ایستاده | Harmonic, amplitude and speed; visible nodes/antinodes and frequency relationship. |
| 7 | `gas` | physics | Ideal gas | گاز ایده‌آل | Temperature, volume and particle number; pressure/readout consistent with the chosen gas equation and units. |
| 8 | `molecule` | chem | 3D molecules | مولکول‌های سه‌بعدی | Selectable molecules, bond geometry and genuine 3D rotation if labelled 3D; do not substitute a static 2D diagram. |
| 9 | `states` | chem | States of matter | حالت‌های ماده | Temperature-dependent solid/liquid/gas behavior with explicit substance/pressure assumptions. |
| 10 | `ph` | chem | pH scale | مقیاس pH | pH and hydrogen-ion concentration, acid/neutral/base labels and clear example substances. |
| 11 | `reaction` | chem | Reaction kinetics | سینتیک واکنش | Temperature/activation-energy controls, reaction progress, reactant/product populations and reset. |
| 12 | `atom` | chem | Atomic structure | ساختار اتم | Element/atomic-number selection and labelled nucleus/electrons; explain shell-model simplification. |
| 13 | `solubility` | chem | Solubility and concentration | انحلال و غلظت | Solute/solvent controls, saturation, dissolved/undissolved amount and concentration units. |
| 14 | `halflife` | chem | Radioactive half-life | نیم‌عمر رادیواکتیو | Half-life and initial amount; decay over time, expected curve versus stochastic realization. |
| 15 | `waves` | math | Wave superposition | برهم‌نهی امواج | Individual and summed waves with amplitude/frequency/phase controls. |
| 16 | `circle` | math | Unit circle | دایرهٔ واحد | Angle, unit-circle point, sine/cosine projections and angle units. |
| 17 | `fractal` | math | Fractal tree | درخت فراکتال | Depth/branch angle, bounded recursive geometry and responsive redraw. |
| 18 | `golden` | math | Golden spiral | مارپیچ طلایی | Golden-ratio spiral growth, scale/turn controls and explanatory construction. |
| 19 | `quadratic` | math | Quadratic functions and roots | سهمی و ریشه‌ها | a/b/c, roots/discriminant/vertex and linear/degenerate cases. |
| 20 | `tangent` | math | Derivative and tangent | مشتق و خط مماس | Function and selected x; derivative/tangent/readout with valid domain handling. |
| 21 | `fourier` | math | Fourier series | سری فوریه | Harmonic count, component waves and resulting approximation; explain overshoot. |
| 22 | `galton` | stats | Galton board | تختهٔ گالتون | Rows/balls, actual bin counts, probability distribution and reset. |
| 23 | `coins` | stats | Law of large numbers | قانون اعداد بزرگ | Trial count/bias, running frequency and convergence to expected probability. |
| 24 | `dice` | stats | Sum of two dice | جمع دو تاس | Independent die outcomes and empirical sum histogram, not a flat fabricated distribution. |
| 25 | `montecarlo` | stats | Monte Carlo estimate of pi | تخمین عدد پی | Random points, inside/outside count, evolving estimate of pi and sample-size effects. |
| 26 | `walk` | stats | Random walk | قدم زدن تصادفی | Step count/bias, realized paths and displacement statistics. |
| 27 | `clt` | stats | Sampling distributions / CLT | میانگین نمونه‌ها | Sample size/repeats, distributions of sample means and standard-error behavior. |
| 28 | `regression` | stats | Correlation and regression | همبستگی و رگرسیون | Data/noise controls, scatterplot, fitted line and meaningful correlation/readouts. |
| 29 | `incline` | physics | Inclined plane | سطح شیب‌دار | Angle/friction, rest/sliding behavior, acceleration and consistent ramp geometry. |
| 30 | `lens` | physics | Converging lens | عدسی همگرا | Focal/object distances, rays, image location/sign, magnification and object-at-focus case. |
| 31 | `circuit` | physics | Ohm’s law | قانون اهم | Voltage/resistance, current/power and a consistent simple circuit visualization. |
| 32 | `doppler` | physics | Doppler effect | اثر دوپلر | Source motion/frequency, wavefronts and observer frequency with stated speed assumptions. |
| 33 | `buoyancy` | physics | Buoyancy | شناوری | Density/displaced volume or object density, forces and float/sink behavior. |
| 34 | `heat` | physics | Heat conduction | رسانش گرما | Temperature difference, transport parameter, evolving temperature profile and equilibrium. |
| 35 | `periodic` | chem | Periodic table | جدول تناوبی | Interactive data-backed periodic table with element name/symbol/number/mass/group/period/category/configuration. |
| 36 | `titration` | chem | Titration | تیتراسیون | Added titrant and concentrations, titration curve/endpoint and correct acid–base assumptions. |
| 37 | `electrolysis` | chem | Water electrolysis | برق‌کافت آب | Charge/current/time, 2:1 H₂/O₂ stoichiometry and correctly labelled gas quantities. |
| 38 | `diffusion` | chem | Gas diffusion | پخش گازها | Concentration spreading/temperature or diffusion-rate control; no unsupported microscopic claims. |
| 39 | `flame` | chem | Flame test | آزمون شعله | Selectable salts/elements and characteristic flame colors with explanatory labels. |
| 40 | `equilibrium` | chem | Chemical equilibrium | تعادل شیمیایی | Forward/reverse dynamics, concentration changes and equilibrium rather than a frozen animation. |
| 41 | `primes` | math | Sieve of primes | غربال اعداد اول | Sieve operation/limit, distinction between prime and composite; 1 is not prime. |
| 42 | `pythagoras` | math | Pythagorean theorem | قضیهٔ فیثاغورس | Right-triangle sides and squares; equal metric scale and c²=a²+b². |
| 43 | `polypi` | math | Polygon approximation of pi | پی با چندضلعی | Polygon side count, inscribed/circumscribed bounds and convergence to pi. |
| 44 | `lissajous` | math | Lissajous curves | منحنی لیساژو | x/y frequency ratios and phase; curve geometry remains undistorted. |
| 45 | `series` | math | Geometric series | سری هندسی | Ratio/term count, partial sums, convergence and divergent cases handled explicitly. |
| 46 | `pascal` | math | Khayyam–Pascal triangle | مثلث خیام–پاسکال | Rows, binomial structure and each entry as the sum above. |
| 47 | `monty` | stats | Monty Hall | بازی مونتی هال | Play/simulate stay versus switch; host knows prize and always reveals a goat. |
| 48 | `birthday` | stats | Birthday paradox | پارادوکس تولد | Group size and probability, simulation optional; state uniform-day assumptions. |
| 49 | `bayes` | stats | Bayes and diagnostic testing | آزمایش پزشکی و بیز | Prevalence/sensitivity/specificity, true/false positives and posterior probability; teaching simulation. |
| 50 | `benford` | stats | Benford’s law | قانون بنفورد | Leading-digit probabilities, suitable data examples and limits of applicability. |
| 51 | `median` | stats | Mean versus median | میانگین یا میانه؟ | Editable/outlier data, mean/median comparison and distribution display. |
| 52 | `streaks` | stats | Random streaks | رشته‌های شانس | Independent trials/bias, run lengths and explanation of chance clusters. |
| 53 | `freefall` | physics | Free fall and air resistance | سقوط آزاد و مقاومت هوا | Height and drag model, evolving position/velocity and comparison with no-drag case. |
| 54 | `interference` | physics | Two-wave interference | تداخل دو موج | Phase/path/frequency controls; constructive/destructive interference and component visibility. |
| 55 | `pressure` | physics | Hydrostatic pressure | فشار در عمق مایع | Depth/fluid density, gauge versus absolute pressure and units. |
| 56 | `lightclock` | physics | Light clock / time dilation | اتساع زمان (نسبیت) | v/c, gamma and proper versus observed time; subluminal range and clear frame labels. |
| 57 | `bonding` | chem | Chemical bonds | انواع پیوند شیمیایی | Ionic/covalent/metallic bonding with distinguishable, scientifically explained representations. |
| 58 | `catalyst` | chem | Catalysts and activation energy | کاتالیزور و انرژی فعال‌سازی | Energy diagram with/without catalyst, lower activation barrier and unchanged equilibrium thermodynamics. |
| 59 | `osmosis` | chem | Osmosis | اسمز | Solute concentration difference, semipermeable membrane, solvent flow direction and stated assumptions. |
| 60 | `density` | chem | Liquid density column | ستون چگالی مایعات | Immiscible layers, object density and meaningful settling/float location. |
| 61 | `collatz` | math | Collatz sequence | دنبالهٔ کولاتز | Starting integer, sequence/step visualization and unproved-conjecture disclaimer. |
| 62 | `modcircle` | math | Modular multiplication circle | جدول ضرب روی دایره | Multiplier/modulus controls and modular chord pattern. |
| 63 | `koch` | math | Koch snowflake | برف‌دانهٔ کخ | Iteration depth and valid Koch construction with bounded geometry growth. |
| 64 | `sorting` | math | Sorting visualization | مرتب‌سازی دیدنی | Real algorithm steps/comparisons/swaps; start/pause/reset and data-size controls. |
| 65 | `sampling` | stats | Sampling error in surveys | نظرسنجی و خطای نمونه‌گیری | Population proportion/sample size, sampling distributions and uncertainty, not one deterministic bar. |
| 66 | `ruin` | stats | Gambler’s ruin | ورشکستگی قمارباز | Starting capital, target and win probability; absorbing outcomes and repeated trials. |
| 67 | `simpson` | stats | Simpson’s paradox | پارادوکس سیمپسون | Combined versus stratified data/regression with an actual reversal, correctly explained. |
| 68 | `markov` | stats | Weather Markov chain | زنجیرهٔ مارکوف آب‌وهوا | Transition probabilities, time evolution and stationary distribution; rows must sum to one. |

# APPENDIX B — COMPLETE 49-GAME REFERENCE INVENTORY

Preserve each game’s mechanic. Rules below describe the reference baseline; any improvements must remain recognizable and be documented. Use shared UI and common match infrastructure, not a duplicate arcade destination.

| # | Reference ID | English title | Persian title | Core rule |
|---|---|---|---|---|
| 1 | `reaction` | Reflex Duel | دوئل واکنش | Wait for green; first tap wins. Tap early and the point goes to your rival! |
| 2 | `draw` | Quick Draw | هفت‌تیرکش | Fire only on “NOW!” — don't fall for the fake words! |
| 3 | `potato` | Hot Bomb | بمب داغ | Tap to pass the bomb; whoever holds it when it blows loses! |
| 4 | `timing` | Perfect Timing | ضربهٔ دقیق | Stop the moving marker dead-center in the green zone. |
| 5 | `stroop` | Color vs Word | رنگ و کلمه | Tap only when the word matches its ink color. |
| 6 | `tap` | Tap War | جنگ کلیک | 10 seconds; most taps wins! |
| 7 | `tug` | Tug of War | طناب‌کشی | Tap rapidly to drag the knot to your side. |
| 8 | `swipe` | Scrub Race | مسابقهٔ سابیدن | Scrub your finger fast to fill your bar first. |
| 9 | `numrace` | Number Hunt | دنبال اعداد | Find and tap 1–12 in order, fast. |
| 10 | `sort` | Sort Sprint | صعودی بزن | Tap 6 numbers from smallest to largest before your rival. |
| 11 | `pin` | PIN Race | کد رو بزن | Type the 4-digit code on your keypad first. |
| 12 | `tiles` | Tile Rush | خونه‌های روشن | Clear all lit tiles before your opponent. |
| 13 | `gridlight` | Follow the Light | دنبال نور | Tap the lit cell 8 times, faster than your rival. |
| 14 | `math` | Fast Math | ریاضی سریع | First correct answer scores; a wrong one locks you out! |
| 15 | `sum` | Mental Sum | جمع ذهنی | Three numbers flash by; tap their sum fast. |
| 16 | `chain` | Math Chain | زنجیرهٔ ریاضی | Start from a number and apply two quick operations. |
| 17 | `tfmath` | True or False | درست یا غلط | Judge the equation fast — mistakes help your rival! |
| 18 | `evenodd` | Even or Odd | زوج یا فرد | Quick! Is the number even or odd? |
| 19 | `operator` | Missing Operator | عملگر گمشده | Find the right operator (+, −, ×). |
| 20 | `compare` | Which Is Bigger | مقایسهٔ ضرب‌ها | Which product is bigger? Tap fast! |
| 21 | `digits` | Digit Memory | حافظهٔ عددی | Memorize a 5-digit number, then spot it among lookalikes. |
| 22 | `dots` | Dot Count | شمارش نقطه‌ها | Count the dots at a glance. |
| 23 | `sheep` | Count the Sheep | شمارش گوسفندها | Count only the sheep in the herd! |
| 24 | `clock` | Clock Reader | ساعت چنده؟ | Read the emoji clock fast. |
| 25 | `simon` | Color Simon | حافظهٔ رنگ‌ها | Memorize the color order and repeat it first. |
| 26 | `pattern` | Pattern Copy | کپی الگو | Memorize the 4-cell pattern and rebuild it. |
| 27 | `colormem` | Color Memory | حافظهٔ رنگ | See a color, then find it among similar shades. |
| 28 | `emojiseq` | Emoji Order | ترتیب اموجی‌ها | Memorize 4 emojis in order, then pick the right one. |
| 29 | `scramble` | Word Scramble | حروف به‌هم‌ریخته | Unscramble the word before your rival. |
| 30 | `odd` | Odd One Out | وصلهٔ ناجور | Spot the different one among 12. |
| 31 | `letter` | Letter Hunt | شکار حرف | Find the odd letter among lookalikes. |
| 32 | `shade` | Odd Shade | رنگ متفاوت | Tap the tile with a slightly different shade. |
| 33 | `arrow` | Arrow Match | جهت فلش | Tap the button matching the arrow, fast. |
| 34 | `rstroop` | Reverse Stroop | دکمهٔ رنگی | Tap the color of the word's MEANING, not its ink! |
| 35 | `bigger` | Bigger Number | عدد بزرگ‌تر | Tap the bigger number, fast. |
| 36 | `bigcircle` | Bigger Circle | دایرهٔ بزرگ‌تر | Two nearly equal circles; spot the bigger one. |
| 37 | `trap` | Stars & Bombs | ستاره و بمب | Collect stars, avoid the bombs! |
| 38 | `evens` | Even Hunter | شکار زوج‌ها | Hunt only the even numbers! |
| 39 | `mole` | Whack-a-Mole | شکار همستر | 15 seconds of whacking; most hits wins! |
| 40 | `hold` | Golden 5 Seconds | پنج ثانیهٔ طلایی | Hold and release at exactly 5 seconds; the timer hides! |
| 41 | `pong` | Touch Pong | پینگ‌پنگ لمسی | Drag your paddle; don't let the ball pass. |
| 42 | `ttt` | Tic-Tac-Toe | دوز | Line up three to take the round. |
| 43 | `c4` | Connect Four | چهار در یک ردیف | Drop discs and connect four first. |
| 44 | `rps` | Rock Paper Scissors | سنگ کاغذ قیچی | Pick in secret; reveal together. |
| 45 | `penalty` | Penalty Shootout | پنالتی | Striker and keeper pick in secret; roles swap each round! |
| 46 | `pairs` | Memory Pairs | جفت‌های حافظه | Flip cards and match all pairs first. |
| 47 | `hilo` | Higher or Lower | بالا یا پایین | Guess if the next card is higher or lower. |
| 48 | `dice` | Dice Duel | تاس شانس | Roll the dice; higher number wins! |
| 49 | `balloon` | Balloon Pump | بادکنک | Inflate big — but release before it pops! |

Memory/timing details matter: retain sequential presentation, hidden information, decoy cues, targets, drag/swipe interactions and specific win conditions where described. Shared-board games may use turn-taking; other activities must accept independent concurrent input. Top/bottom must orient each player’s content and hit-testing correctly; left/right must not rotate either player. Provide explicit layout choice in addition to Auto.

# APPENDIX C — ADDITIONAL EDUCATIONAL CHALLENGES TO PRESERVE

The previous native collection included 24 extra educational challenge categories. Keep these as actual playable activities or clearly identified modes within the single Games catalog; do not silently discard them when rebuilding the reference collection.

| ID | Activity |
|---|---|
| `subtract` | Subtraction |
| `multiply` | Multiplication |
| `divide` | Division |
| `missing_number` | Missing number |
| `squares` | Squares |
| `cubes` | Cubes |
| `powers` | Powers |
| `square_root` | Square roots |
| `fractions` | Fraction arithmetic |
| `percent` | Percentages |
| `decimal` | Decimal arithmetic |
| `sequence` | Number sequences |
| `reverse_sequence` | Reverse sequences |
| `prime` | Prime recognition |
| `divisible` | Divisibility |
| `remainder` | Remainders |
| `gcd` | Greatest common divisor |
| `lcm` | Least common multiple |
| `perimeter` | Perimeter |
| `area` | Area |
| `triangle_angle` | Triangle angles |
| `clock_math` | Clock/time arithmetic |
| `unit_length` | Length-unit conversion |
| `mean` | Arithmetic mean |

A shared question generator is appropriate for genuinely related quiz mechanics, with mathematically valid distractors, localized instructions, fair simultaneous play and seeded regression tests. It is not an acceptable replacement for the distinct arcade/memory/board-game mechanics in Appendix B.

# APPENDIX D — FEATURE STATUS AND REQUIREMENT TRACEABILITY TEMPLATE

Create `docs/REQUIREMENTS_STATUS.md` with columns:

`Requirement ID | User-visible behavior | Source/module | Implementation status | Tests/evidence | Device validation | Remaining limitation`

Use honest status values: planned, implemented-unverified, host-tested, emulator-tested, device-verified, blocked. Do not copy “verified” from this prompt or historical conversations. Every catalog row and every regression item must map to implementation and evidence.

The specification intentionally includes both previously requested baseline features and later additions. If a historic source package lacks an original feature—such as a true 3D viewer, periodic table, geometry instruments, grouping, tables or cross-app annotation—that omission is not permission to remove it from this fresh implementation. Track it and implement it, or report a concrete unavoidable platform limitation with a working supported alternative.

# START THE WORK

Inspect supplied assets and references, establish the durable checkpoint location, research current libraries and competitor UX, then create and build the smallest complete repository skeleton. Continue through the full implementation and verification phases. Make routine technical choices autonomously, with written rationale where it matters. Do not stop at a plan or deliver a mockup. Save source checkpoints before long builds and before ending any work session.
