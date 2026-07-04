package com.example.scanapp.update

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of downloading the update APK. */
sealed class ApkDownloadResult {
    data class Success(val apkUri: Uri) : ApkDownloadResult()
    data class Error(val message: String) : ApkDownloadResult()
}

/**
 * Downloads the release APK named by [UpdateChecker]'s
 * UpdateAvailable.apkDownloadUrl into the app's cache directory, then hands
 * back a content:// URI via the app's existing FileProvider — the same
 * provider already configured for sharing exported documents — so the
 * caller can launch Android's package installer on it.
 *
 * This is the realistic ceiling for "auto install" on a normal,
 * non-system, non-device-owner app: Android requires the
 * REQUEST_INSTALL_PACKAGES permission and still shows its own install
 * confirmation screen for ACTION_INSTALL_PACKAGE / PackageInstaller
 * regardless of that permission — there's no API available to a regular
 * sideloaded app that skips the user's final tap-to-install. What this
 * *does* remove is everything before that tap: manually opening a browser,
 * finding the release, downloading it, then digging it out of Downloads.
 */
object UpdateApkDownloader {

    private const val APK_CACHE_FILENAME = "update.apk"

    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext ApkDownloadResult.Error("Download failed (HTTP $responseCode)")
            }

            // -1 when the server doesn't send Content-Length; the caller
            // treats that as "unknown total" and shows an indeterminate
            // progress bar instead of a percentage.
            val totalBytes = connection.contentLengthLong

            // updateApkDir: a dedicated subfolder of cache, matching the
            // pattern already used for share_scratch in file_paths.xml,
            // rather than dropping update.apk directly into the root cache
            // dir — keeps it easy to grant exactly this subtree via
            // FileProvider without exposing the rest of the cache.
            val updateApkDir = File(context.cacheDir, "update_apk").apply { mkdirs() }
            val apkFile = File(updateApkDir, APK_CACHE_FILENAME)

            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied = 0L
                    // Throttled to ~10 updates/sec rather than firing
                    // onProgress on every 8KB chunk — that would trigger far
                    // more Compose recompositions than a progress bar needs
                    // and adds needless overhead on a fast connection.
                    var lastReportAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportAt >= 100) {
                            onProgress(bytesCopied, totalBytes)
                            lastReportAt = now
                        }
                    }
                    // Final call to guarantee the last chunk (and 100%, when
                    // totalBytes is known) actually reaches the UI even if it
                    // landed inside the last throttle window.
                    onProgress(bytesCopied, totalBytes)
                }
            }
            connection.disconnect()

            val apkUri = FileProvider.getUriForFile(
                context,
                "com.example.scanapp.fileprovider",
                apkFile
            )
            ApkDownloadResult.Success(apkUri)
        } catch (e: Exception) {
            ApkDownloadResult.Error(e.message ?: "Download failed")
        }
    }
}
