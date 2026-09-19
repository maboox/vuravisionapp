# Build and CI

Use JDK 17, Gradle 8.11.1, Android platform 35 and build-tools 35.0.0. The workflow explicitly installs current SDK packages, overriding setup-android v3's obsolete `tools` default. Unit tests, release lint, release APK and instrumentation APK compile run on push. Manual workflow dispatch additionally runs emulator instrumentation tests.

Release APKs use an owner-provided keystore when the four documented secrets exist. Otherwise they use the runner's development key. Development keys can differ between runs; export lessons before uninstalling an incompatible previously signed build. Keep your production key private and stable.

The hosted workflow has not been executed from this workspace. Included verification is from the local equivalent Gradle tasks.

If a local incremental build fails in `compileReleaseArtProfile`, remove generated output with `./gradlew clean` and rebuild. The final package is verified with a clean build; no generated build caches are included.
