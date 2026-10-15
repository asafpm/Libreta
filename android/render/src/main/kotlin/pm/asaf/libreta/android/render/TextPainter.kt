// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/model/Text.cpp and src/core/view/TextView.cpp,
// © Xournal++ contributors.

package pm.asaf.libreta.android.render

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import pm.asaf.libreta.core.geometry.Size
import pm.asaf.libreta.core.geometry.TextMeasurer
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Element
import kotlin.math.ceil

/**
 * Draws text elements (docs/rendering.md §3.4): `size` is the em size in page units, lines
 * break only at newlines, and the layout's top-left is at (x, y).
 *
 * Line positions reproduce Pango's hinted metrics, measured on Xournal++ 1.3.8: the first
 * baseline is ceil(ascent) below y and lines are ceil(ascent + descent) apart, in points.
 */
internal class TextPainter(
    private val fonts: FontResolver,
) : TextMeasurer {
    private class Lines(
        val paint: TextPaint,
        val lines: List<String>,
        val firstBaseline: Float,
        val lineHeight: Float,
    ) {
        val width: Float get() = lines.maxOf { paint.measureText(it) }
        val height: Float get() = lines.size * lineHeight
    }

    override fun measure(text: Element.Text): Size {
        val lines = lines(text)
        return Size(lines.width.toDouble(), lines.height.toDouble())
    }

    fun paint(
        canvas: Canvas,
        text: Element.Text,
    ) {
        val lines = lines(text)
        lines.lines.forEachIndexed { index, line ->
            val baseline = text.y.toFloat() + lines.firstBaseline + index * lines.lineHeight
            canvas.drawText(line, text.x.toFloat(), baseline, lines.paint)
        }
    }

    /** Where lines of [font] at [size] go, in points: the first baseline below the top, and the line height. */
    fun lineMetrics(
        font: String,
        size: Double,
    ): LineMetrics {
        val lines = lines(Element.Text("", font, size, 0.0, 0.0, Color.BLACK))
        return LineMetrics(lines.firstBaseline.toDouble(), lines.lineHeight.toDouble())
    }

    private fun lines(text: Element.Text): Lines {
        val paint =
            TextPaint().apply {
                isAntiAlias = true
                // Unhinted advances that scale with the page, like Pango's layout.
                flags = flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
                typeface = fonts.typeface(text.font)
                textSize = text.size.toFloat()
                color = text.color.toOpaqueArgb()
            }
        val metrics = paint.fontMetrics
        val ascent = -metrics.ascent
        val descent = metrics.descent
        return Lines(paint, text.text.split('\n'), ceil(ascent), ceil(ascent + descent))
    }
}

/** How lines of text are placed, in points: the first baseline below the text's top, and the distance between lines. */
data class LineMetrics(
    val firstBaseline: Double,
    val lineHeight: Double,
)
