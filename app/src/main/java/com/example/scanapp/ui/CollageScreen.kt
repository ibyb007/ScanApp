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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.collage.CollageCellAssignment
import com.example.scanapp.collage.CollageCellTransform
import com.example.scanapp.collage.CollageOrientation
import com.example.scanapp.collage.CollagePageSize
import com.example.scanapp.collage.CollageTemplate
import com.example.scanapp.collage.CollageTemplates
import kotlin.math.max

data class CollagePickerPage(
    val pageId: Long,
    val uri: Uri,
    val documentTitle: String
)

private enum class CollageDockTab { PAGE, TEMPLATE, SIZE }

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

    var assignments by remember {
        mutableStateOf(List(selectedTemplate.cells.size) { CollageCellAssignment(pageId = null) })
    }

    var selectedCellIndex by remember { mutableStateOf<Int?>(null) }
    var showPagePicker by remember { mutableStateOf(false) }
    var isFullscreenEdit by remember { mutableStateOf(false) }

    val pageById = remember(allPages) { allPages.associateBy { it.pageId } }

    fun resizeAssignmentsForTemplate(newTemplate: CollageTemplate) {
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
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { isFullscreenEdit = true }
            ) {
                CollageLiveGrid(
                    template = selectedTemplate,
                    pageSize = selectedPageSize,
                    orientation = selectedOrientation,
                    assignments = assignments,
                    pageById = pageById,
                    selectedCellIndex = selectedCellIndex,
                    onCellTap = { index ->
                        if (!isFullscreenEdit) {
                            isFullscreenEdit = true
                        } else {
                            selectedCellIndex = if (assignments.getOrNull(index)?.pageId == null) {
                                showPagePicker = true
                                index
                            } else if (selectedCellIndex == index) {
                                null
                            } else {
                                index
                            }
                        }
                    },
                    onCellTransformChange = { index, transform -> updateCellTransform(index, transform) },
                    onCellClear = { index -> clearCell(index) },
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
                    selectedTemplate = selectedTemplate,
                    onTemplateChange = { resizeAssignmentsForTemplate(it) },
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
                    IconButton(onClick = { isFullscreenEdit = false; selectedCellIndex = null }) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen")
                    }
                    Text("CamScanner Workspace Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onSaveClick(selectedTemplate, selectedPageSize, selectedOrientation, assignments) }) {
                        Icon(Icons.Filled.Check, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageLiveGrid(
                        template = selectedTemplate,
                        pageSize = selectedPageSize,
                        orientation = selectedOrientation,
                        assignments = assignments,
                        pageById = pageById,
                        selectedCellIndex = selectedCellIndex,
                        onCellTap = { index ->
                            selectedCellIndex = if (assignments.getOrNull(index)?.pageId == null) {
                                showPagePicker = true
                                index
                            } else if (selectedCellIndex == index) {
                                null
                            } else {
                                index
                            }
                        },
                        onCellTransformChange = { index, transform -> updateCellTransform(index, transform) },
                        onCellClear = { index -> clearCell(index) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                HorizontalDivider()
                CollageBottomDock(
                    activeTab = activeTab,
                    onTabChange = { activeTab = it },
                    selectedTemplate = selectedTemplate,
                    onTemplateChange = { resizeAssignmentsForTemplate(it) },
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
            onPickPage = { pageId ->
                val targetIndex = selectedCellIndex ?: assignments.indexOfFirst { it.pageId == null }.takeIf { it >= 0 }
                if (targetIndex != null) {
                    assignPageToCell(targetIndex, pageId)
                    selectedCellIndex = targetIndex
                }
                showPagePicker = false
            },
            onDismiss = { showPagePicker = false }
        )
    }
}

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
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onTap)
    ) {
        if (page != null) {
            Image(
                painter = rememberAsyncImagePainter(page.uri),
                contentDescription = page.documentTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetFractionX * cellWidthPx
                        translationY = transform.offsetFractionY * cellHeightPx
                    }
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
            )

            if (page != null) {
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
                                val cellDiagonalPx = max(cellWidthPx, cellHeightPx)
                                val deltaScale = (dragAmount.x + dragAmount.y) / cellDiagonalPx
                                val newScale = (transform.scale + deltaScale).coerceIn(1f, 4f)
                                
                                val deltaPanX = dragAmount.x / cellWidthPx
                                val deltaPanY = dragAmount.y / cellHeightPx
                                
                                onTransformChange(
                                    transform.copy(
                                        scale = newScale,
                                        offsetFractionX = (transform.offsetFractionX + deltaPanX).coerceIn(-1f, 1f),
                                        offsetFractionY = (transform.offsetFractionY + deltaPanY).coerceIn(-1f, 1f)
                                    )
                                )
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.OpenWith, contentDescription = "Resize/Pan", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else if (page == null) {
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
    selectedTemplate: CollageTemplate,
    onTemplateChange: (CollageTemplate) -> Unit,
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
                        Text("Tap Workspace Cells to Assign Document Pages")
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
                            leading = {
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
                label = "Grid Layout",
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