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

    // Release builds are signed with a dedicated release key, not the AGP
    // debug keystore. The debug keystore is NOT a fixed file — AGP generates
    // a fresh one per machine the first time it's needed, and isn't checked
    // into the repo. On GitHub Actions specifically, every workflow run is a
    // brand-new, throwaway VM with no prior ~/.android/debug.keystore, so
    // AGP silently generated a *different* debug keystore on every single
    // run. Each Release APK on GitHub ended up signed with a different,
    // random key — which is exactly why Android refused to install a new
    // release over an old one without an uninstall first
    // (INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures don't match), no
    // matter what versionCode said.
    //
    // The fix: one real keystore, generated once, stored only as a GitHub
    // Actions secret (never committed — see .gitignore), and reused to sign
    // every release build forever. Credentials are read from Gradle
    // properties so this file has zero secrets in it. CI supplies them via
    // -P flags sourced from repo secrets (see .github/workflows/build.yml);
    // a local dev machine without those properties set falls back to the
    // debug keystore automatically, so `./gradlew assembleRelease` still
    // works out of the box for local testing — that local-signed APK just
    // won't install over a CI-signed one, which is correct: they're
    // legitimately different keys.
    val releaseStoreFile = project.findProperty("RELEASE_STORE_FILE") as String?
    val releaseStorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
    val releaseKeyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
    val releaseKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
    val hasRealReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        getByName("debug") {
            // Uses AGP's default debug keystore — no changes needed here.
        }
        if (hasRealReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = if (hasRealReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Local fallback only — see comment above. Never hit on CI
                // once the repo secrets are configured.
                signingConfigs.getByName("debug")
            }
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

    // Reads/writes image DPI (X/Y resolution) metadata for JPEG & PNG export
    implementation("androidx.exifinterface:exifinterface:1.3.7")

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
