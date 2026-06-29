package com.example.scanapp.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File

/**
 * Rasterizes an externally-picked PDF (e.g. via ActivityResultContracts.OpenDocument)
 * into a list of JPEG page files, so an imported PDF can be saved into the library
 * through the exact same path as a camera scan (DocumentRepository.saveNewDocument
 * just needs decodable image Uris — see copyUriToHighQualityJpeg).
 *
 * Must be called from a background thread (e.g. Dispatchers.IO); PdfRenderer and
 * the underlying file IO are all synchronous.
 */
object PdfImporter {

    /** Render target resolution. PDF page sizes are in points (1/72in); 200dpi gives
     *  crisp text without producing huge files for a typical scanned-text page. */
    private const val TARGET_DPI = 200f
    private const val POINTS_PER_INCH = 72f

    private const val SCRATCH_DIR_NAME = "share_scratch/pdf_import"

    /**
     * Renders every page of the PDF at [pdfUri] to its own JPEG file and returns
     * FileProvider content Uris for them, in page order.
     *
     * @throws IllegalArgumentException if the Uri can't be opened or isn't a valid PDF.
     */
    fun importPagesAsJpegs(context: Context, pdfUri: Uri): List<Uri> {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Could not open PDF: $pdfUri")

        val scratchDir = File(context.cacheDir, SCRATCH_DIR_NAME).apply { mkdirs() }
        // Clear any leftovers from a previous import attempt so we never accumulate
        // orphaned scratch files across imports.
        scratchDir.listFiles()?.forEach { it.delete() }

        val resultFiles = mutableListOf<File>()

        try {
            PdfRenderer(pfd).use { renderer ->
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = TARGET_DPI / POINTS_PER_INCH
                        val widthPx = (page.width * scale).toInt().coerceAtLeast(1)
                        val heightPx = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        // PDF pages can have a transparent background; fill white first so
                        // JPEG compression (no alpha channel) doesn't produce black gaps.
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val pageFile = File(scratchDir, "page_${pageIndex + 1}.jpg")
                        pageFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        bitmap.recycle()
                        resultFiles += pageFile
                    }
                }
            }
        } finally {
            pfd.close()
        }

        if (resultFiles.isEmpty()) {
            throw IllegalArgumentException("PDF has no pages: $pdfUri")
        }

        return resultFiles.map { file ->
            try {
                FileProvider.getUriForFile(context, "com.example.scanapp.fileprovider", file)
            } catch (e: IllegalArgumentException) {
                // Diagnostic: surface exactly what we attempted vs. what FileProvider has
                // configured, since the bare exception message alone isn't enough to tell
                // whether this is a stale-build issue, a context mismatch, or something else.
                val diag = buildString {
                    append("PdfImporter FileProvider mismatch. ")
                    append("file.path=${file.path} ")
                    append("file.canonicalPath=${file.canonicalPath} ")
                    append("file.exists=${file.exists()} ")
                    append("context.cacheDir=${context.cacheDir.path} ")
                    append("context.cacheDir.canonicalPath=${context.cacheDir.canonicalFile.path} ")
                    append("context.packageName=${context.packageName} ")
                    append("original=${e.message}")
                }
                throw IllegalArgumentException(diag, e)
            }
        }
    }

    /** Deletes any scratch files left over from PDF import. Safe to call any time. */
    fun cleanupScratchFiles(context: Context) {
        File(context.cacheDir, SCRATCH_DIR_NAME).listFiles()?.forEach { it.delete() }
    }
}
