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

    /**
     * Single entry point for the Home/Files list: combines search + sort.
     * Page-count sort isn't expressible as a simple Room query without a join
     * against document_pages, and the document list is small, so it's handled
     * here by sorting NAME/DATE_MODIFIED results that ties don't actually need —
     * NAME/DATE go straight to SQL for correct collation/locale behavior, and the
     * caller (ViewModel/Activity) annotates page counts and re-sorts only when
     * PAGE_COUNT is selected.
     */
    fun observeDocuments(
        query: String,
        sortBy: DocumentSortBy,
        direction: SortDirection
    ): Flow<List<DocumentEntity>> {
        val hasQuery = query.isNotBlank()
        return when (sortBy) {
            DocumentSortBy.NAME -> if (direction == SortDirection.ASCENDING) {
                if (hasQuery) dao.observeSearchResultsByNameAsc(query) else dao.observeAllDocumentsByNameAsc()
            } else {
                if (hasQuery) dao.observeSearchResultsByNameDesc(query) else dao.observeAllDocumentsByNameDesc()
            }
            DocumentSortBy.DATE_MODIFIED, DocumentSortBy.PAGE_COUNT -> if (direction == SortDirection.ASCENDING) {
                if (hasQuery) dao.observeSearchResultsByDateAsc(query) else dao.observeAllDocumentsByDateAsc()
            } else {
                if (hasQuery) dao.observeSearchResultsByDateDesc(query) else dao.observeAllDocumentsByDateDesc()
            }
        }
    }

    suspend fun getFirstPagePath(documentId: Long): String? =
        dao.getFirstPage(documentId)?.filePath

    suspend fun getPageCount(documentId: Long): Int = dao.getPageCount(documentId)

    suspend fun getDocumentWithPages(documentId: Long): DocumentWithPages? =
        dao.getDocumentWithPages(documentId)

    suspend fun touchAccessed(documentId: Long) =
        dao.touchAccessedAt(documentId, System.currentTimeMillis())

    /** Every page in the library, across all documents, for the collage picker. */
    suspend fun getAllPagesForPicker(): List<PageWithDocumentTitle> =
        dao.getAllPagesAcrossAllDocuments()

    /**
     * Saves a single already-composed bitmap (e.g. a finished collage) as a
     * brand new standalone one-page document. Distinct from saveNewDocument,
     * which takes scanner Uris — this takes a Bitmap directly since collage
     * output never goes through the scanner.
     */
    suspend fun saveBitmapAsNewDocument(bitmap: android.graphics.Bitmap, title: String): Long =
        withContext(Dispatchers.IO) {
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
            val destFile = File(docDir, uniquePageFileName())
            destFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            dao.insertPages(listOf(DocumentPageEntity(documentId = documentId, pageIndex = 0, filePath = destFile.absolutePath)))
            documentId
        }

    /**
     * Saves a list of already-composed bitmaps (a multi-page collage, one
     * bitmap per output page) as a brand new document with one page per
     * bitmap, in the given order. Used when a collage layout produces more
     * pages than fit a single bitmap — e.g. a "2 per page" layout with more
     * pictures assigned than one page holds.
     */
    suspend fun saveBitmapsAsNewDocument(bitmaps: List<android.graphics.Bitmap>, title: String): Long =
        withContext(Dispatchers.IO) {
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
            val pageEntities = bitmaps.mapIndexed { index, bitmap ->
                val destFile = File(docDir, uniquePageFileName())
                destFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }
                DocumentPageEntity(documentId = documentId, pageIndex = index, filePath = destFile.absolutePath)
            }
            dao.insertPages(pageEntities)
            documentId
        }

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
            val destFile = File(docDir, uniquePageFileName())
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

    /**
     * Appends freshly scanned pages to an EXISTING document, placed after all
     * current pages. Used by the detail screen's "Add pages" action.
     */
    suspend fun addPagesToDocument(documentId: Long, newPageUris: List<Uri>) = withContext(Dispatchers.IO) {
        if (newPageUris.isEmpty()) return@withContext
        val doc = dao.getDocumentById(documentId) ?: return@withContext
        val existingCount = dao.getPageCount(documentId)
        val docDir = File(scansRoot, documentId.toString()).apply { mkdirs() }

        val newPageEntities = newPageUris.mapIndexed { offset, uri ->
            val destFile = File(docDir, uniquePageFileName())
            copyUriToHighQualityJpeg(uri, destFile)
            DocumentPageEntity(
                documentId = documentId,
                pageIndex = existingCount + offset,
                filePath = destFile.absolutePath
            )
        }
        dao.insertPages(newPageEntities)
        dao.updateDocument(doc.copy(modifiedAtMillis = System.currentTimeMillis()))
    }

    /**
     * Persists a new page order. `orderedPageIds` must contain every page ID
     * belonging to this document, in the desired display order — the detail
     * screen's drag-to-reorder produces exactly this after each drop.
     */
    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) = withContext(Dispatchers.IO) {
        val currentPages = dao.getPagesForDocument(documentId)
        val byId = currentPages.associateBy { it.id }
        val updated = orderedPageIds.mapIndexedNotNull { newIndex, pageId ->
            byId[pageId]?.copy(pageIndex = newIndex)
        }
        if (updated.size != currentPages.size) {
            // Safety check: if the provided ID list doesn't match what's actually
            // in the document (stale UI state, race with a concurrent delete),
            // skip the write rather than risk silently dropping a page's order.
            return@withContext
        }
        dao.updatePages(updated)
        markModified(documentId)
    }

    /** Deletes a single page (and its file) from a document, leaving the rest intact. */
    suspend fun deletePageFromDocument(documentId: Long, pageId: Long) = withContext(Dispatchers.IO) {
        val page = dao.getPagesForDocument(documentId).find { it.id == pageId } ?: return@withContext
        File(page.filePath).delete()
        dao.deletePage(pageId)

        // Re-number remaining pages so pageIndex stays contiguous (0..n-1) —
        // simplifies every other query that assumes no gaps.
        val remaining = dao.getPagesForDocument(documentId)
        val renumbered = remaining.mapIndexed { index, p -> p.copy(pageIndex = index) }
        dao.updatePages(renumbered)
        markModified(documentId)
    }

    /**
     * Replaces an existing page's image with edited bitmap bytes (from the page
     * editor: rotate/filter result, or a fresh re-scan replacement). Writes to a
     * NEW file and updates the DB row's filePath, rather than overwriting the
     * existing file in place — overwriting in place risks Coil (or any other
     * image loader) serving a stale cached bitmap for that same file:// URI,
     * since the URI string itself wouldn't change even though its bytes did.
     * The old file is deleted after the DB row is updated.
     */
    suspend fun replacePageImage(documentId: Long, pageId: Long, newBitmap: android.graphics.Bitmap) =
        withContext(Dispatchers.IO) {
            val page = dao.getPagesForDocument(documentId).find { it.id == pageId } ?: return@withContext
            val oldFile = File(page.filePath)
            val docDir = oldFile.parentFile ?: File(scansRoot, documentId.toString())
            val newFile = File(docDir, uniquePageFileName())

            newFile.outputStream().use { out ->
                newBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            dao.updatePages(listOf(page.copy(filePath = newFile.absolutePath)))
            oldFile.delete()
            markModified(documentId)
        }

    private fun uniquePageFileName(): String = "page_${System.currentTimeMillis()}_${(0..9999).random()}.jpg"

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
