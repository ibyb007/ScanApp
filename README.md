# ScanApp

A document scanner app (like Google Drive's scan feature) with customizable
PDF/image export, a photo collage/layout tool, and three different ways to
back up (and restore) your scans: an encrypted local file, Telegram, and
Google Drive.

<!-- Add a screenshot or two here — see "Adding screenshots to this README" below. -->

This project is built entirely through **GitHub Actions** — no Android Studio
required. Every push to `main` triggers a build, and the resulting APK shows
up as a downloadable artifact. The app also self-updates: it checks GitHub
Releases and can download/install new versions on its own.

## What this app does

- **Scan**: Tapping "Scan Document" launches Google's own ML Kit document
  scanner UI — auto edge detection, crop adjustment, multi-page capture,
  or import from gallery/PDF.
- **Library**: Scans are saved as documents (title, thumbnail, page count,
  date) in a local Room database, with drag-to-reorder pages inside a
  document's detail view.
- **Customize export**: Choose PDF, JPEG, or PNG output, then either set a
  target file size (the app reduces quality and, if needed, resolution to
  hit it) or just pick a quality percentage directly.
- **Collage**: Arrange multiple scanned pages/photos into a single laid-out
  page (A4, A5, Letter, Legal, or Square, portrait or landscape) for export.
- **Self-update**: Checks GitHub Releases for a newer `versionName` and can
  download + install the new APK directly from within the app.
- **Backup & restore** (see below): encrypted local file, Telegram, and
  Google Drive — pick whichever you're comfortable with, or use more than
  one.

## Backup & restore

All three backup methods package the same thing — the scan database and all
saved page images — into a single archive. If you set a passphrase, the
archive is encrypted (AES-256, PBKDF2-derived key, random salt/IV per
backup) before it ever leaves the device; without a passphrase it's saved as
a plain zip. **If you encrypt a backup, remember the passphrase** — it isn't
stored anywhere, and there's no way to recover the backup without it.

### Encrypted local backup

- **Backup**: Saves the archive to `Download/ScanApp/` on your device, so it
  survives clearing app data, uninstalling, or a factory reset (unlike the
  app's private storage, which does not).
- **Restore**: Pick a previously saved backup file from `Download/ScanApp/`
  (or anywhere else you've moved it) via the file picker.
- Use this if you want a backup that never touches the network at all —
  copy it to a PC, another phone, or cloud storage manually, on your own
  terms.

### Telegram backup

- Enter a Telegram bot token and a chat/channel ID once in the Backup
  screen (or Settings) and save them.
- **Backup**: Uploads the (optionally encrypted) archive to that chat as a
  document. Only the latest backup is kept — uploading a new one deletes
  the previous backup message automatically, so your chat doesn't fill up
  with old versions.
- Backups larger than Telegram's ~20MB Bot API download limit are
  automatically split into numbered parts on upload and reassembled on
  restore — you don't need to do anything differently for large libraries.
- **Restore**: Downloads the most recent backup from that chat and restores
  it in place.
- Your bot token and chat ID can also be exported to their own small
  encrypted file (independent of a full data backup) — handy for moving
  them to a new phone without having to look up the token again.

### Google Drive backup

- **Backup**: Signs you into Google and uploads the (optionally encrypted)
  archive to your Drive's hidden **app data** folder — the same private,
  per-app storage area Drive itself uses, which never shows up in your
  normal Drive file list and nothing else can read. Only one backup file is
  kept; each backup overwrites the last.
- **Restore**: Downloads that file from your Drive app data folder and
  restores it in place.
- Use this if you want backups to happen without keeping track of a bot
  token, and you're fine with them living in your Google account.

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

7. **(Optional) Set up Telegram or Google Drive backup.**
   - Telegram: create a bot via [@BotFather](https://t.me/BotFather) to get
     a bot token, and get the chat/channel ID you want backups sent to.
     Enter both in the app's Backup screen.
   - Google Drive: sign in from the app's Backup screen when prompted. No
     extra setup on your end beyond the Google sign-in flow itself.

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
    ├── build.gradle.kts          # app-level dependencies (ML Kit, Compose, Coil, Room)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/xml/file_paths.xml
        └── java/com/example/scanapp/
            ├── MainActivity.kt                     # wires everything together
            ├── scan/DocumentScannerLauncher.kt      # launches ML Kit's scan UI
            ├── scan/PdfImporter.kt                  # importing existing PDFs as pages
            ├── scan/TempGalleryExport.kt            # re-opening a saved page in ML Kit's editor
            ├── export/ExportEngine.kt               # compression-to-target-size logic
            ├── export/JpegPdfWriter.kt              # PDF assembly from page images
            ├── export/PublicDocumentSaver.kt        # saving exports to public storage
            ├── collage/CollageEngine.kt             # multi-page layout/collage rendering
            ├── edit/BitmapEditOps.kt                # rotate/filter/enhance bitmap utilities
            ├── data/                                # Room database, DAO, repository
            ├── backup/BackupEngine.kt                # local + Telegram backup/restore, encryption
            ├── backup/GoogleDriveBackupEngine.kt     # Google Drive app-data backup/restore
            ├── update/                               # GitHub Releases self-update checker/installer
            └── ui/
                ├── HomeScreen.kt
                ├── ScanScreen.kt                     # format/size export controls
                ├── CollageScreen.kt                  # collage layout UI
                ├── DocumentDetailScreen.kt           # page grid, reorder, per-page actions
                ├── BackupScreen.kt                   # local/Telegram/Google Drive backup UI
                └── SettingsScreen.kt
```

## Known limitations / things to test on a real device

- No "Share" button yet for exported files (a `FileProvider` is already
  wired up for this, but nothing calls it yet — ask if you want this added).
- PDF export splits the target size evenly across pages, which is a
  reasonable default but not optimal if pages vary a lot in visual
  complexity.
- If a target size is set so low that even minimum quality + downscaling
  can't reach it, the app currently just returns its closest attempt rather
  than showing an explicit "couldn't reach target" warning.
- Each backup method (local/Telegram/Google Drive) keeps only its **most
  recent** backup — there's no version history, so restoring always gets you
  the last backup you made with that method.

## Adding screenshots to this README

The simplest way, no git command line needed:

1. Open this `README.md` file on GitHub and click the pencil (✏️) icon to
   edit it.
2. Drag your screenshot image directly into the edit box (or click inside
   the text box and use **Paste**, or use the small image icon in the
   toolbar above the box to browse for a file).
3. GitHub uploads the image and automatically inserts a line like:
   ```markdown
   ![image](https://github.com/user-attachments/assets/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
   ```
   at your cursor position. Move that line wherever you want the image to
   show up in the file.
4. Commit the change directly to `main`, same as any other edit.

A couple of tips:
- You can drag in multiple images at once; each gets its own auto-generated
  line.
- To control the display size (GitHub's auto-inserted `![image](...)` shows
  it full width), you can instead write raw HTML:
  ```html
  <img src="https://github.com/user-attachments/assets/xxxx..." width="300">
  ```
- If you'd rather commit the actual image file into the repo (e.g. into a
  new `screenshots/` folder) instead of relying on GitHub's attachment CDN,
  upload it via **"Add file" → "Upload files"** like any other file, then
  reference it with a relative path: `![Home screen](screenshots/home.png)`.
  This keeps the image tied to the repo itself rather than a separate
  attachments URL.
