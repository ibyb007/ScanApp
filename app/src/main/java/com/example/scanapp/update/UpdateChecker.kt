package com.example.scanapp.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Result of an update check against GitHub Releases. */
sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class UpdateAvailable(val currentVersion: String, val latestVersion: String, val releaseUrl: String) :
        UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Checks GitHub's Releases API for the latest published release tag and
 * compares it against the app's current versionName.
 *
 * Uses plain HttpURLConnection + a tiny hand-rolled extraction of "tag_name"
 * rather than pulling in a JSON library or networking dependency for a single
 * field — GitHub's response is predictable enough that a direct string scan
 * is reliable and keeps this dependency-free.
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

            val latestTag = extractJsonStringField(body, "tag_name")
                ?: return@withContext UpdateCheckResult.Error("Couldn't read release info")

            val latestVersion = latestTag.removePrefix("v").removePrefix("V")
            val current = currentVersionName.removePrefix("v").removePrefix("V")

            if (isNewerVersion(latestVersion, current)) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = latestTag,
                    releaseUrl = RELEASES_PAGE_URL
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersionName)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Update check failed")
        }
    }

    /** Minimal extraction of a top-level string field from a JSON object, without a JSON library. */
    private fun extractJsonStringField(json: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.getOrNull(1)
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
