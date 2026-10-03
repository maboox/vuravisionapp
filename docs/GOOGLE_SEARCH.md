# Google search from selected writing

Select a word, handwritten question, existing text or sticky note using the selection tool, then tap the magnifying glass (Search Google) in the selection toolbar. This uses the existing Persian/English recognition configuration. Persian handwriting needs its installed language model; bundled offline OCR supports Latin. If recognition fails, the review dialog remains available for manual input.

Search always opens a review dialog, independent of the Smart review preference. Choose a candidate or edit the question, then explicitly submit it. Cancel or an empty draft sends no request. The original ink/text is retained and the board's undo history is not changed by searching. Search works without a Gemini key; recognition-model downloads and Google results have their own internet requirements.

## In-app floating window

The default option opens a single interactive WebView window within the whiteboard activity. Drag the title to move it; drag the bottom handle to resize. The size button switches between the current compact size and the available workspace. Back, reload, browser handoff and close are provided. The board outside the window remains usable. Window bounds are clamped when the host resizes.

WebView is created only on request, and is destroyed on close, replacement, activity pause or renderer failure. No WebView remains active in the background after leaving the activity. No extra overlay permission is required. The window is app UI and does not float over other Android apps. Google's web content and its System WebView resource use depend on the installed WebView/network; this has not been measured on the physical panel. If WebView is unavailable, the app tries the installed browser.

Navigation stays on HTTPS. File/content access, mixed content, automatic popups, geolocation and web microphone/camera permissions are disabled. There is no JavaScript-to-Android bridge, no certificate-error bypass, and no access to lesson editing or the Gemini key. JavaScript and DOM storage are enabled for normal search pages. Embedded web content may store its own web data in System WebView; it is not added to lesson projects or PDF exports.

## External browser and panel windows

Open in browser launches a properly URL-encoded HTTPS Google search using ACTION_VIEW. The optional adjacent-window checkbox adds FLAG_ACTIVITY_LAUNCH_ADJACENT with FLAG_ACTIVITY_NEW_TASK. Support varies by Android version, device and browser; the system may open a normal browser window. Manufacturer floating-window/PiP controls can be used separately; this app does not use private OEM APIs to force another app into those modes.

Standard Android PiP is intended for video/calls/navigation and does not provide normal interaction with activity UI elements. It is unsuitable for an interactive Google page. The in-app movable window provides that interaction without depending on system PiP.

Official references checked 2026-10-03:

- https://developer.android.com/develop/ui/views/picture-in-picture
- https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
- https://developer.android.com/develop/ui/views/layout/webapps/webview

## Verification

Pure JVM tests cover Persian/mixed-math URL encoding, query limits and window bounds. Android UI regressions cover explicit confirmation, correction, cancel/blank drafts, browser flags and retaining the source selection. Current execution status is recorded in VERIFICATION_1.10.1.json. Real Google loading, hardware touch/window performance and the OEM browser/PiP behavior need verification on the target display.
