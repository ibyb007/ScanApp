@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat

data class DetailPage(
    val pageId: Long,
    val pageIndex: Int,
    val uri: Uri
)

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
    onExportSelected: (List<DetailPage>) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPages by remember { mutableStateOf(setOf<Long>()) }

    fun toggleSelection(pageId: Long) {
        selectedPages = if (selectedPages.contains(pageId)) {
            selectedPages - pageId
        } else {
            selectedPages + pageId
        }
        if (selectedPages.isEmpty()) selectionMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text("${selectedPages.size} Selected")
                    } else {
                        Text(title, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedPages = emptySet()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            val selectedObjects = pages.filter { it.pageId in selectedPages }
                            onExportSelected(selectedObjects)
                        }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Export Selected")
                        }
                    } else {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { showMenu = false; showShareSheet = true },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Entire Doc") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!selectionMode) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onAddPagesClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add pages", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val gridCells = if (pages.size <= 2) GridCells.Fixed(1) else GridCells.Fixed(2)
        val configuration = LocalConfiguration.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            LazyVerticalGrid(
                columns = gridCells,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pages, key = { it.pageId }) { page ->
                    val isSelected = selectedPages.contains(page.pageId)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (pages.size == 1) Modifier.height((configuration.screenHeightDp * 0.7f).dp)
                                else if (pages.size == 2) Modifier.height((configuration.screenHeightDp * 0.4f).dp)
                                else Modifier.aspectRatio(0.75f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelection(page.pageId)
                                    } else {
                                        onPageClick(page)
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    toggleSelection(page.pageId)
                                }
                            )
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(page.uri),
                            contentDescription = "Page ${page.pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${page.pageIndex + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (!selectionMode) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onDeletePage(page) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Delete Page",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { toggleSelection(page.pageId) },
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var currentText by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (currentText.isNotBlank()) {
                        onRename(currentText)
                        showRenameDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showShareSheet) {
        LocalShareFormatSheet(
            onFormatSelected = { format ->
                showShareSheet = false
                onShare(format)
            },
            onDismiss = { showShareSheet = false }
        )
    }
}

@Composable
private fun LocalShareFormatSheet(onFormatSelected: (OutputFormat) -> Unit, onDismiss: () -> Unit) {
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