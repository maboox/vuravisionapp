# GitHub Actions setup fix

The supplied run failed before compilation with:

```
Warning: Failed to find package 'tools'
Error: The process '.../sdkmanager' failed with exit code 1
```

`android-actions/setup-android@v3` defaults its `packages` input to `tools platform-tools`. The legacy `tools` package is unavailable in the SDK repository used by that run. The Node 20/24 message was a warning, not this failure's cause.

Both the APK build job and the manual device-test job now explicitly request:

```yaml
- uses: android-actions/setup-android@v3
  with:
    packages: 'platform-tools platforms;android-35 build-tools;35.0.0'
```

The redundant separate SDK-install step was removed. App source, Gradle versions, signing, tests, and the included APK are unchanged.

## Apply to an existing repository

Replace `.github/workflows/android.yml` with the file in this ZIP and commit/push it. Open the new Actions run created by that commit. Re-running the old failed run uses its old workflow and will not apply the fix.

Validation: the workflow parses; both jobs explicitly override the obsolete default. The input and splitting behavior were checked against the upstream v3 action definition/source. This correction has not been executed in your GitHub account.

Primary references: [action definition](https://github.com/android-actions/setup-android/blob/v3/action.yml), [action implementation](https://github.com/android-actions/setup-android/blob/v3/src/main.ts).
