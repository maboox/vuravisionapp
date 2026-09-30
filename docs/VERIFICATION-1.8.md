# VuraVision 1.8 — verification

Application ID: `com.vuravision.classroom.beta`. Version name: `1.8.0`; version code: `11`.

## Validation

The final source was checked with JDK 17, Gradle 8.11.1, Android platform 35 and build-tools 35.0.0. The Gradle distribution SHA-256 matched the checksum in the supplied wrapper configuration.

The release validation command is:

```
validateReleaseSigning testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest
```

Production signing settings were supplied using a **temporary local validation keystore**. This establishes that the release signing path builds correctly; it does not establish use of the owner's production certificate. The production APK must be generated in GitHub Actions with the owner's four repository secrets. No validation key or APK is included in the source ZIP.

The current suite has **101 JVM tests**. New coverage includes four starting corners and midpoint pivots, crossing the resize anchor, actual timed hold gestures, public palm settings and an actual palm area-erase input event, bilingual guide search/navigation, native rendering of every guide illustration, artwork lesson-file round trips, animation cancellation after undo, and entry into the studio from a split board. Existing tests remain in the suite; the hidden-guide test now checks the new guide heading.

Lint reports zero errors. Existing warnings, including legacy/deprecated Android APIs and untranslated/hardcoded catalogue content, remain; this is not a zero-warning certification.

The existing `npm test` HTML smoke suite checks **204 lab configurations and 49 games**, with zero reported failures or errors. This is jsdom/native-canvas validation, not a physical Android WebView test.

## Visual review

The guide's Persian and English workspace/eraser screenshots were captured from this version's native UI. Other figures use the board's own renderer. Native-rendered portraits and the Persian guide were visually inspected. Filled illustration regions are separated pen-hatching paths so disjoint areas are not bridged by extra strokes.

The two original artwork assets contain 124 and 139 editable ink paths respectively. Full output and attached assets survive the existing `.vura` format. Playback preserves prior pages and creates one undo step for the new page. Cancel leaves the partially drawn page; Undo removes it. Backgrounding pauses playback.

## Signing and workflow behavior

- Push/manual builds require the Base64 keystore and the three signing values.
- Production signing is validated before packaging; missing credentials stop release packaging.
- Pull-request tests receive no signing secrets and produce a clearly named debug APK.
- The workflow verifies signatures and APK alignment and generates a SHA-256 file.
- The package ID is unchanged so updates can replace existing installs signed by the same key. A previously debug-signed installation may require migration/export before reinstalling.

## Practical limits

No physical panel or emulator instrumentation was run locally. The instrumented test APK was compiled; the existing workflow can run device tests when dispatched manually. Palm recognition needs meaningful touch-major information from the panel controller and appropriate calibration. Palm eraser radius is configured, not automatically inferred from the entire contact patch. Physical touch latency and real simultaneous contacts still need a panel check.

Shape recognition is heuristic. Closed outlines resize around the first sample using an opposite virtual handle driven by movement from the held endpoint; square/circle aspect ratios remain constrained. The artwork is a stylized original illustration, not a raster reproduction or licensed anime character.

Core drawing and tutorials are offline. Optional Google handwriting models retain their separate download requirements. There is no licensing, activation, account or remote-revocation feature in this release.
