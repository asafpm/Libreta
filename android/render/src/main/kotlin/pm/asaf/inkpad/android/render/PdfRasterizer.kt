// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import okio.ByteString

/** Renders pages of in-memory PDFs, such as LaTeX formulas stored as PDF. */
fun interface PdfRasterizer {
    /**
     * Page [pageIndex] (0-based) of [pdf] scaled to [widthPx] x [heightPx], over a transparent
     * background, or null if the PDF cannot be rendered.
     */
    fun render(
        pdf: ByteString,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap?

    companion object {
        /** Renders nothing. */
        val NONE = PdfRasterizer { _, _, _, _ -> null }
    }
}
