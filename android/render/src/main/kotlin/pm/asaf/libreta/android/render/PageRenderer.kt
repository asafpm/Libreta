// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import pm.asaf.libreta.core.geometry.ElementGeometry
import pm.asaf.libreta.core.geometry.TextMeasurer
import pm.asaf.libreta.core.geometry.intersects
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Page
import java.util.IdentityHashMap
import kotlin.math.ceil

/**
 * Draws Xournal++ pages onto a [Canvas] (docs/rendering.md): the background, then each
 * layer's elements in file order. Not thread-safe: use one renderer per thread.
 */
class PageRenderer(
    context: Context,
    backgroundSource: BackgroundSource = BackgroundSource.None,
    pdfRasterizer: PdfRasterizer = PdfRasterizer.NONE,
) {
    private val fonts = FontResolver(context)
    private val backgrounds = BackgroundPainter(backgroundSource, fonts)
    private val strokes = StrokePainter()
    private val texts = TextPainter(fonts)
    private val images = ImagePainter(pdfRasterizer)

    /** Measures text the way it is drawn, e.g. for bounding boxes. */
    val textMeasurer: TextMeasurer get() = texts

    /**
     * Draws [page] onto [canvas] with page coordinates scaled by [zoom] (pixels per point),
     * limited to [clip] (page units; the whole page by default).
     */
    fun render(
        page: Page,
        canvas: Canvas,
        zoom: Float,
        clip: Bounds = Bounds(0.0, 0.0, page.width, page.height),
    ) {
        val saved = canvas.save()
        try {
            canvas.scale(zoom, zoom)
            canvas.clipRect(clip.toRectF())
            backgrounds.paint(canvas, page, clip, zoom)
            for (layer in page.layers) {
                for (element in layer.elements) {
                    // Like Xournal++, skip elements that do not overlap the area being drawn.
                    val bounds = boundsOf(element) ?: continue
                    if (bounds.intersects(clip)) paintElement(canvas, element, zoom)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    /**
     * Draws [elements] alone, without a background, with page coordinates scaled by [zoom]:
     * e.g. a stroke just written, until the page's tiles show it.
     */
    fun renderElements(
        elements: List<Element>,
        canvas: Canvas,
        zoom: Float,
    ) {
        val saved = canvas.save()
        try {
            canvas.scale(zoom, zoom)
            for (element in elements) paintElement(canvas, element, zoom)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    /** Elements' bounds, by identity (elements never change), so that drawing small areas is quick. */
    private val bounds = IdentityHashMap<Element, Bounds>()

    private fun boundsOf(element: Element): Bounds? {
        bounds[element]?.let { return it.takeIf { it !== NO_BOUNDS } }
        // Kept small: elements no longer shown would stay in memory.
        if (bounds.size >= MAX_CACHED_BOUNDS) bounds.clear()
        val computed = ElementGeometry.bounds(element, textMeasurer)
        bounds[element] = computed ?: NO_BOUNDS
        return computed
    }

    private fun paintElement(
        canvas: Canvas,
        element: Element,
        zoom: Float,
    ) {
        when (element) {
            is Element.Stroke -> strokes.paint(canvas, element)
            is Element.Text -> texts.paint(canvas, element)
            is Element.Image -> images.paint(canvas, element)
            is Element.TexImage -> images.paint(canvas, element, zoom)
            is Element.Unknown -> Unit
        }
    }

    /** The whole page as a bitmap at [zoom] pixels per point. */
    fun renderToBitmap(
        page: Page,
        zoom: Float,
    ): Bitmap {
        val width = ceil(page.width * zoom).toInt().coerceAtLeast(1)
        val height = ceil(page.height * zoom).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render(page, Canvas(bitmap), zoom)
        return bitmap
    }

    private companion object {
        const val MAX_CACHED_BOUNDS = 50_000

        /** Cached for elements that draw nothing. */
        val NO_BOUNDS = Bounds(0.0, 0.0, 0.0, 0.0)
    }
}
