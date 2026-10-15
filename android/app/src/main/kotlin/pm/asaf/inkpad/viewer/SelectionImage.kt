// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.core.geometry.SelectionBox
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** A selection's elements drawn over its box, [zoom] pixels per point; it is drawn from this while it moves. */
internal class SelectionImage(
    val bitmap: Bitmap,
    val zoom: Float,
) {
    companion object {
        /** The zoom an image of [box] gets for a page shown at [zoom]: as sharp, unless that would be huge. */
        fun zoomFor(
            box: SelectionBox,
            zoom: Double,
        ): Float {
            val bounds = box.box
            val area = (bounds.right - bounds.left) * (bounds.bottom - bounds.top)
            return min(zoom, sqrt(MAX_PIXELS / area)).toFloat()
        }

        /** [selected] drawn by [renderer] for a page shown at [zoom]. */
        fun of(
            selected: Selected,
            renderer: PageRenderer,
            zoom: Double,
        ): SelectionImage {
            val box = selected.box.box
            val scale = zoomFor(selected.box, zoom)
            val bitmap =
                Bitmap.createBitmap(
                    ceil((box.right - box.left) * scale).toInt().coerceAtLeast(1),
                    ceil((box.bottom - box.top) * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            val canvas = Canvas(bitmap)
            canvas.translate((-box.left * scale).toFloat(), (-box.top * scale).toFloat())
            renderer.renderElements(selected.elements, canvas, scale)
            return SelectionImage(bitmap, scale)
        }

        /** The most pixels a selection's image has. */
        private const val MAX_PIXELS = 16_000_000.0
    }
}

/**
 * The selection's image, drawn when it is selected (while nothing moves, so that taking it
 * doesn't wait) and kept while it is only moved.
 */
internal class KeptSelectionImage(
    private val host: SelectionTool.Host,
) {
    private var kept: SelectionImage? = null

    /** The selection changed other than by moving: its image is drawn again. */
    fun forget() {
        kept = null
    }

    /** Draws [selected]'s image now if it is on screen. */
    fun prepare(selected: Selected?) {
        selected?.let { current -> host.mappingOf(current.page)?.let { of(current, it.zoom) } }
    }

    /** [selected]'s image for a page shown at [zoom]: the one kept, if it is as sharp as needed. */
    fun of(
        selected: Selected,
        zoom: Double,
    ): SelectionImage? {
        val image = kept
        if (image != null && image.zoom == SelectionImage.zoomFor(selected.box, zoom)) return image
        kept = host.renderer?.let { SelectionImage.of(selected, it, zoom) }
        return kept
    }
}
