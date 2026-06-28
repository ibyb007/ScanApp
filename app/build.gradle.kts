plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// versionCode/versionName are derived from a build number instead of being
// hardcoded. Every previous build shipped versionCode=1, versionName="1.0" —
// identical on every release — which is why Android's package manager
// refused to install a new build over an old one (it looked like the *same*
// version, not an update) without uninstalling first.
//
// The CI workflow passes -PbuildNumber=<run_number> on every build, so each
// GitHub Actions run produces a strictly increasing versionCode and a
// distinct versionName (e.g. "1.0.47"). Local builds (no -P flag) fall back
// to buildNumber 1, so `./gradlew assembleDebug` on your machine still works
// without extra setup — it just won't be installable *over* a CI build with a
// higher versionCode (expected: it's a genuinely older/different version).
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.example.scanapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scanapp"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // NOTE: release builds are signed with the debug keystore for now, purely
    // so the APK attached to GitHub Releases is actually installable on a
    // device without extra setup. This is NOT secure for a real public
    // release — anyone with the AGP-default debug keystore (a well-known,
    // shared file) could sign updates claiming to be this app. Before
    // distributing this beyond testing, generate a real release keystore
    // and replace this signingConfig with one that reads its
    // path/passwords from GitHub Actions secrets instead.
    signingConfigs {
        getByName("debug") {
            // Uses AGP's default debug keystore — no changes needed here.
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    // ML Kit Document Scanner (the Google Drive-style scan UI)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // For coroutines (compression runs off the main thread)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Image loading for thumbnail previews (rememberAsyncImagePainter)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room (persistent scan library: titles, thumbnails, page counts, dates)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Drag-to-reorder for the document detail screen's page grid
    implementation("sh.calvin.reorderable:reorderable:3.0.0")
}
