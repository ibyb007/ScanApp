package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.collage.CollageCellAssignment
import com.example.scanapp.collage.CollageCellTransform
import com.example.scanapp.collage.CollageOrientation
import com.example.scanapp.collage.CollagePageSize
import com.example.scanapp.collage.CollageTemplate
import com.example.scanapp.collage.CollageTemplates
import kotlin.math.roundToInt

data class CollagePickerPage(
    val pageId: Long,
    val uri: Uri,
    val documentTitle: String
)

data class InteractivePagePlacement(
    val pageId: Long,
    val uri: Uri,
    var offset: Offset = Offset(0f, 0f),
    var scale: Float = 1.0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    allPages: List<CollagePickerPage>,
    isSaving: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (CollageTemplate, CollagePageSize, CollageOrientation, List<CollageCellAssignment>) -> Unit
) {
    var selectedLayout by remember { mutableStateOf(CollageTemplates.ALL.first()) }
    var selectedPageSize by remember { mutableStateOf(CollagePageSize.A4) }
    
    val activePlacements = remember { mutableStateListOf<InteractivePagePlacement>() }
    var selectedPlacementIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Collage") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = {
                                val assignments = activePlacements.map { placement ->
                                    CollageCellAssignment(
                                        pageId = placement.pageId,
                                        transform = CollageCellTransform(scale = placement.scale)
                                    )
                                }
                                onSaveClick(selectedLayout, selectedPageSize, CollageOrientation.PORTRAIT, assignments)
                            },
                            enabled = activePlacements.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Save Collage")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable { selectedPlacementIndex = null },
                contentAlignment = Alignment.Center
            ) {
                if (activePlacements.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.OpenInFull, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap library items below to place on canvas", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        activePlacements.forEachIndexed { index, placement ->
                            val isSelected = selectedPlacementIndex == index
                            
                            val transformState = rememberTransformableState { zoomChange, _, _ ->
                                if (isSelected) {
                                    placement.scale = (placement.scale * zoomChange).coerceIn(0.5f, 3.0f)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(placement.offset.x.roundToInt(), placement.offset.y.roundToInt()) }
                                    .size((160 * placement.scale).dp, (220 * placement.scale).dp)
                                    .transformable(state = transformState)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            selectedPlacementIndex = index
                                            placement.offset = Offset(
                                                placement.offset.x + dragAmount.x,
                                                placement.offset.y + dragAmount.y
                                            )
                                        }
                                    }
                                    .clickable { selectedPlacementIndex = index }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
                                    )
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(placement.uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isSelected) {
                                    IconButton(
                                        onClick = {
                                            activePlacements.removeAt(index)
                                            selectedPlacementIndex = null
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset((-8).dp, (-8).dp)
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape)
                                    ) {
                                        Icon(Icons.Filled.Cancel, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(6.dp, 6.dp)
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.OpenInFull, contentDescription = "Scale hint", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Layout Grid", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CollageTemplates.ALL, key = { it.id }) { template ->
                            FilterChip(
                                selected = selectedLayout.id == template.id,
                                onClick = { selectedLayout = template },
                                label = { Text(template.displayName) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Target Master Geometry", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CollagePageSize.entries.toList(), key = { it.name }) { size ->
                            FilterChip(
                                selected = selectedPageSize == size,
                                onClick = { selectedPageSize = size },
                                label = { Text(size.displayName) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Select Media Input Pool", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(allPages, key = { it.pageId }) { pickerPage ->
                            Box(
                                modifier = Modifier
                                    .size(76.dp, 100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        activePlacements.add(
                                            InteractivePagePlacement(
                                                pageId = pickerPage.pageId,
                                                uri = pickerPage.uri,
                                                offset = Offset(120f + (activePlacements.size * 25f), 150f + (activePlacements.size * 25f))
                                            )
                                        )
                                        selectedPlacementIndex = activePlacements.size - 1
                                    }
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(pickerPage.uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .align(Alignment.BottomCenter)
                                        .padding(2.dp)
                                ) {
                                    Text(
                                        text = pickerPage.documentTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}