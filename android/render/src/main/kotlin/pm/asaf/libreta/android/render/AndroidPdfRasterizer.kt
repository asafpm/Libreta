// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import okio.ByteString
import java.io.File
import java.io.IOException

/**
 * [PdfRasterizer] on Android's [PdfRenderer]. PdfRenderer needs a seekable file, so the PDF
 * is written to a temporary file in [cacheDir]. Calls are serialised; results are cached.
 */
class AndroidPdfRasterizer(
    private val cacheDir: File,
) : PdfRasterizer {
    private data class Key(
        val pdf: ByteString,
        val pageIndex: Int,
        val width: Int,
        val height: Int,
    )

    private val cache = LruCache<Key, Bitmap>(CACHE_SIZE)

    @Synchronized
    override fun render(
        pdf: ByteString,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? {
        val key = Key(pdf, pageIndex, widthPx, heightPx)
        return cache.get(key) ?: renderUncached(key)?.also { cache.put(key, it) }
    }

    private fun renderUncached(key: Key): Bitmap? =
        try {
            val file = File.createTempFile("libreta-pdf", ".pdf", cacheDir)
            try {
                file.writeBytes(key.pdf.toByteArray())
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer -> renderPage(renderer, key) }
                }
            } finally {
                file.delete()
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot render PDF", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot render encrypted PDF", e)
            null
        }

    private fun renderPage(
        renderer: PdfRenderer,
        key: Key,
    ): Bitmap? {
        if (key.pageIndex !in 0 until renderer.pageCount) return null
        return renderer.openPage(key.pageIndex).use { page ->
            // Transparent background: only the page's content is drawn.
            val bitmap = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
            val scale =
                Matrix().apply {
                    setScale(
                        key.width.toFloat() / page.width,
                        key.height.toFloat() / page.height,
                    )
                }
            page.render(bitmap, null, scale, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    private companion object {
        const val CACHE_SIZE = 32
        const val TAG = "AndroidPdfRasterizer"
    }
}
