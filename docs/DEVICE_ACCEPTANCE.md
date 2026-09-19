# Target-display acceptance

Use this checklist on the actual VuraVision display before a classroom rollout. The desktop tests cannot qualify the panel's touch controller or physical network.

1. Open the APK and draw with two simultaneous writers. Check fast diagonal strokes, palm placement, stylus eraser if reported, pointer lift order, and undo of a complete gesture.
2. Save a lesson containing ink, Persian/English text, an image, a multipage PDF and a graph. Force-stop/reopen, export `.vura`, import it and compare every page.
3. Export current/all pages to PDF. Open that PDF in a separate reader and check embedded PDF/image objects. Repeat PNG/JPEG export.
4. Download each handwriting model, recognize representative writing, then disable Internet access and recognize again. Measure accuracy with your actual handwriting samples; do not assume a fixed accuracy from model availability.
5. Open the QR link from a phone on the same LAN. Confirm only the intended PDF downloads, close Share and verify the old link stops working.
6. Run both-player games in side-by-side and face-to-face modes. Test genuinely simultaneous taps and false starts.
7. Switch Persian/English, rotate the device and inspect text clipping, RTL menus, keyboard entry and document-picker access.
8. In Engineering, read actual major/minor/pressure/tool values. Enable contact thresholds only if observed hardware data separates the intended classes. Test rejection/erasing afterward.
9. Check large lessons for memory use, frame rate and responsiveness. Autosave provides one backup, not unlimited version history.

The workflow's manual Run workflow includes API 35 instrumented PDF export/model-identifier checks. To run those on a locally connected test device: `./gradlew connectedDebugAndroidTest`.
