// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import pm.asaf.inkpad.core.geometry.ElementGeometry
import pm.asaf.inkpad.core.geometry.TextMeasurer
import pm.asaf.inkpad.core.geometry.intersects
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Page
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
                    val bounds = ElementGeometry.bounds(element, textMeasurer) ?: continue
                    if (bounds.intersects(clip)) paintElement(canvas, element, zoom)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
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
}
