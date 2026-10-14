// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/view/background/*BackgroundView.cpp and
// src/core/control/PdfCache.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.android.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import pm.asaf.inkpad.core.geometry.BackgroundPatterns
import pm.asaf.inkpad.core.geometry.FontDescription
import pm.asaf.inkpad.core.geometry.FontFamily
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Page

/** Draws page backgrounds (docs/rendering.md §2) in page units. */
internal class BackgroundPainter(
    private val source: BackgroundSource,
    private val fonts: FontResolver,
) {
    private val fill = Paint().apply { style = Paint.Style.FILL }
    private val line =
        Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.MITER // cairo's default
        }
    private val image = Paint().apply { isFilterBitmap = true }

    fun paint(
        canvas: Canvas,
        page: Page,
        clip: Bounds,
        zoom: Float,
    ) {
        when (val background = page.background) {
            is Background.Solid -> {
                paintSolid(canvas, background, page, clip)
            }

            is Background.Pixmap -> {
                fillWhite(canvas, clip)
                source.image(background)?.let { bitmap ->
                    // Stretched to the page, like Xournal++.
                    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, page.width.toFloat(), page.height.toFloat()), image)
                }
            }

            is Background.Pdf -> {
                fillWhite(canvas, clip)
                val drawn = background.pageNumber?.let { source.drawPdfPage(canvas, it, zoom) } ?: false
                if (!drawn) paintMissingPdf(canvas, page)
            }

            is Background.Unknown, null -> {
                fillWhite(canvas, clip)
            }
        }
    }

    private fun paintSolid(
        canvas: Canvas,
        background: Background.Solid,
        page: Page,
        clip: Bounds,
    ) {
        val pattern = BackgroundPatterns.of(background, page.width, page.height, clip)
        fill.color = pattern.fill.toOpaqueArgb()
        canvas.drawRect(clip.toRectF(), fill)
        for (stroke in pattern.strokes) {
            line.color = stroke.color.toOpaqueArgb()
            line.strokeWidth = stroke.width.toFloat()
            line.strokeCap = stroke.cap.toPaintCap()
            canvas.drawPath(stroke.path.toAndroidPath(), line)
        }
    }

    private fun fillWhite(
        canvas: Canvas,
        clip: Bounds,
    ) {
        fill.color = android.graphics.Color.WHITE
        canvas.drawRect(clip.toRectF(), fill)
    }

    /**
     * Xournal++'s placeholder (PdfCache::renderMissingPdfPage), which Xournal++ builds but
     * never fills: the baseline starts at the page centre minus half the text's ink extents.
     */
    private fun paintMissingPdf(
        canvas: Canvas,
        page: Page,
    ) {
        val paint =
            Paint().apply {
                isAntiAlias = true
                typeface = fonts.typeface(FontDescription(FontFamily.DEJAVU_SANS, bold = true, italic = false))
                textSize = MISSING_PDF_TEXT_SIZE
                color = MISSING_PDF_COLOR
            }
        val extents = Rect().also { paint.getTextBounds(MISSING_PDF_TEXT, 0, MISSING_PDF_TEXT.length, it) }
        val x = page.width.toFloat() / 2 - extents.width() / 2f
        val y = page.height.toFloat() / 2 - extents.height() / 2f
        canvas.drawText(MISSING_PDF_TEXT, x, y, paint)
    }

    private companion object {
        const val MISSING_PDF_TEXT = "PDF background missing"
        const val MISSING_PDF_TEXT_SIZE = 26f
        const val MISSING_PDF_COLOR = 0xffcccccc.toInt()
    }
}
