// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import pm.asaf.libreta.core.geometry.InkSample
import pm.asaf.libreta.core.geometry.StrokeBuilder
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Made-up handwriting for the sample documents: words of cursive loops, arches and circles,
 * slanted and a little unsteady, written as a pen writes them (pen lifts inside words, dots
 * above them, pressure rising and falling along each stroke). The same [seed] writes the same.
 */
internal class Handwriting(
    seed: Int,
    private val color: Color,
    private val width: Double,
    private val pressure: Boolean,
) {
    private val random = Random(seed)

    /** Words from ([left], [baseline]) as far as [right], letters [xHeight] high. */
    fun line(
        left: Double,
        baseline: Double,
        right: Double,
        xHeight: Double = X_HEIGHT,
    ): List<Element.Stroke> {
        val strokes = ArrayList<Element.Stroke>()
        var x = left
        while (true) {
            val letters = List(1 + random.nextInt(MAX_LETTERS)) { LETTERS.random(random) }
            val advance = xHeight * (0.9 + 0.4 * random.nextDouble())
            if (x + letters.size * advance > right) break
            strokes += word(x, baseline, letters, advance, xHeight)
            x += letters.size * advance + xHeight * (1.2 + random.nextDouble())
        }
        return strokes
    }

    /** A straight stroke, a little unsteady: underlines, fraction bars, equals signs, arrows. */
    fun straight(
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
    ): Element.Stroke {
        val steps = 12
        return stroke(List(steps + 1) { i -> fromX + (toX - fromX) * i / steps to fromY + (toY - fromY) * i / steps })
    }

    /** A closed loop around the box from ([left], [top]) to ([right], [bottom]), as drawn by hand. */
    fun ellipse(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): Element.Stroke {
        val steps = 40
        val (cx, cy) = (left + right) / 2 to (top + bottom) / 2
        val (rx, ry) = (right - left) / 2 to (bottom - top) / 2
        // A little more than once round, as a hand closes a loop.
        return stroke(
            List(steps + 4) { i ->
                val angle = 2 * PI * i / steps - PI / 2
                cx + rx * cos(angle) to cy + ry * sin(angle)
            },
        )
    }

    private fun word(
        left: Double,
        baseline: Double,
        letters: List<Letter>,
        advance: Double,
        xHeight: Double,
    ): List<Element.Stroke> {
        val strokes = ArrayList<Element.Stroke>()
        var points = ArrayList<Pair<Double, Double>>()
        letters.forEachIndexed { index, letter ->
            points += letter.points(left + index * advance, baseline, advance, xHeight)
            // The pen lifts now and then, and goes on where it stopped.
            if (index < letters.lastIndex && random.nextDouble() < LIFT) {
                strokes += stroke(points)
                points = arrayListOf(points.last())
            }
        }
        strokes += stroke(points)
        if (random.nextDouble() < DOT) {
            val x = left + advance * random.nextInt(letters.size) + advance / 2
            val y = baseline - xHeight * 1.6
            strokes += stroke(listOf(x to y, x + 0.4 to y - 0.3))
        }
        return strokes
    }

    private fun Letter.points(
        left: Double,
        baseline: Double,
        advance: Double,
        xHeight: Double,
    ): List<Pair<Double, Double>> =
        List(STEPS + 1) { i ->
            // A trochoid: the pen goes along, up to the letter's height and back, looping when
            // [loop] is wide enough.
            val angle = 2 * PI * i / STEPS
            val up = height * xHeight * (1 - cos(angle)) / 2
            val x = left + advance * i / STEPS - loop * advance * sin(angle)
            val y = baseline - up
            x + up * SLANT + jitter() to y + jitter()
        }

    private fun stroke(points: List<Pair<Double, Double>>): Element.Stroke =
        StrokeBuilder(StrokeTool.PEN, color, width).run {
            points.forEachIndexed { i, (x, y) ->
                val along = if (points.size > 1) i.toDouble() / (points.size - 1) else 0.5
                val force =
                    if (pressure) {
                        (
                            0.3 + 0.6 *
                                sqrt(
                                    sin(PI * along),
                                ) + jitter() / 4
                        ).coerceIn(0.1, 1.0)
                    } else {
                        null
                    }
                add(InkSample(round(x), round(y), force?.let(::round)))
            }
            finish(null)
        }

    private fun jitter() = (random.nextDouble() - 0.5) * JITTER

    /** A letter's shape: [height] in x-heights (negative goes below the line), [loop] its width. */
    private class Letter(
        val height: Double,
        val loop: Double,
    )

    private companion object {
        const val X_HEIGHT = 6.0
        const val MAX_LETTERS = 7
        const val STEPS = 14
        const val SLANT = 0.25
        const val JITTER = 0.3
        const val LIFT = 0.2
        const val DOT = 0.3

        // e, l, n, o, g (below the line), m.
        val LETTERS =
            listOf(
                Letter(1.0, 0.3),
                Letter(2.4, 0.25),
                Letter(1.0, 0.0),
                Letter(1.0, 0.45),
                Letter(-1.5, 0.2),
                Letter(0.9, 0.05),
            )

        fun round(value: Double) = (value * 100).roundToLong() / 100.0
    }
}
