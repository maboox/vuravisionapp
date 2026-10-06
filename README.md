# VuraVision 1.15.0

Full Android source, Gradle Wrapper and GitHub Actions for interactive touch panels.

The main Menu / Files / Undo / Redo / Share toolbar is at the bottom-right. Selected-object actions appear above or beside the object; ordinary dialogs are centered and nested menus use a top Back arrow. Lab/game actions stay at the bottom. Enable/configure Pie menu in **Settings → Pie menu**; open it with the five-finger double tap.

The previous graphical crop, separate free/box selection, Duplicate with plus icon, four vertical panels, live geometry measurements, clean guide snapping and page counter remain available.

## Experimental shared rooms

Set your name, color and emoji/photo in **Settings → Device profile**. To open rooms: **Settings → About VuraVision → double tap the logo → wait 1–3 seconds → double tap again → Shared room**.

One device creates a room; others join by discovery, QR or manual invitation. Devices must share a local Wi-Fi/LAN/hotspot. QR transfers the room address; there is no cross-network relay.

The host approves guests as View, Control or Collaborate. View follows the host without editing; Control shares host tools, page/camera and Undo; Collaborate has independent tools/views and per-user Undo. Profile-colored live strokes, selection leases and fading presence are included, along with conflict reconciliation, guest reconnect/recovery and image/PDF-object transfer. Rooms are experimental and need two-device acceptance testing. See [release details](docs/RELEASE_1.15.md) and [device checklist](docs/DEVICE_ACCEPTANCE_1.15.md).

## Build with GitHub Actions

Extract this ZIP directly into the repository root, including `.github`. Upload the extracted contents, not only the ZIP. Keep the existing secrets:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 of the production keystore |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Key alias |
| ANDROID_KEY_PASSWORD | Key password |

Push or manually run **Android APK**. After a successful signed build, download `VuraVision-1.15.0-<run>` containing the APK and SHA256SUMS.txt. Pull-request builds use debug signing. Application ID and production signing are unchanged.

Current checks are in [VERIFICATION_1.15.json](docs/VERIFICATION_1.15.json). Offline structural checks passed. Local Android compilation/tests/lint/APK are pending because Gradle cannot download in this environment. GitHub Actions retains all 244 unit tests, lint, signing and alignment checks; the manual workflow also runs emulator and web smoke tests.

For optional voice services see [voice setup](docs/VOICE_ASSISTANT.md); for search see [Google search](docs/GOOGLE_SEARCH.md). Earlier release and verification documents are historical.
