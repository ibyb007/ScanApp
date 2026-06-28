package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** One page as shown in the detail screen grid. */
data class DetailPage(
    val pageId: Long,
    val pageIndex: Int,
    val uri: Uri
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    title: String,
    pages: List<DetailPage>,
    onBackClick: () -> Unit,
    onRename: (newTitle: String) -> Unit,
    onDelete: () -> Unit,
    onShare: (OutputFormat) -> Unit,
    onExportClick: () -> Unit,
    onPageClick: (DetailPage) -> Unit,
    onAddPagesClick: () -> Unit,
    onDeletePage: (DetailPage) -> Unit,
    onReorder: (orderedPageIds: List<Long>) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var deletePageTarget by remember { mutableStateOf<DetailPage?>(null) }

    var orderedPages by remember(pages) { mutableStateOf(pages) }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        orderedPages = orderedPages.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onReorder(orderedPages.map { it.pageId })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export (set size/format)") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            onClick = { showMenu = false; onExportClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { showMenu = false; showRenameDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete document") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { showMenu = false; showDeleteConfirm = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddPagesClick) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add pages")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(orderedPages, key = { it.pageId }) { page ->
                ReorderableItem(reorderableState, key = page.pageId) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "pageDragElevation")
                    PageThumbnail(
                        page = page,
                        displayIndex = orderedPages.indexOf(page),
                        elevation = elevation,
                        onClick = { onPageClick(page) },
                        onDeleteClick = { deletePageTarget = page },
                        dragHandleModifier = Modifier.longPressDraggableHandle()
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentTitle = title,
            onConfirm = { newTitle -> showRenameDialog = false; onRename(newTitle) },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete document?") },
            text = { Text("This will permanently delete \"$title\" and all its pages.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    deletePageTarget?.let { page ->
        AlertDialog(
            onDismissRequest = { deletePageTarget = null },
            title = { Text("Delete this page?") },
            text = { Text("Page ${page.pageIndex + 1} will be permanently removed from this document.") },
            confirmButton = {
                TextButton(onClick = { deletePageTarget = null; onDeletePage(page) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePageTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showShareSheet) {
        ShareFormatSheet(
            onFormatSelected = { format -> showShareSheet = false; onShare(format) },
            onDismiss = { showShareSheet = false }
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.PageThumbnail(
    page: DetailPage,
    displayIndex: Int,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    dragHandleModifier: Modifier
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .shadow(elevation, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = rememberAsyncImagePainter(page.uri),
            contentDescription = "Page ${displayIndex + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(6.dp).align(Alignment.BottomStart)
        ) {
            Text(
                "${displayIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(2.dp).align(Alignment.TopEnd)
        ) {
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete page",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(2.dp).align(Alignment.TopStart)
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier
                    .size(32.dp)
                    .padding(6.dp)
                    .then(dragHandleModifier)
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