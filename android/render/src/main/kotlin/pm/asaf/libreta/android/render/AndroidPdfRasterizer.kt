// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.LruCache
import okio.ByteString
import java.io.File
import java.io.IOException

/**
 * [PdfRasterizer] on Android's PdfRenderer ([PdfPageRenderer]). It needs a seekable file, so the PDF
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
            // The folder may not be there yet (or be gone, as caches are).
            cacheDir.mkdirs()
            val file = File.createTempFile("libreta-pdf", ".pdf", cacheDir)
            try {
                file.writeBytes(key.pdf.toByteArray())
                PdfPageRenderer.open(file).use { pages -> renderPage(pages, key) }
            } finally {
                file.delete()
            }
        } catch (e: IOException) {
            // Damaged, or locked with a password.
            Log.w(TAG, "cannot render PDF", e)
            null
        }

    private fun renderPage(
        pages: PdfPageRenderer,
        key: Key,
    ): Bitmap? {
        if (key.pageIndex !in 0 until pages.pageCount) return null
        val (width, height) = pages.pageSize(key.pageIndex)
        // Transparent background: only the page's content is drawn.
        val bitmap = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
        val scale = Matrix().apply { setScale((key.width / width).toFloat(), (key.height / height).toFloat()) }
        pages.render(key.pageIndex, bitmap, scale)
        return bitmap
    }

    private companion object {
        const val CACHE_SIZE = 32
        const val TAG = "AndroidPdfRasterizer"
    }
}
