package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

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
    onDocumentClick: (RecentDocument) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

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
                        RecentDocumentRow(doc = doc, onClick = { onDocumentClick(doc) })
                        HorizontalDivider(modifier = Modifier.padding(start = 96.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentDocumentRow(doc: RecentDocument, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
