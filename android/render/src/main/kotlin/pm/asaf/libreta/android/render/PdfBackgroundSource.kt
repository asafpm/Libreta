// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
    private val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    /** A PDF page rendered, and its size in points. */
    private class Rendered(
        val bitmap: Bitmap,
        val width: Float,
        val height: Float,
    )

    private val cache = LruCache<Pair<Int, Float>, Rendered>(CACHE_SIZE)
    private val paint = Paint().apply { isFilterBitmap = true }

    val pageCount: Int = renderer.pageCount

    override fun image(background: Background.Pixmap): Bitmap? = images.image(background)

    override fun drawPdfPage(
        canvas: Canvas,
        pageNumber: Long,
        zoom: Float,
    ): Boolean {
        val index = (pageNumber - 1).toInt()
        if (pageNumber < 1 || index >= pageCount) return false
        // Rendered pages are drawn without waiting for a render on another thread.
        val rendered = cache.get(index to zoom) ?: renderPage(index, zoom)
        canvas.drawBitmap(rendered.bitmap, null, RectF(0f, 0f, rendered.width, rendered.height), paint)
        return true
    }

    @Synchronized
    private fun renderPage(
        index: Int,
        zoom: Float,
    ): Rendered =
        cache.get(index to zoom) ?: renderer.openPage(index).use { page ->
            Rendered(render(page, zoom), page.width.toFloat(), page.height.toFloat()).also {
                cache.put(index to zoom, it)
            }
        }

    private fun render(
        page: PdfRenderer.Page,
        zoom: Float,
    ): Bitmap {
        // Like Xournal++, never below one pixel per point.
        val scale = maxOf(zoom, 1f)
        val bitmap =
            Bitmap.createBitmap(
                ceil(page.width * scale).toInt(),
                ceil(page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    @Synchronized
    override fun close() {
        renderer.close()
        descriptor.close()
    }

    private companion object {
        const val CACHE_SIZE = 8
    }
}
