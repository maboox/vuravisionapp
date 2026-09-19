plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.vuravision.classroom"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vuravision.classroom.beta"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val signingPath = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (!signingPath.isNullOrBlank()) create("production") {
            storeFile = file(signingPath)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if(!signingPath.isNullOrBlank())"production" else "debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
dependencies {
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("net.objecthunter:exp4j:0.4.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
