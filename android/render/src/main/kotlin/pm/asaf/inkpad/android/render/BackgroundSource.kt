// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import pm.asaf.inkpad.core.model.Background

/**
 * The files behind image and PDF backgrounds. Locating them (next to the document, inside
 * it, at an absolute path) is the caller's job; the renderer only draws.
 */
interface BackgroundSource {
    /** The decoded image of [background], or null if it is not available. */
    fun image(background: Background.Pixmap): Bitmap?

    /**
     * Draws page [pageNumber] (1-based) of the document's PDF onto [canvas] at the PDF page's
     * own size from the origin, in page units. The canvas is already scaled by [zoom], which
     * is given to choose a resolution. Returns false if the PDF or the page is not available.
     */
    fun drawPdfPage(
        canvas: Canvas,
        pageNumber: Long,
        zoom: Float,
    ): Boolean

    /** No background files: image and PDF backgrounds draw as missing. */
    object None : BackgroundSource {
        override fun image(background: Background.Pixmap): Bitmap? = null

        override fun drawPdfPage(
            canvas: Canvas,
            pageNumber: Long,
            zoom: Float,
        ): Boolean = false
    }
}
