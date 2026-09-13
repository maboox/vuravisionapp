# Build versions

Pinned baseline for this repository:

- Android Gradle Plugin: **8.13.2**
- Gradle: **8.13**
- Kotlin: **2.3.21**
- JDK: **17**
- compileSdk / targetSdk: **36**
- minSdk: **26**
- Android Build Tools installed by CI: **36.0.0**

Rationale: API 36 meets the Google Play target requirement effective 31 August 2026. AGP 8.13.2 supports API 36.1 and Kotlin 2.3 while retaining the mature classic Kotlin Android plugin model. AGP 9.4 is current in September 2026 and uses Gradle 9.6 / JDK 17, but this first repository baseline deliberately stays on AGP 8.13.2 to reduce migration risk around AGP 9's built-in Kotlin/new DSL while the product core is being stabilized.

References:
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://developer.android.com/google/play/requirements/target-sdk
- https://kotlinlang.org/docs/releases.html
