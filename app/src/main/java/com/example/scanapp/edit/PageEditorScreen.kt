package com.example.scanapp.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Edits one page in place: rotate (90-degree steps) and a color filter
 * (none/grayscale/black-and-white). On Save, the caller writes the resulting
 * bitmap back to the page's file path.
 *
 * Crop is intentionally NOT handled here — see BitmapEditOps's class doc for
 * why. Instead, "Re-scan" launches ML Kit's own capture+crop UI and replaces
 * this page's image entirely with a fresh scan.
 *
 * Filters are tracked separately from the "base" bitmap (post-rotate,
 * pre-filter) so cycling through filter options re-applies onto the correct
 * base instead of stacking filters on top of each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageFilePath: String,
    onSave: (editedBitmap: Bitmap) -> Unit,
    onRescanRequested: () -> Unit,
    onCancel: () -> Unit
) {
    // baseBitmap = result of rotate only, no filter applied yet.
    var baseBitmap by remember(pageFilePath) {
        mutableStateOf(BitmapFactory.decodeFile(pageFilePath))
    }
    var currentFilter by remember { mutableStateOf(PageFilter.NONE) }
    // displayedBitmap = baseBitmap with currentFilter applied — what's shown and saved.
    val displayedBitmap by remember(baseBitmap, currentFilter) {
        mutableStateOf(BitmapEditOps.applyFilter(baseBitmap, currentFilter))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Page") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
                actions = {
                    TextButton(onClick = { onSave(displayedBitmap) }) { Text("Save") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditToolButton(Icons.Filled.CameraAlt, "Re-scan", onRescanRequested)
                    EditToolButton(Icons.Filled.Rotate90DegreesCw, "Rotate") {
                        baseBitmap = BitmapEditOps.rotate(baseBitmap, 90f)
                    }
                    EditToolButton(Icons.Filled.FilterBAndW, "Filter") {
                        currentFilter = when (currentFilter) {
                            PageFilter.NONE -> PageFilter.GRAYSCALE
                            PageFilter.GRAYSCALE -> PageFilter.BLACK_AND_WHITE
                            PageFilter.BLACK_AND_WHITE -> PageFilter.NONE
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = displayedBitmap.asImageBitmap(),
                contentDescription = "Page preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(displayedBitmap.width.toFloat() / displayedBitmap.height.toFloat())
            )
        }
    }
}

@Composable
private fun EditToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
