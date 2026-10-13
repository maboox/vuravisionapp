# Target-display acceptance

Use this checklist on the actual VuraVision display before a classroom rollout. The desktop tests cannot qualify the panel's touch controller or physical network.

1. Open the APK and draw with one, five and ten simultaneous contacts. Check fast diagonal strokes, palm placement, stylus eraser if reported, pointer lift order, and undo of a complete gesture.
2. Save a lesson containing ink, Persian/English text, an image, a multipage PDF and a graph. Force-stop/reopen, export `.vura`, import it and compare every page.
3. Export current/all pages to PDF. Open that PDF in a separate reader and check embedded PDF/image objects. Repeat PNG/JPEG export.
4. Download each handwriting model, recognize representative writing, then disable Internet access and recognize again. Measure accuracy with your actual handwriting samples; do not assume a fixed accuracy from model availability.
5. Open the QR link from a phone on the same LAN. Confirm only the intended PDF downloads, close Share and confirm it still works; press Stop sharing and verify the old link stops working. Also verify expiry after 30 minutes.
6. Run both-player games in side-by-side and face-to-face modes. Test genuinely simultaneous taps and false starts.
7. Switch Persian/English, rotate the device and inspect text clipping, RTL menus, keyboard entry and document-picker access.
8. In Engineering, read actual major/minor/pressure/tool values. Enable contact thresholds only if observed hardware data separates the intended classes. Test rejection/erasing afterward.
9. Check large lessons for memory use, frame rate and responsiveness. Autosave provides one backup, not unlimited version history.

The workflow's manual Run workflow includes API 35 instrumented PDF export/model-identifier checks. To run those on a locally connected test device: `./gradlew connectedDebugAndroidTest`.

10. Keep the timer/stopwatch floating while drawing and switching pages. Drag, minimize and close tools. Confirm independent pen/highlighter colors after reopening the app.

11. Calibrate both physical tips; set visibly different colors/widths. Test reported stylus and finger tool types, 0/unknown contact, palm rejection and pen-only behavior.
12. Area-erase ink, rotated/resized shapes, styled text and individual PDF pages. Export/reopen, undo/redo and restore erased areas. Verify unmasked content is unchanged.
13. In Smart, circle clear Latin writing offline, review/edit text, calculate 2*(3+4), solve 2x+3=11 and x^2=9, then plot y=2x-5. Test unsupported/misread input and cancel with no ink loss. Test Persian only after its optional model is installed.
14. Open Discovery and Arcade on the actual Android System WebView. Try every category, pause/resume, portrait/landscape, back/re-entry and simultaneous player touches. Export a lab snapshot to the board. Desktop DOM/Canvas smoke checks do not certify WebView rendering or hardware multi-touch.
