package com.example.scanapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val GRID_COLUMNS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    title: String,
    pages: List<DetailPage>,
    onBackClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onShare: (OutputFormat) -> Unit,
    onExportClick: () -> Unit,
    onPageClick: (DetailPage) -> Unit,
    onAddPagesClick: () -> Unit,
    onDeletePage: (DetailPage) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onExportSelected: (List<DetailPage>) -> Unit,
    onEditSelected: (List<DetailPage>) -> Unit
) {
    // Local, instantly-reorderable copy of the pages. Kept in sync whenever a
    // new list comes down from the caller (pages added/removed/refreshed).
    var orderedPages by remember(pages) { mutableStateOf(pages) }

    var selectedPageIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSelecting = selectedPageIds.isNotEmpty()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Measured once from the first grid cell, used to translate a drag's
    // pixel offset into "how many grid slots did this move" for reordering.
    var cellWidthPx by remember { mutableStateOf(0) }
    var cellHeightPx by remember { mutableStateOf(0) }
    var draggingPageId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun togglePageSelection(page: DetailPage) {
        selectedPageIds = if (selectedPageIds.contains(page.pageId)) {
            selectedPageIds - page.pageId
        } else {
            selectedPageIds + page.pageId
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // We handle status/navigation bar insets ourselves inside the bars
        // below, so Scaffold shouldn't reserve extra space for them too.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 1. Liquid Glass Top Navigation Bar Configuration Overlay[cite: 1]
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isSelecting) selectedPageIds = emptySet() else onBackClick()
                            }
                        ) {
                            Icon(
                                imageVector = if (isSelecting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isSelecting) "Cancel selection" else "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isSelecting) "${selectedPageIds.size} selected" else title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isSelecting) {
                            IconButton(onClick = { showRenameDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Rename",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share as PDF") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onShare(OutputFormat.PDF)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share as JPEG") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onShare(OutputFormat.JPEG)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share as PNG") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onShare(OutputFormat.PNG)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete document") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // 2. Liquid Glass Unified Bottom Actions Bar Menu Overlay Dock[cite: 1]
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isSelecting) {
                            val selectedPages = orderedPages.filter { selectedPageIds.contains(it.pageId) }

                            // "Edit selected" Action Button Inside the Dock
                            Button(
                                onClick = { onEditSelected(selectedPages) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // "Export selected" Action Button Inside the Dock
                            Button(
                                onClick = { onExportSelected(selectedPages) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Export",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            // Left "Add pages" Action Button Inside the Dock[cite: 1]
                            Button(
                                onClick = onAddPagesClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Add pages",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            // Right "Export" Action Button Inside the Dock[cite: 1]
                            Button(
                                onClick = onExportClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Export",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Main Scanned Pages Document Grid Layout View[cite: 1]
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 12.dp,
                end = 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(orderedPages, key = { _, page -> page.pageId }) { index, page ->
                val isDragging = draggingPageId == page.pageId
                val isSelected = selectedPageIds.contains(page.pageId)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .onGloballyPositioned {
                            if (cellWidthPx == 0) cellWidthPx = it.size.width
                            if (cellHeightPx == 0) cellHeightPx = it.size.height
                        }
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.04f
                                scaleY = 1.04f
                            }
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .pointerInput(page.pageId, orderedPages.size) {
                            // A single gesture recognizer drives taps, long-press-to-select,
                            // and long-press-to-drag together, so they can't double-fire on
                            // the same touch stream the way two independent detectors
                            // (pointerInput + clickable) used to.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)

                                // Race: did the finger lift before the long-press
                                // timeout, or is it still down once the timeout hits?
                                val upBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                }

                                if (upBeforeLongPress != null) {
                                    // Released quickly, no movement: a plain tap.
                                    if (selectedPageIds.isNotEmpty()) {
                                        togglePageSelection(page)
                                    } else {
                                        onPageClick(page)
                                    }
                                    return@awaitEachGesture
                                }

                                // Long-press recognized and finger is still down. Enter
                                // drag tracking; if it never actually moves, treat the
                                // eventual release as a selection toggle instead.
                                var moved = false
                                dragOffset = Offset.Zero
                                draggingPageId = page.pageId

                                drag(down.id) { change ->
                                    moved = true
                                    change.consume()
                                    dragOffset += change.positionChange()

                                    if (cellWidthPx > 0 && cellHeightPx > 0) {
                                        val colShift = (dragOffset.x / cellWidthPx).roundToInt()
                                        val rowShift = (dragOffset.y / cellHeightPx).roundToInt()
                                        val shift = rowShift * GRID_COLUMNS + colShift
                                        if (shift != 0) {
                                            val fromIndex = orderedPages.indexOfFirst { it.pageId == page.pageId }
                                            val toIndex = (fromIndex + shift).coerceIn(0, orderedPages.lastIndex)
                                            if (toIndex != fromIndex) {
                                                orderedPages = orderedPages.toMutableList().apply {
                                                    add(toIndex, removeAt(fromIndex))
                                                }
                                                // Compensate for the distance the swap just covered so
                                                // the drag keeps tracking the finger smoothly instead of
                                                // jumping.
                                                val consumedShift = toIndex - fromIndex
                                                dragOffset -= Offset(
                                                    x = (consumedShift % GRID_COLUMNS) * cellWidthPx.toFloat(),
                                                    y = (consumedShift / GRID_COLUMNS) * cellHeightPx.toFloat()
                                                )
                                            }
                                        }
                                    }
                                }

                                draggingPageId = null
                                dragOffset = Offset.Zero
                                if (moved) {
                                    onReorder(orderedPages.map { it.pageId })
                                } else if (selectedPageIds.isNotEmpty()) {
                                    togglePageSelection(page)
                                } else {
                                    selectedPageIds = setOf(page.pageId)
                                }
                            }
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(page.uri),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Left Floating Page Counter Index Badge Bubble[cite: 1]
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(28.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Top Right Contextual Icon: delete page normally, selection
                    // checkmark while a multi-select is in progress.
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Black.copy(alpha = 0.5f)
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(28.dp)
                            .align(Alignment.TopEnd)
                            .clickable {
                                if (isSelecting) togglePageSelection(page) else onDeletePage(page)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSelecting) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = if (isSelecting) "Select page" else "Delete page",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) onRename(trimmed)
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete document?") },
            text = { Text("This will permanently delete \"$title\" and all its pages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
