// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import pm.asaf.inkpad.core.model.Background
import java.io.Closeable
import java.io.File
import kotlin.math.ceil

/**
 * A [BackgroundSource] for documents annotating the PDF [pdfFile]: each page is drawn at the
 * PDF page's own size from the origin, like Xournal++ (docs/rendering.md §2.3). Image
 * backgrounds come from [images]. Calls are serialised; renders are cached per page and zoom.
 */
class PdfBackgroundSource(
    pdfFile: File,
    private val images: BackgroundSource = BackgroundSource.None,
) : BackgroundSource,
    Closeable {
    private val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val cache = LruCache<Pair<Int, Float>, Bitmap>(CACHE_SIZE)
    private val paint = Paint().apply { isFilterBitmap = true }

    val pageCount: Int get() = renderer.pageCount

    override fun image(background: Background.Pixmap): Bitmap? = images.image(background)

    @Synchronized
    override fun drawPdfPage(
        canvas: Canvas,
        pageNumber: Long,
        zoom: Float,
    ): Boolean {
        val index = (pageNumber - 1).toInt()
        if (pageNumber < 1 || index >= renderer.pageCount) return false
        val key = index to zoom
        val (bitmap, width, height) =
            renderer.openPage(index).use { page ->
                val rendered = cache.get(key) ?: render(page, zoom).also { cache.put(key, it) }
                Triple(rendered, page.width, page.height)
            }
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        return true
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
