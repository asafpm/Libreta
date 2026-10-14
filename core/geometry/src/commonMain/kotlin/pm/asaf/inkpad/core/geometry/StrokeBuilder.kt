// SPDX-License-Identifier: GPL-2.0-or-later
// Port of Xournal++ v1.3.8 src/core/control/tools/StrokeHandler.cpp (paintTo,
// finalizeStroke) and the pressure filter of src/core/gui/inputdevices/PenInputHandler.cpp,
// © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.DoubleList
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** An input position in page coordinates, with the pressure (0–1) if the device has one. */
data class InkSample(
    val x: Double,
    val y: Double,
    val pressure: Double?,
)

/**
 * Builds a stroke from input samples the way Xournal++ does: points closer than
 * [MIN_DISTANCE] to the last are dropped; with pressure, each point's width is the filtered
 * pressure times [width], and large width changes are spread over several short segments.
 */
class StrokeBuilder(
    private val tool: StrokeTool,
    private val color: Color,
    /** Nominal width, in points. */
    val width: Double,
    private val minimumPressure: Double = DEFAULT_MINIMUM_PRESSURE,
    private val pressureMultiplier: Double = 1.0,
) {
    private var xs = DoubleArray(INITIAL_CAPACITY)
    private var ys = DoubleArray(INITIAL_CAPACITY)
    private var widths = DoubleArray(INITIAL_CAPACITY)
    private var hasPressure = false

    /** The last pressure the device sent, for samples that omit it. */
    private var lastPressure = 1.0

    var pointCount = 0
        private set

    fun x(index: Int): Double = xs[index]

    fun y(index: Int): Double = ys[index]

    /** The width at point [index]: pressure-dependent, or the nominal width. */
    fun width(index: Int): Double = if (hasPressure) widths[index] else width

    /**
     * Adds [sample]; the first one starts the stroke. Returns the index of the first point
     * that is new or changed ([pointCount] if none), from which drawing must be updated.
     */
    fun add(sample: InkSample): Int =
        when {
            pointCount == 0 -> {
                hasPressure = sample.pressure != null
                append(sample.x, sample.y, pressureWidth(sample.pressure))
                0
            }

            // Some devices send a move with pressure 0 as the stylus lifts.
            sample.pressure == 0.0 -> {
                pointCount
            }

            else -> {
                paintTo(sample.x, sample.y, pressureWidth(sample.pressure))
            }
        }

    /**
     * The finished stroke. [releasePressure] is the pressure when the stylus lifted, which
     * can thicken a single dot.
     */
    fun finish(releasePressure: Double?): Element.Stroke {
        check(pointCount > 0) { "no samples" }
        // A visible line needs two points: a dot is its point twice.
        if (pointCount == 1) {
            if (hasPressure) widths[0] = max(widths[0], pressureWidth(releasePressure))
            append(xs[0], ys[0], widths[0])
        }
        return current()
    }

    /** The stroke so far, e.g. to draw it while it is written; a single point is a dot. */
    fun current(): Element.Stroke {
        check(pointCount > 0) { "no samples" }
        val count = max(pointCount, 2)
        val coordinates = DoubleArray(2 * count)
        for (i in 0 until count) {
            coordinates[2 * i] = xs[min(i, pointCount - 1)]
            coordinates[2 * i + 1] = ys[min(i, pointCount - 1)]
        }
        return Element.Stroke(
            tool = tool,
            color = color,
            width = width,
            points = PointList.fromInterleaved(coordinates),
            // Xournal++ stores one width per point except the last.
            segmentWidths = if (hasPressure) DoubleList.fromArray(widths.copyOf(count - 1)) else null,
        )
    }

    /** The smallest rectangle around the points so far (without the stroke's width). */
    fun pointBounds(): Bounds {
        var bounds = Bounds(xs[0], ys[0], xs[0], ys[0])
        for (i in 1 until pointCount) {
            bounds =
                Bounds(
                    min(bounds.left, xs[i]),
                    min(bounds.top, ys[i]),
                    max(bounds.right, xs[i]),
                    max(bounds.bottom, ys[i]),
                )
        }
        return bounds
    }

    private fun pressureWidth(pressure: Double?): Double {
        if (!hasPressure) return width
        val raw = pressure ?: lastPressure
        lastPressure = raw
        return max(minimumPressure, raw * pressureMultiplier) * width
    }

    private fun paintTo(
        x: Double,
        y: Double,
        z: Double,
    ): Int {
        val last = pointCount - 1
        val distance = hypot(x - xs[last], y - ys[last])
        if (distance < MIN_DISTANCE) {
            // Pressure often rises before the pen moves: record it for the first point.
            val thicker = pointCount == 1 && hasPressure && widths[0] < z
            if (thicker) widths[0] = z
            return if (thicker) 0 else pointCount
        }
        val firstNew = pointCount
        val widthDelta = z - widths[last]
        if (hasPressure && abs(widthDelta) > MAX_WIDTH_VARIATION) {
            val steps = min(ceil(abs(widthDelta) / MAX_WIDTH_VARIATION), floor(distance / MIN_DISTANCE))
            val step = 1.0 / steps
            val dx = (x - xs[last]) * step
            val dy = (y - ys[last]) * step
            val dz = widthDelta * step
            var px = xs[last]
            var py = ys[last]
            // As in Xournal++, the width starts one step ahead of the position.
            var pz = widths[last] + dz
            for (i in 1 until steps.toInt()) {
                px += dx
                py += dy
                pz += dz
                append(px, py, pz)
            }
        }
        append(x, y, z)
        return firstNew
    }

    private fun append(
        x: Double,
        y: Double,
        z: Double,
    ) {
        if (pointCount == xs.size) {
            xs = xs.copyOf(pointCount * 2)
            ys = ys.copyOf(pointCount * 2)
            widths = widths.copyOf(pointCount * 2)
        }
        // As written to files: short.
        xs[pointCount] = x.toEightDigits()
        ys[pointCount] = y.toEightDigits()
        widths[pointCount] = z.toEightDigits()
        pointCount++
    }

    companion object {
        /** Xournal++'s `PIXEL_MOTION_THRESHOLD`, in page units (points). */
        const val MIN_DISTANCE = 0.3

        /** Xournal++'s `StrokeHandler::MAX_WIDTH_VARIATION`, in points. */
        const val MAX_WIDTH_VARIATION = 0.3

        /** Xournal++'s default minimum pressure. */
        const val DEFAULT_MINIMUM_PRESSURE = 0.05

        private const val INITIAL_CAPACITY = 256
    }
}
