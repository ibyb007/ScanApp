package com.example.scanapp.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.scanapp.data.ScanAppDatabase
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEngine {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val PREFS_NAME = "backup_rotation_prefs"
    private const val KEY_LAST_MSG_ID = "last_msg_id"

    /** Public subfolder name under Downloads where local backups are saved/visible. */
    private const val BACKUP_SUBDIR = "ScanApp"

    /**
     * Builds an encrypted (or plain, if password is null/blank) backup archive
     * in [context.cacheDir] and returns the scratch file. Callers that want a
     * private scratch copy (e.g. before uploading to Telegram) can use this
     * directly; [createLocalBackupInDownloads] wraps this and publishes the
     * result to the public Downloads/ScanApp folder.
     */
    private fun buildBackupArchive(context: Context, password: String?): File {
        val tempZip = File(context.cacheDir, "backup_tmp_${System.currentTimeMillis()}.zip")
        if (tempZip.exists()) tempZip.delete()

        // Room runs in WAL journal mode, so recently committed rows can still
        // live only in the "-wal" side file rather than in scanapp.db itself
        // until SQLite auto-checkpoints. Since we read scanapp.db directly off
        // disk below (not through Room), force a full checkpoint first —
        // otherwise a backup taken shortly after adding/editing documents can
        // zip up a stale/near-empty main db file even though the app "has"
        // the data, silently dropping it from the backup.
        try {
            val db = ScanAppDatabase.getInstance(context)
            db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
        } catch (_: Exception) {
            // Best-effort: if this fails we still proceed with whatever is on disk.
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
            val dbFile = context.getDatabasePath("scanapp.db")
            if (dbFile.exists()) {
                zos.putNextEntry(ZipEntry("scanapp.db"))
                dbFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            val scansDir = File(context.filesDir, "scans")
            if (scansDir.exists()) {
                scansDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(context.filesDir).path
                        zos.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }

        if (password.isNullOrEmpty()) {
            return tempZip
        }

        val encrypted = File(context.cacheDir, "backup_enc_${System.currentTimeMillis()}.enc")
        encryptFile(tempZip, encrypted, password)
        tempZip.delete()
        return encrypted
    }

    /** Used by the Telegram sync path, which needs a private file to upload from. */
    fun createBackup(context: Context, outputFile: File, password: String?) {
        val archive = buildBackupArchive(context, password)
        archive.copyTo(outputFile, overwrite = true)
        archive.delete()
    }

    /**
     * Creates a backup and saves it to the public Downloads/ScanApp folder, so
     * it survives app data clear / uninstall / factory reset — unlike the
     * app-private cache or files directories, which are wiped along with the
     * app. Returns a human-readable description of where it landed.
     */
    fun createLocalBackupInDownloads(context: Context, password: String?): String {
        val archive = buildBackupArchive(context, password)
        val displayName = "scanapp_backup_${System.currentTimeMillis()}.enc"
        val location = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, archive, displayName)
            } else {
                saveViaLegacyDirectFile(archive, displayName)
            }
        } finally {
            archive.delete()
        }
        return location
    }

    private fun saveViaMediaStore(context: Context, sourceFile: File, displayName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_SUBDIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Files.getContentUri("external")
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert failed for $displayName")

        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not open output stream for $displayName")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "Download/$BACKUP_SUBDIR/$displayName"
    }

    private fun saveViaLegacyDirectFile(sourceFile: File, displayName: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, BACKUP_SUBDIR).apply { mkdirs() }
        val outFile = File(targetDir, displayName)
        sourceFile.copyTo(outFile, overwrite = true)
        return outFile.absolutePath
    }

    /** Restore from an internal/private File (used by the Telegram round-trip path). */
    fun restoreBackup(context: Context, backupFile: File, password: String?) {
        FileInputStream(backupFile).use { input ->
            restoreBackup(context, input, password)
        }
    }

    /**
     * Restore from any readable Uri — in particular a SAF document picked by
     * the user from Downloads/ScanApp via ACTION_OPEN_DOCUMENT, since a plain
     * File path can't reliably reach MediaStore-written public files across
     * all OEMs/API levels.
     */
    fun restoreBackup(context: Context, sourceUri: Uri, password: String?) {
        val stream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Could not open backup file")
        stream.use { input ->
            restoreBackup(context, input, password)
        }
    }

    private fun restoreBackup(context: Context, input: InputStream, password: String?) {
        val rawCopy = File(context.cacheDir, "restore_raw_${System.currentTimeMillis()}")
        rawCopy.outputStream().use { out -> input.copyTo(out) }

        val tempZip = File(context.cacheDir, "restore_tmp_${System.currentTimeMillis()}.zip")
        try {
            if (!password.isNullOrEmpty()) {
                decryptFile(rawCopy, tempZip, password)
            } else {
                rawCopy.copyTo(tempZip, overwrite = true)
            }

            // Close the live Room connection before touching scanapp.db on disk.
            // Otherwise Room keeps its old connection/autoincrement bookkeeping
            // against the file we're about to overwrite, and rowids in the
            // restored data can collide with whatever Room still thinks is
            // "next" — which is what merged distinct restored documents (and
            // later, newly-saved scans) into a single document group.
            ScanAppDatabase.closeAndReset()

            File(context.filesDir, "scans").deleteRecursively()

            var entriesRestored = 0
            ZipInputStream(BufferedInputStream(FileInputStream(tempZip))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "scanapp.db") {
                        val dbTarget = context.getDatabasePath("scanapp.db")
                        dbTarget.parentFile?.mkdirs()
                        // Also clear Room's WAL/SHM side files if present, so a
                        // stale write-ahead log from the old database can't get
                        // replayed against the freshly restored file.
                        File(dbTarget.path + "-wal").delete()
                        File(dbTarget.path + "-shm").delete()
                        dbTarget.outputStream().use { zis.copyTo(it) }
                    } else {
                        val destFile = File(context.filesDir, entry.name)
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { zis.copyTo(it) }
                    }
                    entriesRestored++
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (entriesRestored == 0) {
                throw IOException(
                    "Backup archive was empty or unreadable — likely wrong passphrase, " +
                        "or this file isn't a ScanApp backup"
                )
            }
        } finally {
            rawCopy.delete()
            tempZip.delete()
        }
    }

    /** Reads exactly [size] bytes from [stream], throwing if the stream ends early. InputStream.read(buffer) is not guaranteed to fill the buffer in one call. */
    private fun readExactly(stream: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var totalRead = 0
        while (totalRead < size) {
            val read = stream.read(buffer, totalRead, size - totalRead)
            if (read == -1) {
                throw IOException("Backup file is truncated or not a valid ScanApp backup (expected $size header bytes, got $totalRead)")
            }
            totalRead += read
        }
        return buffer
    }

    private fun encryptFile(inputFile: File, outputFile: File, password: CharSequence) {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(16).also { random.nextBytes(it) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(password.toString().toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, ALGORITHM)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(salt)
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos -> inputFile.inputStream().use { it.copyTo(cos) } }
        }
    }

    private fun decryptFile(inputFile: File, outputFile: File, password: CharSequence) {
        FileInputStream(inputFile).use { fis ->
            val salt = readExactly(fis, 16)
            val iv = readExactly(fis, 16)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val spec = PBEKeySpec(password.toString().toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            }

            CipherInputStream(fis, cipher).use { cis -> outputFile.outputStream().use { cis.copyTo(it) } }
        }
    }

    /**
     * Uploads the file to the channel, extracts the new message ID, and deletes the older backup message if it exists.
     */
    fun uploadToTelegramAndRotate(context: Context, token: String, chatId: String, file: File) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastMsgId = prefs.getString(KEY_LAST_MSG_ID, null)

        val boundary = "===ScanAppBoundary==="
        val LINE_FEED = "\r\n"
        val url = URL("https://api.telegram.org/bot$token/sendDocument")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            useCaches = false
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            requestMethod = "POST"
        }

        connection.outputStream.use { output ->
            PrintWriter(OutputStreamWriter(output, "UTF-8"), true).use { writer ->
                writer.append("--$boundary").append(LINE_FEED)
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append(LINE_FEED)
                writer.append(LINE_FEED).append(chatId).append(LINE_FEED)

                writer.append("--$boundary").append(LINE_FEED)
                writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"").append(LINE_FEED)
                writer.append("Content-Type: application/octet-stream").append(LINE_FEED)
                writer.append(LINE_FEED).flush()

                file.inputStream().use { it.copyTo(output) }
                output.flush()

                writer.append(LINE_FEED)
                writer.append("--$boundary--").append(LINE_FEED).flush()
            }
        }

        val status = connection.responseCode
        if (status == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            
            // Basic manual JSON parsing of message_id to avoid heavy library overheads
            val msgIdMarker = "\"message_id\":"
            if (response.contains(msgIdMarker)) {
                val start = response.indexOf(msgIdMarker) + msgIdMarker.length
                var end = start
                while (end < response.length && (response[end].isDigit() || response[end] == ' ')) {
                    end++
                }
                val newMsgId = response.substring(start, end).trim()
                
                // Store the new message identifier node
                prefs.edit().putString(KEY_LAST_MSG_ID, newMsgId).apply()

                // Delete the old backup if it exists to maintain standard rotation limits
                if (!lastMsgId.isNullOrBlank()) {
                    try {
                        deleteTelegramMessage(token, chatId, lastMsgId)
                    } catch (e: Exception) {
                        // Log failure but don't break current deployment updates
                        e.printStackTrace()
                    }
                }
            }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw IOException("Telegram Server Error (HTTP $status): $error")
        }
    }

    private fun deleteTelegramMessage(token: String, chatId: String, messageId: String) {
        val url = URL("https://api.telegram.org/bot$token/deleteMessage?chat_id=$chatId&message_id=$messageId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Failed to clean up message node: ${connection.responseCode}")
        }
    }
}
