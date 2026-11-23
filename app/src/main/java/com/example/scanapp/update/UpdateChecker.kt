package com.example.scanapp.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
 * Checks GitHub's Releases API for the newest release the selected
 * [UpdateChannel] may offer and compares it against the app's versionName.
 *
 * Releases are tagged by the build workflow (.github/workflows/build.yml):
 *   stable : normal Release,  tag v1.3.5            shown as "1.3.5"
 *   canary : Pre-release,     tag v1.3.5-canary.2   shown as "1.3.5 (2)"
 * Stable only ever sees stable releases; Canary sees both and offers the
 * highest version of either kind (see [VersionUtils] for the ordering).
 *
 * Parses the response with org.json (built into Android — no extra
 * dependency) rather than hand-rolled regex scanning.
 */
object UpdateChecker {

    private const val REPO_OWNER = "ibyb007"
    private const val REPO_NAME = "ScanApp"
    private const val RELEASES_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=30"
    private const val RELEASES_PAGE_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    suspend fun checkForUpdate(
        currentVersionName: String,
        channel: UpdateChannel = UpdateChannel.STABLE
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
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

            val releases = try {
                JSONArray(body)
            } catch (e: Exception) {
                return@withContext UpdateCheckResult.Error("Couldn't read release info")
            }

            val newest = pickNewestRelease(releases, channel)
                ?: return@withContext UpdateCheckResult.UpToDate(VersionUtils.display(currentVersionName))

            val latestTag = newest.optString("tag_name")
            if (VersionUtils.isRemoteNewer(latestTag, currentVersionName)) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = VersionUtils.display(currentVersionName),
                    latestVersion = VersionUtils.display(latestTag),
                    releaseUrl = newest.optString("html_url").ifBlank { RELEASES_PAGE_URL },
                    apkDownloadUrl = extractApkAssetUrl(newest),
                    changelog = parseChangelog(newest.optString("body"))
                )
            } else {
                UpdateCheckResult.UpToDate(VersionUtils.display(currentVersionName))
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Update check failed")
        }
    }

    /**
     * Highest-version release the [channel] may offer. Drafts and tags that
     * aren't versions are ignored; Stable additionally skips pre-releases
     * and canary tags. Releases without an APK are skipped too, so the
     * updater never offers something it can't install.
     */
    private fun pickNewestRelease(releases: JSONArray, channel: UpdateChannel): JSONObject? {
        var best: JSONObject? = null
        var bestVersion: AppVersion? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            val version = VersionUtils.parse(release.optString("tag_name")) ?: continue
            if (channel == UpdateChannel.STABLE && (release.optBoolean("prerelease") || version.isCanary)) continue
            if (extractApkAssetUrl(release) == null) continue
            if (bestVersion == null || version > bestVersion) {
                best = release
                bestVersion = version
            }
        }
        return best
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
        // org.json's optString() returns the literal string "null" (not an
        // empty string) when the JSON value is a real JSON null — which is
        // exactly what GitHub sends for "body" when a release has no
        // description. Without this check, releases with no notes at all
        // would show a single bogus "null" line instead of nothing.
        if (body.isNullOrBlank() || body == "null") return emptyList()

        // GitHub's auto-generated notes attribute each line like:
        //   "* Add dark mode by @user in https://github.com/owner/repo/pull/14"
        // (a full PR URL, not "#14" — an earlier version of this regex
        // assumed a bare issue number and so left this suffix in place).
        val attributionSuffix = Regex(
            """\s+by\s+@\S+\s+in\s+\S+\s*$""",
            RegexOption.IGNORE_CASE
        )

        return body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") }
            // Canary releases open with a "> [!IMPORTANT] ... testing build" warning blockquote.
            .filterNot { it.startsWith(">") }
            .filterNot { it.contains("Full Changelog", ignoreCase = true) }
            .filterNot { it.startsWith("http", ignoreCase = true) }
            .map { line -> line.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim() }
            .map { line -> attributionSuffix.replace(line, "").trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .toList()
    }
}
