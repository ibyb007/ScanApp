package com.example.scanapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.scanapp.data.DocumentEntity
import com.example.scanapp.data.DocumentRepository
import com.example.scanapp.data.DocumentSortBy
import com.example.scanapp.data.SortDirection
import com.example.scanapp.edit.PageEditorScreen
import com.example.scanapp.export.ExportEngine
import com.example.scanapp.export.ExportOptions
import com.example.scanapp.export.OutputFormat
import com.example.scanapp.export.PublicDocumentSaver
import com.example.scanapp.scan.DocumentScannerLauncher
import com.example.scanapp.ui.DetailPage
import com.example.scanapp.ui.DocumentDetailScreen
import com.example.scanapp.ui.ExportUiState
import com.example.scanapp.ui.HomeScreen
import com.example.scanapp.ui.RecentDocument
import com.example.scanapp.ui.ScanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen { HOME, DETAIL, PAGE_EDITOR, SCAN_EXPORT }

class MainActivity : ComponentActivity() {

    private lateinit var scannerLauncher: DocumentScannerLauncher
    private val exportEngine by lazy { ExportEngine(applicationContext) }
    private val repository by lazy { DocumentRepository(applicationContext) }

    // Mutable state Compose observes
    private var currentScreen by mutableStateOf(Screen.HOME)
    private var scannedPages by mutableStateOf<List<Uri>>(emptyList())
    private var isExporting by mutableStateOf(false)
    private var exportResultText by mutableStateOf<String?>(null)
    private var recentDocuments by mutableStateOf<List<RecentDocument>>(emptyList())
    private var homeSearchQuery by mutableStateOf("")
    private var homeSortBy by mutableStateOf(DocumentSortBy.DATE_MODIFIED)
    private var homeSortDirection by mutableStateOf(SortDirection.DESCENDING)

    // Currently-open document (DETAIL/PAGE_EDITOR screens) and page (PAGE_EDITOR only).
    private var openDocumentId by mutableStateOf<Long?>(null)
    private var openDocumentTitle by mutableStateOf("")
    private var openDocumentPages by mutableStateOf<List<DetailPage>>(emptyList())
    private var editingPage by mutableStateOf<DetailPage?>(null)

    /**
     * The scanner launcher is reused for three distinct flows (new document,
     * adding pages to an existing one, re-scanning a single page to replace it).
     * Since GmsDocumentScanner's result callback doesn't carry caller context,
     * we track which flow is in-flight here and branch on it in onResult.
     */
    private sealed class PendingScan {
        object NewDocument : PendingScan()
        data class AddPages(val documentId: Long) : PendingScan()
        data class ReplacePage(val documentId: Long, val pageId: Long) : PendingScan()
    }
    private var pendingScan: PendingScan = PendingScan.NewDocument
    private lateinit var singlePageScannerLauncher: DocumentScannerLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scannerLauncher = DocumentScannerLauncher(
            activity = this,
            onResult = { uris -> onScanCompleted(uris) },
            onError = { e -> exportResultText = "Scan failed: ${e.message}" },
            pageLimit = 100
        )
        singlePageScannerLauncher = DocumentScannerLauncher(
            activity = this,
            onResult = { uris -> onScanCompleted(uris) },
            onError = { e -> exportResultText = "Scan failed: ${e.message}" },
            pageLimit = 1
        )

        observeLibrary()

        setContent {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    recentDocuments = recentDocuments,
                    onScanClick = { scannerLauncher.launch() },
                    onDocumentClick = { doc -> openDocumentDetail(doc) },
                    onRename = { doc, newTitle -> renameDocument(doc, newTitle) },
                    onDelete = { doc -> deleteDocument(doc) },
                    onShare = { doc, format ->
    val documentId = doc.id.toLongOrNull() ?: return@HomeScreen
    shareDocument(documentId, doc.title, format)
                    },
                    onDeleteMultiple = { docs -> deleteMultipleDocuments(docs) },
                    searchQuery = homeSearchQuery,
                    onSearchQueryChange = { query -> onHomeSearchQueryChange(query) },
                    sortBy = homeSortBy,
                    sortDirection = homeSortDirection,
                    onSortChange = { sortBy, direction -> onHomeSortChange(sortBy, direction) }
                )
                Screen.DETAIL -> {
                    val documentId = openDocumentId
                    if (documentId == null) {
                        currentScreen = Screen.HOME
                    } else {
                        DocumentDetailScreen(
                            title = openDocumentTitle,
                            pages = openDocumentPages,
                            onBackClick = { currentScreen = Screen.HOME },
                            onRename = { newTitle -> renameOpenDocument(documentId, newTitle) },
                            onDelete = { deleteOpenDocument(documentId) },
                            onShare = { format -> shareDocument(documentId, openDocumentTitle, format) },
                            onExportClick = { openExportScreenForOpenDocument() },
                            onPageClick = { page -> editingPage = page; currentScreen = Screen.PAGE_EDITOR },
                            onAddPagesClick = { launchAddPagesScan(documentId) },
                            onDeletePage = { page -> deletePageFromOpenDocument(documentId, page) },
                            onReorder = { orderedIds -> reorderOpenDocumentPages(documentId, orderedIds) }
                        )
                    }
                }
                Screen.PAGE_EDITOR -> {
                    val page = editingPage
                    val documentId = openDocumentId
                    if (page == null || documentId == null) {
                        currentScreen = Screen.DETAIL
                    } else {
                        PageEditorScreen(
                            pageFilePath = page.uri.path ?: "",
                            onSave = { editedBitmap -> savePageEdit(documentId, page.pageId, editedBitmap) },
                            onRescanRequested = { launchPageRescan(documentId, page.pageId) },
                            onCancel = { currentScreen = Screen.DETAIL }
                        )
                    }
                }
                Screen.SCAN_EXPORT -> ScanScreen(
                    scannedPages = scannedPages,
                    isExporting = isExporting,
                    exportResultText = exportResultText,
                    onScanClick = { scannerLauncher.launch() },
                    onExportClick = { uiState -> runExport(uiState) },
                    onBackClick = {
                        currentScreen = if (openDocumentId != null) Screen.DETAIL else Screen.HOME
                    }
                )
            }
        }
    }

    private var libraryObserverJob: kotlinx.coroutines.Job? = null

    /**
     * Keeps recentDocuments in sync with the database, including thumbnails,
     * filtered by the current search query and ordered by the current sort.
     * Restarted (old collection cancelled) whenever query/sort change, since
     * this is a plain Activity rather than a ViewModel with combine() available
     * across multiple StateFlows.
     */
    private fun observeLibrary() {
        libraryObserverJob?.cancel()
        libraryObserverJob = lifecycleScope.launch {
            repository.observeDocuments(homeSearchQuery, homeSortBy, homeSortDirection).collectLatest { documents ->
                val mapped = documents.map { doc -> toRecentDocument(doc) }
                recentDocuments = if (homeSortBy == DocumentSortBy.PAGE_COUNT) {
                    // Page-count sort isn't expressible in the DAO's simple queries
                    // (would need a join against document_pages), so it's applied
                    // here once page counts are already known from toRecentDocument.
                    if (homeSortDirection == SortDirection.DESCENDING) {
                        mapped.sortedByDescending { it.pageCount }
                    } else {
                        mapped.sortedBy { it.pageCount }
                    }
                } else {
                    mapped
                }
            }
        }
    }

    private fun onHomeSearchQueryChange(query: String) {
        homeSearchQuery = query
        observeLibrary()
    }

    private fun onHomeSortChange(sortBy: DocumentSortBy, direction: SortDirection) {
        homeSortBy = sortBy
        homeSortDirection = direction
        observeLibrary()
    }

    private suspend fun toRecentDocument(doc: DocumentEntity): RecentDocument {
        val pageCount = repository.getPageCount(doc.id)
        val thumbPath = repository.getFirstPagePath(doc.id)
        val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        return RecentDocument(
            id = doc.id.toString(),
            title = doc.title,
            subtitle = "Modified: ${dateFormat.format(Date(doc.modifiedAtMillis))} · $pageCount page" +
                if (pageCount == 1) "" else "s",
            thumbnailUri = thumbPath?.let { File(it).toUri() },
            pageCount = pageCount,
            modifiedAtMillis = doc.modifiedAtMillis
        )
    }

    /**
     * Routes a finished scan to the right place depending on which flow
     * triggered it: a brand-new document, more pages appended to an existing
     * one, or a single-page replacement from the editor's Re-scan action.
     */
    private fun onScanCompleted(uris: List<Uri>) {
        if (uris.isEmpty()) return
        when (val pending = pendingScan) {
            is PendingScan.NewDocument -> {
                lifecycleScope.launch {
                    val title = "Scan ${SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())}"
                    val documentId = repository.saveNewDocument(uris, title)
                    openDocumentDetailById(documentId)
                }
            }
            is PendingScan.AddPages -> {
                lifecycleScope.launch {
                    repository.addPagesToDocument(pending.documentId, uris)
                    refreshOpenDocument(pending.documentId)
                }
            }
            is PendingScan.ReplacePage -> {
                // Re-scan returns a fresh page image; treat its first result as
                // the replacement bitmap for the page being edited.
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uris.first())?.use { BitmapFactory.decodeStream(it) }
                    } ?: return@launch
                    repository.replacePageImage(pending.documentId, pending.pageId, bitmap)
                    refreshOpenDocument(pending.documentId)
                    currentScreen = Screen.DETAIL
                }
            }
        }
    }

    private fun openDocumentDetail(doc: RecentDocument) {
        val documentId = doc.id.toLongOrNull() ?: return
        openDocumentDetailById(documentId)
    }

    private fun openDocumentDetailById(documentId: Long) {
        lifecycleScope.launch {
            repository.touchAccessed(documentId)
            openDocumentId = documentId
            currentScreen = Screen.DETAIL
            refreshOpenDocument(documentId)
        }
    }

    /** Re-reads the open document's title + pages from the DB into UI state. */
    private suspend fun refreshOpenDocument(documentId: Long) {
        val withPages = repository.getDocumentWithPages(documentId) ?: return
        openDocumentTitle = withPages.document.title
        openDocumentPages = withPages.pages.map { page ->
            DetailPage(
                pageId = page.id,
                pageIndex = page.pageIndex,
                uri = File(page.filePath).toUri()
            )
        }
    }

    private fun renameOpenDocument(documentId: Long, newTitle: String) {
        lifecycleScope.launch {
            repository.renameDocument(documentId, newTitle)
            refreshOpenDocument(documentId)
        }
    }

    private fun deleteOpenDocument(documentId: Long) {
        lifecycleScope.launch {
            repository.deleteDocument(documentId)
            openDocumentId = null
            currentScreen = Screen.HOME
        }
    }

    private fun launchAddPagesScan(documentId: Long) {
        pendingScan = PendingScan.AddPages(documentId)
        scannerLauncher.launch()
    }

    private fun deletePageFromOpenDocument(documentId: Long, page: DetailPage) {
        lifecycleScope.launch {
            repository.deletePageFromDocument(documentId, page.pageId)
            refreshOpenDocument(documentId)
        }
    }

    private fun reorderOpenDocumentPages(documentId: Long, orderedPageIds: List<Long>) {
        lifecycleScope.launch {
            repository.reorderPages(documentId, orderedPageIds)
            refreshOpenDocument(documentId)
        }
    }

    private fun savePageEdit(documentId: Long, pageId: Long, editedBitmap: Bitmap) {
        lifecycleScope.launch {
            repository.replacePageImage(documentId, pageId, editedBitmap)
            refreshOpenDocument(documentId)
            currentScreen = Screen.DETAIL
        }
    }

    private fun launchPageRescan(documentId: Long, pageId: Long) {
        pendingScan = PendingScan.ReplacePage(documentId, pageId)
        singlePageScannerLauncher.launch()
    }

    /** Detail screen's "Export" action: reuse the existing size-limited export flow. */
    private fun openExportScreenForOpenDocument() {
        scannedPages = openDocumentPages.map { it.uri }
        exportResultText = null
        currentScreen = Screen.SCAN_EXPORT
    }

    /** Rename/delete from the Home screen's long-press menu — delegates to the ID-based versions. */
    private fun renameDocument(doc: RecentDocument, newTitle: String) {
        val documentId = doc.id.toLongOrNull() ?: return
        lifecycleScope.launch { repository.renameDocument(documentId, newTitle) }
    }

    private fun deleteDocument(doc: RecentDocument) {
        val documentId = doc.id.toLongOrNull() ?: return
        lifecycleScope.launch { repository.deleteDocument(documentId) }
    }

    /** Deletes every selected document from the Home screen's multi-select batch action. */
    private fun deleteMultipleDocuments(docs: List<RecentDocument>) {
        val ids = docs.mapNotNull { it.id.toLongOrNull() }
        if (ids.isEmpty()) return
        lifecycleScope.launch {
            ids.forEach { id -> repository.deleteDocument(id) }
        }
    }

    /**
     * Builds the requested format at full quality (no size limit — sharing is
     * "send as-is", distinct from the size-constrained Export flow) into the
     * app's cache, then hands it to the Android share sheet via FileProvider.
     * Called from both Home's long-press menu and the Detail screen's share icon.
     */
    private fun shareDocument(documentId: Long, title: String, format: OutputFormat) {
        lifecycleScope.launch {
            val withPages = repository.getDocumentWithPages(documentId) ?: return@launch
            val pageUris = withPages.pages.map { File(it.filePath).toUri() }
            if (pageUris.isEmpty()) return@launch

            val shareDir = File(cacheDir, "share_scratch").apply { mkdirs() }
            val safeName = title.replace(Regex("[^A-Za-z0-9 _-]"), "_").ifBlank { "scan" }

            when (format) {
                OutputFormat.PDF -> {
                    val outFile = File(shareDir, "$safeName.pdf")
                    withContext(Dispatchers.IO) {
                        exportEngine.exportAsPdf(
                            pageUris = pageUris,
                            targetSizeBytes = null, // full quality for sharing
                            outputFile = outFile
                        )
                    }
                    val shareUri = FileProvider.getUriForFile(
                        this@MainActivity, "com.example.scanapp.fileprovider", outFile
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share document"))
                }
                OutputFormat.JPEG, OutputFormat.PNG -> {
                    // Share ALL pages, not just the first — multi-page documents
                    // shouldn't silently lose pages when shared as images.
                    val shareUris = withContext(Dispatchers.IO) {
                        pageUris.mapIndexedNotNull { index, uri ->
                            val bitmap = contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            } ?: return@mapIndexedNotNull null
                            val outFile = File(shareDir, "${safeName}_page${index + 1}.jpg")
                            outFile.outputStream().use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            FileProvider.getUriForFile(
                                this@MainActivity, "com.example.scanapp.fileprovider", outFile
                            )
                        }
                    }
                    if (shareUris.isEmpty()) return@launch

                    val sendIntent = if (shareUris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, shareUris.first())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/jpeg"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareUris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share document"))
                }
            }
        }
    }

    private fun runExport(uiState: ExportUiState) {
        if (scannedPages.isEmpty()) return
        isExporting = true
        exportResultText = null

        lifecycleScope.launch {
            try {
                // Scratch space only — never the final destination. The real save
                // target is the public Documents folder, written via PublicDocumentSaver.
                val scratchDir = File(cacheDir, "export_scratch").apply { mkdirs() }
                val targetBytes = uiState.sizeLimitBytes

                val resultText = withContext(Dispatchers.IO) {
                    when (uiState.format) {
                        OutputFormat.PDF -> {
                            // Use custom filename if provided, otherwise random
                            val baseFileName = if (uiState.fileName.isNotBlank()) {
                                uiState.fileName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                            } else {
                                "scan_${System.currentTimeMillis()}"
                            }
                            val scratchFile = File(scratchDir, "$baseFileName.pdf")
                            val result = exportEngine.exportAsPdf(
                                pageUris = scannedPages,
                                targetSizeBytes = targetBytes,
                                outputFile = scratchFile
                            )
                            val displayName = scratchFile.name
                            val savedPath = PublicDocumentSaver.saveToDocuments(
                                context = applicationContext,
                                bytes = scratchFile.readBytes(),
                                displayName = displayName,
                                mimeType = "application/pdf"
                            )
                            scratchFile.delete()
                            "Saved to $savedPath (${result.finalSizeBytes / 1024} KB, " +
                                "quality ${result.finalQuality}, ${result.finalWidth}x${result.finalHeight})"
                        }
                        OutputFormat.JPEG, OutputFormat.PNG -> {
                            var totalBytes = 0L
                            val ext = if (uiState.format == OutputFormat.PNG) "png" else "jpg"
                            val mime = if (uiState.format == OutputFormat.PNG) "image/png" else "image/jpeg"
                            scannedPages.forEachIndexed { index, uri ->
                                val input = contentResolver.openInputStream(uri)
                                val bitmap = input?.use { BitmapFactory.decodeStream(it) }
                                    ?: return@forEachIndexed
                                val (out, _) = exportEngine.compressImage(
                                    bitmap,
                                    ExportOptions(
                                        format = uiState.format,
                                        targetSizeBytes = targetBytes,
                                        quality = uiState.quality
                                    )
                                )
                                val bytes = out.toByteArray()
                                // Use custom filename if provided, otherwise random
                                val baseName = if (uiState.fileName.isNotBlank()) {
                                    uiState.fileName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                } else {
                                    "page_${index + 1}_${System.currentTimeMillis()}"
                                }
                                val displayName = "${baseName}_page${index + 1}.$ext"
                                PublicDocumentSaver.saveToDocuments(
                                    context = applicationContext,
                                    bytes = bytes,
                                    displayName = displayName,
                                    mimeType = mime
                                )
                                totalBytes += bytes.size
                            }
                            "Saved ${scannedPages.size} image(s) to Documents, total ${totalBytes / 1024} KB"
                        }
                    }
                }

                exportResultText = resultText
            } catch (e: Exception) {
                exportResultText = "Export failed: ${e.message}"
            } finally {
                isExporting = false
            }
        }
    }
}
