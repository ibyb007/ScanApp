package com.example.scanapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A document paired with its ordered pages — what the UI actually needs to render. */
data class DocumentWithPages(
    val document: DocumentEntity,
    val pages: List<DocumentPageEntity>
)

/** Sort options for the Home/Files list. */
enum class DocumentSortBy { NAME, DATE_MODIFIED, PAGE_COUNT }
enum class SortDirection { ASCENDING, DESCENDING }

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY modifiedAtMillis DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' ORDER BY modifiedAtMillis DESC")
    fun observeSearchResults(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY title COLLATE NOCASE ASC")
    fun observeAllDocumentsByNameAsc(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY title COLLATE NOCASE DESC")
    fun observeAllDocumentsByNameDesc(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY modifiedAtMillis ASC")
    fun observeAllDocumentsByDateAsc(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY modifiedAtMillis DESC")
    fun observeAllDocumentsByDateDesc(): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY title COLLATE NOCASE ASC"
    )
    fun observeSearchResultsByNameAsc(query: String): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY title COLLATE NOCASE DESC"
    )
    fun observeSearchResultsByNameDesc(query: String): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY modifiedAtMillis ASC"
    )
    fun observeSearchResultsByDateAsc(query: String): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY modifiedAtMillis DESC"
    )
    fun observeSearchResultsByDateDesc(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(documentId: Long): List<DocumentPageEntity>

    /** First page only — used for the Recents-list thumbnail, avoids loading all pages. */
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC LIMIT 1")
    suspend fun getFirstPage(documentId: Long): DocumentPageEntity?

    @Query("SELECT COUNT(*) FROM document_pages WHERE documentId = :documentId")
    suspend fun getPageCount(documentId: Long): Int

    @Insert
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert
    suspend fun insertPages(pages: List<DocumentPageEntity>)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Update
    suspend fun updatePages(pages: List<DocumentPageEntity>)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity) // cascades to pages via FK

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Long): DocumentEntity?

    @Query("DELETE FROM document_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    @Transaction
    suspend fun getDocumentWithPages(documentId: Long): DocumentWithPages? {
        val doc = getDocumentById(documentId) ?: return null
        return DocumentWithPages(doc, getPagesForDocument(documentId))
    }

    @Query("UPDATE documents SET accessedAtMillis = :timestamp WHERE id = :documentId")
    suspend fun touchAccessedAt(documentId: Long, timestamp: Long)
}
