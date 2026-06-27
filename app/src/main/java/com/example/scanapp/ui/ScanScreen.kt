package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat

/**
 * Customization state for the export step.
 * sizeLimitMb == null means "no limit, just use quality slider directly".
 */
data class ExportUiState(
    val format: OutputFormat = OutputFormat.PDF,
    val sizeLimitMb: Float? = 2f,   // default cap, matches common "under 2MB" use case
    val quality: Int = 90
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    scannedPages: List<Uri>,
    isExporting: Boolean,
    exportResultText: String?,
    onScanClick: () -> Unit,
    onExportClick: (ExportUiState) -> Unit
) {
    var uiState by remember { mutableStateOf(ExportUiState()) }
    var useSizeLimit by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scan & Export") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (scannedPages.isEmpty()) "Scan Document" else "Scan More Pages")
            }

            Spacer(Modifier.height(16.dp))

            if (scannedPages.isNotEmpty()) {
                Text("Pages (${scannedPages.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(scannedPages) { uri ->
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = "Scanned page",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text("Output format", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormatChip("PDF", uiState.format == OutputFormat.PDF) {
                        uiState = uiState.copy(format = OutputFormat.PDF)
                    }
                    Spacer(Modifier.width(8.dp))
                    FormatChip("JPEG", uiState.format == OutputFormat.JPEG) {
                        uiState = uiState.copy(format = OutputFormat.JPEG)
                    }
                    Spacer(Modifier.width(8.dp))
                    FormatChip("PNG", uiState.format == OutputFormat.PNG) {
                        uiState = uiState.copy(format = OutputFormat.PNG)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limit file size", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = useSizeLimit,
                        onCheckedChange = {
                            useSizeLimit = it
                            uiState = uiState.copy(sizeLimitMb = if (it) 2f else null)
                        }
                    )
                }

                if (useSizeLimit) {
                    Text("Target: ${"%.1f".format(uiState.sizeLimitMb ?: 2f)} MB")
                    Slider(
                        value = uiState.sizeLimitMb ?: 2f,
                        onValueChange = { uiState = uiState.copy(sizeLimitMb = it) },
                        valueRange = 0.1f..20f
                    )
                    Text(
                        "The app will reduce quality (and resolution if needed) to fit this size.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Quality: ${uiState.quality}")
                    Slider(
                        value = uiState.quality.toFloat(),
                        onValueChange = { uiState = uiState.copy(quality = it.toInt()) },
                        valueRange = 2f..100f
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { onExportClick(uiState) },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Export")
                    }
                }

                exportResultText?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
