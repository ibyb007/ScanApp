package com.example.scanapp.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Standard output paper sizes for a collage canvas, in pixels at a fixed
 * working resolution (150dpi — sharp enough for on-screen preview and for
 * printing/sharing as a PDF page, without the multi-thousand-pixel canvases
 * a higher DPI would need for a feature that's compositing already-compressed
 * scan JPEGs, not the original 4:3/under camera capture).
 *
 * Each entry stores its physical dimensions in inches (the part a person
 * actually picks by name) plus the pixel size derived from them, so adding a
 * new size later is just one line with the inches for that paper standard.
 */
enum class CollagePageSize(
    val displayName: String,
    val widthInches: Float,
    val heightInches: Float
) {
    A4("A4", 8.27f, 11.69f),
    A5("A5", 5.83f, 8.27f),
    LETTER("Letter", 8.5f, 11f),
    LEGAL("Legal", 8.5f, 14f),
    SQUARE("Square", 8.5f, 8.5f);

    companion object {
        const val WORKING_DPI = 150
    }

    val widthPx: Int get() = (widthInches * WORKING_DPI).toInt()
    val heightPx: Int get() = (heightInches * WORKING_DPI).toInt()
}

/** Page orientation — swaps width/height of whatever [CollagePageSize] is chosen. */
enum class CollageOrientation { PORTRAIT, LANDSCAPE }

/** The effective canvas pixel size for a given page size + orientation pair. */
fun CollagePageSize.canvasPx(orientation: CollageOrientation): Pair<Int, Int> =
    when (orientation) {
        CollageOrientation.PORTRAIT -> widthPx to heightPx
        CollageOrientation.LANDSCAPE -> heightPx to widthPx
    }

/**
 * A collage layout just says how many pictures share a single output page —
 * nothing more. There is deliberately no fixed grid geometry here: "1 x 2"
 * means two pictures placed freely on one page (each independently
 * draggable/resizable by the user), not one page split into two locked
 * halves. If more pictures are assigned than [picturesPerPage] can hold,
 * additional pages are added automatically, each repeating this same count.
 */
data class CollageLayout(
    val id: String,
    val displayName: String,
    val picturesPerPage: Int
)

/** Built-in layout set. "N x M" is just shorthand for "this many pictures per page". */
object CollageLayouts {
    val ONE_PER_PAGE = CollageLayout(id = "1", displayName = "1 per page", picturesPerPage = 1)
    val TWO_PER_PAGE = CollageLayout(id = "1x2", displayName = "1 \u00d7 2", picturesPerPage = 2)
    val THREE_PER_PAGE = CollageLayout(id = "1x3", displayName = "1 \u00d7 3", picturesPerPage = 3)
    val FOUR_PER_PAGE = CollageLayout(id = "2x2", displayName = "2 \u00d7 2", picturesPerPage = 4)

    val ALL = listOf(ONE_PER_PAGE, TWO_PER_PAGE, THREE_PER_PAGE, FOUR_PER_PAGE)
}

/**
 * One picture's free-form placement on a page, in normalized [0,1] fractions
 * of the page's width/height. Unlike a fixed-cell layout, there's no implicit
 * boundary clamping the picture to a slot — x/y/width/height are exactly the
 * rectangle the user dragged and resized it to, so two pictures can be any
 * size, anywhere, even overlapping if the user wants that.
 */
data class CollagePictureFrame(
    val pageId: Long?,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float
) {
    companion object {
        fun empty(x: Float, y: Float, width: Float, height: Float) = CollagePictureFrame(
            pageId = null,
            xFraction = x,
            yFraction = y,
            widthFraction = width,
            heightFraction = height
        )
    }
}

/**
 * One output page's worth of picture frames. Index within [frames] has no
 * special meaning beyond z-order (later entries draw on top) — frames carry
 * their own position/size, they aren't slotted into a template.
 */
data class CollagePage(
    val frames: List<CollagePictureFrame>
)

/**
 * Produces sensible non-overlapping starting rectangles for N pictures on a
 * blank page, so a freshly added page (or a layout switch) doesn't dump
 * every picture in the exact same spot. These are just defaults — every
 * value here is then freely draggable/resizable by the user afterward.
 */
object CollageDefaultArrangement {

    private const val MARGIN = 0.04f
    private const val GUTTER = 0.03f

    fun ratesFor(count: Int): List<RectF> {
        if (count <= 0) return emptyList()
        if (count == 1) {
            return listOf(RectF(MARGIN, MARGIN, 1f - MARGIN, 1f - MARGIN))
        }

        // Choose a near-square grid of rows/cols just to seed starting
        // positions; once placed, each frame is independent of this grid.
        val cols = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(count.toDouble() / cols).toInt().coerceAtLeast(1)

        val usableWidth = 1f - 2 * MARGIN
        val usableHeight = 1f - 2 * MARGIN
        val cellWidth = (usableWidth - GUTTER * (cols - 1)) / cols
        val cellHeight = (usableHeight - GUTTER * (rows - 1)) / rows

        return (0 until count).map { index ->
            val row = index / cols
            val col = index % cols
            val left = MARGIN + col * (cellWidth + GUTTER)
            val top = MARGIN + row * (cellHeight + GUTTER)
            RectF(left, top, left + cellWidth, top + cellHeight)
        }
    }
}

/**
 * Renders a list of pages, each a free-form arrangement of picture frames,
 * into one bitmap per page. Each picture is scaled to FIT within its own
 * frame rectangle (preserving aspect ratio, centered within that
 * rectangle) — for documents, losing part of the page to a crop is much
 * worse than a sliver of empty margin inside the frame.
 *
 * An empty frame (pageId == null) is simply skipped — it never reaches this
 * point in practice since the UI only creates a page once at least one
 * picture is assigned, but staying permissive here means this never throws
 * on a partially-filled page either.
 */
object CollageCompositor {

    /** Composes every page in [pages] into one bitmap per page, in order. */
    fun composePages(
        pageBitmaps: Map<Long, Bitmap>,
        pages: List<CollagePage>,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ): List<Bitmap> = pages.map { page ->
        composePage(pageBitmaps, page, canvasWidthPx, canvasHeightPx)
    }

    /** Composes a single page's frames into one bitmap. */
    fun composePage(
        pageBitmaps: Map<Long, Bitmap>,
        page: CollagePage,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ): Bitmap {
        val result = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        page.frames.forEach { frame ->
            val pageId = frame.pageId ?: return@forEach
            val bitmap = pageBitmaps[pageId] ?: return@forEach
            drawPictureInFrame(canvas, paint, bitmap, frame, canvasWidthPx, canvasHeightPx)
        }
        return result
    }

    /**
     * Draws one picture into its frame rectangle, fitting it within that
     * rectangle (preserve aspect ratio, centered) rather than stretching or
     * cropping to fill — the frame's own size IS the user's chosen size, so
     * "fit" here just avoids distorting the picture's proportions inside it.
     */
    private fun drawPictureInFrame(
        canvas: Canvas,
        paint: Paint,
        bitmap: Bitmap,
        frame: CollagePictureFrame,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ) {
        val frameLeft = frame.xFraction * canvasWidthPx
        val frameTop = frame.yFraction * canvasHeightPx
        val frameWidth = frame.widthFraction * canvasWidthPx
        val frameHeight = frame.heightFraction * canvasHeightPx

        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val frameAspect = frameWidth / frameHeight

        val (drawWidth, drawHeight) = if (bitmapAspect > frameAspect) {
            frameWidth to (frameWidth / bitmapAspect)
        } else {
            (frameHeight * bitmapAspect) to frameHeight
        }

        val drawLeft = frameLeft + (frameWidth - drawWidth) / 2f
        val drawTop = frameTop + (frameHeight - drawHeight) / 2f

        val destRect = RectF(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight)
        canvas.drawBitmap(bitmap, null, destRect, paint)
    }
}
