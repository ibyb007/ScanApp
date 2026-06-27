package com.example.scanapp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.scanapp.export.ExportEngine
import com.example.scanapp.export.ExportOptions
import com.example.scanapp.export.OutputFormat
import com.example.scanapp.scan.DocumentScannerLauncher
import com.example.scanapp.ui.ExportUiState
import com.example.scanapp.ui.ScanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.BitmapFactory

class MainActivity : ComponentActivity() {

    private lateinit var scannerLauncher: DocumentScannerLauncher
    private val exportEngine by lazy { ExportEngine(applicationContext) }

    // Mutable state Compose observes
    private var scannedPages by mutableStateOf<List<Uri>>(emptyList())
    private var isExporting by mutableStateOf(false)
    private var exportResultText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scannerLauncher = DocumentScannerLauncher(
            activity = this,
            onResult = { uris -> scannedPages = scannedPages + uris },
            onError = { e -> exportResultText = "Scan failed: ${e.message}" }
        )

        setContent {
            ScanScreen(
                scannedPages = scannedPages,
                isExporting = isExporting,
                exportResultText = exportResultText,
                onScanClick = { scannerLauncher.launch() },
                onExportClick = { uiState -> runExport(uiState) }
            )
        }
    }

    private fun runExport(uiState: ExportUiState) {
        if (scannedPages.isEmpty()) return
        isExporting = true
        exportResultText = null

        lifecycleScope.launch {
            try {
                val outputDir = File(filesDir, "exports").apply { mkdirs() }
                val targetBytes = uiState.sizeLimitMb?.let { (it * 1024 * 1024).toLong() }

                val resultText = withContext(Dispatchers.IO) {
                    when (uiState.format) {
                        OutputFormat.PDF -> {
                            val outFile = File(outputDir, "scan_${System.currentTimeMillis()}.pdf")
                            val result = exportEngine.exportAsPdf(
                                pageUris = scannedPages,
                                targetSizeBytes = targetBytes,
                                outputFile = outFile
                            )
                            "Saved PDF: ${result.file.name} (${result.finalSizeBytes / 1024} KB, " +
                                "quality ${result.finalQuality}, ${result.finalWidth}x${result.finalHeight})"
                        }
                        OutputFormat.JPEG, OutputFormat.PNG -> {
                            // Export each page as its own image file
                            var totalBytes = 0L
                            scannedPages.forEachIndexed { index, uri ->
                                val input = contentResolver.openInputStream(uri)
                                val bitmap = input?.use { BitmapFactory.decodeStream(it) }
                                    ?: return@forEachIndexed
                                val (out, meta) = exportEngine.compressImage(
                                    bitmap,
                                    ExportOptions(
                                        format = uiState.format,
                                        targetSizeBytes = targetBytes,
                                        quality = uiState.quality
                                    )
                                )
                                val ext = if (uiState.format == OutputFormat.PNG) "png" else "jpg"
                                val outFile = File(outputDir, "page_${index + 1}_${System.currentTimeMillis()}.$ext")
                                outFile.writeBytes(out.toByteArray())
                                totalBytes += outFile.length()
                            }
                            "Saved ${scannedPages.size} image(s), total ${totalBytes / 1024} KB"
                        }
                    }
                }

                exportResultText = resultText
            } catch (e: Exception) {
                exportResultText = "Export failed: ${e.message}"
            } finally {
                isExporting = false
            }
        }
    }
}
