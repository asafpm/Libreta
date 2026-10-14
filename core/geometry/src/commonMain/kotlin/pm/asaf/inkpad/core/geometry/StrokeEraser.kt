// SPDX-License-Identifier: GPL-2.0-or-later
// Port of Xournal++ v1.3.8 src/core/model/Stroke.cpp (intersects, intersectWithPaddedBox,
// cloneSection, cloneCircularSectionOfClosedStroke, getPoint), src/core/model/eraser/
// ErasableStroke.cpp and PaddedBox.h, src/util/include/util/UnionOfIntervals.h and the
// eraser padding of src/core/control/tools/EraseHandler.h, © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.CapStyle
import pm.asaf.inkpad.core.model.DoubleList
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PointList
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A position along a stroke: [t] (0–1) of the way along segment [index] (points index and index + 1). */
data class PathParameter(
    val index: Int,
    val t: Double,
) : Comparable<PathParameter> {
    override fun compareTo(other: PathParameter): Int =
        if (index !=
            other.index
        ) {
            index.compareTo(other.index)
        } else {
            t.compareTo(other.t)
        }
}

/** An axis-aligned rectangle (x, y, width, height), as Xournal++'s `Rectangle<double>`. */
internal data class Rect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    fun contains(
        px: Double,
        py: Double,
    ): Boolean = px >= x && px <= x + width && py >= y && py <= y + height
}

/**
 * The eraser square around ([x], [y]) with half side [halfSize], and the same square grown to
 * [halfSizeWithPadding] for a stroke's width: a stroke is erased wherever it is in the padded
 * square, provided it reaches the square itself.
 */
data class PaddedBox(
    val x: Double,
    val y: Double,
    val halfSize: Double,
    val halfSizeWithPadding: Double,
) {
    internal val inner: Rect get() = Rect(x - halfSize, y - halfSize, 2 * halfSize, 2 * halfSize)
    internal val outer: Rect
        get() = Rect(x - halfSizeWithPadding, y - halfSizeWithPadding, 2 * halfSizeWithPadding, 2 * halfSizeWithPadding)
}

/** A stroke's points with Xournal++'s per-point width (`z`): the width of the segment starting there. */
internal class StrokePath(
    stroke: Element.Stroke,
) {
    private val points: PointList = stroke.points
    val size: Int = points.size

    /** A width for each segment; widths that don't fit the points (a damaged file) are dropped. */
    private val widths: DoubleList? = stroke.segmentWidths?.takeIf { it.size >= size - 1 }
    val hasPressure: Boolean = widths != null

    fun x(index: Int): Double = points.x(index)

    fun y(index: Int): Double = points.y(index)

    /** The width at point [index], or [NO_PRESSURE]. */
    fun z(index: Int): Double = widths?.takeIf { index < it.size }?.get(index) ?: NO_PRESSURE

    /** The point at [parameter]; its width is that of the segment it is on. */
    fun at(parameter: PathParameter): Triple<Double, Double, Double> {
        val i = parameter.index
        val t = parameter.t
        return Triple(x(i) + t * (x(i + 1) - x(i)), y(i) + t * (y(i + 1) - y(i)), z(i))
    }

    companion object {
        const val NO_PRESSURE = -1.0
    }
}

/** Stroke erasing as Xournal++ does it: partial (cutting strokes), and whole-stroke hit testing. */
object StrokeEraser {
    /** How much of the stroke's width pads the eraser square, by cap style (round, butt, square). */
    private const val PADDING_ROUND = 0.4
    private const val PADDING_BUTT = 0.01
    private const val PADDING_SQUARE = 0.5

    /** The padded eraser square of half side [halfSize] around ([x], [y]) for [stroke]. */
    fun boxFor(
        stroke: Element.Stroke,
        x: Double,
        y: Double,
        halfSize: Double,
    ): PaddedBox {
        val coefficient =
            when (stroke.capStyle) {
                CapStyle.BUTT -> PADDING_BUTT
                CapStyle.SQUARE -> PADDING_SQUARE
                else -> PADDING_ROUND
            }
        return PaddedBox(x, y, halfSize, halfSize + coefficient * stroke.width)
    }

    /** Whether the eraser square of half side [halfSize] at ([x], [y]) touches [stroke] ("delete stroke" eraser). */
    fun touches(
        stroke: Element.Stroke,
        x: Double,
        y: Double,
        halfSize: Double,
    ): Boolean {
        val points = stroke.points
        val square = Rect(x - halfSize, y - halfSize, 2 * halfSize, 2 * halfSize)
        return (0 until points.size).any { i ->
            val previous = max(i - 1, 0)
            square.contains(points.x(i), points.y(i)) ||
                nearSegment(points.x(previous), points.y(previous), points.x(i), points.y(i), x, y, halfSize)
        }
    }

    /** Whether ([x], [y]) is within about [halfSize] of the segment from a to b, as Xournal++ tests it. */
    private fun nearSegment(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        x: Double,
        y: Double,
        halfSize: Double,
    ): Boolean {
        val length = hypot(bx - ax, by - ay)
        // The distance to the line through the segment, then to a circle around its middle.
        val nearLine = length >= halfSize && abs((x - ax) * (ay - by) + (y - ay) * (bx - ax)) / length <= halfSize
        return nearLine &&
            hypot(x - (ax + bx) / 2, y - (ay + by) / 2) - halfSize * sqrt(2.0) <= length / 2 + HIT_PADDING
    }

    private const val HIT_PADDING = 0.1

    /**
     * The parts of segments [first] to [last] of [path] erased by [box], as pairs of path
     * parameters (entering, leaving); empty if inconsistent because of rounding.
     */
    internal fun intersectWithPaddedBox(
        path: StrokePath,
        box: PaddedBox,
        first: Int = 0,
        last: Int = path.size - 2,
    ): List<PathParameter> = PaddedBoxIntersection(path, box).compute(first, last)

    /**
     * The interval of line parameters where the line through p and q is inside [rect], or null:
     * the line enters at p + min (q - p) and leaves at p + max (q - p).
     */
    internal fun intersectLineWithRectangle(
        px: Double,
        py: Double,
        qx: Double,
        qy: Double,
        rect: Rect,
    ): ClosedFloatingPointRange<Double>? {
        fun strip(
            a1: Double,
            a2: Double,
            stripMin: Double,
            stripWidth: Double,
        ): ClosedFloatingPointRange<Double> {
            val norm = 1.0 / (a2 - a1)
            val t1 = (stripMin - a1) * norm
            val t2 = t1 + stripWidth * norm
            return min(t1, t2)..max(t1, t2)
        }
        return when {
            px == qx && py == qy -> {
                null
            }

            px == qx -> {
                if (rect.x < px && px < rect.x + rect.width) strip(py, qy, rect.y, rect.height) else null
            }

            py == qy -> {
                if (rect.y < py && py < rect.y + rect.height) strip(px, qx, rect.x, rect.width) else null
            }

            else -> {
                val vertical = strip(py, qy, rect.y, rect.height)
                val horizontal = strip(px, qx, rect.x, rect.width)
                val low = max(vertical.start, horizontal.start)
                val high = min(vertical.endInclusive, horizontal.endInclusive)
                if (low < high) low..high else null
            }
        }
    }

    /** As [intersectLineWithRectangle], but only the parameters in (0, 1]: on the segment from p to q. */
    internal fun intersectLineSegmentWithRectangle(
        px: Double,
        py: Double,
        qx: Double,
        qy: Double,
        rect: Rect,
    ): List<Double> {
        val range = intersectLineWithRectangle(px, py, qx, qy, rect) ?: return emptyList()
        return listOfNotNull(
            range.start.takeIf { it > 0.0 && it <= 1.0 },
            range.endInclusive.takeIf {
                it > 0.0 &&
                    it <= 1.0
            },
        )
    }
}

/** Xournal++'s `Stroke::intersectWithPaddedBox`. */
private class PaddedBoxIntersection(
    private val path: StrokePath,
    private val box: PaddedBox,
) {
    private val inner = box.inner
    private val outer = box.outer
    private val result = ArrayList<PathParameter>()
    private var isInsideOuter = false
    private var wentInsideInner = false
    private var lastSegmentEndedOnBoundary = false

    fun compute(
        first: Int,
        last: Int,
    ): List<PathParameter> {
        initializeFromHalfTangentAtFirstKnot(first)
        // Starting inside the padded box: a fake entry.
        if (isInsideOuter) result += PathParameter(first, 0.0)
        for (index in first..last) processSegment(index)

        // Rounding (a point on the padded box's boundary) can give inconsistent results: drop them.
        var inconsistent = false
        if (result.size % 2 != 0) {
            // Not necessarily inconsistent: the stroke may end in the padded box.
            val endX = path.x(last + 1)
            val endY = path.y(last + 1)
            if (outer.contains(endX, endY)) {
                if (wentInsideInner || halfTangentAtLastKnotGoesInside(last)) {
                    result += PathParameter(last, 1.0)
                } else {
                    result.removeAt(result.lastIndex)
                }
            } else {
                inconsistent = true
            }
        }
        return if (inconsistent || !sane()) emptyList() else result
    }

    private fun initializeFromHalfTangentAtFirstKnot(first: Int) {
        val x = path.x(first)
        val y = path.y(first)
        when {
            inner.contains(x, y) -> {
                isInsideOuter = true
                wentInsideInner = true
            }

            outer.contains(x, y) -> {
                isInsideOuter = true
                // The half tangent going toward the inner box counts as having been inside it.
                val intersections =
                    StrokeEraser.intersectLineWithRectangle(
                        x,
                        y,
                        path.x(first + 1),
                        path.y(first + 1),
                        inner,
                    )
                wentInsideInner = intersections != null && intersections.endInclusive <= 0.0
            }
        }
    }

    private fun halfTangentAtLastKnotGoesInside(last: Int): Boolean {
        val intersections =
            StrokeEraser.intersectLineWithRectangle(
                path.x(last + 1),
                path.y(last + 1),
                path.x(last),
                path.y(last),
                inner,
            )
        return intersections != null && intersections.endInclusive < 0.0
    }

    private fun processSegment(index: Int) {
        val ax = path.x(index)
        val ay = path.y(index)
        val bx = path.x(index + 1)
        val by = path.y(index + 1)
        val outerIntersections = StrokeEraser.intersectLineSegmentWithRectangle(ax, ay, bx, by, outer)
        if (outerIntersections.isEmpty() && !outer.contains(ax, ay) && !outer.contains(bx, by)) return

        // Some part of the segment is in the padded box.
        val innerIntersections = StrokeEraser.intersectLineSegmentWithRectangle(ax, ay, bx, by, inner)
        var innerIndex = 0
        if (lastSegmentEndedOnBoundary) {
            lastSegmentEndedOnBoundary = false
            dropBounce(index, outerIntersections.firstOrNull())
        }
        for (outerIntersection in outerIntersections) {
            // Inner intersections before this one: the stroke went into the inner box.
            var skipped = false
            while (innerIndex < innerIntersections.size && innerIntersections[innerIndex] < outerIntersection) {
                skipped = true
                innerIndex++
            }
            wentInsideInner = wentInsideInner || skipped
            if (!isInsideOuter || wentInsideInner) {
                result += PathParameter(index, outerIntersection)
                if (outerIntersection == 1.0) lastSegmentEndedOnBoundary = true
            } else {
                result.removeAt(result.lastIndex)
            }
            wentInsideInner = false
            isInsideOuter = !isInsideOuter
        }
        if (innerIndex < innerIntersections.size) wentInsideInner = true
    }

    /** After touching the box's border at the end of the last segment, the stroke may have bounced back out. */
    private fun dropBounce(
        index: Int,
        firstIntersection: Double?,
    ) {
        val t = if (firstIntersection == null) 1.0 else firstIntersection / 2
        val (x, y) = path.at(PathParameter(index, t))
        // Never got in or out: the last intersection doesn't count.
        if (outer.contains(x, y) != (result.size % 2 != 0)) result.removeAt(result.lastIndex)
    }

    /** Each erased interval must have a point inside the padded box. */
    private fun sane(): Boolean =
        result.chunked(2).all { (start, end) ->
            val (x, y) =
                if (start.index == end.index) {
                    path.at(PathParameter(start.index, (start.t + end.t) / 2))
                } else {
                    val middle = (start.index + end.index + 1) / 2
                    Triple(path.x(middle), path.y(middle), 0.0)
                }
            outer.contains(x, y)
        }
}

/**
 * A stroke as the eraser goes over it (Xournal++'s ErasableStroke): what remains of it is a
 * union of sections, cut further by each [erase]; [pieces] are the strokes left.
 */
class ErasableStroke(
    val stroke: Element.Stroke,
) {
    private val path = StrokePath(stroke)

    /** The remaining sections, as sorted bounds (start, end, start, end, …). */
    private var remaining: List<PathParameter> =
        if (path.size >= 2) listOf(PathParameter(0, 0.0), PathParameter(path.size - 2, 1.0)) else emptyList()

    /** Xournal++ joins the ends of a closed stroke again when both remain. */
    internal val isClosed: Boolean =
        path.size >= 3 &&
            hypot(path.x(0) - path.x(path.size - 1), path.y(0) - path.y(path.size - 1)) < CLOSED_STROKE_DISTANCE

    /** Whether nothing was erased yet. */
    val untouched: Boolean get() =
        path.size < 2 ||
            remaining == listOf(PathParameter(0, 0.0), PathParameter(path.size - 2, 1.0))

    /** Erases what the square of half side [halfSize] at ([x], [y]) covers. Returns whether anything was left there. */
    fun erase(
        x: Double,
        y: Double,
        halfSize: Double,
    ): Boolean {
        if (path.size < 2 || remaining.isEmpty()) return false
        val box = StrokeEraser.boxFor(stroke, x, y, halfSize)
        // Only the runs of segments whose remaining sections come near the eraser square.
        val near = ArrayList<IntRange>()
        for ((start, end) in remaining.chunked(2)) {
            if (!sectionBounds(start, end).intersectsInclusive(box.inner)) continue
            val previous = near.lastOrNull()
            if (previous != null && previous.last + 1 >= start.index) {
                near[near.lastIndex] = previous.first..max(previous.last, end.index)
            } else {
                near += start.index..end.index
            }
        }
        return remove(near.flatMap { StrokeEraser.intersectWithPaddedBox(path, box, it.first, it.last) })
    }

    /** Removes the sections between each pair of [erased] (sorted, disjoint). Returns whether any remained. */
    internal fun remove(erased: List<PathParameter>): Boolean {
        if (erased.isEmpty()) return false
        val before = remaining
        val all = Intervals.complement(erased, PathParameter(0, 0.0), PathParameter(path.size - 2, 1.0))
        remaining = Intervals.intersect(remaining, all)
        return remaining != before
    }

    /** The strokes left: one per remaining section (a closed stroke's two ends joined). */
    fun pieces(): List<Element.Stroke> {
        val sections = remaining.chunked(2)
        val first = PathParameter(0, 0.0)
        val end = PathParameter(path.size - 2, 1.0)
        val mergeEnds = isClosed && sections.size >= 2 && sections.first()[0] == first && sections.last()[1] == end
        return if (mergeEnds) {
            listOf(circularSection(sections.last()[0], sections.first()[1])) +
                sections.drop(1).dropLast(1).map { section(it[0], it[1]) }
        } else {
            sections.map { section(it[0], it[1]) }
        }
    }

    /** Points from [from] to [to], with their widths (the last one has none, as in Xournal++). */
    private fun section(
        from: PathParameter,
        to: PathParameter,
    ): Element.Stroke {
        val points = ArrayList<Triple<Double, Double, Double>>()
        points += path.at(from)
        for (i in from.index + 1..to.index) points += Triple(path.x(i), path.y(i), path.z(i))
        points += path.at(to)
        return build(points)
    }

    /** From [from] around the end of a closed stroke, then from its start to [to]. */
    private fun circularSection(
        from: PathParameter,
        to: PathParameter,
    ): Element.Stroke {
        val points = ArrayList<Triple<Double, Double, Double>>()
        points += path.at(from)
        // The last point is the first again: once only.
        for (i in from.index + 1 until path.size - 1) points += Triple(path.x(i), path.y(i), path.z(i))
        for (i in 0..to.index) points += Triple(path.x(i), path.y(i), path.z(i))
        points += path.at(to)
        return build(points)
    }

    private fun build(points: List<Triple<Double, Double, Double>>): Element.Stroke {
        val coordinates = DoubleArray(points.size * 2)
        points.forEachIndexed { i, (x, y) ->
            coordinates[2 * i] = x
            coordinates[2 * i + 1] = y
        }
        val widths =
            if (path.hasPressure) {
                DoubleList.fromArray(
                    DoubleArray(points.size - 1) { points[it].third },
                )
            } else {
                null
            }
        return stroke.copy(points = PointList.fromInterleaved(coordinates), segmentWidths = widths)
    }

    /** The area covered by a section, with half the stroke's (or the local) width around the points. */
    private fun sectionBounds(
        from: PathParameter,
        to: PathParameter,
    ): Rect {
        var lastPressure = 0.0
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        fun add(point: Triple<Double, Double, Double>) {
            val (x, y, z) = point
            val padding = if (path.hasPressure) 0.5 * max(lastPressure, z) else 0.5 * stroke.width
            lastPressure = z
            minX = min(minX, x - padding)
            minY = min(minY, y - padding)
            maxX = max(maxX, x + padding)
            maxY = max(maxY, y + padding)
        }
        add(path.at(from))
        for (i in from.index + 1..to.index) add(Triple(path.x(i), path.y(i), path.z(i)))
        add(path.at(to))
        return Rect(minX, minY, maxX - minX, maxY - minY)
    }

    private fun Rect.intersectsInclusive(other: Rect): Boolean =
        x <= other.x + other.width && other.x <= x + width && y <= other.y + other.height && other.y <= y + height

    private companion object {
        const val CLOSED_STROKE_DISTANCE = 0.3
    }
}

/** Unions of intervals as sorted bounds (Xournal++'s UnionOfIntervals). */
internal object Intervals {
    /** The complement of [data] within [low, high]. */
    fun complement(
        data: List<PathParameter>,
        low: PathParameter,
        high: PathParameter,
    ): List<PathParameter> {
        if (data.isEmpty()) return listOf(low, high)
        val result = ArrayList(data)
        if (result.first() == low) result.removeAt(0) else result.add(0, low)
        if (result.last() == high) result.removeAt(result.lastIndex) else result.add(high)
        return result
    }

    /** The intersection of [a] and [b]. */
    fun intersect(
        a: List<PathParameter>,
        b: List<PathParameter>,
    ): List<PathParameter> {
        val data = ArrayList(a)
        var i = 0
        var j = 0
        var insideA = false
        var insideB = false
        while (i < data.size && j < b.size) {
            when {
                b[j] == data[i] -> {
                    if (insideA != insideB) data.removeAt(i) else i++
                    j++
                    insideA = !insideA
                    insideB = !insideB
                }

                b[j] < data[i] -> {
                    insideB = !insideB
                    if (insideA) data.add(i++, b[j])
                    j++
                }

                else -> {
                    insideA = !insideA
                    if (!insideB) data.removeAt(i) else i++
                }
            }
        }
        while (data.size > i) data.removeAt(data.lastIndex)
        return data
    }
}
