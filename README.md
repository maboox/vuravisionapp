# VuraVision Classroom Suite — 1.0.0

Android 8+, English/Persian. Default screen is the whiteboard. Version code 3. The package ID remains `com.vuravision.classroom.beta` to preserve existing lesson storage; the displayed version is 1.0.0.

Features: cached multi-touch ink, separate pen/highlighter profiles, 24 vector shapes, page strip and thumbnail management, five floating classroom widgets, 32 games/challenges, 16 labs, English/Persian model management, offline basic shape recognition, and LAN QR/PDF sharing. Your supplied logo is included.

Tap the active pen/highlighter again for settings. Shapes opens directly from the toolbar. Page controls remain visible. Drag floating tools by their title and minimize with −. Settings → About: tap version text seven times, then reopen Settings → Engineering for raw-pixel touch calibration and thick-tip width. Some controllers do not report distinguishable contact sizes.

Push the contents inside `VuraVision-1.0/` to your repository root, including `.github/`, `gradlew`, `gradle/` and `app/`; pushing the ZIP alone does not build. A new commit triggers Actions. Download the APK artifact when the run succeeds.

Recognition requires one Google model download per language, then works offline. The app exposes status/retry/errors and Download Manager diagnostics. A blocked Google endpoint or unavailable Download Manager cannot be fixed by UI code alone. The QR link uses **http**, must use a reachable Wi-Fi/Ethernet address, and remains active for 30 minutes while the app stays open; router client isolation can prevent connections.

The APK is a release-mode build, using a development signing certificate unless you configure repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Use your own stable key for commercial distribution and compatible future updates. **Export lessons before uninstalling a previous app if its signing certificate differs.** No private signing key is included.

Local build: Java 17, Android SDK 35, then `./gradlew testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest`.

The version number is not a claim that every original-spec requirement has shipped. See `docs/CURRENT_SCOPE.md` and `docs/VERIFICATION.md` for limitations and actual verification.
