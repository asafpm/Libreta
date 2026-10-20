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
 * backgrounds come from [images]. Renders are serialised and cached per page and zoom; cached
 * ones are drawn without waiting.
 */
class PdfBackgroundSource(
    pdfFile: File,
    private val images: BackgroundSource = BackgroundSource.None,
) : BackgroundSource,
    Closeable {
    private val renderer = PdfPageRenderer.open(pdfFile)

    /** A PDF page rendered, and its size in points. */
    private class Rendered(
        val bitmap: Bitmap,
        val width: Float,
        val height: Float,
    )

    private val cache = LruCache<Pair<Int, Float>, Rendered>(CACHE_SIZE)

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
        // Rendered pages are drawn without waiting for a render on another thread.
        val rendered =
            if (pageNumber < 1 || index >= pageCount) null else cache.get(index to zoom) ?: renderPage(index, zoom)
        rendered?.let { canvas.drawBitmap(it.bitmap, null, RectF(0f, 0f, it.width, it.height), paint) }
        return rendered != null
    }

    /** The size of PDF page [pageNumber] (from 1) in points, or null if there is no such page. */
    @Synchronized
    fun pageSize(pageNumber: Long): Pair<Double, Double>? {
        val index = (pageNumber - 1).toInt()
        if (pageNumber < 1 || index >= pageCount) return null
        return renderer.pageSize(index)
    }

    @Synchronized
    private fun renderPage(
        index: Int,
        zoom: Float,
    ): Rendered? =
        if (closed) {
            null
        } else {
            cache.get(index to zoom) ?: render(index, zoom)
        }

    private fun render(
        index: Int,
        zoom: Float,
    ): Rendered =
        run {
            val (width, height) = renderer.pageSize(index)
            // Like Xournal++, never below one pixel per point.
            val scale = maxOf(zoom, 1f)
            val bitmap =
                Bitmap.createBitmap(
                    ceil(width * scale).toInt(),
                    ceil(height * scale).toInt(),
                    Bitmap.Config.ARGB_8888,
                )
            renderer.render(index, bitmap, Matrix().apply { setScale(scale, scale) })
            Rendered(bitmap, width.toFloat(), height.toFloat()).also { cache.put(index to zoom, it) }
        }

    @Synchronized
    override fun close() {
        closed = true
        cache.evictAll()
        renderer.close()
    }

    private companion object {
        const val CACHE_SIZE = 8
    }
}
