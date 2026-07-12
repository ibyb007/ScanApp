plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// versionCode and versionName now do two different jobs, on purpose:
//
// versionCode is the plain, ever-climbing integer Android actually uses to
// decide "is this build newer than the installed one" — it's invisible to
// users, so it's fine (expected, even) for it to be a big ugly number. The
// CI workflow passes -PbuildNumber=<run_number> on every build, giving a
// strictly increasing versionCode automatically. Local builds (no -P flag)
// fall back to buildNumber 1.
//
// versionName is the human-facing string (Play Store, Settings, the update
// dialog). It used to be derived from the same buildNumber ("1.0.$buildNumber"),
// which meant it climbed by one on every single CI run instead of only on
// releases that actually warranted a new version — hence versionName drifting
// into things like "1.0.101".
//
// It's set below as a plain literal, but you normally won't touch this by
// hand: the release workflow's "Bump app version" step auto-increments it
// on every release dispatch (PATCH 0→9, then MINOR bumps and PATCH resets:
// 1.1.0 → 1.1.9 → 1.2.0 → …) and commits the new value back to this file
// before building. Edit it here yourself only for a MAJOR bump, or to
// correct the sequence. UpdateChecker compares the release tag numerically
// against BuildConfig.VERSION_NAME to detect updates, and the release
// workflow tags each release from this exact value — see
// .github/workflows/build.yml.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
val appVersionName = "1.2.9"

android {
    namespace = "com.example.scanapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scanapp"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNumber
        versionName = appVersionName
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

    // Google Identity Services' AuthorizationClient — used to obtain a
    // short-lived Drive access token for the Google Drive backup/restore
    // feature (see MainActivity's withGoogleDriveAuthorization and
    // backup/GoogleDriveBackupEngine.kt). Deliberately NOT pulling in
    // google-api-client / google-api-services-drive: those add a large
    // transitive dependency tree (Guava, Jackson, Gson, Apache HttpClient)
    // for what this app only needs three raw REST calls to do.
    implementation("com.google.android.gms:play-services-auth:21.6.0")

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
