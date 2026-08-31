package com.example.scanapp.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat
import com.example.scanapp.data.DocumentSortBy
import com.example.scanapp.data.SortDirection
import kotlinx.coroutines.launch

// Explicit import to fix the build failure
import com.example.scanapp.ui.ThemeMode

data class RecentDocument(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUri: Uri?,
    val pageCount: Int,
    val modifiedAtMillis: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentDocuments: List<RecentDocument>,
    onScanClick: () -> Unit,
    onImportPdfClick: () -> Unit = {},
    isImportingPdf: Boolean = false,
    pdfImportError: String? = null,
    pdfImportProgressText: String? = null,
    onDocumentClick: (RecentDocument) -> Unit,
    onRename: (RecentDocument, newTitle: String) -> Unit = { _, _ -> },
    onDelete: (RecentDocument) -> Unit = {},
    onShare: (RecentDocument, OutputFormat) -> Unit = { _, _ -> },
    onDeleteMultiple: (List<RecentDocument>) -> Unit = {},
    onReorder: (List<RecentDocument>) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    sortBy: DocumentSortBy = DocumentSortBy.DATE_MODIFIED,
    sortDirection: SortDirection = SortDirection.DESCENDING,
    onSortChange: (DocumentSortBy, SortDirection) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeSelected: (ThemeMode, Offset) -> Unit = { _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    navBarGlassOpacity: Float = NavBarPreferences.DEFAULT_GLASS_OPACITY
) {
    var actionSheetTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var renameTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var shareTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var deleteMultipleConfirm by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var searchExpanded by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    var toggleButtonCenter by remember { mutableStateOf(Offset.Zero) }

    var orderedDocs by remember { mutableStateOf(recentDocuments) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0) }
    LaunchedEffect(recentDocuments) {
        if (draggingId == null) orderedDocs = recentDocuments
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(pdfImportError) {
        if (pdfImportError != null) {
            snackbarHostState.showSnackbar(pdfImportError)
        }
    }

    fun clearSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun toggleSelected(doc: RecentDocument) {
        selectedIds = if (selectedIds.contains(doc.id)) {
            selectedIds - doc.id
        } else {
            selectedIds + doc.id
        }
        if (selectedIds.isEmpty()) selectionMode = false
    }

    fun endDrag() {
        draggingId = null
        dragOffsetY = 0f
        onReorder(orderedDocs)
    }

    BackHandler(enabled = selectionMode || searchExpanded) {
        if (selectionMode) {
            clearSelection()
        } else if (searchExpanded) {
            onSearchQueryChange("")
            searchExpanded = false
        }
    }

    var navBarHeightPx by remember { mutableStateOf(0) }
    val navBarHeightDp = with(LocalDensity.current) { navBarHeightPx.toDp() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            Box(Modifier.padding(bottom = navBarHeightDp)) {
                SnackbarHost(snackbarHostState)
            }
        },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            shareTarget = recentDocuments.firstOrNull { it.id in selectedIds }
                        }, enabled = selectedIds.size == 1) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(
                            onClick = { deleteMultipleConfirm = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = navBarHeightDp)
                ) {
                    if (isImportingPdf && pdfImportProgressText != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                pdfImportProgressText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = { if (!isImportingPdf) onImportPdfClick() }
                    ) {
                        if (isImportingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Import PDF")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FloatingActionButton(onClick = onScanClick) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Scan document")
                    }
                }
            }
        }
    ) { padding ->
        var headerHeightPx by remember { mutableStateOf(0) }
        val headerHeightDp = with(LocalDensity.current) { headerHeightPx.toDp() }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (recentDocuments.isEmpty()) {
                EmptyRecentsState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerHeightDp, bottom = navBarHeightDp),
                    isSearching = searchQuery.isNotBlank()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = headerHeightDp, bottom = navBarHeightDp)
                ) {
                    items(orderedDocs, key = { it.id }) { doc ->
                        val isDragging = draggingId == doc.id
                        Box(
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                }
                                .onGloballyPositioned { coords ->
                                    if (rowHeightPx == 0) rowHeightPx = coords.size.height
                                }
                        ) {
                            Column {
                                RecentDocumentRow(
                                    doc = doc,
                                    selectionMode = selectionMode,
                                    selected = doc.id in selectedIds,
                                    onClick = {
                                        if (selectionMode) toggleSelected(doc) else onDocumentClick(doc)
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = setOf(doc.id)
                                        } else {
                                            toggleSelected(doc)
                                        }
                                    },
                                    onMoreClick = { actionSheetTarget = doc },
                                    showDragHandle = selectionMode && searchQuery.isBlank(),
                                    onDragStart = { draggingId = doc.id; dragOffsetY = 0f },
                                    onDrag = { deltaY ->
                                        dragOffsetY += deltaY
                                        val heightPx = rowHeightPx
                                        if (heightPx > 0) {
                                            val fromIndex = orderedDocs.indexOfFirst { it.id == doc.id }
                                            if (fromIndex != -1) {
                                                if (dragOffsetY > heightPx / 2 && fromIndex < orderedDocs.lastIndex) {
                                                    orderedDocs = orderedDocs.toMutableList().apply {
                                                        add(fromIndex + 1, removeAt(fromIndex))
                                                    }
                                                    dragOffsetY -= heightPx
                                                } else if (dragOffsetY < -heightPx / 2 && fromIndex > 0) {
                                                    orderedDocs = orderedDocs.toMutableList().apply {
                                                        add(fromIndex - 1, removeAt(fromIndex))
                                                    }
                                                    dragOffsetY += heightPx
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = { endDrag() }
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 116.dp))
                            }
                        }
                    }
                }
            }

            // Header background is always fully transparent; only its text/icons remain visible.
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .onGloballyPositioned { headerHeightPx = it.size.height }
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!selectionMode) Modifier.statusBarsPadding() else Modifier)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Search files") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    onSearchQueryChange("")
                                    searchExpanded = false
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                        )
                    } else {
                        Text(
                            "All files",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            IconButton(
                                onClick = { themeMenuExpanded = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        toggleButtonCenter = Offset(
                                            pos.x + coords.size.width / 2f,
                                            pos.y + coords.size.height / 2f
                                        )
                                    }
                            ) {
                                Icon(
                                    when (themeMode) {
                                        ThemeMode.AUTO -> Icons.Filled.BrightnessAuto
                                        ThemeMode.DARK -> Icons.Filled.DarkMode
                                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    },
                                    contentDescription = "Day/night mode"
                                )
                            }
                            ThemeMenu(
                                expanded = themeMenuExpanded,
                                current = themeMode,
                                onDismiss = { themeMenuExpanded = false },
                                onSelect = { newMode ->
                                    themeMenuExpanded = false
                                    onThemeModeSelected(newMode, toggleButtonCenter)
                                }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { searchExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            SortMenu(
                                expanded = sortMenuExpanded,
                                sortBy = sortBy,
                                direction = sortDirection,
                                onDismiss = { sortMenuExpanded = false },
                                onSelect = { newSortBy, newDirection ->
                                    sortMenuExpanded = false
                                    onSortChange(newSortBy, newDirection)
                                }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        if (recentDocuments.isNotEmpty()) {
                            TextButton(onClick = {
                                selectionMode = true
                                selectedIds = recentDocuments.map { it.id }.toSet()
                            }) {
                                Text("Select all")
                            }
                        }
                    }
                }
                }
            }

            ScanAppBottomNav(
                onSettingsClick = onSettingsClick,
                onToolsClick = onToolsClick,
                onBackupClick = onBackupClick,
                glassOpacity = navBarGlassOpacity,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { navBarHeightPx = it.size.height }
            )
        }
    }

    actionSheetTarget?.let { doc ->
        DocumentActionSheet(
            onDismiss = { actionSheetTarget = null },
            onRenameClick = { actionSheetTarget = null; renameTarget = doc },
            onDeleteClick = { actionSheetTarget = null; deleteTarget = doc },
            onShareClick = { actionSheetTarget = null; shareTarget = doc }
        )
    }

    renameTarget?.let { doc ->
        RenameDialog(
            currentTitle = doc.title,
            onConfirm = { newTitle -> renameTarget = null; onRename(doc, newTitle) },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete document?") },
            text = { Text("This will permanently delete \"${doc.title}\" and all its pages.") },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDelete(doc) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (deleteMultipleConfirm) {
        val targets = recentDocuments.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { deleteMultipleConfirm = false },
            title = { Text("Delete ${targets.size} document${if (targets.size == 1) "" else "s"}?") },
            text = { Text("This will permanently delete the selected document(s) and all their pages.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteMultipleConfirm = false
                    onDeleteMultiple(targets)
                    clearSelection()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteMultipleConfirm = false }) { Text("Cancel") }
            }
        )
    }

    shareTarget?.let { doc ->
        ShareFormatSheet(
            onFormatSelected = { format -> shareTarget = null; onShare(doc, format); clearSelection() },
            onDismiss = { shareTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenu(
    expanded: Boolean,
    sortBy: DocumentSortBy,
    direction: SortDirection,
    onDismiss: () -> Unit,
    onSelect: (DocumentSortBy, SortDirection) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        SortMenuItem("Name (A–Z)", sortBy == DocumentSortBy.NAME && direction == SortDirection.ASCENDING) {
            onSelect(DocumentSortBy.NAME, SortDirection.ASCENDING)
        }
        SortMenuItem("Name (Z–A)", sortBy == DocumentSortBy.NAME && direction == SortDirection.DESCENDING) {
            onSelect(DocumentSortBy.NAME, SortDirection.DESCENDING)
        }
        SortMenuItem(
            "Date modified (newest)",
            sortBy == DocumentSortBy.DATE_MODIFIED && direction == SortDirection.DESCENDING
        ) { onSelect(DocumentSortBy.DATE_MODIFIED, SortDirection.DESCENDING) }
        SortMenuItem(
            "Date modified (oldest)",
            sortBy == DocumentSortBy.DATE_MODIFIED && direction == SortDirection.ASCENDING
        ) { onSelect(DocumentSortBy.DATE_MODIFIED, SortDirection.ASCENDING) }
        SortMenuItem(
            "Page count (most)",
            sortBy == DocumentSortBy.PAGE_COUNT && direction == SortDirection.DESCENDING
        ) { onSelect(DocumentSortBy.PAGE_COUNT, SortDirection.DESCENDING) }
        SortMenuItem(
            "Page count (fewest)",
            sortBy == DocumentSortBy.PAGE_COUNT && direction == SortDirection.ASCENDING
        ) { onSelect(DocumentSortBy.PAGE_COUNT, SortDirection.ASCENDING) }
        SortMenuItem(
            "Custom order (drag to arrange)",
            sortBy == DocumentSortBy.MANUAL
        ) { onSelect(DocumentSortBy.MANUAL, SortDirection.ASCENDING) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeMenu(
    expanded: Boolean,
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            "Appearance",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        ThemeMenuItem("Light", Icons.Filled.LightMode, current == ThemeMode.LIGHT) {
            onSelect(ThemeMode.LIGHT)
        }
        ThemeMenuItem("Dark", Icons.Filled.DarkMode, current == ThemeMode.DARK) {
            onSelect(ThemeMode.DARK)
        }
        ThemeMenuItem("Auto (system default)", Icons.Filled.BrightnessAuto, current == ThemeMode.AUTO) {
            onSelect(ThemeMode.AUTO)
        }
    }
}

@Composable
private fun ThemeMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        trailingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) }
    )
}

@Composable
private fun SortMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        onClick = onClick,
        trailingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentDocumentRow(
    doc: RecentDocument,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    showDragHandle: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            if (doc.thumbnailUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(doc.thumbnailUri),
                    contentDescription = doc.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                doc.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showDragHandle) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(8.dp)
                    .pointerInput(doc.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        )
                    }
            )
        } else if (!selectionMode) {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
        }
    }
}

@Composable
private fun EmptyRecentsState(modifier: Modifier = Modifier, isSearching: Boolean = false) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isSearching) Icons.Filled.Search else Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSearching) "No documents match your search" else "No scans yet — tap the camera button to start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The floating bottom nav, shared by every top-level screen (Home, Collage,
 * Backup, Settings). Rendered as clean "liquid glass": a flat translucent
 * tinted pill with a subtle outline, no sheens/highlights/blur — just a
 * simple frosted look. [glassOpacity] (0 = almost see-through, 1 = fully
 * opaque) is user-controlled from the Settings screen and persisted via
 * [NavBarPreferences].
 *
 * The selection indicator is a custom "liquid" pill (see
 * [LiquidTabIndicator]) that stretches toward the newly selected tab before
 * catching up and snapping back into shape, rather than Material3's default
 * NavigationBar/NavigationBarItem (which only cross-fades). Items are
 * hand-built too — a Column centered both ways in its slot — so the
 * icon+label are always dead-center in the pill regardless of its height,
 * instead of relying on NavigationBarItem's fixed internal paddings (tuned
 * for the default 80dp-tall bar) which looked slightly off-center once the
 * bar was shrunk to fit here.
 */
@Composable
internal fun ScanAppBottomNav(
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    glassOpacity: Float = NavBarPreferences.DEFAULT_GLASS_OPACITY,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onBackupClick: () -> Unit = {}
) {
    val items = listOf(
        Triple("Home", Icons.Filled.Home, 0),
        Triple("Tools", Icons.Filled.Description, 1),
        Triple("Backup", Icons.Filled.Folder, 2),
        Triple("Settings", Icons.Filled.Settings, 3)
    )

    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(68.dp)
            .clip(shape)
            .cleanGlassBackground(
                tint = MaterialTheme.colorScheme.surfaceContainer,
                opacity = glassOpacity
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = shape
            )
    ) {
        LiquidTabIndicator(
            selectedIndex = selectedIndex,
            itemCount = items.size,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { (label, icon, index) ->
                ScanAppBottomNavItem(
                    icon = icon,
                    label = label,
                    selected = selectedIndex == index,
                    onClick = {
                        when (index) {
                            0 -> onHomeClick()
                            1 -> onToolsClick()
                            2 -> onBackupClick()
                            3 -> onSettingsClick()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

/**
 * One tab: icon above label, both perfectly centered — horizontally and
 * vertically — in the slot it's given, so alignment stays correct no
 * matter the bar's height. The icon does a small springy "pop" on
 * selection and both icon and label crossfade color, echoing the bounce in
 * the reference animation without needing a bespoke keyframe animation.
 */
@Composable
private fun ScanAppBottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navItemColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navItemIconScale"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/**
 * The selection pill behind the active tab. Instead of just cross-fading
 * or sliding at a constant rate, its two edges animate independently: the
 * edge in the direction of travel races ahead on a fast spring while the
 * trailing edge lags on a slower one, so the pill visibly stretches like a
 * blob of liquid while moving and then snaps back to a clean capsule once
 * both edges arrive — matching the reference animation.
 */
@Composable
private fun LiquidTabIndicator(
    selectedIndex: Int,
    itemCount: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val slotWidthPx = if (itemCount > 0) totalWidthPx / itemCount else 0f
        val insetPx = with(density) { 6.dp.toPx() }

        val targetLeft = selectedIndex * slotWidthPx + insetPx
        val targetRight = (selectedIndex + 1) * slotWidthPx - insetPx

        val leftEdge = remember { Animatable(targetLeft) }
        val rightEdge = remember { Animatable(targetRight) }
        var previousIndex by remember { mutableStateOf(selectedIndex) }

        LaunchedEffect(selectedIndex, totalWidthPx) {
            val movingForward = selectedIndex >= previousIndex
            previousIndex = selectedIndex
            val leadingSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 380f)
            val trailingSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 200f)
            if (movingForward) {
                launch { rightEdge.animateTo(targetRight, leadingSpec) }
                launch { leftEdge.animateTo(targetLeft, trailingSpec) }
            } else {
                launch { leftEdge.animateTo(targetLeft, leadingSpec) }
                launch { rightEdge.animateTo(targetRight, trailingSpec) }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = leftEdge.value.coerceAtMost(rightEdge.value)
            val right = rightEdge.value.coerceAtLeast(leftEdge.value)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }
    }
}

/**
 * Flat translucent tint used for every "liquid glass" surface in the app
 * (the bottom nav, the Settings header): just the theme's container color
 * at a caller-chosen alpha. Deliberately plain — no gradients, sheens, or
 * blurred highlight blobs — for a clean frosted look rather than a
 * reflective one. [opacity] is clamped to [NavBarPreferences]'s allowed
 * range so callers can pass a raw slider value straight through.
 */
internal fun Modifier.cleanGlassBackground(tint: Color, opacity: Float): Modifier {
    val clamped = opacity.coerceIn(
        NavBarPreferences.MIN_GLASS_OPACITY,
        NavBarPreferences.MAX_GLASS_OPACITY
    )
    return this.background(tint.copy(alpha = clamped))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentActionSheet(
    onDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onShareClick)
            )
            ListItem(
                headlineContent = { Text("Rename") },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onRenameClick)
            )
            ListItem(
                headlineContent = { Text("Delete") },
                leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onDeleteClick)
            )
        }
    }
}

@Composable
private fun RenameDialog(currentTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename document") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareFormatSheet(onFormatSelected: (OutputFormat) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Share as", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            ListItem(
                headlineContent = { Text("PDF") },
                supportingContent = { Text("All pages combined into one PDF") },
                modifier = Modifier.clickable { onFormatSelected(OutputFormat.PDF) }
            )
            ListItem(
                headlineContent = { Text("JPEG images") },
                supportingContent = { Text("Each page as a separate image") },
                modifier = Modifier.clickable { onFormatSelected(OutputFormat.JPEG) }
            )
        }
    }
}
