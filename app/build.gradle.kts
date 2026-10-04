plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.vuravision.classroom"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vuravision.classroom.beta"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.10.2"
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
            if (!signingPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("production")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE") }
}
dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
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

// Keep assertion messages in Actions so a failing layout reports its geometry.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// Customer releases must never silently use the development certificate.
val validateReleaseSigning by tasks.registering {
    doLast {
        val required = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
        val missing = required.filter { System.getenv(it).isNullOrBlank() }
        if (missing.isNotEmpty()) throw GradleException("Production signing is required. Missing environment settings: ${missing.joinToString()}")
        if (!file(System.getenv("ANDROID_KEYSTORE_PATH")).isFile) throw GradleException("Production keystore file does not exist")
    }
}
tasks.matching { it.name in listOf("packageRelease", "assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(validateReleaseSigning)
}
