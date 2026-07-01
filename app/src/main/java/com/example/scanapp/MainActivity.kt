package com.example.scanapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.scanapp.collage.canvasPx
import com.example.scanapp.data.DocumentEntity
import com.example.scanapp.data.DocumentRepository
import com.example.scanapp.data.DocumentSortBy
import com.example.scanapp.data.SortDirection
import com.example.scanapp.export.ExportEngine
import com.example.scanapp.export.ExportOptions
import com.example.scanapp.export.OutputFormat
import com.example.scanapp.export.PublicDocumentSaver
import com.example.scanapp.scan.DocumentScannerLauncher
import com.example.scanapp.scan.TempGalleryExport
import com.example.scanapp.ui.DetailPage
import com.example.scanapp.ui.DocumentDetailScreen
import com.example.scanapp.ui.ExportUiState
import com.example.scanapp.ui.HomeScreen
import com.example.scanapp.ui.RecentDocument
import com.example.scanapp.ui.ScanScreen
import com.example.scanapp.ui.SizeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Material 3 Expressive Color Schemes
private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3B5BDB),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE7FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00144B),
    secondary = androidx.compose.ui.graphics.Color(0xFF5B5D72),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE0E1F9),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF181A2C),
    tertiary = androidx.compose.ui.graphics.Color(0xFF75546F),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surface = androidx.compose.ui.graphics.Color(0xFFFEF8FF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1D1B20),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE2E1EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF45464F)
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFB4C5FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00287D),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF2042C3),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE7FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFC4C5DD),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF2C2F42),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF434559),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE0E1F9),
    tertiary = androidx.compose.ui.graphics.Color(0xFFE4BBDB),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF442740),
    surface = androidx.compose.ui.graphics.Color(0xFF151318),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE6E1E6),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF45464F),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC6C5D0)
)

@Composable
fun ScanAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private enum class Screen { HOME, DETAIL, SCAN_EXPORT, SETTINGS, COLLAGE, BACKUP }

class MainActivity : ComponentActivity() {

    private lateinit var scannerLauncher: DocumentScannerLauncher
    private lateinit var pdfImportLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var restoreBackupLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var pendingRestorePassword: String = ""
    private lateinit var importTelegramCredsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private var pendingImportCredsPassword: String = ""
    private val exportEngine by lazy { ExportEngine(applicationContext) }
    private lateinit var repository: DocumentRepository

    private var currentScreen by mutableStateOf(Screen.HOME)
    private var scannedPages by mutableStateOf<List<Uri>>(emptyList())
    private var isExporting by mutableStateOf(false)
    private var exportResultText by mutableStateOf<String?>(null)
    private var isImportingPdf by mutableStateOf(false)
    private var pdfImportError by mutableStateOf<String?>(null)
    private var recentDocuments by mutableStateOf<List<RecentDocument>>(emptyList())
    private var homeSearchQuery by mutableStateOf("")
    private var homeSortBy by mutableStateOf(DocumentSortBy.DATE_MODIFIED)
    private var homeSortDirection by mutableStateOf(SortDirection.DESCENDING)
    private var updateStatus by mutableStateOf(com.example.scanapp.ui.UpdateCheckUiStatus.IDLE)
    private var updateStatusMessage by mutableStateOf<String?>(null)
    private var latestReleaseUrl by mutableStateOf<String?>(null)
    private var latestApkDownloadUrl by mutableStateOf<String?>(null)

    private var checkUpdatesOnStart by mutableStateOf(true)
    private var autoInstallUpdates by mutableStateOf(false)

    private var showUpdateAvailableDialog by mutableStateOf(false)
    private var startupUpdateVersion by mutableStateOf("")
    private var isDownloadingUpdate by mutableStateOf(false)
    private var updateDownloadError by mutableStateOf<String?>(null)

    private var exportUiState by mutableStateOf(ExportUiState())
    private var exportUseSizeLimit by mutableStateOf(true)
    private var exportSizeUnit by mutableStateOf(SizeUnit.KB)
    private var exportSizeText by mutableStateOf("500")

    private var openDocumentId by mutableStateOf<Long?>(null)
    private var openDocumentTitle by mutableStateOf("")
    private var openDocumentPages by mutableStateOf<List<DetailPage>>(emptyList())

    private var collagePickerPages by mutableStateOf<List<com.example.scanapp.ui.CollagePickerPage>>(emptyList())
    private var isSavingCollage by mutableStateOf(false)

    private var isBackupActive by mutableStateOf(false)
    private var backupStatusMessage by mutableStateOf<String?>(null)
    private var telegramActivity by mutableStateOf(com.example.scanapp.ui.TelegramActivityState.NONE)

    private sealed class PendingScan {
        object NewDocument : PendingScan()
        data class AddPages(val documentId: Long) : PendingScan()
        data class ReplacePage(val documentId: Long, val pageId: Long) : PendingScan()
    }
    private var pendingScan: PendingScan = PendingScan.NewDocument
    private lateinit var singlePageScannerLauncher: DocumentScannerLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = DocumentRepository(applicationContext)

        checkUpdatesOnStart = com.example.scanapp.update.UpdatePreferences.isCheckOnStartEnabled(applicationContext)
        autoInstallUpdates = com.example.scanapp.update.UpdatePreferences.isAutoInstallEnabled(applicationContext)
        if (checkUpdatesOnStart) {
            checkForUpdateOnStartup()
        }

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

        pdfImportLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                onPdfPicked(uri)
            }
        }

        restoreBackupLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                runLocalRestore(uri, pendingRestorePassword)
            } else {
                isBackupActive = false
            }
        }

        importTelegramCredsLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                runImportTelegramCredentials(uri, pendingImportCredsPassword)
            } else {
                isBackupActive = false
            }
        }

        observeLibrary()

        setContent {
            ScanAppTheme {
                BackHandler(enabled = currentScreen != Screen.HOME) {
                    currentScreen = when (currentScreen) {
                        Screen.DETAIL -> Screen.HOME
                        Screen.SCAN_EXPORT -> if (openDocumentId != null) Screen.DETAIL else Screen.HOME
                        Screen.SETTINGS -> Screen.HOME
                        Screen.COLLAGE -> Screen.HOME
                        Screen.BACKUP -> Screen.HOME
                        Screen.HOME -> Screen.HOME
                    }
                }

                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        recentDocuments = recentDocuments,
                        onScanClick = { scannerLauncher.launch() },
                        onImportPdfClick = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
                        isImportingPdf = isImportingPdf,
                        pdfImportError = pdfImportError,
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
                        onSortChange = { sortBy, direction -> onHomeSortChange(sortBy, direction) },
                        onSettingsClick = { currentScreen = Screen.SETTINGS },
                        onToolsClick = { openCollageScreen() },
                        onBackupClick = { currentScreen = Screen.BACKUP }
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
                                onPageClick = { page -> launchPageEditViaMlKit(documentId, page) },
                                onAddPagesClick = { launchAddPagesScan(documentId) },
                                onDeletePage = { page -> deletePageFromOpenDocument(documentId, page) },
                                onReorder = { orderedIds -> reorderOpenDocumentPages(documentId, orderedIds) },
                                onExportSelected = { selectedPages -> openExportScreenForSelectedPages(selectedPages) }
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
                        },
                        initialUiState = exportUiState,
                        initialUseSizeLimit = exportUseSizeLimit,
                        initialSizeUnit = exportSizeUnit,
                        initialSizeText = exportSizeText,
                        onExportUiStateChange = { uiState, useSizeLimit, sizeUnit, sizeText ->
                            exportUiState = uiState
                            exportUseSizeLimit = useSizeLimit
                            exportSizeUnit = sizeUnit
                            exportSizeText = sizeText
                        }
                    )
                    Screen.SETTINGS -> com.example.scanapp.ui.SettingsScreen(
                        versionName = com.example.scanapp.BuildConfig.VERSION_NAME,
                        versionCode = com.example.scanapp.BuildConfig.VERSION_CODE,
                        updateStatus = updateStatus,
                        updateStatusMessage = updateStatusMessage,
                        onCheckForUpdateClick = { checkForUpdate() },
                        onOpenReleaseClick = {
                            latestReleaseUrl?.let { url ->
                                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        },
                        checkUpdatesOnStart = checkUpdatesOnStart,
                        onCheckUpdatesOnStartChange = { enabled ->
                            checkUpdatesOnStart = enabled
                            com.example.scanapp.update.UpdatePreferences.setCheckOnStartEnabled(applicationContext, enabled)
                        },
                        autoInstallUpdates = autoInstallUpdates,
                        onAutoInstallUpdatesChange = { enabled ->
                            autoInstallUpdates = enabled
                            com.example.scanapp.update.UpdatePreferences.setAutoInstallEnabled(applicationContext, enabled)
                        },
                        onBackClick = { currentScreen = Screen.HOME },
                        onHomeClick = { currentScreen = Screen.HOME },
                        onToolsClick = { openCollageScreen() },
                        onBackupClick = { currentScreen = Screen.BACKUP }
                    )
                    Screen.COLLAGE -> com.example.scanapp.ui.CollageScreen(
                        allPages = collagePickerPages,
                        isSaving = isSavingCollage,
                        onBackClick = { currentScreen = Screen.HOME },
                        onSaveClick = { layout, pageSize, orientation, pages ->
                            saveCollageAsNewDocument(layout, pageSize, orientation, pages)
                        },
                        onHomeClick = { currentScreen = Screen.HOME },
                        onSettingsClick = { currentScreen = Screen.SETTINGS },
                        onBackupClick = { currentScreen = Screen.BACKUP }
                    )
                Screen.BACKUP -> {
                    val savedTelegramCreds = com.example.scanapp.backup.BackupEngine.getTelegramCredentials(applicationContext)
                    com.example.scanapp.ui.BackupScreen(
                        isProcessing = isBackupActive,
                        statusMessage = backupStatusMessage,
                        savedBotToken = savedTelegramCreds.first,
                        savedChatId = savedTelegramCreds.second,
                        onLocalBackup = { password -> runLocalBackup(password) },
                        onLocalRestore = { password -> runLocalRestore(password) },
                        onTelegramSync = { token, chatId, password -> runTelegramSync(token, chatId, password) },
                        onTelegramRestore = { token, password -> runTelegramRestore(token, password) },
                        onSaveTelegramCredentials = { token, chatId ->
                            com.example.scanapp.backup.BackupEngine.saveTelegramCredentials(applicationContext, token, chatId)
                        },
                        onExportTelegramCredentials = { password -> runExportTelegramCredentials(password) },
                        onImportTelegramCredentials = { password -> runImportTelegramCredentials(password) },
                        telegramActivity = telegramActivity,
                        onHomeClick = { currentScreen = Screen.HOME },
                        onToolsClick = { openCollageScreen() },
                        onSettingsClick = { currentScreen = Screen.SETTINGS }
                    )
                }
                }

                if (showUpdateAvailableDialog) {
                    AlertDialog(
                        onDismissRequest = { showUpdateAvailableDialog = false },
                        title = { Text("Update available") },
                        text = {
                            Column {
                                Text(
                                    "Version $startupUpdateVersion is available." +
                                        if (isDownloadingUpdate) "\n\nDownloading…" else ""
                                )
                                updateDownloadError?.let { error ->
                                    Text(
                                        "Download failed: $error",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !isDownloadingUpdate,
                                onClick = {
                                    val apkUrl = latestApkDownloadUrl
                                    if (apkUrl != null) {
                                        downloadAndLaunchInstall(apkUrl)
                                    } else {
                                        latestReleaseUrl?.let { url ->
                                            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                        }
                                        showUpdateAvailableDialog = false
                                    }
                                }
                            ) {
                                Text(if (latestApkDownloadUrl != null) "Update now" else "View release")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !isDownloadingUpdate,
                                onClick = { showUpdateAvailableDialog = false }
                            ) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
    }

    private var libraryObserverJob: kotlinx.coroutines.Job? = null

    private fun observeLibrary() {
        libraryObserverJob?.cancel()
        libraryObserverJob = lifecycleScope.launch {
            repository.observeDocuments(homeSearchQuery, homeSortBy, homeSortDirection).collectLatest { documents ->
                val mapped = documents.map { doc -> toRecentDocument(doc) }
                recentDocuments = if (homeSortBy == DocumentSortBy.PAGE_COUNT) {
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

    private fun onPdfPicked(uri: Uri) {
        if (isImportingPdf) return
        isImportingPdf = true
        pdfImportError = null

        lifecycleScope.launch {
            try {
                val pageUris = withContext(Dispatchers.IO) {
                    com.example.scanapp.scan.PdfImporter.importPagesAsJpegs(this@MainActivity, uri)
                }

                val title = pdfDisplayNameOrNull(uri)
                    ?: "Imported PDF ${SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())}"

                val documentId = repository.saveNewDocument(pageUris, title)
                repository.touchAccessed(documentId)
                openDocumentId = documentId
                refreshOpenDocument(documentId)
                currentScreen = Screen.DETAIL
            } catch (e: Exception) {
                pdfImportError = "PDF import failed: ${e.message}"
            } finally {
                isImportingPdf = false
                withContext(Dispatchers.IO) {
                    com.example.scanapp.scan.PdfImporter.cleanupScratchFiles(this@MainActivity)
                }
            }
        }
    }

    /** Looks up the picked PDF's display name (without extension) to use as the document title. */
    private fun pdfDisplayNameOrNull(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameColumn < 0 || !cursor.moveToFirst()) return null
            cursor.getString(nameColumn)?.removeSuffix(".pdf")?.removeSuffix(".PDF")?.ifBlank { null }
        }
    }

    private fun onScanCompleted(uris: List<Uri>) {
        if (uris.isEmpty()) return
        when (val pending = pendingScan) {
            is PendingScan.NewDocument -> {
                scannedPages = uris
                exportResultText = null
                currentScreen = Screen.SCAN_EXPORT

                lifecycleScope.launch {
                    val title = "Scan ${SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())}"
                    val documentId = repository.saveNewDocument(uris, title)
                    repository.touchAccessed(documentId)
                    openDocumentId = documentId
                    refreshOpenDocument(documentId)
                }
            }
            is PendingScan.AddPages -> {
                lifecycleScope.launch {
                    repository.addPagesToDocument(pending.documentId, uris)
                    refreshOpenDocument(pending.documentId)
                }
            }
            is PendingScan.ReplacePage -> {
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uris.first())?.use { BitmapFactory.decodeStream(it) }
                    }
                    if (bitmap != null) {
                        repository.replacePageImage(pending.documentId, pending.pageId, bitmap)
                        refreshOpenDocument(pending.documentId)
                    }
                    currentScreen = Screen.DETAIL
                    withContext(Dispatchers.IO) { TempGalleryExport.cleanupAllTempCopies(applicationContext) }
                }
            }
        }
    }

    private fun openCollageScreen() {
        lifecycleScope.launch {
            val pages = withContext(Dispatchers.IO) { repository.getAllPagesForPicker() }
            collagePickerPages = pages.map { withTitle ->
                com.example.scanapp.ui.CollagePickerPage(
                    pageId = withTitle.page.id,
                    uri = File(withTitle.page.filePath).toUri(),
                    documentTitle = withTitle.documentTitle
                )
            }
            isSavingCollage = false
            currentScreen = Screen.COLLAGE
        }
    }

    private fun saveCollageAsNewDocument(
        layout: com.example.scanapp.collage.CollageLayout,
        pageSize: com.example.scanapp.collage.CollagePageSize,
        orientation: com.example.scanapp.collage.CollageOrientation,
        pages: List<com.example.scanapp.collage.CollagePage>
    ) {
        val assignedPageIds = pages.flatMap { it.frames }.mapNotNull { it.pageId }.distinct()
        if (assignedPageIds.isEmpty()) return

        isSavingCollage = true
        lifecycleScope.launch {
            val title = "Collage ${SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())}"
            val pageById = collagePickerPages.associateBy { it.pageId }

            val bitmaps = withContext(Dispatchers.IO) {
                val pageBitmaps = assignedPageIds.mapNotNull { id ->
                    val path = pageById[id]?.uri?.path ?: return@mapNotNull null
                    val decoded = BitmapFactory.decodeFile(path) ?: return@mapNotNull null
                    id to decoded
                }.toMap()

                if (pageBitmaps.isEmpty()) {
                    emptyList()
                } else {
                    val (canvasWidthPx, canvasHeightPx) = pageSize.canvasPx(orientation)
                    com.example.scanapp.collage.CollageCompositor.composePages(
                        pageBitmaps = pageBitmaps,
                        pages = pages,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx
                    )
                }
            }

            if (bitmaps.isNotEmpty()) {
                repository.saveBitmapsAsNewDocument(bitmaps, title)
                currentScreen = Screen.HOME
            }
            isSavingCollage = false
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

    private fun launchPageEditViaMlKit(documentId: Long, page: DetailPage) {
        lifecycleScope.launch {
            val sourceFile = File(page.uri.path ?: return@launch)
            if (!sourceFile.exists()) return@launch

            withContext(Dispatchers.IO) {
                TempGalleryExport.exportForPicking(applicationContext, sourceFile)
            }

            android.widget.Toast.makeText(
                this@MainActivity,
                "Tap the gallery icon and pick the page you just opened",
                android.widget.Toast.LENGTH_LONG
            ).show()

            pendingScan = PendingScan.ReplacePage(documentId, page.pageId)
            singlePageScannerLauncher.launch()
        }
    }

    private fun openExportScreenForOpenDocument() {
        scannedPages = openDocumentPages.map { it.uri }
        exportResultText = null
        currentScreen = Screen.SCAN_EXPORT
    }

    private fun openExportScreenForSelectedPages(selectedPages: List<DetailPage>) {
        scannedPages = selectedPages.map { it.uri }
        exportResultText = null
        currentScreen = Screen.SCAN_EXPORT
    }

    private fun renameDocument(doc: RecentDocument, newTitle: String) {
        val documentId = doc.id.toLongOrNull() ?: return
        lifecycleScope.launch { repository.renameDocument(documentId, newTitle) }
    }

    private fun deleteDocument(doc: RecentDocument) {
        val documentId = doc.id.toLongOrNull() ?: return
        lifecycleScope.launch { repository.deleteDocument(documentId) }
    }

    private fun deleteMultipleDocuments(docs: List<RecentDocument>) {
        val ids = docs.mapNotNull { it.id.toLongOrNull() }
        if (ids.isEmpty()) return
        lifecycleScope.launch {
            ids.forEach { id -> repository.deleteDocument(id) }
        }
    }

    private fun checkForUpdate() {
        updateStatus = com.example.scanapp.ui.UpdateCheckUiStatus.CHECKING
        updateStatusMessage = null
        lifecycleScope.launch {
            val result = com.example.scanapp.update.UpdateChecker.checkForUpdate(
                com.example.scanapp.BuildConfig.VERSION_NAME
            )
            when (result) {
                is com.example.scanapp.update.UpdateCheckResult.UpToDate -> {
                    updateStatus = com.example.scanapp.ui.UpdateCheckUiStatus.UP_TO_DATE
                }
                is com.example.scanapp.update.UpdateCheckResult.UpdateAvailable -> {
                    updateStatus = com.example.scanapp.ui.UpdateCheckUiStatus.UPDATE_AVAILABLE
                    updateStatusMessage = "Version ${result.latestVersion} is available (you have ${result.currentVersion})"
                    latestReleaseUrl = result.releaseUrl
                    latestApkDownloadUrl = result.apkDownloadUrl
                }
                is com.example.scanapp.update.UpdateCheckResult.Error -> {
                    updateStatus = com.example.scanapp.ui.UpdateCheckUiStatus.ERROR
                    updateStatusMessage = result.message
                }
            }
        }
    }

    private fun checkForUpdateOnStartup() {
        lifecycleScope.launch {
            val result = com.example.scanapp.update.UpdateChecker.checkForUpdate(
                com.example.scanapp.BuildConfig.VERSION_NAME
            )
            if (result is com.example.scanapp.update.UpdateCheckResult.UpdateAvailable) {
                latestReleaseUrl = result.releaseUrl
                latestApkDownloadUrl = result.apkDownloadUrl
                startupUpdateVersion = result.latestVersion
                showUpdateAvailableDialog = true

                if (autoInstallUpdates && result.apkDownloadUrl != null) {
                    downloadAndLaunchInstall(result.apkDownloadUrl)
                }
            }
        }
    }

    private fun downloadAndLaunchInstall(apkUrl: String) {
        lifecycleScope.launch {
            isDownloadingUpdate = true
            updateDownloadError = null
            val result = com.example.scanapp.update.UpdateApkDownloader.download(applicationContext, apkUrl)
            isDownloadingUpdate = false
            when (result) {
                is com.example.scanapp.update.ApkDownloadResult.Success -> {
                    launchApkInstall(result.apkUri)
                    showUpdateAvailableDialog = false
                }
                is com.example.scanapp.update.ApkDownloadResult.Error -> {
                    updateDownloadError = result.message
                }
            }
        }
    }

    private fun runLocalBackup(password: String) {
        if (isBackupActive) return
        isBackupActive = true
        backupStatusMessage = "Creating backup..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val location = com.example.scanapp.backup.BackupEngine.createLocalBackupInDownloads(
                    applicationContext, password.ifBlank { null }
                )
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Backup saved to $location"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Backup failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackupActive = false
                }
            }
        }
    }

    /** Entry point from the Backup screen's Restore button: opens a file picker scoped to Downloads/ScanApp. */
    private fun runLocalRestore(password: String) {
        if (isBackupActive) return
        isBackupActive = true
        pendingRestorePassword = password
        backupStatusMessage = "Choose a backup file to restore..."
        restoreBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    /** Actual restore once the user has picked a backup file via SAF. */
    private fun runLocalRestore(backupUri: Uri, password: String) {
        backupStatusMessage = "Restoring backup..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.example.scanapp.backup.BackupEngine.restoreBackup(
                    applicationContext, backupUri, password.ifBlank { null }
                )
                // The restore just overwrote scanapp.db on disk. The old
                // repository/DAO/Room connection still points at the now-stale
                // file, so it must be rebuilt before the UI re-observes the
                // library — otherwise inserts (restored rows or new scans)
                // collide with Room's old in-memory autoincrement state and
                // end up merged into a single document group.
                repository = DocumentRepository(applicationContext)
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Restore complete"
                    openDocumentId = null
                    currentScreen = Screen.HOME
                }
                observeLibrary()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Restore failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackupActive = false
                }
            }
        }
    }

    private fun runTelegramSync(token: String, chatId: String, password: String) {
        if (isBackupActive) return
        isBackupActive = true
        telegramActivity = com.example.scanapp.ui.TelegramActivityState.UPLOADING
        backupStatusMessage = "Uploading to Telegram..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(cacheDir, "scanapp_tg.enc")
                com.example.scanapp.backup.BackupEngine.createBackup(applicationContext, file, password.ifBlank { null })
                com.example.scanapp.backup.BackupEngine.uploadToTelegramAndRotate(applicationContext, token, chatId, file)
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Upload complete"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Upload failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackupActive = false
                    telegramActivity = com.example.scanapp.ui.TelegramActivityState.NONE
                }
            }
        }
    }

    private fun runTelegramRestore(token: String, password: String) {
        if (isBackupActive) return
        isBackupActive = true
        telegramActivity = com.example.scanapp.ui.TelegramActivityState.DOWNLOADING
        backupStatusMessage = "Downloading backup from Telegram..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.example.scanapp.backup.BackupEngine.downloadFromTelegramAndRestore(
                    applicationContext, token, password.ifBlank { null }
                )
                // Same reasoning as the local restore path: rebuild the Room
                // connection so the UI doesn't observe stale/merged state.
                repository = DocumentRepository(applicationContext)
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Restore from Telegram complete"
                    openDocumentId = null
                    currentScreen = Screen.HOME
                }
                observeLibrary()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Telegram restore failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackupActive = false
                    telegramActivity = com.example.scanapp.ui.TelegramActivityState.NONE
                }
            }
        }
    }

    /** Encrypts the saved bot token + chat ID and writes them to Downloads/ScanApp. */
    private fun runExportTelegramCredentials(password: String) {
        if (isBackupActive) return
        isBackupActive = true
        backupStatusMessage = "Exporting Telegram credentials..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (token, chatId) = com.example.scanapp.backup.BackupEngine.getTelegramCredentials(applicationContext)
                val location = com.example.scanapp.backup.BackupEngine.exportTelegramCredentialsToDownloads(
                    applicationContext, token, chatId, password
                )
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Credentials exported to $location"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Credentials export failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackupActive = false
                }
            }
        }
    }

    /** Entry point from the Backup screen's "Import credentials from file" button: opens a file picker. */
    private fun runImportTelegramCredentials(password: String) {
        if (isBackupActive) return
        isBackupActive = true
        pendingImportCredsPassword = password
        backupStatusMessage = "Choose a credentials file to import..."
        importTelegramCredsLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    /** Actual import once the user has picked a credentials file via SAF. */
    private fun runImportTelegramCredentials(sourceUri: Uri, password: String) {
        backupStatusMessage = "Importing Telegram credentials..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (token, chatId) = com.example.scanapp.backup.BackupEngine.importTelegramCredentialsFromUri(
                    applicationContext, sourceUri, password
                )
                com.example.scanapp.backup.BackupEngine.saveTelegramCredentials(applicationContext, token, chatId)
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Credentials imported"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Credentials import failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackupActive = false
                }
            }
        }
    }

    private fun launchApkInstall(apkUri: Uri) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri())
            )
            return
        }

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(installIntent)
    }

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
                            targetSizeBytes = null,
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
                val scratchDir = File(cacheDir, "export_scratch").apply { mkdirs() }
                val targetBytes = uiState.sizeLimitBytes

                val resultText = withContext(Dispatchers.IO) {
                    when (uiState.format) {
                        OutputFormat.PDF -> {
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