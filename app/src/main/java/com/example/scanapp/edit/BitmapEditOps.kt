package com.example.scanapp.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

enum class PageFilter { NONE, GRAYSCALE, BLACK_AND_WHITE }

/**
 * Pure bitmap transforms for the page editor. Each function returns a NEW bitmap
 * rather than mutating in place, since Android bitmaps are awkward to resize in
 * place and callers (the editor's "apply" flow) always want the result as a
 * fresh object to display and then persist.
 *
 * NOTE: cropping is intentionally not handled here. ML Kit's GmsDocumentScanner
 * provides the crop experience at SCAN time (live edge detection + corner drag
 * during capture). It has no API to re-open an arbitrary existing file for
 * re-cropping, so "fixing the crop" on an already-saved page means re-scanning
 * that page from the camera, not an in-app crop tool. See PageEditorScreen's
 * "Re-scan" action for that flow.
 */
object BitmapEditOps {

    /** Rotates clockwise by the given degrees (expected: 90, 180, or 270). */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Applies a color filter by drawing through a ColorMatrix onto a fresh canvas. */
    fun applyFilter(bitmap: Bitmap, filter: PageFilter): Bitmap {
        if (filter == PageFilter.NONE) return bitmap

        val colorMatrix = when (filter) {
            PageFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            PageFilter.BLACK_AND_WHITE -> blackAndWhiteMatrix()
            PageFilter.NONE -> return bitmap
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * High-contrast black/white "document scan" look — desaturate, then push
     * contrast hard so midtones snap toward black or white. This mirrors what
     * CamScanner-style "B&W" modes do; a true per-pixel threshold would need a
     * manual pixel loop, which is unnecessary here since the contrast push gets
     * a visually equivalent result far faster.
     */
    private fun blackAndWhiteMatrix(): ColorMatrix {
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 2.2f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val combined = ColorMatrix()
        combined.postConcat(grayscale)
        combined.postConcat(contrastMatrix)
        return combined
    }
}
