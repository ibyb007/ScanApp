@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.collage.CollageDefaultArrangement
import com.example.scanapp.collage.CollageLayout
import com.example.scanapp.collage.CollageLayouts
import com.example.scanapp.collage.CollageOrientation
import com.example.scanapp.collage.CollagePage
import com.example.scanapp.collage.CollagePageSize
import com.example.scanapp.collage.CollagePictureFrame

/**
 * A single scanned page as shown in the collage page picker — just enough
 * to render a thumbnail and identify which page got assigned to a frame.
 */
data class CollagePickerPage(
    val pageId: Long,
    val uri: Uri,
    val documentTitle: String
)

private const val MIN_FRAME_FRACTION = 0.12f

/** Builds a fresh, empty output page sized for [layout]'s picture count. */
private fun emptyPage(layout: CollageLayout): CollagePage {
    val rects = CollageDefaultArrangement.ratesFor(layout.picturesPerPage)
    return CollagePage(frames = rects.map { rect ->
        CollagePictureFrame.empty(x = rect.left, y = rect.top, width = rect.width(), height = rect.height())
    })
}

@Composable
fun CollageScreen(
    allPages: List<CollagePickerPage>,
    isSaving: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (
        layout: CollageLayout,
        pageSize: CollagePageSize,
        orientation: CollageOrientation,
        pages: List<CollagePage>
    ) -> Unit
) {
    var selectedLayout by remember { mutableStateOf(CollageLayouts.ALL.first()) }
    var selectedPageSize by remember { mutableStateOf(CollagePageSize.A4) }
    var selectedOrientation by remember { mutableStateOf(CollageOrientation.PORTRAIT) }
    var activeTab by remember { mutableStateOf(CollageDockTab.PAGE) }

    var pages by remember { mutableStateOf(listOf(emptyPage(selectedLayout))) }
    var currentPageIndex by remember { mutableStateOf(0) }

    var selectedFrameIndex by remember { mutableStateOf<Int?>(null) }
    var showPagePicker by remember { mutableStateOf(false) }
    var isFullscreenEdit by remember { mutableStateOf(false) }

    val pageById = remember(allPages) { allPages.associateBy { it.pageId } }

    /** Re-flows every currently assigned picture across pages sized for [newLayout]. */
    fun resizePagesForLayout(newLayout: CollageLayout) {
        val assignedPageIds = pages.flatMap { it.frames }.mapNotNull { it.pageId }
        selectedLayout = newLayout
        pages = if (assignedPageIds.isEmpty()) {
            listOf(emptyPage(newLayout))
        } else {
            assignedPageIds.chunked(newLayout.picturesPerPage).map { chunk ->
                val rects = CollageDefaultArrangement.ratesFor(newLayout.picturesPerPage)
                CollagePage(frames = chunk.mapIndexed { index, pageId ->
                    val rect = rects.getOrNull(index) ?: rects.last()
                    CollagePictureFrame(
                        pageId = pageId,
                        xFraction = rect.left,
                        yFraction = rect.top,
                        widthFraction = rect.width(),
                        heightFraction = rect.height()
                    )
                })
            }
        }
        currentPageIndex = 0
        selectedFrameIndex = null
    }

    /**
     * Assigns [pickedPageId] to an empty frame on the current page. If the
     * current page has no empty frame left (already at this layout's
     * picture count), a brand new page is appended and the picture goes
     * there instead — this is the "extra page added automatically" behavior.
     */
    fun assignPickedPage(pickedPageId: Long) {
        val current = pages.getOrNull(currentPageIndex) ?: return
        val emptyIndex = current.frames.indexOfFirst { it.pageId == null }
        if (emptyIndex >= 0) {
            val updatedFrame = current.frames[emptyIndex].copy(pageId = pickedPageId)
            val updatedFrames = current.frames.toMutableList().also { it[emptyIndex] = updatedFrame }
            pages = pages.toMutableList().also { it[currentPageIndex] = current.copy(frames = updatedFrames) }
            selectedFrameIndex = emptyIndex
        } else {
            val newPage = emptyPage(selectedLayout)
            val firstFrame = newPage.frames.first().copy(pageId = pickedPageId)
            val newFrames = newPage.frames.toMutableList().also { it[0] = firstFrame }
            pages = pages + newPage.copy(frames = newFrames)
            currentPageIndex = pages.lastIndex
            selectedFrameIndex = 0
        }
        showPagePicker = false
    }

    fun updateFrame(frameIndex: Int, newFrame: CollagePictureFrame) {
        val current = pages.getOrNull(currentPageIndex) ?: return
        val updatedFrames = current.frames.toMutableList().also {
            if (frameIndex in it.indices) it[frameIndex] = newFrame
        }
        pages = pages.toMutableList().also { it[currentPageIndex] = current.copy(frames = updatedFrames) }
    }

    fun clearFrame(frameIndex: Int) {
        val current = pages.getOrNull(currentPageIndex) ?: return
        val frame = current.frames.getOrNull(frameIndex) ?: return
        updateFrame(frameIndex, frame.copy(pageId = null))
        selectedFrameIndex = null
    }

    fun goToPage(index: Int) {
        if (index in pages.indices) {
            currentPageIndex = index
            selectedFrameIndex = null
        }
    }

    fun addBlankPage() {
        pages = pages + emptyPage(selectedLayout)
        currentPageIndex = pages.lastIndex
        selectedFrameIndex = null
    }

    val hasAnyAssignedPage = pages.any { page -> page.frames.any { it.pageId != null } }

    Scaffold(
        topBar = {
            if (!isFullscreenEdit) {
                TopAppBar(
                    title = { Text("Create Collage") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { onSaveClick(selectedLayout, selectedPageSize, selectedOrientation, pages) },
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
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { isFullscreenEdit = true }
            ) {
                CollagePageCanvas(
                    page = pages.getOrNull(currentPageIndex) ?: emptyPage(selectedLayout),
                    pageSize = selectedPageSize,
                    orientation = selectedOrientation,
                    pageById = pageById,
                    selectedFrameIndex = selectedFrameIndex,
                    isInteractive = isFullscreenEdit,
                    onFrameTap = { index ->
                        if (!isFullscreenEdit) {
                            isFullscreenEdit = true
                        } else {
                            val frame = pages.getOrNull(currentPageIndex)?.frames?.getOrNull(index)
                            selectedFrameIndex = if (frame?.pageId == null) {
                                showPagePicker = true
                                index
                            } else if (selectedFrameIndex == index) {
                                null
                            } else {
                                index
                            }
                        }
                    },
                    onFrameChange = { index, frame -> updateFrame(index, frame) },
                    onFrameClear = { index -> clearFrame(index) },
                    modifier = Modifier.fillMaxSize()
                )

                if (!isFullscreenEdit) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Tap Workspace to Expand Full Editing Mode", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (!isFullscreenEdit) {
                HorizontalDivider()
                CollageBottomDock(
                    activeTab = activeTab,
                    onTabChange = { activeTab = it },
                    selectedLayout = selectedLayout,
                    onLayoutChange = { resizePagesForLayout(it) },
                    selectedPageSize = selectedPageSize,
                    selectedOrientation = selectedOrientation,
                    onPageSizeChange = { selectedPageSize = it },
                    onOrientationToggle = {
                        selectedOrientation = if (selectedOrientation == CollageOrientation.PORTRAIT) CollageOrientation.LANDSCAPE else CollageOrientation.PORTRAIT
                    },
                    onAddPagesClick = { showPagePicker = true }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isFullscreenEdit,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isFullscreenEdit = false; selectedFrameIndex = null }) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen")
                    }
                    Text("CamScanner Workspace Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onSaveClick(selectedLayout, selectedPageSize, selectedOrientation, pages) }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                CollagePageStrip(
                    pageCount = pages.size,
                    currentPageIndex = currentPageIndex,
                    onPageSelected = { goToPage(it) },
                    onAddPage = { addBlankPage() }
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollagePageCanvas(
                        page = pages.getOrNull(currentPageIndex) ?: emptyPage(selectedLayout),
                        pageSize = selectedPageSize,
                        orientation = selectedOrientation,
                        pageById = pageById,
                        selectedFrameIndex = selectedFrameIndex,
                        isInteractive = true,
                        onFrameTap = { index ->
                            val frame = pages.getOrNull(currentPageIndex)?.frames?.getOrNull(index)
                            selectedFrameIndex = if (frame?.pageId == null) {
                                showPagePicker = true
                                index
                            } else if (selectedFrameIndex == index) {
                                null
                            } else {
                                index
                            }
                        },
                        onFrameChange = { index, frame -> updateFrame(index, frame) },
                        onFrameClear = { index -> clearFrame(index) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                HorizontalDivider()
                CollageBottomDock(
                    activeTab = activeTab,
                    onTabChange = { activeTab = it },
                    selectedLayout = selectedLayout,
                    onLayoutChange = { resizePagesForLayout(it) },
                    selectedPageSize = selectedPageSize,
                    selectedOrientation = selectedOrientation,
                    onPageSizeChange = { selectedPageSize = it },
                    onOrientationToggle = {
                        selectedOrientation = if (selectedOrientation == CollageOrientation.PORTRAIT) CollageOrientation.LANDSCAPE else CollageOrientation.PORTRAIT
                    },
                    onAddPagesClick = { showPagePicker = true }
                )
            }
        }
    }

    if (showPagePicker) {
        PagePickerSheet(
            allPages = allPages,
            onPickPage = { pageId -> assignPickedPage(pageId) },
            onDismiss = { showPagePicker = false }
        )
    }
}

/** Small horizontal strip for jumping between output pages and adding new ones. */
@Composable
private fun CollagePageStrip(
    pageCount: Int,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onAddPage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (currentPageIndex > 0) onPageSelected(currentPageIndex - 1) }, enabled = currentPageIndex > 0) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
        }
        Text(
            "Page ${currentPageIndex + 1} of $pageCount",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = { if (currentPageIndex < pageCount - 1) onPageSelected(currentPageIndex + 1) }, enabled = currentPageIndex < pageCount - 1) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAddPage) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add page")
        }
    }
}

/**
 * Renders one output page: the blank canvas plus every picture frame on it,
 * each freely positioned/sized rather than slotted into a fixed grid cell.
 */
@Composable
private fun CollagePageCanvas(
    page: CollagePage,
    pageSize: CollagePageSize,
    orientation: CollageOrientation,
    pageById: Map<Long, CollagePickerPage>,
    selectedFrameIndex: Int?,
    isInteractive: Boolean,
    onFrameTap: (Int) -> Unit,
    onFrameChange: (Int, CollagePictureFrame) -> Unit,
    onFrameClear: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageAspect = if (orientation == CollageOrientation.PORTRAIT) {
        pageSize.widthInches / pageSize.heightInches
    } else {
        pageSize.heightInches / pageSize.widthInches
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .aspectRatio(pageAspect)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            val canvasWidthDp = maxWidth
            val canvasHeightDp = maxHeight

            page.frames.forEachIndexed { index, frame ->
                val picture = frame.pageId?.let { pageById[it] }
                val isSelected = selectedFrameIndex == index

                CollagePictureFrameView(
                    picture = picture,
                    frame = frame,
                    isSelected = isSelected,
                    isInteractive = isInteractive,
                    canvasWidthDp = canvasWidthDp.value,
                    canvasHeightDp = canvasHeightDp.value,
                    onTap = { onFrameTap(index) },
                    onFrameChange = { updated -> onFrameChange(index, updated) },
                    onClear = { onFrameClear(index) }
                )
            }
        }
    }
}

/**
 * One picture's draggable, resizable box on the page canvas. When selected
 * and interactive: dragging the picture body moves it; dragging the
 * bottom-right handle resizes it (anchored at its own top-left, so resizing
 * never has the picture jump position out from under your finger).
 */
@Composable
private fun CollagePictureFrameView(
    picture: CollagePickerPage?,
    frame: CollagePictureFrame,
    isSelected: Boolean,
    isInteractive: Boolean,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    onTap: () -> Unit,
    onFrameChange: (CollagePictureFrame) -> Unit,
    onClear: () -> Unit
) {
    val density = LocalDensity.current
    val canvasWidthPx = with(density) { canvasWidthDp.dp.toPx() }
    val canvasHeightPx = with(density) { canvasHeightDp.dp.toPx() }

    val frameLeftDp = frame.xFraction * canvasWidthDp
    val frameTopDp = frame.yFraction * canvasHeightDp
    val frameWidthDp = frame.widthFraction * canvasWidthDp
    val frameHeightDp = frame.heightFraction * canvasHeightDp

    Box(
        modifier = Modifier
            .offset(x = frameLeftDp.dp, y = frameTopDp.dp)
            .size(width = frameWidthDp.dp, height = frameHeightDp.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onTap)
    ) {
        if (picture != null) {
            var dragXFraction by remember(frame.pageId) { mutableStateOf(frame.xFraction) }
            var dragYFraction by remember(frame.pageId) { mutableStateOf(frame.yFraction) }

            Image(
                painter = rememberAsyncImagePainter(picture.uri),
                contentDescription = picture.documentTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(frame.pageId, isSelected, isInteractive) {
                        if (isSelected && isInteractive) {
                            detectDragGestures(
                                onDragStart = {
                                    dragXFraction = frame.xFraction
                                    dragYFraction = frame.yFraction
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val deltaX = dragAmount.x / canvasWidthPx
                                    val deltaY = dragAmount.y / canvasHeightPx
                                    dragXFraction = (dragXFraction + deltaX).coerceIn(0f, 1f - frame.widthFraction)
                                    dragYFraction = (dragYFraction + deltaY).coerceIn(0f, 1f - frame.heightFraction)
                                    onFrameChange(frame.copy(xFraction = dragXFraction, yFraction = dragYFraction))
                                }
                            )
                        }
                    }
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
            )

            if (picture != null) {
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
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                if (isInteractive) {
                    var dragWidthFraction by remember(frame.pageId) { mutableStateOf(frame.widthFraction) }
                    var dragHeightFraction by remember(frame.pageId) { mutableStateOf(frame.heightFraction) }

                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .pointerInput(frame.pageId) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragWidthFraction = frame.widthFraction
                                        dragHeightFraction = frame.heightFraction
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaWidth = dragAmount.x / canvasWidthPx
                                        val deltaHeight = dragAmount.y / canvasHeightPx

                                        val maxAllowedWidth = 1f - frame.xFraction
                                        val maxAllowedHeight = 1f - frame.yFraction
                                        dragWidthFraction = (dragWidthFraction + deltaWidth).coerceIn(MIN_FRAME_FRACTION, maxAllowedWidth)
                                        dragHeightFraction = (dragHeightFraction + deltaHeight).coerceIn(MIN_FRAME_FRACTION, maxAllowedHeight)

                                        onFrameChange(frame.copy(widthFraction = dragWidthFraction, heightFraction = dragHeightFraction))
                                    }
                                )
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.OpenWith, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else if (picture == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.InsertPageBreak, contentDescription = "Empty", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun CollageBottomDock(
    activeTab: CollageDockTab,
    onTabChange: (CollageDockTab) -> Unit,
    selectedLayout: CollageLayout,
    onLayoutChange: (CollageLayout) -> Unit,
    selectedPageSize: CollagePageSize,
    selectedOrientation: CollageOrientation,
    onPageSizeChange: (CollagePageSize) -> Unit,
    onOrientationToggle: () -> Unit,
    onAddPagesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        when (activeTab) {
            CollageDockTab.PAGE -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onAddPagesClick) {
                        Icon(Icons.Filled.InsertPageBreak, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tap Workspace to Assign Document Pages")
                    }
                }
            }
            CollageDockTab.TEMPLATE -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(CollageLayouts.ALL, key = { it.id }) { layout ->
                        FilterChip(
                            selected = layout.id == selectedLayout.id,
                            onClick = { onLayoutChange(layout) },
                            label = { Text(layout.displayName) }
                        )
                    }
                }
            }
            CollageDockTab.SIZE -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(CollagePageSize.values().toList(), key = { it.name }) { size ->
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
                                    if (selectedOrientation == CollageOrientation.PORTRAIT) Icons.Filled.CropPortrait else Icons.Filled.CropLandscape,
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

        Row(modifier = Modifier.fillMaxWidth()) {
            DockTabButton(
                label = "Page Pool",
                icon = Icons.Filled.InsertPageBreak,
                selected = activeTab == CollageDockTab.PAGE,
                onClick = { onTabChange(CollageDockTab.PAGE) },
                modifier = Modifier.weight(1f)
            )
            DockTabButton(
                label = "Layout",
                icon = Icons.Filled.ViewModule,
                selected = activeTab == CollageDockTab.TEMPLATE,
                onClick = { onTabChange(CollageDockTab.TEMPLATE) },
                modifier = Modifier.weight(1f)
            )
            DockTabButton(
                label = "Dimensions",
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
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun PagePickerSheet(
    allPages: List<CollagePickerPage>,
    onPickPage: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text("Pick a library scan entry", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))

            if (allPages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No scanned pages yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(4.dp).align(Alignment.BottomStart)
        ) {
            Text(
                page.documentTitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

private enum class CollageDockTab { PAGE, TEMPLATE, SIZE }
