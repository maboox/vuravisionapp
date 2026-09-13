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

The product contains:

## A. VuraVision Whiteboard
The core product.

## B. VuraVision Lab
Interactive simulations for educational subjects.

## C. VuraVision Games
Competitive multiplayer touch games.

## D. Files
Open/manage VuraVision documents, PDFs and media.

## E. Share
Local QR sharing initially.

## F. Settings

## G. Hidden Engineering / Diagnostics Mode

All modules must share one coherent design system.

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

# 10. DRAWING ENGINE

Research the best open-source/native approach first.

Required tools:

- Pen
- Pencil
- Marker
- Highlighter
- Brush

Parameters:

- color
- width
- opacity
- smoothing
- brush style where applicable

Drawing must feel extremely smooth.

Avoid visible jagged lines.

Account for:

- historical MotionEvent samples if useful
- stroke interpolation
- velocity
- pressure where available
- path smoothing
- rendering performance
- vector data

Aim for stable 60 FPS minimum on target hardware.

Higher refresh-rate hardware should benefit naturally when possible.

---

# 11. PEN TIP / PEN BACK / FINGER

The VuraVision stylus may be passive.

There may be no electronic identifier for front/back stylus tips.

The smaller stylus end produces a relatively small touch contact area.

The larger stylus end produces a larger contact area.

A finger may produce a similar contact area to the larger end.

Therefore implement a configurable classification system using available Android touch metrics such as:

- toolType
- touchMajor
- touchMinor
- size
- pressure
- orientation
- pointer dimensions
- device characteristics

Never assume every Android panel reports these values consistently.

Create a calibration system.

Allow user/device profile to assign independent behavior to:

### Thin tip
Example:
Pen

### Thick tip
Example:
Eraser / thick marker

### Finger
Example:
Pan or drawing

### Palm
Temporary eraser or rejection depending on mode.

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

# 13. ERASER SYSTEM

Provide:

- stroke eraser
- area/pixel-style eraser where technically appropriate
- object eraser
- palm eraser

Clear operations:

- Clear ink
- Clear selected objects
- Clear page
- Clear canvas/document with confirmation where appropriate

Undo must recover destructive operations.

---

# 14. SELECTION

Implement:

- tap select
- box select
- lasso select
- multiple selection
- Select All

Selection affordances should be professional and touch-friendly.

Objects need handles for:

- resize
- rotation
- relevant object actions

Handles must not be tiny.

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

# 17. MODEL MANAGER

If recognition models are too large to bundle in the APK:

Implement:

# Model Manager

Settings should show:

- installed models
- available models
- download size
- download progress
- version
- SHA-256 verification
- remove model
- reinstall model
- update model

Downloaded models should work offline after installation.

Do not silently re-download them every session.

---

# 18. HANDWRITING UX

Example workflow:

Teacher writes:

Hello class

Then:

- select strokes
- press Smart
- choose Convert to Text

Recognize the strokes.

Create an editable TextObject.

Allow:

- replace original ink
- keep original + create text
- cancel

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

# 20. SMART MATH ACTIONS

Example:

Teacher writes:

2x + 4 = 12

Smart selection should produce:

`2x + 4 = 12`

Possible actions:

- Convert to Equation
- Solve
- Simplify
- Evaluate
- Plot
- Copy

Result:

`x = 4`

The recognized equation and result should become movable objects.

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

# 23. SMART CIRCLE / REGION ACTION

Implement an interaction like:

1. Teacher writes content.
2. Activates Smart Tool.
3. Draws a lasso/circle around handwriting.
4. System identifies strokes inside.
5. Context menu appears.

For text:

- Recognize Text

For math:

- Convert to Equation
- Solve
- Plot

The region selection should be tolerant and touch-friendly.

---

# 24. FUNCTION PLOTTER

Required.

Input options:

- keyboard
- handwriting recognition
- equation object

Example:

`y = x² - 4x + 3`

Actions:

- Plot

Graph becomes a GraphObject on the board.

Provide:

- axes
- labels
- grid
- pan
- pinch zoom
- reset
- configurable domain
- multiple functions
- function legend
- trace point if practical

Research expression parsers and graphing projects before implementing.

Reuse suitable open-source math parser/rendering logic where appropriate.

Do not send ordinary plotting queries to cloud services.

---

# 25. SMART SHAPES

Research available shape-recognition techniques/libraries.

If useful reliable OSS exists, reuse/adapt it.

Otherwise implement basic geometry recognition.

Examples:

hand-drawn circle → perfect circle

rough rectangle → rectangle

rough line → straight line

triangle → triangle

Allow original ink to be replaced by recognized shape.

---

# 26. 2D GEOMETRY

Include:

- line
- arrow
- rectangle
- square
- circle
- ellipse
- triangle
- polygon
- star
- arc

Objects remain editable.

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

# 29. TEXT

TextObject supports:

- Persian
- English
- RTL
- LTR
- color
- size
- weight
- alignment
- editing

Use fonts legally redistributable in a commercial product.

Do not package proprietary font files without permission.

---

# 30. STICKY NOTES

Create touch-friendly movable sticky notes.

Support:

- several colors
- text edit
- resize
- move
- duplicate
- delete

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

# 34. PDF IS A REAL CANVAS OBJECT

This is extremely important.

PDF should NOT simply open as a separate basic viewer.

A PDF may exist on the Whiteboard as a PdfObject.

Required:

- import PDF
- place on canvas
- move
- resize
- select
- lock
- change page
- previous/next
- page picker
- zoom content where sensible
- annotate

Research maintained PDF rendering engines.

Evaluate:

- Android PdfRenderer
- current Pdfium forks
- open-source PDF viewers
- PDF annotation projects

Pay close attention to:

- Android 14
- future Android compatibility
- native ABI
- 16 KB page-size support
- large PDF memory use

Do not automatically use an old famous Pdfium fork if a better maintained solution exists.

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

# 37. QUICK CLASSROOM TOOLS

Provide polished floating tools such as:

- Timer
- Stopwatch
- Countdown
- Dice
- Spinner
- Random number
- Scoreboard
- Screen curtain / screen shade

These should appear above the board without destroying document state.

---

# 38. SAVE FORMAT

Create an editable VuraVision document format.

Suggested extension:

`.vura`

Design it as a versioned container rather than one fragile serialized object.

For example:

```
document.vura
 ├── manifest.json
 ├── document.json
 ├── thumbnails/
 ├── assets/
 │    ├── images/
 │    └── pdf/
 └── metadata/
```

ZIP/container-based implementation is acceptable.

Include schema version.

Future versions must be able to migrate old documents.

Do not serialize Android UI classes directly.

Serialize domain models.

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

# 40. EXPORT

Support:

- PNG
- JPEG
- PDF

PDF export options:

- current page
- selected pages
- all pages

Export at good quality.

Large 4K displays must not force low-resolution exports.

---

# 41. LOCAL QR SHARING

V1 does NOT require VuraVision cloud.

Implement local sharing.

Flow:

Teacher:
Export lesson → Share via QR

App:

1. generates PDF
2. starts temporary local HTTP server
3. creates unguessable session token
4. detects LAN address
5. generates QR
6. mobile user scans
7. browser opens download page
8. file downloads directly from VuraVision display

Devices are normally on same Wi-Fi/LAN.

Research maintained Android solutions first.

Possible technologies worth investigating:

- NanoHTTPD
- Ktor embedded server if suitable
- ZXing
- other current OSS alternatives

There are open-source Android projects implementing LAN file sharing through QR; inspect them for patterns rather than reinventing network handling.

Security requirements:

- temporary token
- limited lifetime
- no directory exposure
- only explicitly shared files
- stop sharing button
- display connected/download count if practical
- stop server automatically after timeout or user action

Do not require internet.

---

# 42. VURAVISION LAB

The user may provide a legacy HTML VuraVision Lab implementation.

Use it as:

- idea inventory
- interaction reference
- formula reference
- simulation logic reference

Do NOT copy its old UI.

Redesign Lab completely using the new VuraVision design system.

V1 should include approximately 12–15 genuinely polished simulations rather than 70 poor ones.

Suggested V1:

## Physics
- Projectile Motion
- Pendulum
- Spring / Hooke's Law
- Collision & Momentum
- Standing Waves

## Chemistry
- 3D Molecules
- States of Matter
- pH
- Atomic Structure
- Reaction Kinetics

## Mathematics / Geometry
- Quadratic Function
- Trigonometry
- Function Plotting
- Interactive Geometry

## Statistics / Probability
- Probability simulation
- Sampling / distribution simulation

Each simulation should include where appropriate:

- touch manipulation
- sliders
- animation
- realtime values
- start
- pause
- reset
- formulas
- brief educational explanation

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

# 45. VURAVISION GAMES

The user may provide an older HTML implementation containing approximately 49 two-player games.

Use it as:

- game idea inventory
- gameplay-rule reference
- orientation interaction reference

Do NOT copy its outdated visual design.

V1 should implement approximately 15–20 of the best games extremely well.

Examples:

- Reaction Race
- Tap Race
- Tug of War
- Math Race
- Bigger Number
- Even / Odd
- Stroop
- Simon
- Pattern Memory
- Find the Odd One
- Number Sequence
- Timing Challenge
- Pong
- Tic Tac Toe
- Connect Four
- Rock Paper Scissors

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

# 47. GAME ENGINE

Separate common game engine logic from individual games.

Common concepts:

- Player
- Score
- Round
- Match
- Timer
- Win
- Lose
- Tie
- Restart
- Orientation
- Language
- Sound/haptic hooks

Adding a new game later should be straightforward.

V2 target:

40–50+ games.

---

# 48. SOUND

Research legally safe/open sounds if needed.

Prefer subtle classroom-friendly sounds.

Allow mute.

Do not use copyrighted game sounds.

---

# 49. HIDDEN ENGINEERING MODE

Create a powerful hidden VuraVision Engineering Mode.

It is NOT only a calibration screen.

It is also a:

- diagnostics center
- hardware inspection tool
- developer debug panel
- touch debugger
- performance profiler
- application troubleshooting area

Possible unlock:

Settings
→ About
→ tap VuraVision logo 7 times
→ Engineering Mode unlocked

Optionally protect advanced/destructive settings with an admin PIN.

Do not expose sensitive secrets.

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

# 58. EXPERIMENTAL FEATURE FLAGS

Engineering Mode may contain development feature flags.

Examples:

- alternative ink engine
- alternative palm classifier
- experimental renderer
- debug bounding boxes
- touch heatmap
- verbose recognition logs

Keep these centralized.

Do NOT scatter random BuildConfig checks throughout the codebase.

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

# 61. VURAVISION DESIGN SYSTEM

Create module/package for VuraVision design system.

Define:

- colors
- spacing
- typography
- radii
- elevations
- animation durations
- large-display dimensions
- touch target sizes
- icons
- dialog styles
- panels
- toolbars
- chips
- cards
- sliders

Brand:

VuraVision.

Use a premium modern educational visual language.

The existing legacy VuraVision material uses dark surfaces and orange brand accents. You may preserve brand recognition while substantially modernizing the UI.

Avoid:

- generic sample-app appearance
- excessive gradients
- childish styling
- random neon
- inconsistent icons
- cramped menus

---

# 62. ICONOGRAPHY

Research current high-quality open-source icon libraries compatible with the product's license.

Use one coherent family plus custom VuraVision icons where needed.

Do not mix emoji into professional application controls unless a specific game intentionally uses emoji.

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

# 64. PERFORMANCE ARCHITECTURE

Avoid recomposing the entire Whiteboard on every pointer move.

Avoid holding huge full-resolution bitmaps unnecessarily.

Research and implement appropriate:

- rendering cache
- tile cache
- PDF page cache
- spatial indexing
- object bounds
- viewport culling
- background processing

For huge canvases, only render visible/relevant content where practical.

For selection on thousands of objects, consider spatial indexing rather than scanning everything every frame.

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

# 71. LEGACY FILES

I may provide two old HTML files:

1. VuraVision Lab
2. Two-player game collection

Inspect them thoroughly.

Extract:

- existing experiment list
- equations
- interactions
- game rules
- two-player layout behavior

They are old prototypes.

DO NOT preserve their visual appearance.

They are functional/product references only.

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
- local QR sharing explanation
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

# 85. DEVELOPMENT DEBUG BUILD

Debug builds may expose a direct Engineering Mode shortcut to developers if useful.

Release build should keep Engineering Mode hidden behind its intended unlock flow.

Do not expose dangerous developer menus openly to normal classroom users.

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

# 89. SETTINGS

Include sections:

## General
- Language
- Theme
- autosave
- default background

## Pen & Touch
- default pen
- finger behavior
- palm eraser
- calibration status

## Smart Tools
- handwriting models
- math model
- offline model management

## Sharing
- local sharing settings
- timeout

## Storage
- autosave
- cache
- model storage
- clear cache

## About
- version
- VuraVision
- Engineering Mode secret activation

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

# 91. V1 IMPLEMENTATION PRIORITY

Do NOT attempt to impress me by implementing 100 shallow features.

Prioritize:

### Whiteboard
Extremely strong.

### Smart Handwriting
Actually works.

### Smart Math
Actually works.

### Function Plot
Actually works.

### PDF Object
Actually works.

### Save/Open
Actually works.

### QR Local Share
Actually works.

### Engineering Mode
Actually works.

### Games
15–20 polished games.

### Lab
12–15 polished simulations.

Quality > quantity.

---

# 92. V2 EXTENSIBILITY

Architecture must be ready for later:

- 50–100+ Lab experiments
- 40–50+ games
- advanced AI
- cloud accounts
- cloud document synchronization
- student phones
- polls
- classroom responses
- quizzes
- teacher-generated questions
- 4-player modes
- team games
- remote collaboration
- screen casting
- lesson assistant
- OCR from arbitrary documents
- advanced geometry solver

Do not implement the entire V2 now.

Just avoid architectural dead ends.

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

# 94. CONTINUOUS BUILD VALIDATION

Do NOT write the entire repository and only then discover it does not compile.

Build repeatedly during development.

At important milestones run:

`./gradlew assembleDebug`

and relevant tests.

Resolve:

- missing imports
- resource errors
- manifest errors
- dependency conflicts
- Kotlin errors
- Compose errors
- native ABI errors

before continuing.

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

# 98. NO WEBVIEW-FIRST ARCHITECTURE

The old Lab and Games are HTML.

Do NOT simply package those old HTML files inside WebView and call the Android app complete.

Native Android is the primary architecture.

A WebView may be used only when there is a specific technically justified component where web technology is genuinely the best solution.

Document any such decision.

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
Import PDF → place it as Object → change page → resize/move.

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
Generate QR → phone on same LAN downloads the PDF through browser.

### Scenario 16
Open Lab → interact with simulation → send snapshot to Whiteboard.

### Scenario 17
Open a two-player game → both players can interact simultaneously.

### Scenario 18
Switch Top/Bottom ↔ Left/Right.

### Scenario 19
Change language Persian ↔ English and UI direction changes correctly.

### Scenario 20
GitHub Actions builds downloadable APK successfully.

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