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

/** One cell's position within the collage canvas, in normalized [0,1] coordinates. */
data class CollageCell(val rect: RectF)

/**
 * A user adjustment layered on top of a cell's default fit-to-cell framing:
 * extra zoom beyond the minimum that fills the cell, plus a pan offset
 * (in cell-relative fractions, so it scales correctly regardless of the
 * cell's actual pixel size at render time vs. export time).
 *
 * scale = 1f means "just fills the cell, no extra zoom" — the same framing
 * CollageCompositor already produced before any per-cell editing existed.
 * Values above 1f zoom in further (cropping more of the page to fill the
 * cell), matching the drag-handle resize gesture in the reference editor.
 */
data class CollageCellTransform(
    val scale: Float = 1f,
    val offsetFractionX: Float = 0f,
    val offsetFractionY: Float = 0f
)

/**
 * Which page (if any) occupies a given cell index, plus that cell's own
 * transform. Indexed by cell position within the template rather than by
 * page id, since the whole point of per-cell editing is that a cell can be
 * empty, reassigned, or independently adjusted regardless of selection order.
 */
data class CollageCellAssignment(
    val pageId: Long?,
    val transform: CollageCellTransform = CollageCellTransform()
)

/**
 * A collage template is just a named arrangement of cells. The compositor
 * doesn't care how many pages were picked vs. how many cells exist — see
 * CollageCompositor for how mismatches are handled (extra cells stay blank,
 * extra pages are simply unused).
 */
data class CollageTemplate(
    val id: String,
    val displayName: String,
    val cells: List<CollageCell>
)

/**
 * Built-in template set. Deliberately small and easy to extend — adding a
 * new layout later is just adding another entry with its own cell rects.
 * Cell rects intentionally leave a thin gutter between cells (rather than
 * exactly tiling 0..1) so pages don't visually merge into each other.
 */
object CollageTemplates {

    private const val GUTTER = 0.015f

    val TWO_BY_ONE = CollageTemplate(
        id = "2x1",
        displayName = "2 \u00d7 1",
        cells = listOf(
            CollageCell(RectF(0f, 0f, 0.5f - GUTTER, 1f)),
            CollageCell(RectF(0.5f + GUTTER, 0f, 1f, 1f))
        )
    )

    val ONE_BY_TWO = CollageTemplate(
        id = "1x2",
        displayName = "1 \u00d7 2",
        cells = listOf(
            CollageCell(RectF(0f, 0f, 1f, 0.5f - GUTTER)),
            CollageCell(RectF(0f, 0.5f + GUTTER, 1f, 1f))
        )
    )

    val TWO_BY_TWO = CollageTemplate(
        id = "2x2",
        displayName = "2 \u00d7 2",
        cells = listOf(
            CollageCell(RectF(0f, 0f, 0.5f - GUTTER, 0.5f - GUTTER)),
            CollageCell(RectF(0.5f + GUTTER, 0f, 1f, 0.5f - GUTTER)),
            CollageCell(RectF(0f, 0.5f + GUTTER, 0.5f - GUTTER, 1f)),
            CollageCell(RectF(0.5f + GUTTER, 0.5f + GUTTER, 1f, 1f))
        )
    )

    val THREE_BY_ONE = CollageTemplate(
        id = "3x1",
        displayName = "3 \u00d7 1",
        cells = listOf(
            CollageCell(RectF(0f, 0f, 1f / 3f - GUTTER, 1f)),
            CollageCell(RectF(1f / 3f + GUTTER, 0f, 2f / 3f - GUTTER, 1f)),
            CollageCell(RectF(2f / 3f + GUTTER, 0f, 1f, 1f))
        )
    )

    val ALL = listOf(TWO_BY_ONE, ONE_BY_TWO, TWO_BY_TWO, THREE_BY_ONE)
}

/**
 * Renders a list of page bitmaps into a single collage bitmap according to a
 * template's cell layout.
 *
 * Mismatch handling (template cell count and selected page count won't
 * always match):
 * - Fewer pages than cells: leftover cells are left blank (white), not an error.
 * - More pages than cells: extra pages beyond the cell count are ignored.
 * The picker UI should make this visually obvious rather than the
 * compositor silently surprising the user, but the engine itself stays
 * permissive so it never throws on a mismatched count.
 *
 * Each page is scaled to FIT within its cell (preserving aspect ratio,
 * centered) rather than CROP — for documents, losing part of the page to a
 * crop is much worse than a bit of empty margin within the cell.
 */
object CollageCompositor {

    /** Legacy entry point: pages fill cells in order, no per-cell adjustment. Kept for callers not yet using per-cell editing. */
    fun compose(
        pages: List<Bitmap>,
        template: CollageTemplate,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ): Bitmap {
        val result = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        template.cells.forEachIndexed { index, cell ->
            val page = pages.getOrNull(index) ?: return@forEachIndexed
            drawPageInCell(canvas, paint, page, cell, canvasWidthPx, canvasHeightPx, CollageCellTransform())
        }
        return result
    }

    /**
     * Per-cell-aware composition: each cell may have its own page assignment
     * and its own zoom/pan transform, exactly mirroring what the live preview
     * in CollageScreen rendered — this is what makes "Save" produce the same
     * framing the person actually arranged, not just a default fit-to-cell
     * version of it.
     */
    fun composeWithAssignments(
        pageBitmaps: Map<Long, Bitmap>,
        assignments: List<CollageCellAssignment>,
        template: CollageTemplate,
        canvasWidthPx: Int,
        canvasHeightPx: Int
    ): Bitmap {
        val result = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        template.cells.forEachIndexed { index, cell ->
            val assignment = assignments.getOrNull(index) ?: return@forEachIndexed
            val pageId = assignment.pageId ?: return@forEachIndexed // empty cell, nothing to draw
            val page = pageBitmaps[pageId] ?: return@forEachIndexed
            drawPageInCell(canvas, paint, page, cell, canvasWidthPx, canvasHeightPx, assignment.transform)
        }
        return result
    }

    /**
     * Draws one page into one cell, applying the base fit-to-cell framing
     * (preserve aspect ratio, centered — for documents, a little empty
     * margin beats losing part of the page to a crop) and then the user's
     * extra scale/pan on top of that base framing.
     */
    private fun drawPageInCell(
        canvas: Canvas,
        paint: Paint,
        page: Bitmap,
        cell: CollageCell,
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        transform: CollageCellTransform
    ) {
        val cellLeft = cell.rect.left * canvasWidthPx
        val cellTop = cell.rect.top * canvasHeightPx
        val cellWidth = (cell.rect.right - cell.rect.left) * canvasWidthPx
        val cellHeight = (cell.rect.bottom - cell.rect.top) * canvasHeightPx

        val pageAspect = page.width.toFloat() / page.height.toFloat()
        val cellAspect = cellWidth / cellHeight

        val (baseWidth, baseHeight) = if (pageAspect > cellAspect) {
            cellWidth to (cellWidth / pageAspect)
        } else {
            (cellHeight * pageAspect) to cellHeight
        }

        // User's extra zoom on top of the base fit-to-cell size.
        val drawWidth = baseWidth * transform.scale
        val drawHeight = baseHeight * transform.scale

        // Centered placement, then the user's pan offset — expressed as a
        // fraction of cell size so it's resolution-independent between the
        // (usually smaller) preview render and this (usually larger) export
        // canvas.
        val baseLeft = cellLeft + (cellWidth - drawWidth) / 2f
        val baseTop = cellTop + (cellHeight - drawHeight) / 2f
        val drawLeft = baseLeft + transform.offsetFractionX * cellWidth
        val drawTop = baseTop + transform.offsetFractionY * cellHeight

        // Clip to the cell's own bounds — zooming in (scale > 1) makes the
        // drawn image larger than the cell, and without clipping it would
        // bleed into neighboring cells rather than just cropping within its own.
        canvas.save()
        canvas.clipRect(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
        val destRect = RectF(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight)
        canvas.drawBitmap(page, null, destRect, paint)
        canvas.restore()
    }
}