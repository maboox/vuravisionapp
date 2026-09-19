# VuraVision Classroom Suite — 1.1.0

Android 8+, English/Persian, version code 4. The application ID remains `com.vuravision.classroom.beta` for existing lesson compatibility; the displayed release is 1.1.0.

## Install or build

The install folder contains the universal release APK. To build on GitHub, extract this ZIP and push **the contents of VuraVision-1.1/** to the repository root, including `.github/`, `gradle/`, `gradlew` and `app/`. Uploading the ZIP itself will not trigger a build. Actions → Android APK produces a downloadable APK artifact on push. Manual Run workflow also runs the supplied Android device tests on an emulator.

Java 17 + Android SDK 35: `./gradlew testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest`. Optional HTML smoke checks: `npm ci && npm test` with Node 22+. They use a DOM and native Canvas, not a full browser or Android WebView.

## What changed

- Pen dialog: labeled color/thickness, live preview, separate fine/broad-tip appearance, distinct round ink and square opaque marker. Highlighter retains its independent color/width.
- Dual-tip calibration: raw contact-width thresholds, palm behavior and diagnostics. Quick Guide → tap its heading three times → Engineering. Appearance is also accessible by re-tapping Pen.
- Area eraser for ink and objects, alongside whole-stroke/object erasing. Undo and Object Actions → Restore erased areas preserve the original editable object. PDF erasures belong to their individual PDF page.
- Selected object: tap it again for actions. Text supports bold, color, alignment and fitted bounds. Sticky notes have independent background color. Insert no longer duplicates Text or Shapes.
- Uniform circles/regular shapes; directional line endpoints; cylinder/cone cap proportions. Function graphs retain equal x/y scale, accept `y=2x-5`, `x^2`, `sin(x);cos(x)` and have examples and explanations.
- Selected PDF: vertical swipe or mouse wheel turns pages, toolbar buttons show the current page; horizontal drag moves the object.
- Smart pen modes: text, arithmetic/linear/quadratic equation, graph, continuous automatic basic shapes. Circle existing ink, then review/edit recognition before applying. Original ink is retained by default.
- Bundled Latin image OCR provides an offline option without downloading a language model. Optional Google digital-ink English/Persian models have status, fresh retry and concise diagnostics, including HTTP 404.
- Discovery: 68 Persian simulations adapted from the supplied HTML, search, reset/pause and Add to board. Arcade: 49 bilingual games with categories/search and celebration effects. The existing 16 bilingual native labs and 32 native games/challenges remain available in a separate catalog.

## Release notes and limits

See `docs/CHANGES-1.1.md`, `docs/VERIFICATION.md` and `docs/DEVICE_ACCEPTANCE.md`. This version does not claim perfect recognition or hardware certification. Bundled OCR is Latin image recognition, not a general handwriting/formula model. Persian handwriting still requires its Google language model. An HTTP 404 from Google's model service remains an external dependency; a fresh retry is not a guaranteed fix.

The release APK uses a development signing certificate unless the repository has your production secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Use your own stable signing key for distribution. Export `.vura` lessons before uninstalling a differently signed old build. No private key is included.

LAN sharing uses http and a reachable Wi-Fi/Ethernet address. Keep the app open; the link expires after 30 minutes. Router client isolation can prevent access even on the same Wi-Fi. Classroom widgets float within this app, not over other Android apps.
