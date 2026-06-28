package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat
import com.example.scanapp.data.DocumentSortBy
import com.example.scanapp.data.SortDirection

/** One saved scan shown in the Recents list. */
data class RecentDocument(
    val id: String,
    val title: String,
    val subtitle: String,   // e.g. "Saved 27/06/26 18:09 · 2 pages"
    val thumbnailUri: Uri?,
    val pageCount: Int,
    val modifiedAtMillis: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentDocuments: List<RecentDocument>,
    onScanClick: () -> Unit,
    onDocumentClick: (RecentDocument) -> Unit,
    onRename: (RecentDocument, newTitle: String) -> Unit = { _, _ -> },
    onDelete: (RecentDocument) -> Unit = {},
    onShare: (RecentDocument, OutputFormat) -> Unit = { _, _ -> },
    onDeleteMultiple: (List<RecentDocument>) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    sortBy: DocumentSortBy = DocumentSortBy.DATE_MODIFIED,
    sortDirection: SortDirection = SortDirection.DESCENDING,
    onSortChange: (DocumentSortBy, SortDirection) -> Unit = { _, _ -> },
    onMeClick: () -> Unit = {},
    onToolsClick: () -> Unit = {}
) {
    var actionSheetTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var renameTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var shareTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var deleteMultipleConfirm by remember { mutableStateOf(false) }

    // Selection mode state. Entered via long-press; exited by tapping the X
    // in the top bar or once the selection becomes empty after a delete.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    // Search bar starts collapsed to a small icon; tapping it (or the icon)
    // expands it into a full-width field. Collapses again when cleared+unfocused.
    var searchExpanded by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

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

    Scaffold(
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
        bottomBar = {
            Box(modifier = Modifier.background(Color.Transparent)) {
                ScanAppBottomNav(onMeClick = onMeClick, onToolsClick = onToolsClick)
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onScanClick) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Scan document")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Header row: compact search icon/field + "All files" + select/sort controls.
            // Search starts as a small icon button (per request) rather than a
            // full-width bar, and expands in place when tapped.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                } else {
                    Text(
                        "All files",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
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

            if (recentDocuments.isEmpty()) {
                EmptyRecentsState(
                    modifier = Modifier.weight(1f),
                    isSearching = searchQuery.isNotBlank()
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(recentDocuments, key = { it.id }) { doc ->
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
                            onMoreClick = { actionSheetTarget = doc }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 96.dp))
                    }
                }
            }
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
    }
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
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
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

        if (!selectionMode) {
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
 * Floating, frosted-glass-styled bottom nav: an inset pill rather than an
 * edge-to-edge bar, with a translucent layered surface to suggest glass.
 *
 * True backdrop blur (blurring the page content showing *through* the bar)
 * needs Android 12+ RenderEffect APIs or a third-party blur library — neither
 * is worth pulling in for one visual flourish on an app with minSdk 24, where
 * it would either silently do nothing pre-31 or need a new dependency. This
 * gets the same frosted-glass identity — translucency, a soft top highlight,
 * a hairline border catching the light — through layered semi-transparent
 * surfaces, which is what most "glass" UIs render as in practice anyway.
 */
@Composable
private fun ScanAppBottomNav(onMeClick: () -> Unit = {}, onToolsClick: () -> Unit = {}) {
    var selected by remember { mutableStateOf(0) }
    val items = listOf(
        Triple("Home", Icons.Filled.Home, 0),
        Triple("Files", Icons.Filled.Folder, 1),
        Triple("Tools", Icons.Filled.Description, 2),
        Triple("Me", Icons.Filled.Person, 3)
    )

    val glassColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                // Frosted base fill: translucent rather than opaque, so
                // whatever's behind (content scrolled under the floating bar)
                // still tints through faintly.
                .background(glassColor.copy(alpha = 0.72f))
                // Soft vertical sheen, brighter near the top edge — the part
                // of a glass pane that catches the most light — fading by
                // the bottom, which is what reads as "glass" rather than
                // just "a translucent rectangle."
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Black.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, icon, index) ->
                GlassNavItem(
                    label = label,
                    icon = icon,
                    selected = selected == index,
                    onClick = {
                        // Home/Files remain visual-only placeholders for now;
                        // Tools opens the collage builder and Me opens
                        // Settings — both navigate away immediately, so we
                        // don't persist them as the "selected" tab the way a
                        // real destination would.
                        when (index) {
                            2 -> onToolsClick()
                            3 -> onMeClick()
                            else -> selected = index
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = if (selected) {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            }
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
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
