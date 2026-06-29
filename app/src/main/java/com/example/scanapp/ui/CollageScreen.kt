package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.InsertPageBreak
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.collage.CollageCellAssignment
import com.example.scanapp.collage.CollageCellTransform
import com.example.scanapp.collage.CollageOrientation
import com.example.scanapp.collage.CollagePageSize
import com.example.scanapp.collage.CollageTemplate
import com.example.scanapp.collage.CollageTemplates
import kotlin.math.max

/** One selectable page in the cross-document picker. */
data class CollagePickerPage(
    val pageId: Long,
    val uri: Uri,
    val documentTitle: String
)

private enum class CollageDockTab { PAGE, TEMPLATE, SIZE }

/**
 * Cross-document collage builder: pick pages, arrange them live in a
 * directly-editable grid (tap a cell to select it, drag its handle to zoom,
 * tap the X to clear it), choose page size, save as a new document.
 *
 * Unlike the previous version of this screen, the grid here is rendered
 * directly in Compose rather than as a pre-composed bitmap preview — that's
 * what makes per-cell drag-to-resize feel immediate rather than waiting on a
 * recompute round-trip for every drag frame. CollageCompositor (bitmap
 * flattening) is only invoked once, at Save, using the same per-cell
 * transforms the live grid used — so the saved file matches what was seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    allPages: List<CollagePickerPage>,
    isSaving: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (
        template: CollageTemplate,
        pageSize: CollagePageSize,
        orientation: CollageOrientation,
        assignments: List<CollageCellAssignment>
    ) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf(CollageTemplates.ALL.first()) }
    var selectedPageSize by remember { mutableStateOf(CollagePageSize.A4) }
    var selectedOrientation by remember { mutableStateOf(CollageOrientation.PORTRAIT) }
    var activeTab by remember { mutableStateOf(CollageDockTab.PAGE) }

    // One assignment per cell of the CURRENT template, resized whenever the
    // template changes. Index-aligned with selectedTemplate.cells.
    var assignments by remember {
        mutableStateOf(List(selectedTemplate.cells.size) { CollageCellAssignment(pageId = null) })
    }

    // Which cell (by index) is currently selected, showing the green border +
    // resize handle + remove X — null means no cell is selected, just a plain grid.
    var selectedCellIndex by remember { mutableStateOf<Int?>(null) }

    var showPagePicker by remember { mutableStateOf(false) }
    var pendingPageSizeChange by remember { mutableStateOf<CollagePageSize?>(null) }

    val pageById = remember(allPages) { allPages.associateBy { it.pageId } }

    fun resizeAssignmentsForTemplate(newTemplate: CollageTemplate) {
        // Carry over existing page assignments by cell index where possible
        // (e.g. growing 2x1 -> 2x2 keeps the first two pages in place rather
        // than clearing everything), padding/truncating to the new cell count.
        val carried = List(newTemplate.cells.size) { index -> assignments.getOrNull(index) ?: CollageCellAssignment(pageId = null) }
        assignments = carried
        selectedTemplate = newTemplate
        selectedCellIndex = null
    }

    fun assignPageToCell(cellIndex: Int, pageId: Long) {
        assignments = assignments.toMutableList().also {
            it[cellIndex] = CollageCellAssignment(pageId = pageId)
        }
    }

    fun clearCell(cellIndex: Int) {
        assignments = assignments.toMutableList().also {
            it[cellIndex] = CollageCellAssignment(pageId = null)
        }
        selectedCellIndex = null
    }

    fun updateCellTransform(cellIndex: Int, transform: CollageCellTransform) {
        assignments = assignments.toMutableList().also {
            val current = it.getOrNull(cellIndex) ?: return
            it[cellIndex] = current.copy(transform = transform)
        }
    }

    val hasAnyAssignedPage = assignments.any { it.pageId != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Collage") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSaveClick(selectedTemplate, selectedPageSize, selectedOrientation, assignments) },
                        enabled = hasAnyAssignedPage && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Live, directly-editable grid — the dominant element on screen.
            // Tapping empty space (not a cell) deselects whichever cell was active.
            CollageLiveGrid(
                template = selectedTemplate,
                pageSize = selectedPageSize,
                orientation = selectedOrientation,
                assignments = assignments,
                pageById = pageById,
                selectedCellIndex = selectedCellIndex,
                onCellTap = { index ->
                    selectedCellIndex = if (assignments.getOrNull(index)?.pageId == null) {
                        // Empty cell tapped: jump straight to the page picker
                        // for it rather than just "selecting" nothing to drag.
                        showPagePicker = true
                        index
                    } else if (selectedCellIndex == index) {
                        null // tapping the already-selected cell again deselects it
                    } else {
                        index
                    }
                },
                onCellTransformChange = { index, transform -> updateCellTransform(index, transform) },
                onCellClear = { index -> clearCell(index) },
                modifier = Modifier.weight(1f)
            )

            HorizontalDivider()

            CollageBottomDock(
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                selectedTemplate = selectedTemplate,
                onTemplateChange = { resizeAssignmentsForTemplate(it) },
                selectedPageSize = selectedPageSize,
                selectedOrientation = selectedOrientation,
                onPageSizeChange = { newSize ->
                    if (hasAnyAssignedPage && newSize != selectedPageSize) {
                        // Matches the reference editor: changing page size
                        // after pages are already placed re-derives every
                        // cell's pixel dimensions, which can shift how each
                        // page's existing zoom/pan looks — worth a confirm
                        // rather than silently rearranging what someone just
                        // spent time positioning.
                        pendingPageSizeChange = newSize
                    } else {
                        selectedPageSize = newSize
                    }
                },
                onOrientationToggle = {
                    selectedOrientation = when (selectedOrientation) {
                        CollageOrientation.PORTRAIT -> CollageOrientation.LANDSCAPE
                        CollageOrientation.LANDSCAPE -> CollageOrientation.PORTRAIT
                    }
                },
                onAddPagesClick = { showPagePicker = true }
            )
        }
    }

    if (showPagePicker) {
        PagePickerSheet(
            allPages = allPages,
            onPickPage = { pageId ->
                val targetIndex = selectedCellIndex
                    ?: assignments.indexOfFirst { it.pageId == null }.takeIf { it >= 0 }
                if (targetIndex != null) {
                    assignPageToCell(targetIndex, pageId)
                    selectedCellIndex = targetIndex
                }
                showPagePicker = false
            },
            onDismiss = { showPagePicker = false }
        )
    }

    pendingPageSizeChange?.let { newSize ->
        AlertDialog(
            onDismissRequest = { pendingPageSizeChange = null },
            title = { Text("Change page size?") },
            text = { Text("If you change the size of the document, the layout will be rearranged. Proceed?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedPageSize = newSize
                    pendingPageSizeChange = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPageSizeChange = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * The live, directly-editable collage grid. Each cell is rendered as its own
 * Image with a graphicsLayer scale/translation driven by that cell's
 * transform — dragging updates state synchronously so the resize feels
 * immediate, with no bitmap recompute in the loop.
 */
@Composable
private fun CollageLiveGrid(
    template: CollageTemplate,
    pageSize: CollagePageSize,
    orientation: CollageOrientation,
    assignments: List<CollageCellAssignment>,
    pageById: Map<Long, CollagePickerPage>,
    selectedCellIndex: Int?,
    onCellTap: (Int) -> Unit,
    onCellTransformChange: (Int, CollageCellTransform) -> Unit,
    onCellClear: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageAspect = if (orientation == CollageOrientation.PORTRAIT) {
        pageSize.widthInches / pageSize.heightInches
    } else {
        pageSize.heightInches / pageSize.widthInches
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .aspectRatio(pageAspect)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
        ) {
            val canvasWidthDp = maxWidth
            val canvasHeightDp = maxHeight

            template.cells.forEachIndexed { index, cell ->
                val assignment = assignments.getOrNull(index) ?: CollageCellAssignment(pageId = null)
                val page = assignment.pageId?.let { pageById[it] }
                val isSelected = selectedCellIndex == index

                val cellWidthDp = (cell.rect.right - cell.rect.left) * canvasWidthDp.value
                val cellHeightDp = (cell.rect.bottom - cell.rect.top) * canvasHeightDp.value
                val cellLeftDp = cell.rect.left * canvasWidthDp.value
                val cellTopDp = cell.rect.top * canvasHeightDp.value

                CollageGridCell(
                    page = page,
                    transform = assignment.transform,
                    isSelected = isSelected,
                    cellWidthDp = cellWidthDp,
                    cellHeightDp = cellHeightDp,
                    onTap = { onCellTap(index) },
                    onTransformChange = { transform -> onCellTransformChange(index, transform) },
                    onClear = { onCellClear(index) },
                    modifier = Modifier
                        .offset(x = cellLeftDp.dp, y = cellTopDp.dp)
                        .size(width = cellWidthDp.dp, height = cellHeightDp.dp)
                )
            }
        }
    }
}

/**
 * One cell of the grid: shows its assigned page (fit-to-cell, then the
 * user's extra scale/pan), or an empty placeholder if unassigned. When
 * selected, overlays the green selection border, a draggable resize handle
 * in the bottom-right corner, and a remove (X) button — mirroring the
 * reference editor's selected-cell treatment.
 */
@Composable
private fun CollageGridCell(
    page: CollagePickerPage?,
    transform: CollageCellTransform,
    isSelected: Boolean,
    cellWidthDp: Float,
    cellHeightDp: Float,
    onTap: () -> Unit,
    onTransformChange: (CollageCellTransform) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cellWidthPx = with(density) { cellWidthDp.dp.toPx() }
    val cellHeightPx = with(density) { cellHeightDp.dp.toPx() }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onTap)
    ) {
        if (page != null) {
            Image(
                painter = rememberAsyncImagePainter(page.uri),
                contentDescription = page.documentTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Lambda form (not the eager-parameter overload) is
                        // required here, not just preferred — this block runs
                        // during layout/draw with the layer's own measured
                        // `size` available, which the eager-parameter
                        // overload has no access to at all. It also means a
                        // transform change only triggers relayout/redraw, not
                        // a full recomposition — meaningfully cheaper for a
                        // value that changes every frame of a drag gesture.
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetFractionX * cellWidthPx
                        translationY = transform.offsetFractionY * cellHeightPx
                    }
            )
        }

        if (isSelected) {
            // Selection border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
            )

            if (page != null) {
                // Remove (X) — top-start, matching the reference editor's
                // red circular X in the corner of the selected cell.
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(24.dp)
                        .clickable(onClick = onClear)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove page",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Resize handle — bottom-end corner, dragging adjusts zoom.
                // Dragging down-and-right zooms in (matches dragging a corner
                // "outward" to enlarge, the familiar resize-handle direction).
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Normalize against the CELL's own size, not
                                // this small handle's pointerInput size (which
                                // is just the 28dp handle itself) — otherwise
                                // the same finger movement would zoom by a
                                // wildly different amount depending on how
                                // large the cell happens to be on screen.
                                val cellDiagonalPx = max(cellWidthPx, cellHeightPx)
                                val delta = (dragAmount.x + dragAmount.y) / cellDiagonalPx
                                val newScale = (transform.scale + delta).coerceIn(1f, 4f)
                                onTransformChange(transform.copy(scale = newScale))
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.OpenWith,
                            contentDescription = "Resize",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else if (page == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.InsertPageBreak,
                    contentDescription = "Empty — tap to add a page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/** Bottom dock: Page / Template / Size tabs, matching the reference editor's bottom toolbar. */
@Composable
private fun CollageBottomDock(
    activeTab: CollageDockTab,
    onTabChange: (CollageDockTab) -> Unit,
    selectedTemplate: CollageTemplate,
    onTemplateChange: (CollageTemplate) -> Unit,
    selectedPageSize: CollagePageSize,
    selectedOrientation: CollageOrientation,
    onPageSizeChange: (CollagePageSize) -> Unit,
    onOrientationToggle: () -> Unit,
    onAddPagesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tab-specific strip, shown above the tab bar itself — same
        // structure as the reference editor (template/size swatches sit just
        // above the Page/Template/Size row that selects which strip shows).
        when (activeTab) {
            CollageDockTab.PAGE -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onAddPagesClick) {
                        Icon(Icons.Filled.InsertPageBreak, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tap a cell, or tap here to pick a page")
                    }
                }
            }
            CollageDockTab.TEMPLATE -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(CollageTemplates.ALL, key = { it.id }) { template ->
                        FilterChip(
                            selected = template.id == selectedTemplate.id,
                            onClick = { onTemplateChange(template) },
                            label = { Text(template.displayName) }
                        )
                    }
                }
            }
            CollageDockTab.SIZE -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(CollagePageSize.entries.toList(), key = { it.name }) { size ->
                        FilterChip(
                            selected = size == selectedPageSize,
                            onClick = { onPageSizeChange(size) },
                            label = { Text(size.displayName) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onOrientationToggle,
                            leadingIcon = {
                                Icon(
                                    if (selectedOrientation == CollageOrientation.PORTRAIT)
                                        Icons.Filled.CropPortrait
                                    else
                                        Icons.Filled.CropLandscape,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = {
                                Text(if (selectedOrientation == CollageOrientation.PORTRAIT) "Portrait" else "Landscape")
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // The tab bar itself — Page / Template / Size, matching the reference
        // editor's bottom row (its 4th tab, Add Watermark, is intentionally
        // out of scope here).
        Row(modifier = Modifier.fillMaxWidth()) {
            DockTabButton(
                label = "Page",
                icon = Icons.Filled.InsertPageBreak,
                selected = activeTab == CollageDockTab.PAGE,
                onClick = { onTabChange(CollageDockTab.PAGE) },
                modifier = Modifier.weight(1f)
            )
            DockTabButton(
                label = "Template",
                icon = Icons.Filled.ViewModule,
                selected = activeTab == CollageDockTab.TEMPLATE,
                onClick = { onTabChange(CollageDockTab.TEMPLATE) },
                modifier = Modifier.weight(1f)
            )
            DockTabButton(
                label = "Size",
                icon = Icons.Filled.PhotoSizeSelectActual,
                selected = activeTab == CollageDockTab.SIZE,
                onClick = { onTabChange(CollageDockTab.SIZE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DockTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Full-screen page picker bottom sheet — picking a page assigns it to the currently selected (or first empty) cell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagePickerSheet(
    allPages: List<CollagePickerPage>,
    onPickPage: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text(
                "Pick a page",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp)
            )

            if (allPages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No scanned pages yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(allPages, key = { it.pageId }) { page ->
                        PickerThumbnail(page = page, onClick = { onPickPage(page.pageId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerThumbnail(page: CollagePickerPage, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = rememberAsyncImagePainter(page.uri),
            contentDescription = page.documentTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(4.dp).align(Alignment.BottomStart)
        ) {
            Text(
                page.documentTitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
