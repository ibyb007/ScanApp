package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat

/** One saved scan shown in the Recents list. */
data class RecentDocument(
    val id: String,
    val title: String,
    val subtitle: String,   // e.g. "Saved 27/06/26 18:09 · 2 pages"
    val thumbnailUri: Uri?,
    val pageCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentDocuments: List<RecentDocument>,
    onScanClick: () -> Unit,
    onDocumentClick: (RecentDocument) -> Unit,
    onRename: (RecentDocument, newTitle: String) -> Unit = { _, _ -> },
    onDelete: (RecentDocument) -> Unit = {},
    onShare: (RecentDocument, OutputFormat) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var actionSheetTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var renameTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<RecentDocument?>(null) }
    var shareTarget by remember { mutableStateOf<RecentDocument?>(null) }

    Scaffold(
        bottomBar = { ScanAppBottomNav() },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Scan document")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recents",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (recentDocuments.isNotEmpty()) {
                    TextButton(onClick = { /* navigate to full Files list */ }) {
                        Text("See All")
                    }
                }
            }

            if (recentDocuments.isEmpty()) {
                EmptyRecentsState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(recentDocuments, key = { it.id }) { doc ->
                        RecentDocumentRow(
                            doc = doc,
                            onClick = { onDocumentClick(doc) },
                            onLongClick = { actionSheetTarget = doc }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 96.dp))
                    }
                }
            }
        }
    }

    actionSheetTarget?.let { doc ->
        DocumentActionSheet(
            onDismiss = { actionSheetTarget = null },
            onRenameClick = { actionSheetTarget = null; renameTarget = doc },
            onDeleteClick = { actionSheetTarget = null; deleteTarget = doc },
            onShareClick = { actionSheetTarget = null; shareTarget = doc }
        )
    }

    renameTarget?.let { doc ->
        RenameDialog(
            currentTitle = doc.title,
            onConfirm = { newTitle -> renameTarget = null; onRename(doc, newTitle) },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete document?") },
            text = { Text("This will permanently delete \"${doc.title}\" and all its pages.") },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDelete(doc) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    shareTarget?.let { doc ->
        ShareFormatSheet(
            onFormatSelected = { format -> shareTarget = null; onShare(doc, format) },
            onDismiss = { shareTarget = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentDocumentRow(doc: RecentDocument, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (doc.thumbnailUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(doc.thumbnailUri),
                    contentDescription = doc.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                doc.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyRecentsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No scans yet — tap the camera button to start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanAppBottomNav() {
    var selected by remember { mutableStateOf(0) }
    val items = listOf(
        Triple("Home", Icons.Filled.Home, 0),
        Triple("Files", Icons.Filled.Folder, 1),
        Triple("Tools", Icons.Filled.Description, 2),
        Triple("Me", Icons.Filled.Person, 3)
    )
    NavigationBar {
        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { selected = index },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentActionSheet(
    onDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onShareClick)
            )
            ListItem(
                headlineContent = { Text("Rename") },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onRenameClick)
            )
            ListItem(
                headlineContent = { Text("Delete") },
                leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onDeleteClick)
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
