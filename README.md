# VuraVision 1.5.0 — source for GitHub Actions

Android classroom whiteboard, Kotlin/native Views. Application ID remains `com.vuravision.classroom.beta`; version code is **8**. No newly built APK is included in this archive.

## Build and test

Extract the archive and place the **contents** of its `vuravisionapp` folder at your GitHub repository root, including `.github` and Gradle files. Push, or run **Actions → Android APK → Run workflow**. The workflow runs JVM tests, release lint, APK assembly and APK verification. A manual run also runs the existing device tests.

Download `VuraVision-1.5-<run number>` from the successful build's artifacts. Screenshots produced by tests are under `build/qa` in the verification artifact. Existing screenshots in `docs/screenshots` are historical.

Local build requires JDK 17 and Android SDK 35:

```sh
chmod +x gradlew
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest
```

The supplied Actions run for the prior source passed unit tests and assembled APKs, then stopped at release lint. The icon superclass was corrected. Version 1.5 changes have not been built in Actions yet. Local compilation is unavailable without Gradle and the Android SDK. See [verification status](docs/VERIFICATION-1.5.md).

## Changes

- White canvas, floating icon-only navigation, scrolling vertical tool dock, icon page navigation. Tooltips/accessibility labels identify controls; menus and settings retain names. Lower-corner full-screen button hides/restores chrome and system bars.
- Pen palette uses actual Material background colors. Duplicate two-tip/input-mode switch removed from the pen panel; input mode remains on the main dock. Fine/broad appearance profiles remain independently editable.
- Adjustable dash length and gap, persisted on each stroke and used by both live drawing and exports. Older strokes retain their original width-relative pattern.
- Nonmodal layer panel beside the canvas: active layer, visibility, lock, opacity, drag-handle reorder, accessible up/down alternatives, rename (hold the name), move selection and confirmed deletion.
- Split count applies immediately. Each panel has small pen/background controls; pen color, width, style and dash settings are independent and saved with the lesson.
- Smart → Convert units supports common length, mass, area, volume, speed, time and temperature units, English aliases and several Persian aliases/digits. Examples: `12 inch to cm`, `2 kg to g`, `32 F to C`. Results preserve the original writing. Incompatible units and temperatures below absolute zero are rejected. Recognition accuracy remains dependent on the selected OCR source; review/correct text when needed.
- QR sharing is a public toolbar action. It exports all lesson pages to PDF, offers alternate LAN addresses and Copy link, and stops when its window closes or after 30 minutes. Both devices must be on a reachable local network. This is file delivery, not live collaboration.
- Model errors distinguish refused connections, DNS failures, timeouts and 404. Added network settings and offline OCR actions. The supplied error is `ECONNREFUSED` to `dl.google.com:443`; application code cannot guarantee access through an unavailable network route. Persian handwriting still needs Google's model.
- Image export checks compression success and nonempty output. Clear-page respects object/layer locks. Autosave failure and backup recovery are surfaced even with the status strip removed.
- Ruler, set square and protractor edges and the compass circle guide pen strokes that start near them; select a guide to move, rotate or resize it. The ruler can change length without changing thickness.
- Mind map starts with three blank nodes. Pen strokes starting inside a node become attached, expand its bounds on release and follow its branch when moved, copied or deleted. The side plus adds a child; the lower plus adds a sibling; typed text is optional.
- Hold the pen still at the end of a stroke for two seconds to convert recognized lines and shapes for live adjustment. Triangle, quadrilateral and circle classification has been revised.
- Lesson Files and Classroom Tools no longer repeat in the main menu; Rename moved to Lesson Files. UI size has Small/Medium/Large settings. More background colors preserve dots/grid/ruled patterns. The Persian app label is VuraVision and About includes “Beyond Vision.”
- Smart toolbar opens language/model settings only; selected writing uses the contextual actions. Auto compares installed English and Persian handwriting models, falling back to bundled Latin OCR when unavailable. A recognized shape action is available on selected ink.
- For different networks, share the exported PDF through an installed cloud app and make a QR from its public HTTPS link. The local QR still requires reachable devices on the same LAN. The app has no hosted backend and cannot publish or validate cloud permissions itself.

## Installation

Keep the existing production signing key in GitHub Secrets so updates can install over your current app. The workflow falls back to development signing when no production key is supplied. Export important `.vura` lessons before any uninstall. No signing credentials are included.

Earlier scope/specification/handoff files are historical references. This README and `docs/VERIFICATION-1.5.md` describe the current delivery.
