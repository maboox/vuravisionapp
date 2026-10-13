# VuraVision 1.9.4

## 1.9.4 offline bilingual fonts

Settings → Fonts selects Persian and English independently, with family previews. Bundled families: Kahroba, Vazirmatn, Sahel, Shabnam, B Nazanin, B Zar, B Titr, Montserrat, Rubik and Bahnschrift, alongside system sans/serif/monospace choices. Regular/bold variants are grouped as families, not listed as separate font sizes. Text editing also supports italic.

New text, sticky notes and smart-conversion results stamp both selected family IDs into project objects. Mixed Persian/Latin text uses separate metric-affecting spans, including measurement and exported PDF rendering. Existing objects can be changed on the active page with Undo; protected, locked or hidden items are excluded. Legacy objects with no font IDs retain the system face until explicitly reformatted. The schema remains backward compatible. Native labels, buttons, fields and common dialogs use the selected fonts. The legacy HTML lab/game page body and canvas labels remain controlled by their packaged HTML styles.

Font files are local and faces are cached. Variable uploaded families are instantiated as static regular/bold faces before packaging. StaticLayout caching avoids remeasuring unchanged board text. FontsTest adds five Android regression tests for script runs, preference/project persistence, caching, cursor/preview stability and the settings/apply/undo flow. Current verification: 119 unit tests passed, debug/release lint passed with zero errors (671 warnings per variant), debug APK and instrumented-test APK assembled, debug APK ZIP alignment passed. Font settings preview was rendered and inspected. Device/emulator tests were not executed and physical-panel performance remains unmeasured. Full records are in docs/FONTS.md and docs/VERIFICATION_1.9.4.json.

## 1.9.3 PDF stability and unlock recovery

- Mounted PDF page renderers retain their latest frame independently of shared LRU eviction. A sufficient retained or higher-quality cached frame avoids another render request. References are released when the page view is evicted or disposed. Workspace previews cap their longest edge at 1536 pixels; synchronous export resolution is unchanged.
- Nearby-page materialization is coalesced to animation frames and waits for committed layout geometry. Unchanged page dimensions and page-number text no longer trigger redundant layout.
- Fullscreen, return to split, side swaps and copying a page from fullscreen request layout directly on PdfSplitLayout. Visibility is synchronized before measurement.
- Layers includes Unlock all on this page, with independent undo for the active board/PDF page. Hidden objects and layer locks can be recovered; hidden-layer visibility stays as configured and the PDF source item remains locked.
- SmartMath remains a one-variable linear/quadratic solver, not a two-variable system solver.
- Four Robolectric regression tests cover unlock/undo/protected PDF source, retained frames after cache clearing, split-layout updates without touch, and solver scope. They were added for Actions in 1.9.3. They are included in the full unit-test suite run for 1.9.4.

At the time of the 1.9.3 delivery, offline source structure and ZIP integrity checks passed; the Android SDK was unavailable, so Android checks were pending. The 1.9.4 verification below supersedes that status for the current source. Physical display-panel flicker/performance checks remain pending. 1.9.3 used versionCode 15 and versionName 1.9.3.

## 1.9.2 unit-test compilation correction

The second supplied Actions log confirms main debug/release Kotlin compilation proceeded successfully, then unit-test compilation failed because Release19Test imported AndroidX ApplicationProvider without a unit-test dependency on androidx.test:core. The test now obtains its Context lazily from Robolectric RuntimeEnvironment, matching existing tests and using the already-declared Robolectric dependency. All nine test cases remain enabled. versionCode is 14 and workflow artifacts identify 1.9.2. No complete local Android build was run; GitHub Actions must confirm this correction.

## 1.9.1 build correction

The supplied GitHub Actions log reports recursive Kotlin return-type inference between Lesson and PdfWorkspaceState copy methods. Their return types, and PdfSheet.deepCopy, are now explicit. This addresses the reported recursive-inference errors and consequent unresolved pages references without changing copy behavior. versionCode is 13 and workflow artifacts identify 1.9.1. Release signing validation passed in the supplied log. Full Android compilation of this correction has not been executed locally; GitHub Actions must confirm it.

## Changes

- PDF import offers a movable board object or a fixed, scrollable PDF workspace beside the board.
- Drag the divider, swap sides, use PDF fullscreen, jump to a page, fit width or zoom. Only nearby pages allocate board views.
- Each PDF page uses the existing drawing tools and keeps independent editable annotations and undo history. Board undo does not revert PDF notes.
- Hand mode scrolls with one contact. With two-tip mode off (Pen + touch on), a broad touch contact scrolls and a fine contact draws. Two contacts navigate and zoom when multitouch is enabled. Unknown contact width needs the explicit Hand button. A separately identified active stylus keeps writing; a hardware eraser keeps erasing.
- PDF eraser defaults to annotations only. An explicit option also covers original page content with reversible white marks. These marks do not remove searchable confidential content.
- Save notes into the original PDF (provider permission required), save a new PDF, or keep editable notes in the project. The original PDF page streams/text are retained; notes are appended as a fixed transparent overlay. Keep the .vura project for separate object editing.
- Before replacing an original file, check whether it changed outside the app, retain a recovery copy, verify written bytes and attempt restoration after a write failure.
- Closing/replacing a PDF with unsaved notes offers Save, Leave without PDF save and Continue editing. Projects still autosave.
- Copy the current PDF page or a rectangle to the whiteboard.
- Image/object transforms render a moving layer instead of rebuilding the full board at each motion. During navigation, cached preview frames refresh periodically and fully after release.
- Background patterns become coarser at distant zoom. Display paths use fewer samples at distant zoom; source ink and full export paths are retained.
- PDF rendering uses persistent sessions, a priority queue, bounded bitmap cache, independent image decoding and nearby-page prefetching.
- Object metadata edits avoid copying all existing point samples. PDF snapshots keep immutable point lists; asset archives avoid redundant compression. Autosave waits for a pause in interaction.
- Divider dragging uses hardware drawing rather than reallocating page-sized backing bitmaps for every movement.
- The bilingual guide has 27 chapters and an explicit ordered chapter index, including PDF and performance instructions.
- All 73 native experiments have a subject and lesson-topic classification, subject/topic filters, localized descriptions and search. The 68-experiment HTML fallback also has a searchable card catalog.
- Package ID and release signing remain unchanged. No activation or serial-number feature was added.

## Original 1.9.0 session verification (historical)

Passed locally:

- JavaScript simulations: 68 experiments at default/minimum/maximum settings (204 cases), 49 games, zero reported failures.
- HTML catalog: 68 cards, complete lesson mappings, subject filter, lesson filter, search and opening experiments.
- Offline source structure checks: balanced Kotlin delimiters, XML parsing, 27 matching bilingual guide chapters, 73 unique native experiment mappings.

Added for GitHub Actions, NOT executed locally:

- Nine Android unit tests for project round-trips, source URI isolation, independent undo, legacy import, snapshot stability, broad-touch routing, background density, protected PDF base and complete bilingual catalogs.
- An Android device test for notes/covering on 0/90/180/270-degree rotated crop boxes, unchanged page count and preserved searchable original text.
- Existing regression tests remain, with guide count updated from 24 to 27.

Android compilation, lint and device checks could not run here: the Gradle distribution download failed because the configured network proxy was unavailable; this environment also has no Android SDK. Delimiter checks are not a Kotlin compiler. A successful GitHub Actions build and actual-panel testing are still required before distributing the APK. No performance percentage or frame-rate improvement is claimed.

## Build and device verification

Replace the repository root with this ZIP's contents, including .github. The four existing ANDROID signing secrets are still used. Push runs unit tests, release lint, signed APK assembly and APK verification; the artifact is VuraVision-1.9.4-<run number>. Run the workflow manually to also execute Android device PDF tests.

On the panel, compare the same heavy .vura, large image and multi-page PDF against 1.8: drag/resize a large photo, zoom a dense page out/in, scroll a PDF with the broad tip, switch two-tip mode, draw and erase on two pages, resize the divider, undo on both sides, save original/new PDF and reopen both the PDF and .vura project. File providers that do not grant write permission require Save as new PDF.
