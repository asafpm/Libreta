// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/model/StrokeContour.cpp and
// src/core/view/StrokeViewHelper.cpp (drawWithPressure), © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * The filled outline of a pressure stroke (docs/rendering.md §3.2): round ends, segment i
 * of width zᵢ, smooth couplings between segments of different widths. `capStyle` does not
 * apply. With a dash pattern, every dash is its own closed outline with round ends.
 */
object StrokeContour {
    private const val HALF_PI = PI / 2

    fun outline(
        piece: StrokePiece,
        dashes: DoubleArray? = null,
    ): ShapePath {
        val widths = requireNotNull(piece.segmentWidths) { "not a pressure stroke" }
        require(piece.points.size >= 2 && widths.size == piece.points.size - 1) { "one width per segment required" }
        val points =
            List(piece.points.size) { index ->
                Vertex(
                    piece.points.x(index),
                    piece.points.y(index),
                    widths[
                        min(
                            index,
                            widths.size - 1,
                        ),
                    ],
                )
            }
        val builder = ShapePathBuilder()
        when {
            points.size == 2 && points[0].x == points[1].x && points[0].y == points[1].y -> {
                builder.arc(points[0].x, points[0].y, points[0].z / 2, 0.0, 2 * PI)
            }

            dashes != null -> {
                DashedOutline(builder, points, dashes).draw()
            }

            else -> {
                drawSolid(builder, points)
            }
        }
        return builder.build()
    }

    /** A point with the width of the segment that starts at it (the last one repeats the previous width). */
    private data class Vertex(
        val x: Double,
        val y: Double,
        val z: Double,
    ) {
        /** The point at [length] along the way to [other] (Point::lineTo). */
        fun towards(
            other: Vertex,
            length: Double,
        ): Vertex {
            val ratio = length / hypot(other.x - x, other.y - y)
            return Vertex(x + ratio * (other.x - x), y + ratio * (other.y - y), z + ratio * (other.z - z))
        }
    }

    /** Vector from [from] to [to] (MathVect2(from, to)). */
    private fun norm(
        from: Vertex,
        to: Vertex,
    ) = hypot(to.x - from.x, to.y - from.y)

    private fun argument(
        from: Vertex,
        to: Vertex,
    ) = atan2(to.y - from.y, to.x - from.x)

    /** An angle in [-2π, 2π] mapped to [-π, π]. */
    private fun inMinusPiPi(angle: Double): Double =
        when {
            angle > PI -> angle - 2 * PI
            angle < -PI -> angle + 2 * PI
            else -> angle
        }

    /** A path operation recorded on the way out and replayed in reverse on the way back. */
    private fun interface ReturnOp {
        fun apply(builder: ShapePathBuilder)
    }

    private fun chord(
        builder: ShapePathBuilder,
        x: Double,
        y: Double,
        radius: Double,
        angle1: Double,
        angle2: Double,
    ) {
        builder.lineTo(x + radius * kotlin.math.cos(angle1), y + radius * kotlin.math.sin(angle1))
        builder.lineTo(x + radius * kotlin.math.cos(angle2), y + radius * kotlin.math.sin(angle2))
    }

    private fun arcOrChord(
        builder: ShapePathBuilder,
        useArc: Boolean,
        x: Double,
        y: Double,
        radius: Double,
        angle1: Double,
        angle2: Double,
    ) {
        if (useArc) builder.arc(x, y, radius, angle1, angle2) else chord(builder, x, y, radius, angle1, angle2)
    }

    /**
     * Joins the segment ending at [p2] (width [z1], direction [a1] pointing back towards the
     * previous point, length [n1]) to the next one (direction [a3], length [n3]).
     */
    @Suppress("LongParameterList")
    private fun drawCoupling(
        builder: ShapePathBuilder,
        ops: MutableList<ReturnOp>,
        p2: Vertex,
        n1: Double,
        n3: Double,
        a1: Double,
        a3: Double,
        z1: Double,
    ) {
        val turn = inMinusPiPi(a3 - a1)
        if (z1 < p2.z) {
            if (p2.z / 2 < n1 || p2.z / 2 <= hypot(z1 / 2, n1)) {
                val angleIn = asin(z1 / p2.z)
                val a = inMinusPiPi(a1 + angleIn)
                val b = inMinusPiPi(a3 - HALF_PI)
                arcOrChord(builder, turn <= 0.0 || inMinusPiPi(b - a) > 0.0, p2.x, p2.y, p2.z / 2, a, b)
                val bp = inMinusPiPi(a1 - angleIn)
                val ap = inMinusPiPi(a3 + HALF_PI)
                val useArc = turn >= 0.0 || inMinusPiPi(bp - ap) > 0.0
                ops += ReturnOp { arcOrChord(it, useArc, p2.x, p2.y, p2.z / 2, ap, bp) }
            } else {
                builder.lineTo(p2.x, p2.y)
                builder.arc(p2.x, p2.y, p2.z / 2, a1, a3 - HALF_PI)
                ops += ReturnOp { hookBefore(it, p2, p2.z / 2, a3 + HALF_PI, a1) }
            }
        } else {
            if (z1 / 2 < n3 || z1 / 2 <= hypot(p2.z / 2, n3)) {
                val angleOut = asin(p2.z / z1)
                val a = inMinusPiPi(a1 + HALF_PI)
                val b = inMinusPiPi(a3 - angleOut)
                arcOrChord(builder, turn <= 0.0 || inMinusPiPi(b - a) > 0.0, p2.x, p2.y, z1 / 2, a, b)
                val bp = inMinusPiPi(a1 - HALF_PI)
                val ap = inMinusPiPi(a3 + angleOut)
                val useArc = turn >= 0.0 || inMinusPiPi(bp - ap) > 0.0
                ops += ReturnOp { arcOrChord(it, useArc, p2.x, p2.y, z1 / 2, ap, bp) }
            } else {
                builder.arc(p2.x, p2.y, z1 / 2, a1 + HALF_PI, a3)
                builder.lineTo(p2.x, p2.y)
                ops += ReturnOp { hookAfter(it, p2, z1 / 2, a3, a1 - HALF_PI) }
            }
        }
    }

    private fun hookAfter(
        builder: ShapePathBuilder,
        p: Vertex,
        radius: Double,
        a: Double,
        b: Double,
    ) {
        builder.lineTo(p.x, p.y)
        builder.arc(p.x, p.y, radius, a, b)
    }

    private fun hookBefore(
        builder: ShapePathBuilder,
        p: Vertex,
        radius: Double,
        a: Double,
        b: Double,
    ) {
        builder.arc(p.x, p.y, radius, a, b)
        builder.lineTo(p.x, p.y)
    }

    /** Round end around [end]; [forward] uses its own width, otherwise the adjacent point's. */
    private fun strokeEnd(
        builder: ShapePathBuilder,
        end: Vertex,
        adjacent: Vertex,
        forward: Boolean,
    ) {
        val a = argument(end, adjacent)
        builder.arc(end.x, end.y, (if (forward) end.z else adjacent.z) / 2, a + HALF_PI, a - HALF_PI)
    }

    private fun drawSolid(
        builder: ShapePathBuilder,
        points: List<Vertex>,
    ) {
        strokeEnd(builder, points.first(), points[1], forward = true)
        val ops = mutableListOf<ReturnOp>()
        for (index in 1 until points.size - 1) {
            val p1 = points[index - 1]
            val p2 = points[index]
            val p3 = points[index + 1]
            drawCoupling(builder, ops, p2, norm(p2, p1), norm(p2, p3), argument(p2, p1), argument(p2, p3), p1.z)
        }
        strokeEnd(builder, points.last(), points[points.size - 2], forward = false)
        ops.asReversed().forEach { it.apply(builder) }
        builder.close()
    }

    /** StrokeContourDashes::addToCairo with a dash offset of 0. */
    private class DashedOutline(
        private val builder: ShapePathBuilder,
        private val points: List<Vertex>,
        private val dashes: DoubleArray,
    ) {
        private val ops = mutableListOf<ReturnOp>()
        private var dashIndex = 0
        private var on = true
        private var dashOffset = 0.0

        fun draw() {
            strokeEnd(builder, points.first(), points[1], forward = true)
            for (index in 1 until points.size - 1) {
                val p1 = points[index - 1]
                val p2 = points[index]
                val p3 = points[index + 1]
                val norm1 = norm(p2, p1)
                val a1 = argument(p2, p1)
                dashSegment(p1, p2, norm1, a1)
                if (on) {
                    val n1 = min(dashOffset, norm1)
                    val n3 = min(dashes[dashIndex] - dashOffset, norm(p2, p3))
                    drawCoupling(builder, ops, p2, n1, n3, a1, argument(p2, p3), p1.z)
                }
            }
            val p1 = points[points.size - 2]
            val p2 = points.last()
            val a = argument(p2, p1)
            dashSegment(p1, p2, norm(p2, p1), a)
            if (on) {
                builder.arc(p2.x, p2.y, p1.z / 2, a + HALF_PI, a - HALF_PI)
                ops.asReversed().forEach { it.apply(builder) }
                builder.close()
            }
        }

        private fun dashSegment(
            p1: Vertex,
            p2: Vertex,
            norm1: Double,
            a1: Double,
        ) {
            dashOffset += norm1
            while (dashOffset >= dashes[dashIndex]) {
                val p = p1.towards(p2, dashes[dashIndex] - dashOffset + norm1)
                if (on) {
                    builder.arc(p.x, p.y, p1.z / 2, a1 + HALF_PI, a1 - HALF_PI)
                    ops.asReversed().forEach { it.apply(builder) }
                    builder.close()
                    ops.clear()
                } else {
                    builder.newSubPathArc(p.x, p.y, p1.z / 2, a1 - HALF_PI, a1 + HALF_PI)
                }
                dashOffset -= dashes[dashIndex]
                dashIndex = (dashIndex + 1) % dashes.size
                on = !on
            }
        }
    }
}
