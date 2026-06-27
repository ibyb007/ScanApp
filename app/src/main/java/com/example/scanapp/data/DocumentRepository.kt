package com.example.scanapp.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bridges the Room database with on-disk page storage. Pages live under
 * filesDir/scans/<documentId>/page_<index>.jpg — private app storage, persistent
 * across app restarts (unlike cacheDir, which the OS can clear under pressure).
 */
class DocumentRepository(private val context: Context) {

    private val dao = ScanAppDatabase.getInstance(context).documentDao()
    private val scansRoot = File(context.filesDir, "scans")

    fun observeAllDocuments(): Flow<List<DocumentEntity>> = dao.observeAllDocuments()

    fun observeSearchResults(query: String): Flow<List<DocumentEntity>> =
        dao.observeSearchResults(query)

    suspend fun getFirstPagePath(documentId: Long): String? =
        dao.getFirstPage(documentId)?.filePath

    suspend fun getPageCount(documentId: Long): Int = dao.getPageCount(documentId)

    suspend fun getDocumentWithPages(documentId: Long): DocumentWithPages? =
        dao.getDocumentWithPages(documentId)

    suspend fun touchAccessed(documentId: Long) =
        dao.touchAccessedAt(documentId, System.currentTimeMillis())

    /**
     * Save freshly scanned page URIs (from ML Kit, which returns content:// or
     * file:// URIs into its own cache) as a new persistent Document. Each page
     * is re-encoded as a high-quality JPEG (quality 95 — this is the source
     * library copy, kept high quality; lossy compression for size limits only
     * happens later at export time via ExportEngine).
     */
    suspend fun saveNewDocument(pageUris: List<Uri>, title: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val documentId = dao.insertDocument(
            DocumentEntity(
                title = title,
                createdAtMillis = now,
                modifiedAtMillis = now,
                accessedAtMillis = now
            )
        )

        val docDir = File(scansRoot, documentId.toString()).apply { mkdirs() }
        val pageEntities = pageUris.mapIndexed { index, uri ->
            val destFile = File(docDir, "page_$index.jpg")
            copyUriToHighQualityJpeg(uri, destFile)
            DocumentPageEntity(
                documentId = documentId,
                pageIndex = index,
                filePath = destFile.absolutePath
            )
        }
        dao.insertPages(pageEntities)

        documentId
    }

    /** Re-encodes whatever the scanner gave us as a quality-95 JPEG on durable storage. */
    private fun copyUriToHighQualityJpeg(sourceUri: Uri, destFile: File) {
        val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Could not read scanned page: $sourceUri")

        destFile.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
        }
    }

    suspend fun renameDocument(documentId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId) ?: return@withContext
        dao.updateDocument(doc.copy(title = newTitle, modifiedAtMillis = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId) ?: return@withContext
        // Delete files first, then the DB row (cascade handles page rows automatically).
        File(scansRoot, documentId.toString()).deleteRecursively()
        dao.deleteDocument(doc)
    }

    suspend fun markModified(documentId: Long) = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId) ?: return@withContext
        dao.updateDocument(doc.copy(modifiedAtMillis = System.currentTimeMillis()))
    }
}
