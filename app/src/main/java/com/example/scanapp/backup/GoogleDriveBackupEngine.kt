package com.example.scanapp.backup

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Backs up to / restores from the Google Drive "app data" folder — a
 * hidden, per-app storage area that never appears in the user's normal
 * Drive UI and that (per the drive.appdata OAuth scope requested in
 * MainActivity) only this app can ever read or write. There's exactly one
 * backup file in there at a time; each backup overwrites the previous one,
 * the same "one rolling backup" model BackupEngine already uses for local
 * and Telegram backups.
 *
 * Talks to the Drive v3 REST API directly with plain HttpURLConnection
 * calls — the same approach BackupEngine already uses for the Telegram Bot
 * API — rather than pulling in Google's much heavier
 * google-api-client / google-api-services-drive libraries (and their
 * Guava/Jackson/Gson/HttpClient transitive dependencies) for what amounts
 * to three simple HTTP calls.
 *
 * The caller (MainActivity) is responsible for obtaining a Drive
 * access token via AuthorizationClient before calling into this object;
 * this object just spends that token.
 */
object GoogleDriveBackupEngine {

    private const val BACKUP_FILENAME = "scanapp_backup.enc"
    private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

    /**
     * Builds a fresh encrypted (or plain, if [password] is null/blank)
     * backup via [BackupEngine.createBackup] and uploads it to the
     * appDataFolder, overwriting any previous backup file of the same name
     * so Drive doesn't accumulate an endless pile of old backups.
     */
    fun uploadBackup(context: Context, accessToken: String, password: String?) {
        val tempFile = File(context.cacheDir, "scanapp_gdrive_upload.enc")
        try {
            BackupEngine.createBackup(context, tempFile, password)

            val existingFileId = findBackupFileId(accessToken)
            val fileId = existingFileId ?: createBackupFileEntry(accessToken)
            uploadFileContent(accessToken, fileId, tempFile)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Downloads the backup file from the appDataFolder (if one exists) and
     * restores it in place via [BackupEngine.restoreBackup].
     */
    fun downloadAndRestoreBackup(context: Context, accessToken: String, password: String?) {
        val fileId = findBackupFileId(accessToken)
            ?: throw IOException("No backup found in your Google Drive account. Run a backup first.")

        val tempFile = File(context.cacheDir, "scanapp_gdrive_download.enc")
        try {
            val url = URL("$FILES_ENDPOINT/$fileId?alt=media")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
                readTimeout = 30000
            }

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Google Drive download failed (HTTP $status): ${readError(connection)}")
            }

            FileOutputStream(tempFile).use { output ->
                BufferedInputStream(connection.inputStream).use { input -> input.copyTo(output) }
            }

            BackupEngine.restoreBackup(context, tempFile, password)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Searches the appDataFolder for our backup file and returns its file
     * ID, or null if no backup has been uploaded yet.
     */
    private fun findBackupFileId(accessToken: String): String? {
        val q = URLEncoder.encode("name = '$BACKUP_FILENAME' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id)", "UTF-8")
        val url = URL("$FILES_ENDPOINT?spaces=appDataFolder&q=$q&fields=$fields")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 10000
            readTimeout = 10000
        }

        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            throw IOException("Google Drive lookup failed (HTTP $status): ${readError(connection)}")
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        // Manual JSON parsing to avoid pulling in a JSON library just for
        // this one field — same approach BackupEngine already uses to pull
        // message_id/file_id out of Telegram's responses.
        val filesStart = response.indexOf("\"files\":[")
        if (filesStart == -1) return null
        val idMarker = "\"id\":"
        val idStart = response.indexOf(idMarker, filesStart)
        if (idStart == -1) return null
        val valueStart = response.indexOf('"', idStart + idMarker.length) + 1
        val valueEnd = response.indexOf('"', valueStart)
        if (valueStart <= 0 || valueEnd <= valueStart) return null
        return response.substring(valueStart, valueEnd)
    }

    /** Creates an empty file entry named [BACKUP_FILENAME] inside the appDataFolder and returns its new file ID. */
    private fun createBackupFileEntry(accessToken: String): String {
        val connection = (URL(FILES_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 10000
            readTimeout = 10000
        }

        val body = """{"name":"$BACKUP_FILENAME","parents":["appDataFolder"]}"""
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            throw IOException("Could not create the backup file on Google Drive (HTTP $status): ${readError(connection)}")
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val idMarker = "\"id\":"
        val idStart = response.indexOf(idMarker)
        if (idStart == -1) throw IOException("Google Drive did not return a file ID for the new backup entry")
        val valueStart = response.indexOf('"', idStart + idMarker.length) + 1
        val valueEnd = response.indexOf('"', valueStart)
        return response.substring(valueStart, valueEnd)
    }

    /**
     * Uploads [file]'s raw bytes as the content of the Drive file
     * identified by [fileId]. Drive's REST API expects a PATCH here, but
     * java.net.HttpURLConnection hard-rejects "PATCH" as an unrecognized
     * method (a long-standing JDK limitation — see JDK-8207840), so this
     * sends a POST with an X-HTTP-Method-Override header instead, which
     * Google's APIs explicitly support as the standard workaround for
     * clients that can't issue PATCH directly.
     */
    private fun uploadFileContent(accessToken: String, fileId: String, file: File) {
        val url = URL("$UPLOAD_ENDPOINT/$fileId?uploadType=media")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Content-Type", "application/octet-stream")
            connectTimeout = 15000
            readTimeout = 30000
        }

        connection.outputStream.use { output ->
            file.inputStream().use { it.copyTo(output) }
        }

        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            throw IOException("Google Drive upload failed (HTTP $status): ${readError(connection)}")
        }
        connection.inputStream.close()
    }

    private fun readError(connection: HttpURLConnection): String {
        return connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no error body"
    }
}
