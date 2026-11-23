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
 *
 * [gridCols]/[gridRows] only steer the *starting* arrangement (see
 * [CollageDefaultArrangement]) — e.g. distinguishing "side by side" from
 * "stacked" for the same picture count. Leave both null to let the starting
 * grid be chosen automatically (a near-square arrangement).
 */
data class CollageLayout(
    val id: String,
    val displayName: String,
    val picturesPerPage: Int,
    val gridCols: Int? = null,
    val gridRows: Int? = null
)

/** Built-in layout set. "N x M" is just shorthand for "this many pictures per page". */
object CollageLayouts {
    val TWO_PER_PAGE = CollageLayout(id = "1x2", displayName = "1 \u00d7 2", picturesPerPage = 2, gridCols = 2, gridRows = 1)
    val TWO_PER_PAGE_VERTICAL = CollageLayout(
        id = "1x2v",
        displayName = "1 \u00d7 2 (vertical)",
        picturesPerPage = 2,
        gridCols = 1,
        gridRows = 2
    )
    val THREE_PER_PAGE = CollageLayout(id = "1x3", displayName = "1 \u00d7 3", picturesPerPage = 3)
    val FOUR_PER_PAGE = CollageLayout(id = "2x2", displayName = "2 \u00d7 2", picturesPerPage = 4)
    val ONE_PER_PAGE = CollageLayout(id = "1", displayName = "1 per page", picturesPerPage = 1)

    val ALL = listOf(TWO_PER_PAGE, TWO_PER_PAGE_VERTICAL, THREE_PER_PAGE, FOUR_PER_PAGE, ONE_PER_PAGE)
}

/**
 * One picture's free-form placement on a page, in normalized [0,1] fractions
 * of the page's width/height. There's no implicit boundary clamping the
 * picture to a slot — x/y/width/height are exactly the rectangle the user
 * dragged and resized it to, so two pictures can be any size, anywhere, even
 * overlapping if the user wants that.
 *
 * A frame holding a picture always has the picture's own aspect ratio (see
 * [CollageFit]) and resizing keeps that ratio locked, so pictures are never
 * stretched. [rotationDegrees] spins the frame around its centre.
 */
data class CollagePictureFrame(
    val pageId: Long?,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    /** Clockwise rotation in degrees, applied around the frame's centre. */
    val rotationDegrees: Float = 0f
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
 * Aspect-ratio helpers. Frame geometry is stored as fractions of the canvas,
 * so a square fraction is NOT a square on screen unless the canvas is square —
 * every calculation here goes through [canvasAspect] (canvas width / height)
 * to stay in true pixel proportions.
 */
object CollageFit {

    /**
     * Largest rectangle with [imageAspect] (width / height) that fits inside
     * [cell] (fractions of the canvas), centred in it. Never stretches.
     */
    fun fitInto(cell: RectF, imageAspect: Float, canvasAspect: Float): RectF {
        val safeAspect = if (imageAspect > 0f) imageAspect else 1f
        val cellAspectPx = (cell.width() * canvasAspect) / cell.height()
        val width: Float
        val height: Float
        if (safeAspect > cellAspectPx) {
            width = cell.width()
            height = cell.width() * canvasAspect / safeAspect
        } else {
            height = cell.height()
            width = cell.height() * safeAspect / canvasAspect
        }
        val left = cell.left + (cell.width() - width) / 2f
        val top = cell.top + (cell.height() - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    /**
     * Re-expresses a frame for a canvas whose aspect changed from
     * [oldCanvasAspect] to [newCanvasAspect] (page size / orientation
     * switch): same centre, same share of the page area, picture ratio kept.
     */
    fun rescaleForCanvas(
        frame: CollagePictureFrame,
        imageAspect: Float,
        oldCanvasAspect: Float,
        newCanvasAspect: Float
    ): CollagePictureFrame {
        val aspect = if (imageAspect > 0f) imageAspect else 1f
        val area = frame.widthFraction * frame.heightFraction
        var height = kotlin.math.sqrt(area * newCanvasAspect / aspect)
        var width = aspect * height / newCanvasAspect
        val shrink = minOf(1f / width, 1f / height, 1f)
        width *= shrink
        height *= shrink
        val centerX = frame.xFraction + frame.widthFraction / 2f
        val centerY = frame.yFraction + frame.heightFraction / 2f
        return frame.copy(
            xFraction = (centerX - width / 2f).coerceIn(0f, 1f - width),
            yFraction = (centerY - height / 2f).coerceIn(0f, 1f - height),
            widthFraction = width,
            heightFraction = height
        )
    }
}

/**
 * Produces non-overlapping starting cells for N pictures on a blank page.
 * These are only the *slots* pictures get fitted into ([CollageFit.fitInto]);
 * once a picture is placed its frame is independent and freely
 * draggable / resizable / rotatable.
 */
object CollageDefaultArrangement {

    private const val MARGIN = 0.04f
    private const val GUTTER = 0.03f

    fun ratesFor(count: Int, gridCols: Int? = null, gridRows: Int? = null): List<RectF> {
        if (count <= 0) return emptyList()
        if (count == 1) {
            return listOf(RectF(MARGIN, MARGIN, 1f - MARGIN, 1f - MARGIN))
        }

        val cols = gridCols ?: kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = gridRows ?: kotlin.math.ceil(count.toDouble() / cols).toInt().coerceAtLeast(1)

        val slotWidth = (1f - 2 * MARGIN - GUTTER * (cols - 1)) / cols
        val slotHeight = (1f - 2 * MARGIN - GUTTER * (rows - 1)) / rows

        return (0 until count).map { index ->
            val row = index / cols
            val col = index % cols
            val left = MARGIN + col * (slotWidth + GUTTER)
            val top = MARGIN + row * (slotHeight + GUTTER)
            RectF(left, top, left + slotWidth, top + slotHeight)
        }
    }
}

/**
 * Renders a list of pages, each a free-form arrangement of picture frames,
 * into one bitmap per page. Pictures are drawn fit-inside their frame
 * (never stretched) and rotated around the frame centre.
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

    private fun drawPictureInFrame(
        canvas: Canvas,
        paint: Paint,
        bitmap: Bitmap,
        frame: CollagePictureFrame,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ) {
        val frameRect = RectF(
            frame.xFraction * canvasWidthPx,
            frame.yFraction * canvasHeightPx,
            (frame.xFraction + frame.widthFraction) * canvasWidthPx,
            (frame.yFraction + frame.heightFraction) * canvasHeightPx
        )
        // Fit (not stretch) in true pixel space: treat the frame rect as the
        // cell on a canvas whose aspect is 1, i.e. work directly in pixels.
        val destRect = CollageFit.fitInto(
            cell = frameRect,
            imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat(),
            canvasAspect = 1f
        )

        canvas.save()
        canvas.rotate(frame.rotationDegrees, frameRect.centerX(), frameRect.centerY())
        canvas.drawBitmap(bitmap, null, destRect, paint)
        canvas.restore()
    }
}
