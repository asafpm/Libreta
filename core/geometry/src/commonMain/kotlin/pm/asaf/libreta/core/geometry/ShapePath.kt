// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Drawing commands of a [ShapePath], in page units. */
sealed interface PathCommand {
    data class MoveTo(
        val x: Double,
        val y: Double,
    ) : PathCommand

    data class LineTo(
        val x: Double,
        val y: Double,
    ) : PathCommand

    /**
     * A circular arc with cairo's semantics (`cairo_arc`): a line from the current point (if
     * any) to the arc's start, then the arc in the direction of increasing angles from
     * [startAngle] to [endAngle], where [endAngle] is first increased by 2π until it is not
     * smaller than [startAngle]. Angles in radians, measured from +x towards +y (y points down).
     */
    data class Arc(
        val centerX: Double,
        val centerY: Double,
        val radius: Double,
        val startAngle: Double,
        val endAngle: Double,
    ) : PathCommand {
        /** Swept angle in [0, 2π]. */
        val sweep: Double
            get() {
                var end = endAngle
                while (end < startAngle) end += 2 * PI
                return end - startAngle
            }
    }

    data object Close : PathCommand
}

/** A platform-neutral path made of [PathCommand]s; filled with the non-zero winding rule. */
data class ShapePath(
    val commands: List<PathCommand>,
) {
    /**
     * Approximates the path by closed polygons (one per sub-path) as interleaved x, y
     * coordinates, with arcs split into steps of at most [maxStepAngle] radians.
     */
    fun toPolygons(maxStepAngle: Double = DEFAULT_STEP_ANGLE): List<DoubleArray> {
        val polygons = mutableListOf<DoubleArray>()
        var current = mutableListOf<Double>()

        fun finish() {
            if (current.size >= 2 * MIN_POLYGON_POINTS) polygons += current.toDoubleArray()
            current = mutableListOf()
        }
        for (command in commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    finish()
                    current += listOf(command.x, command.y)
                }

                is PathCommand.LineTo -> {
                    current += listOf(command.x, command.y)
                }

                is PathCommand.Arc -> {
                    val steps = maxOf(1, ceil(command.sweep / maxStepAngle).toInt())
                    for (step in 0..steps) {
                        val angle = command.startAngle + command.sweep * step / steps
                        current +=
                            listOf(
                                command.centerX + command.radius * cos(angle),
                                command.centerY + command.radius * sin(angle),
                            )
                    }
                }

                PathCommand.Close -> {
                    finish()
                }
            }
        }
        finish()
        return polygons
    }

    /** Whether (x, y) is inside the path under the non-zero winding rule (polygon approximation). */
    fun contains(
        x: Double,
        y: Double,
    ): Boolean = toPolygons().sumOf { windingNumber(it, x, y) } != 0

    private fun windingNumber(
        polygon: DoubleArray,
        x: Double,
        y: Double,
    ): Int {
        var winding = 0
        val count = polygon.size / 2
        for (index in 0 until count) {
            val x1 = polygon[2 * index]
            val y1 = polygon[2 * index + 1]
            val x2 = polygon[2 * ((index + 1) % count)]
            val y2 = polygon[2 * ((index + 1) % count) + 1]
            val side = (x2 - x1) * (y - y1) - (x - x1) * (y2 - y1)
            if (y1 <= y && y2 > y && side > 0) winding++
            if (y1 > y && y2 <= y && side < 0) winding--
        }
        return winding
    }

    private companion object {
        const val MIN_POLYGON_POINTS = 3
        const val DEFAULT_STEP_ANGLE = PI / 32
    }
}

/** Builds a [ShapePath] with cairo-like calls. */
internal class ShapePathBuilder {
    private val commands = mutableListOf<PathCommand>()

    fun moveTo(
        x: Double,
        y: Double,
    ) {
        commands += PathCommand.MoveTo(x, y)
    }

    fun lineTo(
        x: Double,
        y: Double,
    ) {
        commands += PathCommand.LineTo(x, y)
    }

    fun arc(
        centerX: Double,
        centerY: Double,
        radius: Double,
        startAngle: Double,
        endAngle: Double,
    ) {
        commands += PathCommand.Arc(centerX, centerY, radius, startAngle, endAngle)
    }

    /** Starts a new sub-path at the start of the arc that follows (cairo_new_sub_path + cairo_arc). */
    fun newSubPathArc(
        centerX: Double,
        centerY: Double,
        radius: Double,
        startAngle: Double,
        endAngle: Double,
    ) {
        moveTo(centerX + radius * cos(startAngle), centerY + radius * sin(startAngle))
        arc(centerX, centerY, radius, startAngle, endAngle)
    }

    fun close() {
        commands += PathCommand.Close
    }

    fun build(): ShapePath = ShapePath(commands.toList())
}
