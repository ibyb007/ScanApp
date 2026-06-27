package com.example.scanapp.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Saves a byte array to the public Documents folder so it shows up in any file
 * manager / "Files" app, instead of the app's private internal storage (which
 * is invisible without root or a special "show hidden" file manager mode).
 *
 * - API 29+ (Android 10+): uses MediaStore.Files with RELATIVE_PATH = Documents.
 *   This is the scoped-storage-compliant way to land a file in the public
 *   Documents directory without needing WRITE_EXTERNAL_STORAGE.
 * - API 24-28: scoped storage doesn't apply yet, so we write directly to
 *   Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).
 *   This path does require WRITE_EXTERNAL_STORAGE on these versions.
 */
object PublicDocumentSaver {

    /** Returns a human-readable path/description of where the file landed. */
    fun saveToDocuments(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bytes, displayName, mimeType)
        } else {
            saveViaLegacyDirectFile(bytes, displayName)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Files.getContentUri("external")
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert failed for $displayName")

        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Could not open output stream for $displayName")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "Documents/$displayName"
    }

    private fun saveViaLegacyDirectFile(bytes: ByteArray, displayName: String): String {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!docsDir.exists()) docsDir.mkdirs()
        val outFile = File(docsDir, displayName)
        outFile.writeBytes(bytes)
        return outFile.absolutePath
    }
}
