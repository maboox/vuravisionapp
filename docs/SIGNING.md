# Release signing

GitHub Actions builds a signed release APK only when all signing secrets are configured.

Required repository secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Create a keystore (example):
```bash
keytool -genkeypair -v -keystore vuravision-release.jks -alias vuravision -keyalg RSA -keysize 4096 -validity 10000
```

Base64 on Linux/macOS:
```bash
base64 < vuravision-release.jks | tr -d '\n'
```

PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("vuravision-release.jks"))
```

Add the resulting value and passwords in GitHub → Settings → Secrets and variables → Actions. Trigger **Android CI** manually or push to `main`/`master`. The workflow decodes the keystore only into `$RUNNER_TEMP`, builds `VuraVision-release.apk`, locates `apksigner` under `$ANDROID_HOME/build-tools`, verifies the signature, then uploads the APK artifact.
