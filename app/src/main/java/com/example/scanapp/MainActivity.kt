package com.example.scanapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private enum class Screen { HOME, DETAIL, SCAN_EXPORT, SETTINGS, COLLAGE }

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
    private var updateStatus by mutableStateOf(com.example.scanapp.ui.UpdateCheckUiStatus.IDLE)
    private var updateStatusMessage by mutableStateOf<String?>(null)
    private var latestReleaseUrl by mutableStateOf<String?>(null)
    private var latestApkDownloadUrl by mutableStateOf<String?>(null)

    // Settings toggles, loaded from UpdatePreferences in onCreate and written
    // back through it on every change — see UpdatePreferences for why plain
    // SharedPreferences rather than DataStore.
    private var checkUpdatesOnStart by mutableStateOf(true)
    private var autoInstallUpdates by mutableStateOf(false)

    // Drives the standard "update available" popup shown after a silent
    // startup check (distinct from updateStatus/updateStatusMessage, which
    // drive the always-visible Settings row — a manual tap there already
    // gets its own inline feedback and doesn't need this popup on top of it).
    private var showUpdateAvailableDialog by mutableStateOf(false)
    private var startupUpdateVersion by mutableStateOf("")
    private var isDownloadingUpdate by mutableStateOf(false)
    private var updateDownloadError by mutableStateOf<String?>(null)

    // Export screen's customization state, hoisted here (rather than living
    // inside ScanScreen's own `remember`) so it survives leaving and
    // re-entering the export screen — e.g. size limit, unit, and filename
    // used to silently reset every time this screen was recomposed fresh.
    private var exportUiState by mutableStateOf(ExportUiState())
    private var exportUseSizeLimit by mutableStateOf(true)
    private var exportSizeUnit by mutableStateOf(SizeUnit.KB)
    private var exportSizeText by mutableStateOf("500")

    // Currently-open document (DETAIL screen).
    private var openDocumentId by mutableStateOf<Long?>(null)
    private var openDocumentTitle by mutableStateOf("")
    private var openDocumentPages by mutableStateOf<List<DetailPage>>(emptyList())

    // Collage screen state. The live grid (template, page size, orientation,
    // per-cell assignments) is now owned internally by CollageScreen's own
    // remember state — MainActivity only needs the page library to hand it,
    // and a saving flag for the one-shot compose-and-save at the end.
    private var collagePickerPages by mutableStateOf<List<com.example.scanapp.ui.CollagePickerPage>>(emptyList())
    private var isSavingCollage by mutableStateOf(false)

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

        observeLibrary()

        setContent {
            // Without this, the system back gesture/button has no idea this
            // app has its own in-app "pages" (currentScreen is a plain enum,
            // not Jetpack Navigation) — Android's default behavior for an
            // unhandled back press is to finish the Activity, so swiping back
            // from DETAIL, SETTINGS, SCAN_EXPORT, or COLLAGE exited the whole
            // app instead of returning to the previous screen.
            //
            // BackHandler intercepts that gesture and routes it through the
            // exact same target each screen's own back ARROW already uses
            // (see each screen's onBackClick below), so the two stay in sync
            // by construction rather than needing a second navigation map to
            // maintain. enabled = false on HOME lets the system handle back
            // normally there — exiting the app from the root screen is the
            // expected, standard behavior.
            BackHandler(enabled = currentScreen != Screen.HOME) {
                currentScreen = when (currentScreen) {
                    Screen.DETAIL -> Screen.HOME
                    Screen.SCAN_EXPORT -> if (openDocumentId != null) Screen.DETAIL else Screen.HOME
                    Screen.SETTINGS -> Screen.HOME
                    Screen.COLLAGE -> Screen.HOME
                    Screen.HOME -> Screen.HOME // unreachable while enabled = false
                }
            }

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
                    onSortChange = { sortBy, direction -> onHomeSortChange(sortBy, direction) },
                    onSettingsClick = { currentScreen = Screen.SETTINGS },
                    onToolsClick = { openCollageScreen() }
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
                    onBackClick = { currentScreen = Screen.HOME }
                )
                Screen.COLLAGE -> com.example.scanapp.ui.CollageScreen(
                    allPages = collagePickerPages,
                    isSaving = isSavingCollage,
                    onBackClick = { currentScreen = Screen.HOME },
                    onSaveClick = { template, pageSize, orientation, assignments ->
                        saveCollageAsNewDocument(template, pageSize, orientation, assignments)
                    }
                )
            }

            // Standard "update available" popup, shown after a silent
            // startup check finds a newer release — floats above whichever
            // screen the person happens to land on (usually HOME, since this
            // fires right at launch), independent of the when() above.
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
                                    // No APK attached to this release (e.g. a
                                    // source-only release) — fall back to the
                                    // browser, same as the Settings row's own
                                    // "Update" button does in that situation.
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
     * triggered it: a brand-new document goes straight to the export screen
     * (skipping the detail viewer, so the user can export immediately);
     * more pages appended to an existing one return to that document's detail
     * view; a single-page replacement from the editor's Re-scan action returns
     * to the detail view too.
     */
    private fun onScanCompleted(uris: List<Uri>) {
        if (uris.isEmpty()) return
        when (val pending = pendingScan) {
            is PendingScan.NewDocument -> {
                // Switch to the export screen and populate its pages FIRST, and
                // synchronously (no suspension point before this happens). The
                // detail-viewer fields (openDocumentId / refreshOpenDocument) are
                // only needed in case the user later backs out of export into
                // Detail, so they're updated afterward, off the critical path.
                // Previously these were set in the other order, which left a
                // window where openDocumentId had already flipped non-null
                // while currentScreen hadn't caught up yet — long enough for
                // the Detail viewer to flash on screen before Export appeared.
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
                // The user edited (Filters/Crop/Clean) the temp gallery copy of this
                // page inside ML Kit's own UI; treat its first result as the
                // replacement image for the page being edited.
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uris.first())?.use { BitmapFactory.decodeStream(it) }
                    }
                    if (bitmap != null) {
                        repository.replacePageImage(pending.documentId, pending.pageId, bitmap)
                        refreshOpenDocument(pending.documentId)
                    }
                    currentScreen = Screen.DETAIL
                    // Clean up the temp gallery copy now that ML Kit is done with it —
                    // whether or not the user actually completed the edit, we don't
                    // want scratch copies permanently littering their real Photos.
                    withContext(Dispatchers.IO) { TempGalleryExport.cleanupAllTempCopies(applicationContext) }
                }
            }
        }
    }

    /** Loads every page across the whole library and opens the collage builder. */
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

    /**
     * Called once, when the person taps Save on the collage screen — not on
     * every edit, unlike the old preview-recompute approach this replaced.
     * The live grid in CollageScreen now renders its own preview directly in
     * Compose as the person drags/resizes, so there's nothing to keep
     * recomposing in the background here; this only needs to flatten the
     * final arrangement into a real bitmap once, for saving.
     *
     * Decodes only the pages actually assigned to a cell (not the whole
     * library) — composeWithAssignments needs a pageId->Bitmap map, not a
     * flat ordered list, since cells can be reassigned/cleared independently
     * of selection order.
     */
    private fun saveCollageAsNewDocument(
        template: com.example.scanapp.collage.CollageTemplate,
        pageSize: com.example.scanapp.collage.CollagePageSize,
        orientation: com.example.scanapp.collage.CollageOrientation,
        assignments: List<com.example.scanapp.collage.CollageCellAssignment>
    ) {
        val assignedPageIds = assignments.mapNotNull { it.pageId }.distinct()
        if (assignedPageIds.isEmpty()) return

        isSavingCollage = true
        lifecycleScope.launch {
            val title = "Collage ${SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())}"
            val pageById = collagePickerPages.associateBy { it.pageId }

            val bitmap = withContext(Dispatchers.IO) {
                val pageBitmaps = assignedPageIds.mapNotNull { id ->
                    val path = pageById[id]?.uri?.path ?: return@mapNotNull null
                    val decoded = BitmapFactory.decodeFile(path) ?: return@mapNotNull null
                    id to decoded
                }.toMap()

                if (pageBitmaps.isEmpty()) {
                    null
                } else {
                    val (canvasWidthPx, canvasHeightPx) = pageSize.canvasPx(orientation)
                    com.example.scanapp.collage.CollageCompositor.composeWithAssignments(
                        pageBitmaps = pageBitmaps,
                        assignments = assignments,
                        template = template,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx
                    )
                }
            }

            if (bitmap != null) {
                repository.saveBitmapAsNewDocument(bitmap, title)
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

    /**
     * Tapping a page now opens ML Kit's OWN scanner UI (Filters, Crop and
     * rotate, Clean) instead of an in-app editor — see TempGalleryExport's
     * doc comment for why this requires a manual extra step from the user
     * (ML Kit has no API to open an arbitrary existing file directly; the
     * user has to tap its gallery-import button and pick the temp copy we
     * just placed there).
     */
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

    /** Detail screen's "Export" action: reuse the existing size-limited export flow. */
    private fun openExportScreenForOpenDocument() {
        scannedPages = openDocumentPages.map { it.uri }
        exportResultText = null
        currentScreen = Screen.SCAN_EXPORT
    }

    /**
     * Detail screen's multi-select "Export selected" action: same export flow
     * as the full-document export, but scoped to only the pages the user
     * checked off, in their current (possibly reordered) display order.
     */
    private fun openExportScreenForSelectedPages(selectedPages: List<DetailPage>) {
        scannedPages = selectedPages.map { it.uri }
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

    /** Triggered from the Settings screen's "Check for update" row. */
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

    /**
     * Silent version of [checkForUpdate], run once from onCreate when the
     * "Check Updates on Start" toggle is on. Doesn't touch updateStatus —
     * that drives the always-visible Settings row, and a check the user
     * didn't ask for shouldn't make that row look like it's mid-check if
     * they happen to be on the Settings screen already for something else.
     *
     * On finding an update: shows the standard "update available" popup, and
     * if "Auto Install Updates" is also on, immediately starts downloading
     * the APK so the install prompt is ready the moment the person dismisses
     * (or even before — download proceeds independently of the popup).
     */
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
            // UpToDate and Error are both silently ignored here — a startup
            // check finding nothing wrong, or failing to reach GitHub (no
            // connection, rate limit, etc.), shouldn't interrupt the person
            // with a popup either way. They can always check manually from
            // Settings if they want to see why.
        }
    }

    /**
     * Downloads the update APK and launches the system install prompt on it.
     * Used by both the auto-install startup path and the popup's manual
     * "Update now" button (same download, same install call either way —
     * only the trigger differs).
     */
    private fun downloadAndLaunchInstall(apkUrl: String) {
        lifecycleScope.launch {
            isDownloadingUpdate = true
            updateDownloadError = null
            val result = com.example.scanapp.update.UpdateApkDownloader.download(applicationContext, apkUrl)
            isDownloadingUpdate = false
            when (result) {
                is com.example.scanapp.update.ApkDownloadResult.Success -> {
                    launchApkInstall(result.apkUri)
                    // The system installer (or the "allow unknown sources"
                    // settings screen, if that's what fired instead) is
                    // about to cover this dialog anyway — leaving it open
                    // underneath serves no purpose once that's launched.
                    showUpdateAvailableDialog = false
                }
                is com.example.scanapp.update.ApkDownloadResult.Error -> {
                    updateDownloadError = result.message
                }
            }
        }
    }

    /**
     * Fires the system package installer on a downloaded update APK.
     *
     * Uses the simpler ACTION_INSTALL_PACKAGE intent rather than the
     * session-based PackageInstaller API — ACTION_INSTALL_PACKAGE has been
     * deprecated since API 29 in favor of PackageInstaller, but still works
     * on every supported version including targetSdk 35, and avoids the
     * BroadcastReceiver + PendingIntent + session write/commit plumbing
     * PackageInstaller needs for what's still just "show the install
     * screen" — there's no silent-install capability either API gives a
     * normal, non-system app, so the simpler option costs nothing here.
     *
     * Either way, Android still requires one thing no API can skip: the
     * person tapping "Install" on the system's own confirmation screen.
     */
    private fun launchApkInstall(apkUri: Uri) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            // "Install unknown apps" hasn't been granted for this app yet.
            // Send the person straight to the system screen for it instead
            // of firing an install intent that Android will just block.
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
