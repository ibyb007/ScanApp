package com.example.scanapp.scan

import android.app.Activity
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Thin wrapper around ML Kit's GmsDocumentScanner.
 *
 * This launches Google's built-in scanning UI: live edge detection, auto-capture,
 * manual crop adjustment, multi-page support, and basic filters (auto/photo/mono).
 * We just register a launcher and hand back the result.
 *
 * @param pageLimit max pages in one scan session. Use a high limit (e.g. 20) for
 *   "scan a new document"; use 1 for "re-scan this single existing page" (the
 *   page-editor's Re-scan action), since GmsDocumentScannerOptions is immutable
 *   once built and each distinct page limit needs its own scanner client.
 */
class DocumentScannerLauncher(
    private val activity: ComponentActivity,
    private val onResult: (List<Uri>) -> Unit,
    private val onError: (Exception) -> Unit,
    pageLimit: Int = 20
) {

    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)          // let user import existing photos too
        .setPageLimit(pageLimit)
        .setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, // we want raw pages, we'll build our own PDF
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        )
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // full editing UI
        .build()

    private val scanner = GmsDocumentScanning.getClient(scannerOptions)

    private lateinit var launcher: ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>

    init {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val uris = scanResult?.pages?.map { it.imageUri } ?: emptyList()
                onResult(uris)
            } else {
                onError(Exception("Scan cancelled or failed (resultCode=${result.resultCode})"))
            }
        }
    }

    /** Call this to open the scanner. */
    fun launch() {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e -> onError(e) }
    }
}
