// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/** Where the hovering stylus would write: the pen's dot, the highlighter's bar, or a text cursor. */
internal class PenCursor {
    /** What the cursor looks like, in view pixels. */
    sealed interface Look {
        /** A dot of [color], as wide as the pen's stroke. */
        data class Dot(
            val color: Int,
            val diameter: Float,
        ) : Look

        /** A translucent bar of [color], as wide as the highlighter's stroke. */
        data class Bar(
            val color: Int,
            val width: Float,
        ) : Look

        /** A text cursor as tall as text of the size chosen. */
        data class Caret(
            val height: Float,
        ) : Look
    }

    private var x = 0f
    private var y = 0f
    private var look: Look? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = LINE_WIDTH
        }

    /** Shows [look] at a view point; null hides it. */
    fun show(
        viewX: Float,
        viewY: Float,
        look: Look?,
    ) {
        x = viewX
        y = viewY
        this.look = look
    }

    fun draw(canvas: Canvas) {
        when (val shown = look) {
            null -> {
                Unit
            }

            is Look.Dot -> {
                fill.color = shown.color
                canvas.drawCircle(x, y, max(shown.diameter / 2, MIN_RADIUS), fill)
                // A light rim: visible on ink of the same colour.
                line.color = RIM
                canvas.drawCircle(x, y, max(shown.diameter / 2, MIN_RADIUS) + LINE_WIDTH, line)
            }

            is Look.Bar -> {
                fill.color = shown.color
                val half = max(shown.width / 2, MIN_RADIUS)
                canvas.drawRect(x - half / 2, y - half, x + half / 2, y + half, fill)
            }

            is Look.Caret -> {
                line.color = CARET
                canvas.drawLine(x, y - shown.height / 2, x, y + shown.height / 2, line)
                canvas.drawLine(x - SERIF, y - shown.height / 2, x + SERIF, y - shown.height / 2, line)
                canvas.drawLine(x - SERIF, y + shown.height / 2, x + SERIF, y + shown.height / 2, line)
            }
        }
    }

    private companion object {
        const val MIN_RADIUS = 1.5f
        const val LINE_WIDTH = 1.5f
        const val SERIF = 4f
        const val RIM = 0xC0FFFFFF.toInt()
        const val CARET = 0xFF404040.toInt()
    }
}
