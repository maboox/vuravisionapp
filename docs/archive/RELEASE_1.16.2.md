# VuraVision 1.16.2

## Reported failure and fix

The supplied GitHub Actions log for 1.16.1 confirms successful Debug/Release application and test compilation. Of 278 unit tests, 275 passed and three failed:

- `Studio116Test.constructionAndItsLabelUsePanelPenAndUndoTogether`: constructions incorrectly used the global pen color/width on split canvases. They now use the pen settings of the guide's panel. The same correction covers compass arcs and protractor rays, including previews. Solo mode still uses the current global pen/highlighter.
- `StudioGestureTest.compassTurnDrawsOneArcAndUndoRestoresGuide` and `rotatedCompassKeepsStartHandleUnderTheFingerAndDrawsFromThere`: old assertions expected only guide + arc and selected the last item as the arc. The requested permanent measurement text is a third item. Tests now identify the ink by kind, verify the arc geometry and measurement text, and check both disappear/reappear with one Undo/Redo. The permanent label remains enabled.

The construction regression now covers both panels and parallel/perpendicular lines. Two additional regressions cover compass/protractor pen settings on both panels and restoring solo pen/highlighter behavior. All 280 tests remain enabled; no failures are suppressed or skipped.

## Build and validation

Version code is 28. Application ID, dependencies, Gradle toolchain, production signing secrets and build/test/lint requirements are unchanged. APK/workflow artifact names use 1.16.2. Prior release reports remain historical.

Offline structural checks passed. A local Gradle build/test/lint attempt was blocked before compilation by network access to the Gradle distribution. This patch therefore still needs GitHub Actions compilation and execution of the 280 tests; the prior log does not validate the modified source. See docs/VERIFICATION_1.16.2.json.

ZIP را باز کنید و همهٔ محتویات، از جمله `.github`، در ریشهٔ ریپو جایگزین کنید. secretهای امضای قبلی را حفظ کنید و workflow «Android APK» را اجرا کنید. روی نمایشگر، رنگ و ضخامت متفاوت برای دو بخش انتخاب کنید و خط‌کش، پرگار و نقاله را در هر بخش امتحان کنید؛ رسم و متن اندازه باید با یک Undo حذف و با Redo برگردند.
