@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.scanapp.ui

import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.InsertPageBreak
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.collage.CollageDefaultArrangement
import com.example.scanapp.collage.CollageFit
import com.example.scanapp.collage.CollageLayout
import com.example.scanapp.collage.CollageLayouts
import com.example.scanapp.collage.CollageOrientation
import com.example.scanapp.collage.CollagePage
import com.example.scanapp.collage.CollagePageSize
import com.example.scanapp.collage.CollagePictureFrame
import com.example.scanapp.collage.canvasPx
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A single scanned page as shown in the collage page picker — just enough
 * to render a thumbnail and identify which page got assigned to a frame.
 * [aspectRatio] (width / height of the stored picture) is what lets frames
 * match the picture's own shape so it is never stretched.
 */
data class CollagePickerPage(
    val pageId: Long,
    val uri: Uri,
    val documentTitle: String,
    val aspectRatio: Float = 0.75f
)

/** Smallest a frame may shrink to, as a fraction of the page's width / height. */
private const val MIN_FRAME_SIDE_FRACTION = 0.08f

/** Diameter of the corner handles (delete / rotate / resize). */
private const val HANDLE_SIZE_DP = 26f

private fun canvasAspectOf(size: CollagePageSize, orientation: CollageOrientation): Float {
    val (w, h) = size.canvasPx(orientation)
    return w.toFloat() / h.toFloat()
}

/** Sensible starting template for however many pages were handed in. */
private fun layoutForCount(count: Int): CollageLayout = when {
    count >= 4 -> CollageLayouts.FOUR_PER_PAGE
    count == 3 -> CollageLayouts.THREE_PER_PAGE
    count == 2 -> CollageLayouts.TWO_PER_PAGE
    count == 1 -> CollageLayouts.ONE_PER_PAGE
    else -> CollageLayouts.ALL.first()
}

private fun cellFrame(rect: RectF) =
    CollagePictureFrame.empty(x = rect.left, y = rect.top, width = rect.width(), height = rect.height())

/** Fits this frame's rectangle (treated as a slot) around a picture of [imageAspect] — no stretching. */
private fun CollagePictureFrame.fittedFor(imageAspect: Float, canvasAspect: Float): CollagePictureFrame {
    val cell = RectF(xFraction, yFraction, xFraction + widthFraction, yFraction + heightFraction)
    val fitted = CollageFit.fitInto(cell, imageAspect, canvasAspect)
    return copy(
        xFraction = fitted.left,
        yFraction = fitted.top,
        widthFraction = fitted.width(),
        heightFraction = fitted.height(),
        rotationDegrees = 0f
    )
}

/** Builds a fresh, empty output page sized for [layout]'s picture count. */
private fun emptyPage(layout: CollageLayout): CollagePage {
    val rects = CollageDefaultArrangement.ratesFor(layout.picturesPerPage, layout.gridCols, layout.gridRows)
    return CollagePage(frames = rects.map { cellFrame(it) })
}

/**
 * Builds pages for [layout] from [pictureIds] (in order), one page per
 * [CollageLayout.picturesPerPage] pictures. Every picture is fitted into its
 * slot at its own aspect ratio. A page always has the layout's full frame
 * count, even when the last chunk is short.
 */
private fun buildPagesForPictureIds(
    layout: CollageLayout,
    pictureIds: List<Long>,
    canvasAspect: Float,
    aspectOf: (Long) -> Float
): List<CollagePage> {
    val rects = CollageDefaultArrangement.ratesFor(layout.picturesPerPage, layout.gridCols, layout.gridRows)
    if (pictureIds.isEmpty()) return listOf(CollagePage(frames = rects.map { cellFrame(it) }))
    return pictureIds.chunked(layout.picturesPerPage).map { chunk ->
        CollagePage(frames = rects.mapIndexed { index, rect ->
            val id = chunk.getOrNull(index)
            val slot = cellFrame(rect)
            if (id == null) slot else slot.copy(pageId = id).fittedFor(aspectOf(id), canvasAspect)
        })
    }
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
    ) -> Unit,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    navBarGlassOpacity: Float = NavBarPreferences.DEFAULT_GLASS_OPACITY,
    /** Pages to drop straight into the collage on open (e.g. the pages selected in a document). */
    initialPageIds: List<Long> = emptyList()
) {
    val pageById = remember(allPages) { allPages.associateBy { it.pageId } }
    fun aspectOf(id: Long): Float = pageById[id]?.aspectRatio ?: 0.75f

    val initialLayout = remember { layoutForCount(initialPageIds.size) }
    var selectedLayout by remember { mutableStateOf(initialLayout) }
    var selectedPageSize by remember { mutableStateOf(CollagePageSize.A4) }
    var selectedOrientation by remember { mutableStateOf(CollageOrientation.PORTRAIT) }
    var activeTab by remember { mutableStateOf(CollageDockTab.PAGE) }

    var pages by remember {
        mutableStateOf(
            if (initialPageIds.isEmpty()) {
                listOf(emptyPage(initialLayout))
            } else {
                buildPagesForPictureIds(
                    initialLayout,
                    initialPageIds,
                    canvasAspectOf(CollagePageSize.A4, CollageOrientation.PORTRAIT),
                    ::aspectOf
                )
            }
        )
    }
    var currentPageIndex by remember { mutableStateOf(0) }
    var selectedFrameIndex by remember { mutableStateOf<Int?>(null) }
    var showPagePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canvasAspect = canvasAspectOf(selectedPageSize, selectedOrientation)

    /** Re-flows every currently assigned picture across pages sized for [newLayout]. */
    fun resizePagesForLayout(newLayout: CollageLayout) {
        val assignedPageIds = pages.flatMap { it.frames }.mapNotNull { it.pageId }
        selectedLayout = newLayout
        pages = buildPagesForPictureIds(newLayout, assignedPageIds, canvasAspect, ::aspectOf)
        currentPageIndex = 0
        selectedFrameIndex = null
    }

    /** Page size / orientation switch: keep each picture's centre and size, never distort it. */
    fun applyCanvasChange(newSize: CollagePageSize, newOrientation: CollageOrientation) {
        val oldAspect = canvasAspect
        val newAspect = canvasAspectOf(newSize, newOrientation)
        pages = pages.map { page ->
            page.copy(frames = page.frames.map { frame ->
                val id = frame.pageId
                if (id == null) frame else CollageFit.rescaleForCanvas(frame, aspectOf(id), oldAspect, newAspect)
            })
        }
        selectedPageSize = newSize
        selectedOrientation = newOrientation
    }

    /**
     * Assigns [pickedPageId] to an empty frame on the current page, fitted to
     * the picture's own shape. With no empty frame left, a new page is added.
     */
    fun assignPickedPage(pickedPageId: Long) {
        val aspect = aspectOf(pickedPageId)
        val current = pages.getOrNull(currentPageIndex) ?: return
        val emptyIndex = current.frames.indexOfFirst { it.pageId == null }
        if (emptyIndex >= 0) {
            val updatedFrame = current.frames[emptyIndex].copy(pageId = pickedPageId).fittedFor(aspect, canvasAspect)
            val updatedFrames = current.frames.toMutableList().also { it[emptyIndex] = updatedFrame }
            pages = pages.toMutableList().also { it[currentPageIndex] = current.copy(frames = updatedFrames) }
            selectedFrameIndex = emptyIndex
        } else {
            val newPage = emptyPage(selectedLayout)
            val firstFrame = newPage.frames.first().copy(pageId = pickedPageId).fittedFor(aspect, canvasAspect)
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

    /** Removes the picture from a frame, with a "Deleted · Undo" snackbar like CamScanner. */
    fun clearFrame(frameIndex: Int) {
        val pageIndex = currentPageIndex
        val current = pages.getOrNull(pageIndex) ?: return
        val frame = current.frames.getOrNull(frameIndex) ?: return
        if (frame.pageId == null) return
        updateFrame(frameIndex, frame.copy(pageId = null, rotationDegrees = 0f))
        selectedFrameIndex = null
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                val page = pages.getOrNull(pageIndex)
                if (page != null && frameIndex in page.frames.indices && page.frames[frameIndex].pageId == null) {
                    val restored = page.frames.toMutableList().also { it[frameIndex] = frame }
                    pages = pages.toMutableList().also { it[pageIndex] = page.copy(frames = restored) }
                    currentPageIndex = pageIndex
                    selectedFrameIndex = frameIndex
                }
            }
        }
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

    // The editor Column reserves the floating nav's height so the fixed
    // CollageBottomDock toolbar still sits fully above the pill.
    // The floating glass nav now lives in MainActivity (see GlassNav.kt); pad content by its height.
    val navBarHeightDp = LocalFloatingNavHeight.current

    Scaffold(
        // See HomeScreen: stop Scaffold from painting an opaque
        // system-bar-height strip behind the floating bottom nav pill.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CollageTopBar(
                onBackClick = onBackClick,
                isSaving = isSaving,
                canSave = hasAnyAssignedPage && !isSaving,
                onSaveClick = { onSaveClick(selectedLayout, selectedPageSize, selectedOrientation, pages) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navBarHeightDp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    CollagePageCanvas(
                        page = pages.getOrNull(currentPageIndex) ?: emptyPage(selectedLayout),
                        canvasAspect = canvasAspect,
                        pageById = pageById,
                        selectedFrameIndex = selectedFrameIndex,
                        onFrameTap = { index ->
                            val frame = pages.getOrNull(currentPageIndex)?.frames?.getOrNull(index)
                            selectedFrameIndex = index
                            if (frame?.pageId == null) showPagePicker = true
                        },
                        onBackgroundTap = { selectedFrameIndex = null },
                        onFrameChange = { index, frame -> updateFrame(index, frame) },
                        onFrameClear = { index -> clearFrame(index) },
                        modifier = Modifier.fillMaxSize()
                    )

                    CollagePageNavigator(
                        currentIndex = currentPageIndex,
                        total = pages.size,
                        onPrevious = { goToPage(currentPageIndex - 1) },
                        onNext = { goToPage(currentPageIndex + 1) },
                        onAddPage = { addBlankPage() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                    )

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                    )
                }

                CollageBottomDock(
                    activeTab = activeTab,
                    onTabChange = { activeTab = it },
                    selectedLayout = selectedLayout,
                    onLayoutChange = { resizePagesForLayout(it) },
                    selectedPageSize = selectedPageSize,
                    selectedOrientation = selectedOrientation,
                    onPageSizeChange = { applyCanvasChange(it, selectedOrientation) },
                    onOrientationToggle = {
                        applyCanvasChange(
                            selectedPageSize,
                            if (selectedOrientation == CollageOrientation.PORTRAIT) CollageOrientation.LANDSCAPE else CollageOrientation.PORTRAIT
                        )
                    },
                    onAddPagesClick = { showPagePicker = true },
                    glassOpacity = navBarGlassOpacity
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


/** Opacity of the Collage screen's glass surfaces (header, bottom dock) that aren't the bottom nav pill itself — fixed, not user-tunable. */
private const val GLASS_HEADER_OPACITY = 0.6f

/**
 * Compact liquid-glass header for the collage editor: back
 * arrow, title, and Save in a single tight row, matching the style used on
 * the Settings screen rather than the taller default Material3 TopAppBar.
 */
@Composable
private fun CollageTopBar(
    onBackClick: () -> Unit,
    isSaving: Boolean,
    canSave: Boolean,
    onSaveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cleanGlassBackground(
                tint = MaterialTheme.colorScheme.surfaceContainer,
                opacity = GLASS_HEADER_OPACITY
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Create Collage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSaveClick, enabled = canSave) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}


/** Compact "‹ 1 / 2 ›  + Page" pill floating over the canvas. */
@Composable
private fun CollagePageNavigator(
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = currentIndex > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page", tint = Color.White)
        }
        Text(
            "${currentIndex + 1} / $total",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        IconButton(onClick = onNext, enabled = currentIndex < total - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next page", tint = Color.White)
        }
        TextButton(onClick = onAddPage, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text("Page", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Renders one output page. The white page is centred in the available space
 * at the chosen paper's aspect ratio; picture frames are laid over the whole
 * area (not clipped to the page) so their corner handles stay tappable even
 * when a picture sits against the page edge.
 */
@Composable
private fun CollagePageCanvas(
    page: CollagePage,
    canvasAspect: Float,
    pageById: Map<Long, CollagePickerPage>,
    selectedFrameIndex: Int?,
    onFrameTap: (Int) -> Unit,
    onBackgroundTap: () -> Unit,
    onFrameChange: (Int, CollagePictureFrame) -> Unit,
    onFrameClear: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        val availableWidth = maxWidth.value
        val availableHeight = maxHeight.value
        val fill = 0.94f
        val pageWidthDp: Float
        val pageHeightDp: Float
        if (availableWidth / availableHeight > canvasAspect) {
            pageHeightDp = availableHeight * fill
            pageWidthDp = pageHeightDp * canvasAspect
        } else {
            pageWidthDp = availableWidth * fill
            pageHeightDp = pageWidthDp / canvasAspect
        }
        val originX = (availableWidth - pageWidthDp) / 2f
        val originY = (availableHeight - pageHeightDp) / 2f

        Box(
            modifier = Modifier
                .offset(x = originX.dp, y = originY.dp)
                .size(width = pageWidthDp.dp, height = pageHeightDp.dp)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        )

        // Tap on empty space deselects the current frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onBackgroundTap() }) }
        )

        page.frames.forEachIndexed { index, frame ->
            key(index) {
                CollagePictureFrameView(
                    picture = frame.pageId?.let { pageById[it] },
                    frame = frame,
                    isSelected = selectedFrameIndex == index,
                    originXDp = originX,
                    originYDp = originY,
                    pageWidthDp = pageWidthDp,
                    pageHeightDp = pageHeightDp,
                    onTap = { onFrameTap(index) },
                    onFrameChange = { updated -> onFrameChange(index, updated) },
                    onClear = { onFrameClear(index) }
                )
            }
        }
    }
}

/**
 * One picture's frame, CamScanner style. Selected frames show:
 *  - top-left  ✕  delete the picture (with Undo)
 *  - top-right ⟳  drag to rotate around the centre
 *  - bottom-right ⤢ drag to resize — aspect ratio is locked, so the picture
 *    is scaled, never stretched
 * Dragging the picture body moves it (and selects it on the way).
 *
 * Gestures keep their own running copy of the frame (`live`) instead of
 * reading the last-composed one, so several touch events arriving before the
 * next recomposition never lose movement.
 */
@Composable
private fun CollagePictureFrameView(
    picture: CollagePickerPage?,
    frame: CollagePictureFrame,
    isSelected: Boolean,
    originXDp: Float,
    originYDp: Float,
    pageWidthDp: Float,
    pageHeightDp: Float,
    onTap: () -> Unit,
    onFrameChange: (CollagePictureFrame) -> Unit,
    onClear: () -> Unit
) {
    val density = LocalDensity.current
    val pageWidthPx = with(density) { pageWidthDp.dp.toPx() }
    val pageHeightPx = with(density) { pageHeightDp.dp.toPx() }

    val latestFrame by rememberUpdatedState(frame)
    val latestOnChange by rememberUpdatedState(onFrameChange)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnClear by rememberUpdatedState(onClear)

    // The touch box is the frame grown by half a handle on every side, so
    // handles centred on the frame's corners are still inside it (and hit-testable).
    val outset = HANDLE_SIZE_DP / 2f
    val leftDp = originXDp + frame.xFraction * pageWidthDp
    val topDp = originYDp + frame.yFraction * pageHeightDp
    val widthDp = frame.widthFraction * pageWidthDp
    val heightDp = frame.heightFraction * pageHeightDp

    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 1f else 0f)
            .offset(x = (leftDp - outset).dp, y = (topDp - outset).dp)
            .size(width = (widthDp + HANDLE_SIZE_DP).dp, height = (heightDp + HANDLE_SIZE_DP).dp)
            .graphicsLayer { rotationZ = frame.rotationDegrees }
    ) {
        Box(
            modifier = Modifier
                .padding(outset.dp)
                .fillMaxSize()
                .background(
                    if (picture == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else Color.Transparent
                )
                .pointerInput(Unit) { detectTapGestures(onTap = { latestOnTap() }) }
                .pointerInput(picture != null, pageWidthPx, pageHeightPx) {
                    if (picture != null) {
                        var live = latestFrame
                        detectDragGestures(
                            onDragStart = {
                                live = latestFrame
                                latestOnTap()
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                // `drag` is in the frame's own (rotated) axes — turn it back into page axes.
                                val radians = Math.toRadians(live.rotationDegrees.toDouble())
                                val cosA = cos(radians).toFloat()
                                val sinA = sin(radians).toFloat()
                                val dx = drag.x * cosA - drag.y * sinA
                                val dy = drag.x * sinA + drag.y * cosA
                                val centerX = (live.xFraction + live.widthFraction / 2f + dx / pageWidthPx).coerceIn(0f, 1f)
                                val centerY = (live.yFraction + live.heightFraction / 2f + dy / pageHeightPx).coerceIn(0f, 1f)
                                live = live.copy(
                                    xFraction = centerX - live.widthFraction / 2f,
                                    yFraction = centerY - live.heightFraction / 2f
                                )
                                latestOnChange(live)
                            }
                        )
                    }
                }
        ) {
            if (picture != null) {
                Image(
                    painter = rememberAsyncImagePainter(picture.uri),
                    contentDescription = picture.documentTitle,
                    // Frame already has the picture's own ratio; Fit just guarantees no stretching.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.InsertPageBreak,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                )
            }
        }

        if (isSelected && picture != null) {
            // ✕ delete (top-left)
            FrameHandle(
                icon = Icons.Filled.Close,
                description = "Remove",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .pointerInput(Unit) { detectTapGestures(onTap = { latestOnClear() }) }
            )

            // ⟳ rotate (top-right)
            FrameHandle(
                icon = Icons.Filled.RotateRight,
                description = "Rotate",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .pointerInput(pageWidthPx, pageHeightPx) {
                        var live = latestFrame
                        // Vector from the frame centre to the finger, in the frame's own axes.
                        var vx = 0f
                        var vy = 0f
                        detectDragGestures(
                            onDragStart = {
                                live = latestFrame
                                vx = live.widthFraction * pageWidthPx / 2f
                                vy = -live.heightFraction * pageHeightPx / 2f
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val nx = vx + drag.x
                                val ny = vy + drag.y
                                var delta = atan2(ny, nx) - atan2(vy, vx)
                                val pi = Math.PI.toFloat()
                                if (delta > pi) delta -= 2f * pi
                                if (delta < -pi) delta += 2f * pi
                                // The frame's axes turn by `delta` too, so re-express the vector in them.
                                val cosD = cos(-delta)
                                val sinD = sin(-delta)
                                vx = nx * cosD - ny * sinD
                                vy = nx * sinD + ny * cosD
                                live = live.copy(
                                    rotationDegrees = live.rotationDegrees + Math.toDegrees(delta.toDouble()).toFloat()
                                )
                                latestOnChange(live)
                            },
                            onDragEnd = {
                                // Snap to straight when released within 4° of 0/90/180/270.
                                val nearest = (live.rotationDegrees / 90f).roundToInt() * 90f
                                if (abs(live.rotationDegrees - nearest) < 4f) {
                                    live = live.copy(rotationDegrees = nearest)
                                    latestOnChange(live)
                                }
                            }
                        )
                    }
            )

            // ⤢ resize, aspect locked, scaled about the centre (bottom-right)
            FrameHandle(
                icon = Icons.Filled.OpenInFull,
                description = "Resize",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .pointerInput(pageWidthPx, pageHeightPx) {
                        var live = latestFrame
                        detectDragGestures(
                            onDragStart = { live = latestFrame },
                            onDrag = { change, drag ->
                                change.consume()
                                val widthPx = live.widthFraction * pageWidthPx
                                val heightPx = live.heightFraction * pageHeightPx
                                val halfDiagonal = hypot(widthPx, heightPx) / 2f
                                // How far the finger moved along the frame's own diagonal.
                                val along = (drag.x * widthPx + drag.y * heightPx) / (halfDiagonal * 2f)
                                val minScale = maxOf(
                                    MIN_FRAME_SIDE_FRACTION * pageWidthPx / widthPx,
                                    MIN_FRAME_SIDE_FRACTION * pageHeightPx / heightPx
                                )
                                val maxScale = minOf(1.5f * pageWidthPx / widthPx, 1.5f * pageHeightPx / heightPx)
                                val scale = ((halfDiagonal + along) / halfDiagonal)
                                    .coerceIn(minScale, maxOf(minScale, maxScale))
                                val newWidth = widthPx * scale / pageWidthPx
                                val newHeight = heightPx * scale / pageHeightPx
                                val centerX = live.xFraction + live.widthFraction / 2f
                                val centerY = live.yFraction + live.heightFraction / 2f
                                live = live.copy(
                                    xFraction = centerX - newWidth / 2f,
                                    yFraction = centerY - newHeight / 2f,
                                    widthFraction = newWidth,
                                    heightFraction = newHeight
                                )
                                latestOnChange(live)
                            }
                        )
                    }
            )
        }
    }
}

@Composable
private fun FrameHandle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color,
        shape = CircleShape,
        shadowElevation = 2.dp,
        modifier = modifier.size(HANDLE_SIZE_DP.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
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
    onAddPagesClick: () -> Unit,
    glassOpacity: Float = NavBarPreferences.DEFAULT_GLASS_OPACITY
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cleanGlassBackground(
                tint = MaterialTheme.colorScheme.surfaceContainer,
                opacity = glassOpacity
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
    ) {
        when (activeTab) {
            CollageDockTab.PAGE -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onAddPagesClick) {
                        Icon(Icons.Filled.InsertPageBreak, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tap Here to Assign Document Pages")
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Row(modifier = Modifier.fillMaxWidth()) {
            DockTabButton(
                label = "Page Pool",
                selected = activeTab == CollageDockTab.PAGE,
                onClick = { onTabChange(CollageDockTab.PAGE) },
                modifier = Modifier.weight(1f),
                icon = { tint -> Icon(Icons.Filled.InsertPageBreak, contentDescription = "Page Pool", tint = tint) }
            )
            DockTabButton(
                label = "Layout",
                selected = activeTab == CollageDockTab.TEMPLATE,
                onClick = { onTabChange(CollageDockTab.TEMPLATE) },
                modifier = Modifier.weight(1f),
                icon = { tint -> Icon(Icons.Filled.ViewModule, contentDescription = "Layout", tint = tint) }
            )
            DockTabButton(
                label = "Dimensions",
                selected = activeTab == CollageDockTab.SIZE,
                onClick = { onTabChange(CollageDockTab.SIZE) },
                modifier = Modifier.weight(1f),
                icon = { tint ->
                    Text(
                        "A4",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
            )
        }
    }
}

@Composable
private fun DockTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(tint)
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
