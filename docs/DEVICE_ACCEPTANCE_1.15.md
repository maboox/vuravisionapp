# 1.15 device acceptance

Run GitHub Actions first; retain test and lint reports. Use at least two Android devices on the same local network, preferably one classroom panel and one tablet. The feature is experimental until these checks pass.

1. Verify Menu / Files / Undo / Redo / Share at bottom-right and no Pie toolbar button. Select an object near each edge; actions stay near it. Open nested settings, labs and games: Back is a top arrow, ordinary dialogs centered, lab/game actions bottom.
2. Set different names, colors and emoji/photos in Settings → Device profile. Restart; profiles persist locally. Open hidden Engineering using the About double-tap / pause / double-tap sequence.
3. Create a room with an existing multipage board. Join by automatic discovery, camera QR, QR image and manual address. Test a hotspot and a network with discovery blocked. QR must not imply it joins Wi-Fi or bridges networks.
4. Before approval, guest sees waiting status and no shared document; its prior lesson is preserved. Approve View: host page and camera follow, drawing and object edits are blocked. Remove guest and confirm further access stops.
5. Grant Control. Change tool, pen, selected panel, zoom and page from both devices. Draw and Undo from the tablet; history matches the host. Check all four vertical panels and geometry tools.
6. Grant Collaborate. Use different tools, pages and cameras. Draw simultaneously: each Undo removes only that user's edit. Test concurrent additions, deleting and restoring a middle object, and front/back stacking.
7. Both select the same object; first selection wins and the other sees name/color. Release or disconnect; the lease becomes available. Attempt concurrent edits and Undo after another user's edit; later work must survive and rejected edits must reconcile.
8. Check live strokes, moving selections and last-position/viewport ghosts. Stop all input: ghost fades within one minute despite polling. Set device clocks differently and repeat.
9. Import an image, crop with handles, add a lab snapshot and a PDF object. Confirm identical content on both devices; save/leave/reopen and inspect export. Separate PDF reading panes stay local.
10. Interrupt Wi-Fi briefly while drawing. Reconnect: no duplicated strokes, operations converge. Close and reopen the guest app, choose Reconnect to previous room, and confirm private credentials/history/outbox recover. If host closed the room, local board copy remains accessible after leaving.
11. Exit host while guest is drawing; guest sees room closed and retains its last local copy. Reopen the original local lesson and the shared copy to ensure they were saved under separate identities.
12. Repeat in Persian (RTL), landscape, portrait and larger interface sizes. Test camera denied/missing, invalid QR/address, approval changes, and a pending photo/file picker result after closing its dialog.

Record device models, Android versions, network type, observed latency and any failed step. Do not treat localhost or structural checks as proof of two-device behavior.
