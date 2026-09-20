# Version 1.2 verification

## Supplied GitHub build log and menu-test correction

The user's `Pasted markdown(3).md` reports successful debug/release Kotlin compilation and 67 completed unit tests, with one failure: `UpgradeTest.compactPersianScreenKeepsMenuReachable`, a `NoSuchElementException`.

Source review found that this test searched for button text `☰`, while `Design.kt` now replaces that text with a Feather vector drawable. `MainActivity.kt` explicitly supplies the localized `tools` content description. The test now finds the menu by that accessible name, asserts exactly one match and retains the compact-screen visibility/right-boundary checks. Activity cleanup now runs in `finally`.

The test has not been disabled or skipped. This patch has been checked against the source, but has not been rerun in CI. The supplied log does not establish successful release lint, APK assembly or signing verification. The local compiler limitation described below still applies.

Source reconstruction in progress. No version 1.2 APK has been built. Historical 1.1 APK and test reports have been excluded. Older screenshots are historical references only.

## Build attempt — 2026-09-20

Android resources and manifests processed successfully. `:app:compileDebugKotlin` failed before source compilation: the Java runtime does not provide `JAVA_COMPILER`. Installing the JDK through the system package manager was denied (`setgroups`/`seteuid`). No permission workaround was attempted.

Unit tests, lint, APK assembly, signing verification and device checks have **not** completed. This source is not compile-verified.
