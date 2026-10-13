# VuraVision 1.16.0

Version code 26. Complete Android source update from 1.15.0; application ID, signing, SDK/toolchain and dependencies are unchanged.

## Split workspace

Background color **and pattern** belong to each panel. Legacy page patterns remain the fallback. Solo and split workspaces are retained separately, including objects, layers, backgrounds, pen settings and camera transforms. Returning to one panel restores the original solo workspace; reopening split mode restores its drawings. The first split starts from a copy of the solo board in panel one. Reducing four panels to two/three hides the other panels without moving their objects. Older split lessons with no solo archive recover panel one rather than merge all panels. All split exports use vertical columns.

Dormant workspace objects and media are included in save/load, validation, page duplication, history and shared-room metadata. Only the current visible panels appear in previews and exports. Existing document size/sample limits also include retained workspaces.

## Geometry and graph

- Graph/function objects, images, PDFs, periodic tables and fixed-style geometry guides have no object-color action. Mixed selections change only objects with meaningful colors.
- Coordinate graphs can connect manually placed points in insertion order. Intersection search combines function/function, nonadjacent polyline segment/segment and polyline segment/function intersections. Orange intersection markers and coordinates are separate from input points. Editing points, curves or connection mode clears stale results.
- Ruler retains Parallel and Perpendicular commands; duplicate Draw with tool is removed from the ruler menu. Other guides retain their construction commands.
- Drawing against guide edges, compass arcs, protractor rays and construction commands commit the live length/angle as ordinary editable text. Drawing and label share a single Undo; labels save and export. The selected-shape Measure overlay remains temporary.
- Ruler rotation has a visible handle below the guide plus a named Rotate command. Guide fonts and ruler thickness follow object resizing; tick/label spacing adapts to scale/calibration. Defaults remain ten board coordinates per `u`; calibrated display measurements use `cm`.
- Set square has a slope handle and a numeric slope command (10–80°); resizing retains its slope. Each snapped contact locks its starting edge until lift. Crossing a corner cannot attach to the next edge; leaving the magnetic area stops the remaining contact.

Numerical function intersections may miss tangencies or narrow features. Coincident sections are not emitted as isolated intersections. Points form one ordered open polyline; entering a repeated first point can close it.

## Editing and controls

- Mind-map nodes retain text/color and named child/sibling branch commands. Smart conversions and the two ambiguous plus controls are removed.
- Periodic table can insert a complete 118-element PNG, alongside its interactive-table/card options.
- Pie shortcuts include Clear page. Its command and Eraser settings use the same intentional slide-to-clear control: drag from the thumb to the end and release. Cancellation, partial movement and ordinary taps do not clear. In split mode it clears only the active panel; locked items/layers, other panels, dormant workspace and PDF source remain. One Undo restores the cleared items.
- Page previews have direct Duplicate, Move up, Move down, Clear and Delete icons; destructive page-row actions retain confirmation. Page-row Clear targets visible active-workspace panels. Duplication preserves both workspaces with new object IDs. Page operations preserve the selected page during reordering.
- Workspace vector icons now scale with UI size, matching their targets and text.
- Undo defaults to 50 entries. Hidden Engineering → Undo history limit offers 10/25/50/100/200. It applies to the whiteboard, existing/new PDF annotation stores and host room per-author history. Collaborate still has personal history; Control shares the host history.
- Selection settings are available only from a repeated tap on the active Select icon.
- Tap a game player name to rename it locally (24 characters). Native and legacy games use names in panels, turns and results; timed rounds pause during name editing.

Previous bottom-right main actions, centered dialogs, selection-adjacent actions, top Back icons and bottom lab/game buttons remain. Experimental LAN room scope is unchanged; see RELEASE_1.15.md for its permission/network model.

## Verification

See VERIFICATION_1.16.json and DEVICE_ACCEPTANCE_1.16.md. Offline structural/XML/font/data, resource/workflow and JavaScript syntax checks ran locally. Android compilation, unit-test execution, lint and APK are **pending**: Gradle download fails with `java.net.SocketException: Network is unreachable`. Added regression tests are enabled, not claimed to have executed. GitHub Actions is the build/Android test gate.
