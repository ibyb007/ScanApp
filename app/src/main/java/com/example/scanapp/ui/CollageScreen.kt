package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.collage.CollageOrientation
import com.example.scanapp.collage.CollagePageSize
import com.example.scanapp.collage.CollageTemplate
import com.example.scanapp.collage.CollageTemplates
import kotlin.math.min

/** One selectable page in the cross-document picker. */
data class CollagePickerPage(
    val pageId: Long,
    val uri: Uri,
    val documentTitle: String
)

/**
 * Cross-document collage builder: pick any pages from anywhere in the
 * library, choose a layout template and an output page size, preview the
 * composed result full-screen with pinch-to-zoom, save it as a brand new
 * standalone document.
 *
 * Layout mirrors a familiar scan-app collage editor: a large, dedicated
 * preview canvas up top (the thing the person is actually deciding about)
 * with compact, scrollable controls docked below it — template, page size,
 * and orientation — rather than competing for space with the preview.
 *
 * Selection order matters — pages fill template cells in the order they were
 * tapped, not their library order, so the user can control which page lands
 * in which cell just by tap sequence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    allPages: List<CollagePickerPage>,
    previewBitmap: android.graphics.Bitmap?,
    isComposing: Boolean,
    onBackClick: () -> Unit,
    onSelectionOrTemplateChanged: (
        selectedPageIds: List<Long>,
        template: CollageTemplate,
        pageSize: CollagePageSize,
        orientation: CollageOrientation
    ) -> Unit,
    onSaveClick: () -> Unit
) {
    var selectedPageIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf(CollageTemplates.ALL.first()) }
    var selectedPageSize by remember { mutableStateOf(CollagePageSize.A4) }
    var selectedOrientation by remember { mutableStateOf(CollageOrientation.PORTRAIT) }
    var showPagePicker by remember { mutableStateOf(false) }

    fun notifyChanged() {
        onSelectionOrTemplateChanged(selectedPageIds, selectedTemplate, selectedPageSize, selectedOrientation)
    }

    fun togglePage(pageId: Long) {
        selectedPageIds = if (pageId in selectedPageIds) {
            selectedPageIds - pageId
        } else {
            selectedPageIds + pageId
        }
        notifyChanged()
    }

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
                        onClick = onSaveClick,
                        enabled = selectedPageIds.isNotEmpty() && !isComposing
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Full preview canvas — the dominant element on screen, matching
            // the "this is the thing you're deciding about" treatment of a
            // dedicated collage preview rather than a small confirmation box.
            CollagePreviewCanvas(
                previewBitmap = previewBitmap,
                isComposing = isComposing,
                hasSelection = selectedPageIds.isNotEmpty(),
                pageSize = selectedPageSize,
                orientation = selectedOrientation,
                modifier = Modifier.weight(1f)
            )

            HorizontalDivider()

            // Compact control dock: template, page size, orientation, then the
            // "pick pages" entry point. Kept short and scrollable-by-section
            // rather than tall, since the preview above should own most of
            // the screen's height.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ControlSectionLabel("Layout")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(CollageTemplates.ALL, key = { it.id }) { template ->
                        FilterChip(
                            selected = template.id == selectedTemplate.id,
                            onClick = {
                                selectedTemplate = template
                                notifyChanged()
                            },
                            label = { Text(template.displayName) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                ControlSectionLabel("Page size")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems(CollagePageSize.entries.toList(), key = { it.name }) { size ->
                        FilterChip(
                            selected = size == selectedPageSize,
                            onClick = {
                                selectedPageSize = size
                                notifyChanged()
                            },
                            label = { Text(size.displayName) }
                        )
                    }
                    item {
                        // Orientation toggle lives at the end of the same row —
                        // it's a property of the page size choice, not a
                        // separate decision the person makes independently.
                        FilterChip(
                            selected = false,
                            onClick = {
                                selectedOrientation = when (selectedOrientation) {
                                    CollageOrientation.PORTRAIT -> CollageOrientation.LANDSCAPE
                                    CollageOrientation.LANDSCAPE -> CollageOrientation.PORTRAIT
                                }
                                notifyChanged()
                            },
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

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Entry point into the full-screen page picker, showing a
                // running count + small stack of the chosen thumbnails inline
                // so the dock stays compact instead of always showing the grid.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPagePicker = true }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pages", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (selectedPageIds.isEmpty()) "Tap to pick pages from your library"
                            else "${selectedPageIds.size} selected, in tap order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SelectedPagesPreviewStack(
                        pageIds = selectedPageIds,
                        allPages = allPages
                    )
                }
            }
        }
    }

    if (showPagePicker) {
        PagePickerSheet(
            allPages = allPages,
            selectedPageIds = selectedPageIds,
            onTogglePage = ::togglePage,
            onDismiss = { showPagePicker = false }
        )
    }
}

@Composable
private fun ControlSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * The dedicated preview area: shows the composed collage at the chosen page
 * size's aspect ratio, pannable and pinch-to-zoomable so the person can check
 * fine detail (e.g. is text in a corner cell actually legible) before saving —
 * not just a small static thumbnail.
 */
@Composable
private fun CollagePreviewCanvas(
    previewBitmap: android.graphics.Bitmap?,
    isComposing: Boolean,
    hasSelection: Boolean,
    pageSize: CollagePageSize,
    orientation: CollageOrientation,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom/pan whenever the underlying bitmap is recomposed for a new
    // selection/template/size — an old zoom level carried over to a
    // differently-shaped collage is disorienting, not helpful.
    LaunchedEffect(previewBitmap, pageSize, orientation) {
        scale = 1f
        offset = Offset.Zero
    }

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
        Box(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .aspectRatio(pageAspect)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .pointerInput(previewBitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        offset = if (newScale == 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isComposing -> CircularProgressIndicator()
                previewBitmap != null -> {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Collage preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
                hasSelection -> {
                    // Selection exists but no bitmap yet — first compose pass
                    // hasn't landed; isComposing should usually cover this, but
                    // guards against a frame where neither is true.
                    CircularProgressIndicator()
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ZoomOutMap,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pick pages below to preview",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (previewBitmap != null && !isComposing && scale > 1f) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) {
                Text(
                    "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Small overlapping thumbnail stack (max 3 shown) used as a compact preview of the current page selection. */
@Composable
private fun SelectedPagesPreviewStack(pageIds: List<Long>, allPages: List<CollagePickerPage>) {
    if (pageIds.isEmpty()) return
    val pageById = remember(allPages) { allPages.associateBy { it.pageId } }
    Box(modifier = Modifier.width((24 + min(pageIds.size, 3) * 14).dp).height(32.dp)) {
        pageIds.take(3).forEachIndexed { i, id ->
            val page = pageById[id] ?: return@forEachIndexed
            Image(
                painter = rememberAsyncImagePainter(page.uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = (i * 14).dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/** Full-screen page picker, opened from the compact "Pages" row in the control dock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagePickerSheet(
    allPages: List<CollagePickerPage>,
    selectedPageIds: List<Long>,
    onTogglePage: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text(
                "Pick pages (${selectedPageIds.size} selected, in tap order)",
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
                        val selectionOrder = selectedPageIds.indexOf(page.pageId)
                        PickerThumbnail(
                            page = page,
                            selectionNumber = if (selectionOrder >= 0) selectionOrder + 1 else null,
                            onClick = { onTogglePage(page.pageId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerThumbnail(
    page: CollagePickerPage,
    selectionNumber: Int?,
    onClick: () -> Unit
) {
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

        if (selectionNumber != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(6.dp).align(Alignment.TopEnd).size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$selectionNumber",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

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
