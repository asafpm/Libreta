// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import pm.asaf.libreta.core.model.Background
import java.io.Closeable
import java.io.File
import kotlin.math.ceil

/**
 * A [BackgroundSource] for documents annotating the PDF [pdfFile]: each page is drawn at the
 * PDF page's own size from the origin, like Xournal++ (docs/rendering.md §2.3). Image
 * backgrounds come from [images]. Renders are serialised. A page that is small enough at a
 * zoom is rendered whole and cached (up to [CACHE_BYTES]), and cached ones are drawn without
 * waiting; at higher zooms only the part a canvas shows is rendered, so that memory stays
 * bounded however far one zooms in.
 */
class PdfBackgroundSource(
    pdfFile: File,
    private val images: BackgroundSource = BackgroundSource.None,
) : BackgroundSource,
    Closeable {
    private val renderer = PdfPageRenderer.open(pdfFile)

    /** Whole pages by page and pixels a point. */
    private val cache =
        object : LruCache<Pair<Int, Float>, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(
                key: Pair<Int, Float>,
                value: Bitmap,
            ) = value.allocationByteCount
        }

    /** Page sizes in points, as asked for. */
    private val sizes = HashMap<Int, Pair<Float, Float>>()

    /** Closed (its note was closed): renders still running draw nothing. */
    private var closed = false
    private val paint = Paint().apply { isFilterBitmap = true }

    val pageCount: Int = renderer.pageCount

    override fun image(background: Background.Pixmap): Bitmap? = images.image(background)

    override fun drawPdfPage(
        canvas: Canvas,
        pageNumber: Long,
        zoom: Float,
    ): Boolean {
        val index = (pageNumber - 1).toInt()
        // Like Xournal++, never below one pixel per point.
        return pageNumber >= 1 && index < pageCount && draw(canvas, index, maxOf(zoom, 1f))
    }

    /** The size of PDF page [pageNumber] (from 1) in points, or null if there is no such page. */
    @Synchronized
    fun pageSize(pageNumber: Long): Pair<Double, Double>? {
        val index = (pageNumber - 1).toInt()
        if (pageNumber < 1 || index >= pageCount) return null
        return renderer.pageSize(index)
    }

    /** Draws page [index] at [scale] pixels a point: whole if that is small enough, else the part [canvas] shows. */
    private fun draw(
        canvas: Canvas,
        index: Int,
        scale: Float,
    ): Boolean {
        val (width, height) = size(index) ?: return false
        val area = RectF(0f, 0f, width, height)
        return if (width * scale * height * scale <= MAX_WHOLE_PIXELS) {
            drawWhole(canvas, index, scale, area)
        } else {
            drawPart(canvas, index, scale, area)
        }
    }

    @Synchronized
    private fun size(index: Int): Pair<Float, Float>? =
        if (closed) {
            null
        } else {
            sizes.getOrPut(index) {
                renderer.pageSize(index).let { (width, height) ->
                    width.toFloat() to
                        height.toFloat()
                }
            }
        }

    private fun drawWhole(
        canvas: Canvas,
        index: Int,
        scale: Float,
        area: RectF,
    ): Boolean {
        // Rendered pages are drawn without waiting for a render on another thread.
        val bitmap = cache.get(index to scale) ?: renderWhole(index, scale, area)
        bitmap?.let { canvas.drawBitmap(it, null, area, paint) }
        return bitmap != null
    }

    @Synchronized
    private fun renderWhole(
        index: Int,
        scale: Float,
        area: RectF,
    ): Bitmap? =
        if (closed) {
            null
        } else {
            cache.get(index to scale) ?: rendered(index, scale, area).also { cache.put(index to scale, it) }
        }

    /** Draws the part of the page [canvas] shows (its clip, in page units). */
    private fun drawPart(
        canvas: Canvas,
        index: Int,
        scale: Float,
        area: RectF,
    ): Boolean {
        val part = RectF(canvas.clipBounds).takeIf { it.intersect(area) }
        val bitmap = part?.let { renderPart(index, scale, it) }
        if (part != null && bitmap != null) {
            // The bitmap's pixels, which round the part up.
            val place = RectF(part.left, part.top, part.left + bitmap.width / scale, part.top + bitmap.height / scale)
            canvas.drawBitmap(bitmap, null, place, paint)
            bitmap.recycle()
        }
        // Nothing of the page shown is drawn too.
        return part == null || bitmap != null
    }

    @Synchronized
    private fun renderPart(
        index: Int,
        scale: Float,
        part: RectF,
    ): Bitmap? = if (closed) null else rendered(index, scale, part)

    /** The [part] (points) of page [index] at [scale] pixels a point. */
    private fun rendered(
        index: Int,
        scale: Float,
        part: RectF,
    ): Bitmap {
        val bitmap =
            Bitmap.createBitmap(
                ceil(part.width() * scale).toInt().coerceAtLeast(1),
                ceil(part.height() * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val transform =
            Matrix().apply {
                setScale(scale, scale)
                postTranslate(-part.left * scale, -part.top * scale)
            }
        renderer.render(index, bitmap, transform)
        return bitmap
    }

    @Synchronized
    override fun close() {
        closed = true
        cache.evictAll()
        renderer.close()
    }

    private companion object {
        /** Whole pages are rendered up to this many pixels (about a page at fit width on a tablet). */
        const val MAX_WHOLE_PIXELS = 8_000_000f

        /** Whole pages kept: a few at fit width, many as thumbnails. */
        const val CACHE_BYTES = 64 * 1024 * 1024
    }
}
