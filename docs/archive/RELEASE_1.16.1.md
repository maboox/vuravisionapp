# VuraVision 1.16.1

Fixes the Kotlin error reported by GitHub Actions in MainActivity.historySettings(): within Spinner.apply, `this` referred to the Spinner, while HistorySettings.load requires an Android Context. The call now explicitly uses `this@MainActivity`.

This patch retains the complete 1.16.0 feature set. Version code is 27; application ID, dependencies and production signing are unchanged. APK/workflow artifact names use 1.16.1.

See docs/VERIFICATION_1.16.1.json for validation status. Android compilation, unit tests, lint and APK generation must be confirmed by GitHub Actions; offline source checks are not a substitute for compilation.

ZIP را باز کنید و تمام محتویات، از جمله پوشهٔ `.github`، در ریشهٔ ریپوی گیت‌هاب جایگزین کنید. چهار secret امضای موجود را نگه دارید و workflow «Android APK» را اجرا کنید.
