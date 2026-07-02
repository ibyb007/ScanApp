package com.example.scanapp.ui

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scanapp.export.OutputFormat
import kotlinx.coroutines.delay

/** Which unit the user is typing the exact size limit in. */
enum class SizeUnit { KB, MB }

/**
 * Customization state for the export step.
 * sizeLimitBytes == null means "no limit, just use quality slider directly".
 * fileName is the custom filename (without extension); null or empty means use random name.
 */
data class ExportUiState(
    val format: OutputFormat = OutputFormat.PDF,
    val sizeLimitBytes: Long? = 500L * 1024, // default 500KB cap (KB is now the default unit)
    val quality: Int = 90,
    val fileName: String = "",
    val customWidth: Int? = null,   // null = keep the scanned page's original width
    val customHeight: Int? = null,  // null = keep the scanned page's original height
    val dpi: Int? = null            // null = don't override DPI metadata
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    scannedPages: List<Uri>,
    isExporting: Boolean,
    exportResultText: String?,
    onScanClick: () -> Unit,
    onExportClick: (ExportUiState) -> Unit,
    onBackClick: () -> Unit = {},
    // Hoisted so the caller (MainActivity) can persist these across screen
    // switches — previously these lived in `remember` blocks scoped to this
    // composable, so every time the user left and re-entered this screen
    // (e.g. Detail -> Export -> back -> Export again) the size limit, unit,
    // and filename silently reset to their defaults.
    initialUiState: ExportUiState = ExportUiState(),
    initialUseSizeLimit: Boolean = true,
    initialSizeUnit: SizeUnit = SizeUnit.KB,
    initialSizeText: String = "500",
    onExportUiStateChange: (ExportUiState, useSizeLimit: Boolean, sizeUnit: SizeUnit, sizeText: String) -> Unit = { _, _, _, _ -> },
    // Reads a scanned page's real pixel width/height/DPI off the main thread so the
    // resolution fields can be pre-filled with the source image's current values.
    fetchImageInfo: suspend (Uri) -> Triple<Int, Int, Int>? = { null }
) {
    var uiState by remember { mutableStateOf(initialUiState) }
    var useSizeLimit by remember { mutableStateOf(initialUseSizeLimit) }
    var sizeUnit by remember { mutableStateOf(initialSizeUnit) }
    // What's actually typed in the box, kept as text so partial/invalid entry
    // (like "" or "1.") doesn't get force-corrected while the user is mid-edit.
    var sizeText by remember { mutableStateOf(initialSizeText) }

    // Reports every change back up to the caller immediately so the latest
    // values are always available to persist, regardless of whether the user
    // eventually taps Export or just navigates away.
    fun reportChange() {
        onExportUiStateChange(uiState, useSizeLimit, sizeUnit, sizeText)
    }

    // Resolution/DPI text fields — kept as strings for the same reason as sizeText
    // (mid-edit states like "" shouldn't get force-corrected).
    var widthText by remember { mutableStateOf(initialUiState.customWidth?.toString() ?: "") }
    var heightText by remember { mutableStateOf(initialUiState.customHeight?.toString() ?: "") }
    var dpiText by remember { mutableStateOf(initialUiState.dpi?.toString() ?: "") }
    // Tracks whether the user has touched the resolution fields yet, so the one-time
    // "fetch current width/height/dpi" prefill doesn't clobber their edits later.
    var hasPrefilledResolution by remember { mutableStateOf(initialUiState.customWidth != null) }

    LaunchedEffect(scannedPages.firstOrNull()) {
        val firstPage = scannedPages.firstOrNull() ?: return@LaunchedEffect
        if (hasPrefilledResolution) return@LaunchedEffect
        val info = fetchImageInfo(firstPage) ?: return@LaunchedEffect
        val (w, h, dpi) = info
        widthText = w.toString()
        heightText = h.toString()
        dpiText = dpi.toString()
        hasPrefilledResolution = true
        uiState = uiState.copy(customWidth = w, customHeight = h, dpi = dpi)
        reportChange()
    }

    fun applyWidthText(text: String) {
        widthText = text
        uiState = uiState.copy(customWidth = text.toIntOrNull()?.takeIf { it > 0 })
        reportChange()
    }

    fun applyHeightText(text: String) {
        heightText = text
        uiState = uiState.copy(customHeight = text.toIntOrNull()?.takeIf { it > 0 })
        reportChange()
    }

    fun applyDpiText(text: String) {
        dpiText = text
        uiState = uiState.copy(dpi = text.toIntOrNull()?.takeIf { it > 0 })
        reportChange()
    }

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
        reportChange()
    }

    // Tracks whether we've already "consumed" the current exportResultText for
    // popup purposes, so the popup doesn't immediately re-trigger after its own
    // auto-dismiss sets nothing else changing. Keyed on the text's identity.
    var lastShownResult by remember { mutableStateOf<String?>(null) }
    var showPopup by remember { mutableStateOf(false) }

    LaunchedEffect(exportResultText) {
        if (exportResultText != null && exportResultText != lastShownResult) {
            lastShownResult = exportResultText
            showPopup = true
            val isError = exportResultText.startsWith("Export failed") || exportResultText.startsWith("Scan failed")
            delay(if (isError) 4500 else 2200)
            showPopup = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            reportChange()
                        }
                        Spacer(Modifier.width(8.dp))
                        FormatChip("JPEG", uiState.format == OutputFormat.JPEG) {
                            uiState = uiState.copy(format = OutputFormat.JPEG)
                            reportChange()
                        }
                        Spacer(Modifier.width(8.dp))
                        FormatChip("PNG", uiState.format == OutputFormat.PNG) {
                            uiState = uiState.copy(format = OutputFormat.PNG)
                            reportChange()
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // File name input
                    OutlinedTextField(
                        value = uiState.fileName,
                        onValueChange = { uiState = uiState.copy(fileName = it); reportChange() },
                        label = { Text("File name (optional)") },
                        placeholder = { Text("Leave blank for random name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.format != OutputFormat.PDF) {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        Text("Resolution & DPI", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pre-filled with the scan's current values — edit to resize or change print density.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = widthText,
                                onValueChange = { applyWidthText(it) },
                                label = { Text("Width (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { applyHeightText(it) },
                                label = { Text("Height (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = dpiText,
                            onValueChange = { applyDpiText(it) },
                            label = { Text("DPI") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(140.dp)
                        )
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
                                else {
                                    uiState = uiState.copy(sizeLimitBytes = null)
                                    reportChange()
                                }
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
                            onValueChange = { uiState = uiState.copy(quality = it.toInt()); reportChange() },
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
                }
            }
        }

        ExportConfirmationPopup(
            visible = showPopup,
            resultText = exportResultText,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun ExportConfirmationPopup(
    visible: Boolean,
    resultText: String?,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "popupScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "popupAlpha"
    )

    // Keep the Surface composed through its fade/scale-out, not just while
    // `visible` is true — otherwise it vanishes instantly instead of animating out.
    var hasBeenShown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) hasBeenShown = true }

    if (resultText == null || !hasBeenShown) return

    val isError = resultText.startsWith("Export failed") || resultText.startsWith("Scan failed")

    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .padding(24.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isError) "Export failed" else "Export complete",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                resultText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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
