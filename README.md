# ScanApp

A document scanner app (like Google Drive's scan feature) with customizable
PDF/image export that targets a specific file size.

This project is built entirely through **GitHub Actions** — no Android Studio
required. Every push to `main` triggers a build, and the resulting APK shows
up as a downloadable artifact.

## One-time setup

1. **Create the repo.**
   Go to github.com → New repository → name it `ScanApp` (or anything) →
   **Public or Private, either works** → create it empty (don't add a
   README/gitignore yet, to avoid merge conflicts with these files).

2. **Upload these files.**
   On the repo's main page, click **"Add file" → "Upload files"**, then drag
   in this entire folder structure (or upload the zip and extract — GitHub's
   web uploader accepts folders dragged from your file explorer, which
   preserves the paths like `app/src/main/java/...`).

   Alternatively, if you'd rather not drag a nested folder through the
   browser: use **"Add file" → "Create new file"** and type the path
   (e.g. `app/build.gradle.kts`) into the filename box — GitHub auto-creates
   the folders for you. This is more tedious but more reliable for deeply
   nested files than drag-and-drop.

3. **Commit.**
   Scroll down, write a commit message like "initial commit", click
   **"Commit changes directly to the main branch."**

4. **Watch it build.**
   Click the **"Actions"** tab at the top of the repo. You should see a
   workflow run start automatically (triggered by your push). Click into it
   to watch the build log live — this is genuinely useful for you since it's
   the same kind of log-reading you already do with your kernel CI builds.

5. **Download the APK.**
   Once the run finishes (green checkmark, usually 3-6 minutes for a first
   build since it has to download the Android SDK), scroll to the bottom of
   that run's page to **"Artifacts"** and download `scanapp-debug-apk`. It's
   a zip containing `app-debug.apk`.

6. **Install on your phone.**
   Transfer the APK to your device (or download directly on-device if you
   open the Actions page in your phone's browser while logged into GitHub).
   You'll need **"Install from unknown sources"** enabled for whichever app
   you use to open it — standard sideloading, same as installing Magisk
   modules outside the Play Store.

## Making changes

Since there's no live preview here (that's the real cost of skipping Android
Studio), the loop is:

1. Tell me what to change.
2. I give you updated file contents.
3. You open that file on GitHub (click it → pencil/edit icon), paste the new
   content, commit directly to `main`.
4. Actions tab → wait for the new run → download the new APK.

If a build fails, click into the failed run and the red ❌ step will show you
the error log — paste that back to me and I'll fix it from there.

## Project structure

```
ScanApp/
├── build.gradle.kts              # root build config (plugin versions)
├── settings.gradle.kts           # declares the :app module
├── gradle.properties             # Gradle/AndroidX settings
├── .github/workflows/build.yml   # the CI pipeline — builds + uploads APK
└── app/
    ├── build.gradle.kts          # app-level dependencies (ML Kit, Compose, Coil)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/xml/file_paths.xml
        └── java/com/example/scanapp/
            ├── MainActivity.kt           # wires everything together
            ├── scan/DocumentScannerLauncher.kt   # launches ML Kit's scan UI
            ├── export/ExportEngine.kt            # compression-to-target-size logic
            └── ui/ScanScreen.kt                  # Compose UI (format/size controls)
```

## What this app does

- **Scan**: Tapping "Scan Document" launches Google's own ML Kit document
  scanner UI — auto edge detection, crop adjustment, multi-page capture,
  or import from gallery.
- **Customize export**: Choose PDF, JPEG, or PNG output, then either set a
  target file size (the app reduces quality and, if needed, resolution to
  hit it) or just pick a quality percentage directly.
- **Export**: Saved to the app's internal storage under `files/exports/`.

## Known limitations / things to test on a real device

- No "Share" button yet (export saves locally; sharing via
  WhatsApp/Drive/email intent isn't wired up — ask if you want this added).
- PDF export splits the target size evenly across pages, which is a
  reasonable default but not optimal if pages vary a lot in visual
  complexity.
- If a target size is set so low that even minimum quality + downscaling
  can't reach it, the app currently just returns its closest attempt rather
  than showing an explicit "couldn't reach target" warning.
