package com.example.scanapp.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 * that page from the camera, not an in-app crop tool. The current app builds
 * on this by re-importing a saved page into ML Kit's own scanner UI for full
 * Filters/Crop/Clean access — see TempGalleryExport and MainActivity's
 * launchPageEditViaMlKit. This file's functions (rotate/filter/autoEnhance)
 * are currently unused by any screen but kept as working utilities.
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
     * Auto-enhance: analyzes the page's actual luminance histogram and applies
     * a contrast stretch + brightness correction tailored to THIS image, rather
     * than a fixed filter. This is the standard "auto levels" technique used by
     * document scanners to even out shadows/uneven lighting from phone-camera
     * captures — not a trained model, but adaptive to each photo's real
     * brightness distribution rather than a one-size-fits-all matrix.
     *
     * Approach:
     *  1. Downsample for histogram analysis only (full-res histogram is
     *     wasteful — a 200px-wide sample is statistically equivalent for this
     *     purpose and far faster on large scan photos).
     *  2. Find the 1st and 99th percentile luminance values, clipping outliers
     *     (a few stray very-dark/very-bright pixels shouldn't skew the stretch).
     *  3. Build a per-channel linear stretch mapping [low, high] -> [0, 255]
     *     and apply it to the FULL-resolution bitmap via ColorMatrix (fast,
     *     GPU/Skia-accelerated draw rather than a manual pixel loop on the
     *     full image).
     */
    fun autoEnhance(bitmap: Bitmap): Bitmap {
        val (low, high) = analyzeLuminanceRange(bitmap)
        if (high <= low) return bitmap // degenerate image (e.g. solid color); nothing to stretch

        val range = (high - low).coerceAtLeast(1f)
        val scale = 255f / range
        val translate = -low * scale

        val stretchMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(stretchMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /** Returns the (1st percentile, 99th percentile) luminance values, 0-255, from a downsampled sample. */
    private fun analyzeLuminanceRange(bitmap: Bitmap): Pair<Float, Float> {
        val sampleWidth = min(200, bitmap.width)
        val sampleHeight = max(1, (bitmap.height.toFloat() * sampleWidth / bitmap.width).roundToInt())
        val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)

        val histogram = IntArray(256)
        val pixels = IntArray(sampleWidth * sampleHeight)
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        if (sample !== bitmap) sample.recycle()

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Standard relative-luminance weighting (perceptual, not a flat average).
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            histogram[luminance]++
        }

        val totalPixels = pixels.size
        val lowCutoff = (totalPixels * 0.01f)
        val highCutoff = (totalPixels * 0.99f)

        var cumulative = 0
        var low = 0
        var high = 255
        for (level in 0..255) {
            cumulative += histogram[level]
            if (cumulative >= lowCutoff) { low = level; break }
        }
        cumulative = 0
        for (level in 255 downTo 0) {
            cumulative += histogram[level]
            if (cumulative >= (totalPixels - highCutoff)) { high = level; break }
        }

        return low.toFloat() to high.toFloat()
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
