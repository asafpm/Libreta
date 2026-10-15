// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException

/** Draws the pages of one PDF into bitmaps. Not thread-safe. */
interface PdfPageRenderer : Closeable {
    val pageCount: Int

    /** The size of page [index] (from 0) in points, width to height. */
    fun pageSize(index: Int): Pair<Double, Double>

    /** Draws page [index] into [bitmap], points mapped to pixels by [transform]. */
    fun render(
        index: Int,
        bitmap: Bitmap,
        transform: Matrix,
    )

    companion object {
        /**
         * Opens [file] for drawing: with Android's [PdfRenderer]. Robolectric can't run it, so
         * tests replace this.
         */
        var open: (File) -> PdfPageRenderer = ::AndroidPdfPageRenderer
    }
}

/** [PdfPageRenderer] with Android's [PdfRenderer]. */
private class AndroidPdfPageRenderer(
    file: File,
) : PdfPageRenderer {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer =
        try {
            PdfRenderer(descriptor)
        } catch (e: IOException) {
            descriptor.close()
            throw e
        }

    override val pageCount: Int = renderer.pageCount

    override fun pageSize(index: Int): Pair<Double, Double> =
        renderer.openPage(index).use { it.width.toDouble() to it.height.toDouble() }

    override fun render(
        index: Int,
        bitmap: Bitmap,
        transform: Matrix,
    ) = renderer.openPage(index).use { it.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}
