package com.example.scanapp.scan

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Temporarily exposes one of our private-storage page images to the public
 * Gallery/Photos app, specifically so ML Kit's GmsDocumentScanner gallery
 * import picker can see and select it.
 *
 * WHY THIS EXISTS: GmsDocumentScanner has no API to open an arbitrary existing
 * file directly — its only entry points are camera capture or the user
 * manually tapping its own gallery-import button and picking from the
 * system's Photos picker. Our pages live in private app storage (filesDir),
 * which the system Photos picker can't see at all. So "re-edit this page
 * with ML Kit's real Filters/Crop/Clean tools" requires: copy the page out
 * to a public, gallery-visible location first, then the user manually picks
 * it inside ML Kit's UI (one extra tap we can't eliminate — see the
 * googlesamples/mlkit#788 feature request, which is still open).
 *
 * The temp copy is tagged with a recognizable filename prefix so it can be
 * found and deleted again afterward — we don't want to permanently litter
 * the user's real Photos library with scan-editing scratch copies.
 */
object TempGalleryExport {

    private const val TEMP_PREFIX = "scanapp_edit_tmp_"

    /** Copies [sourceFile] into the public Pictures gallery; returns its MediaStore content Uri. */
    fun exportForPicking(context: Context, sourceFile: File): Uri {
        val displayName = "$TEMP_PREFIX${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, sourceFile, displayName)
        } else {
            exportViaLegacyDirectFile(sourceFile, displayName)
        }
    }

    /**
     * Deletes every temp copy this helper has ever created that's still
     * sitting in the gallery. Safe to call liberally (e.g. right after the
     * scanner returns, and also on app start) since it only ever matches our
     * own tagged prefix, never the user's real photos.
     */
    fun cleanupAllTempCopies(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("$TEMP_PREFIX%")

        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(collection, id.toString())
                resolver.delete(uri, null, null)
            }
        }
    }

    private fun exportViaMediaStore(context: Context, sourceFile: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert failed for $displayName")

        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not open output stream for $displayName")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    private fun exportViaLegacyDirectFile(sourceFile: File, displayName: String): Uri {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (!picturesDir.exists()) picturesDir.mkdirs()
        val destFile = File(picturesDir, displayName)
        sourceFile.copyTo(destFile, overwrite = true)
        return Uri.fromFile(destFile)
    }
}
