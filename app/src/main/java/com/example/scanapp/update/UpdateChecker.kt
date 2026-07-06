package com.example.scanapp.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Result of an update check against GitHub Releases. */
sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        // Direct download URL for the release's .apk asset, or null if this
        // release has no APK attached (e.g. source-only release) — auto-install
        // has nothing to fetch in that case and falls back to just opening
        // releaseUrl in the browser, same as a manual "View" tap would.
        val apkDownloadUrl: String?,
        // Up to 3 short changelog bullets extracted from the release notes,
        // for a compact "what's new" summary in the update dialog. Empty if
        // the release has no usable body text.
        val changelog: List<String> = emptyList()
    ) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Checks GitHub's Releases API for the latest published release tag and
 * compares it against the app's current versionName.
 *
 * Parses the response with org.json (built into Android — no extra
 * dependency to add) rather than hand-rolled regex scanning. An earlier
 * version scanned the raw JSON text directly to avoid a JSON library, but
 * that approach silently failed to find the .apk asset's
 * browser_download_url in real-world responses (it relied on the asset's
 * fields staying within a fixed-size text window, which is exactly the kind
 * of assumption a real parser doesn't need to make). A real parser is both
 * more reliable and barely more code.
 */
object UpdateChecker {

    private const val REPO_OWNER = "ibyb007"
    private const val REPO_NAME = "ScanApp"
    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RELEASES_PAGE_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext UpdateCheckResult.Error("No releases found (HTTP $responseCode)")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val release = try {
                JSONObject(body)
            } catch (e: Exception) {
                return@withContext UpdateCheckResult.Error("Couldn't read release info")
            }

            val latestTag = release.optString("tag_name").ifBlank { null }
                ?: return@withContext UpdateCheckResult.Error("Couldn't read release info")

            val latestVersion = latestTag.removePrefix("v").removePrefix("V")
            val current = currentVersionName.removePrefix("v").removePrefix("V")

            if (isNewerVersion(latestVersion, current)) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = latestTag,
                    releaseUrl = RELEASES_PAGE_URL,
                    apkDownloadUrl = extractApkAssetUrl(release),
                    changelog = parseChangelog(release.optString("body"))
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersionName)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Update check failed")
        }
    }

    /**
     * Finds the .apk asset's browser_download_url in the release's "assets"
     * array. Prefers a name containing "universal" (matches this project's
     * single-APK build, and mirrors how multi-ABI projects like Vega app
     * pick their APK), falling back to the first ".apk" asset found —
     * ScanApp's CI only ever uploads one, so that fallback is normally what
     * fires.
     */
    private fun extractApkAssetUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null

        var firstApkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue

            val url = asset.optString("browser_download_url").ifBlank { null } ?: continue
            if (firstApkUrl == null) firstApkUrl = url
            if (name.contains("universal", ignoreCase = true)) return url
        }
        return firstApkUrl
    }

    /**
     * Turns a GitHub release body into up to 3 short, compact changelog
     * bullets for the update dialog.
     *
     * GitHub's auto-generated notes look like:
     * ```
     * ## What's Changed
     * * Add dark mode toggle by @user in #14
     * * Fix crash on export by @user in #12
     *
     * **Full Changelog**: https://github.com/.../compare/v1...v2
     * ```
     * so this strips section headers, the trailing changelog-compare link
     * line, bullet markers, and the "by @user in #NN" attribution suffix
     * (noise for a short in-app summary), keeping just the change itself.
     */
    private fun parseChangelog(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        val attributionSuffix = Regex("""\s+by\s+@\S+\s+in\s+#?\d+.*$""", RegexOption.IGNORE_CASE)

        return body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") }
            .filterNot { it.contains("Full Changelog", ignoreCase = true) }
            .filterNot { it.startsWith("http", ignoreCase = true) }
            .map { line -> line.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim() }
            .map { line -> attributionSuffix.replace(line, "").trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .toList()
    }

    /** Compares dot-separated numeric version strings, e.g. "1.10.2" > "1.9.0". */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}
