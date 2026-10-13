# VuraVision 1.14.1

Version code 24. Build verification repair for 1.14.0; the application ID,
signing secrets, document format and all 13 whiteboard changes are preserved.
Local collaboration/casting is not included in this patch.

The supplied GitHub Actions log shows successful debug/release Kotlin and Java
compilation, followed by 213 Robolectric tests with 210 passing and three failing.
Those failures stopped `testDebugUnitTest`; the deprecation and native-library
strip messages were warnings, not the build failure.

1. The menu Back test now drains the main message queue after clicking the
   AlertDialog button, then checks that the old dialog closed and the parent
   menu actually reopened before selecting another entry. Menu rows also report
   a useful assertion if the requested item is absent.
2. The lab/game footer test uses matching 1400-by-900 mdpi panel qualifiers and
   measures the application's content in those dimensions. It asserts that the
   action footer reaches the padded bottom, that Close and Add to board are in
   that footer, and that their visible bounds fit in the bottom third.
3. The older settings test now expects Pie menu and Selection settings in normal
   Settings, consistent with 1.14. It still verifies the single guide location,
   normal calibration access and the voice assistant's hidden/off defaults.

No tests were disabled or excluded, and the build still runs unit tests, lint,
APK assembly and APK signature/alignment checks. The suite remains 213 tests.

Local source/resource checks can run in the delivery environment. A fresh Android
test/build run remains pending GitHub Actions because downloading the Gradle
distribution here fails with `Network is unreachable`. Do not interpret the
earlier 210 passing cases as a completed pass for this patch. See
`VERIFICATION_1.14.1.json` for the evidence and current limits.
