# VuraVision 1.8.0

Native Kotlin classroom whiteboard. Version code **11**; application ID remains `com.vuravision.classroom.beta`. No licensing service, serial-number gate or account requirement.

## New in 1.8

- Palm erasing, calibration and touch diagnostics are accessible from the normal Eraser settings. Select Eraser, then re-tap it. Palm erasing remains opt-in; its switch persists. Reliable distinction requires contact width from the device controller. Eraser mode and radius also apply to palm contacts.
- Held-shape resizing scales around the actual first stroke sample, including right/bottom starts and edge starts. Closed outlines use a virtual opposite handle driven by displacement from the held endpoint, avoiding a size jump when recognition occurs. Squares and circles retain their aspect ratio.
- Offline illustrated bilingual guide: 24 chapters, search, in-guide Persian/English toggle, steps and practice tips. Accessible through Help and Settings → Help. Examples use the application's object renderer; workspace and eraser screenshots come from this version's automated rendering.
- Hidden advanced controls: hold recognition toggle, hold duration (500–2500 ms), stationary pen tolerance, ruler/compass snapping and palm rejection. Open the guide and tap its heading three times.
- Secret studio: original stylized Renaissance portrait with the Vura emblem and original anime character Aurora, drawn progressively using editable board ink. New page per artwork, pause/resume, playback speed, stop and one-step undo. A constellation is an additional surprise.
- Hidden `Powered by Maboox` credits and copyable `maboox@yahoo.com` email.
- Production APK builds fail if the production key is missing, rather than silently using a development key. Pull-request checks produce a clearly named debug APK.

## GitHub Actions

Extract the ZIP and commit its **contents at repository root**, including `.github`, `app`, Gradle files and wrapper. Do not upload the ZIP itself as the build project.

Set these repository Actions secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded private keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Push or run **Android APK** manually. Download `VuraVision-1.8-<run>` from successful run artifacts; it contains `VuraVision-1.8.apk` and `SHA256SUMS.txt`. Pull-request APKs are named `VuraVision-1.8-debug.apk` and are not customer releases. Reports are in a separate verification artifact. A manual workflow also runs the existing emulator instrumented tests.

Keep the keystore and credentials safely backed up; never commit them. Future in-place updates require the same application ID and signing key, plus a higher version code. Export lessons before uninstalling a development-signed installation that cannot be upgraded with your production key.

## Validation and scope

See `docs/VERIFICATION-1.8.md` for current validation and limitations. Historical documents report earlier builds only. Touch controller behavior and physical latency need testing on the actual panel. Core board and guide work offline; optional handwriting model downloads and network sharing have their own connectivity requirements.

Artwork source is in `scripts/generate-studio.py` (optional Python + Pillow). The shipped JSON assets require no generator at build time. Artwork is original stylized vector/pen illustration, not a reproduction of a photographed painting or an existing anime character. All drawing data remains ordinary editable `.vura` ink.
