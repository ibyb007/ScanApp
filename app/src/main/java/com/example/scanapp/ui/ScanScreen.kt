package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat

/** Which unit the user is typing the exact size limit in. */
enum class SizeUnit { KB, MB }

/**
 * Customization state for the export step.
 * sizeLimitBytes == null means "no limit, just use quality slider directly".
 */
data class ExportUiState(
    val format: OutputFormat = OutputFormat.PDF,
    val sizeLimitBytes: Long? = 2L * 1024 * 1024, // default 2MB cap
    val quality: Int = 90
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    scannedPages: List<Uri>,
    isExporting: Boolean,
    exportResultText: String?,
    onScanClick: () -> Unit,
    onExportClick: (ExportUiState) -> Unit,
    onBackClick: () -> Unit = {}
) {
    var uiState by remember { mutableStateOf(ExportUiState()) }
    var useSizeLimit by remember { mutableStateOf(true) }
    var sizeUnit by remember { mutableStateOf(SizeUnit.MB) }
    // What's actually typed in the box, kept as text so partial/invalid entry
    // (like "" or "1.") doesn't get force-corrected while the user is mid-edit.
    var sizeText by remember { mutableStateOf("2") }

    fun applySizeText(text: String, unit: SizeUnit) {
        sizeText = text
        val value = text.toFloatOrNull()
        uiState = uiState.copy(
            sizeLimitBytes = if (value == null || value <= 0f) {
                null
            } else when (unit) {
                SizeUnit.KB -> (value * 1024).toLong()
                SizeUnit.MB -> (value * 1024 * 1024).toLong()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan & Export") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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
                        onCheckedChange = { checked ->
                            useSizeLimit = checked
                            if (checked) applySizeText(sizeText, sizeUnit)
                            else uiState = uiState.copy(sizeLimitBytes = null)
                        }
                    )
                }

                if (useSizeLimit) {
                    Spacer(Modifier.height(8.dp))

                    // Exact-value input box + unit toggle (KB/MB) — the precise control.
                    // The slider below is the quick control; both stay in sync.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = sizeText,
                            onValueChange = { applySizeText(it, sizeUnit) },
                            label = { Text("Target size") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(140.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        SegmentedUnitToggle(
                            selected = sizeUnit,
                            onSelect = { unit ->
                                sizeUnit = unit
                                applySizeText(sizeText, unit)
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val sliderMaxValue = if (sizeUnit == SizeUnit.KB) 5000f else 20f
                    val sliderValue = (sizeText.toFloatOrNull() ?: 0f).coerceIn(0f, sliderMaxValue)
                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            val rounded = if (sizeUnit == SizeUnit.KB) {
                                newValue.toInt().toString()
                            } else {
                                "%.1f".format(newValue)
                            }
                            applySizeText(rounded, sizeUnit)
                        },
                        valueRange = 0.1f..sliderMaxValue
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

@Composable
private fun SegmentedUnitToggle(selected: SizeUnit, onSelect: (SizeUnit) -> Unit) {
    Row {
        SegmentedButtonOption("KB", selected == SizeUnit.KB) { onSelect(SizeUnit.KB) }
        Spacer(Modifier.width(4.dp))
        SegmentedButtonOption("MB", selected == SizeUnit.MB) { onSelect(SizeUnit.MB) }
    }
}

@Composable
private fun SegmentedButtonOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
