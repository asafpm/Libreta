// SPDX-License-Identifier: GPL-2.0-or-later
// testIntersectWithPaddedBox and testGetStrokes are ported from Xournal++ v1.3.8
// test/unit_tests/model/ErasableStrokeTest.cpp, © Xournal++ contributors.

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.AudioLink
import pm.asaf.libreta.core.model.CapStyle
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DoubleList
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokeEraserTest {
    /** A point as in Xournal++: x, y and the width of the segment starting there (none for the last). */
    private data class P(
        val x: Double,
        val y: Double,
        val z: Double? = null,
    )

    private fun p(
        x: Number,
        y: Number,
        z: Number? = null,
    ) = P(x.toDouble(), y.toDouble(), z?.toDouble())

    private fun stroke(
        points: List<P>,
        width: Double = 3.0,
        tool: StrokeTool = StrokeTool.PEN,
        fill: Int? = null,
        capStyle: CapStyle? = null,
    ): Element.Stroke {
        val coordinates = points.flatMap { listOf(it.x, it.y) }.toDoubleArray()
        val widths = points.dropLast(1).mapNotNull { it.z }
        return Element.Stroke(
            tool = tool,
            color = Color.of(0, 0, 0),
            width = width,
            points = PointList.fromInterleaved(coordinates),
            segmentWidths = if (widths.isEmpty()) null else DoubleList.fromArray(widths.toDoubleArray()),
            capStyle = capStyle,
            fill = fill,
        )
    }

    private fun Element.Stroke.asPoints(): List<P> =
        (0 until points.size).map { i ->
            P(points.x(i), points.y(i), segmentWidths?.takeIf { i < it.size }?.get(i))
        }

    /** The points rounded to 1e-9, for cuts computed with rounding errors. */
    private fun Element.Stroke.roundedPoints(): List<P> {
        fun round(value: Double) = kotlin.math.round(value * 1e9) / 1e9
        return asPoints().map { P(round(it.x), round(it.y), it.z?.let(::round)) }
    }

    private val path =
        listOf(
            p(0, 0, 2),
            p(2, 2, 2.5),
            p(5, 2, 3),
            p(7, 4, 2.5),
            p(3, 6, 2),
            p(2, 8, 1.5),
            p(5, 11, 1),
            p(7, 10, 1.5),
            p(7, 6, 2),
            p(6, 7, 2.5),
            p(4, 4, 3),
            p(1, 3),
        )

    @Test
    fun intersectsWithPaddedBoxes() {
        val cases =
            listOf(
                // Start in padding. Pass by inner box. End in padding, not going toward inner box.
                PaddedBox(1.0, 1.0, 0.5, 3.0) to listOf(0 to 0.0, 1 to 2.0 / 3.0),
                // Start on inner box boundary. Pass inside. End in padding, not going toward inner box.
                PaddedBox(1.0, 1.0, 1.0, 3.0) to listOf(0 to 0.0, 1 to 2.0 / 3.0),
                // Start in padding. Pass by inner box. End in padding going toward inner box.
                PaddedBox(1.0, 1.0, 1.9, 3.5) to listOf(0 to 0.0, 1 to 5.0 / 6.0, 9 to 5.0 / 6.0, 10 to 1.0),
                // Start on padding boundary, pass by inner box.
                PaddedBox(1.0, 1.0, 0.5, 1.0) to listOf(0 to 0.0, 0 to 1.0),
                // Intersect padding, not inner box.
                PaddedBox(2.0, 11.0, 1.0, 2.0) to emptyList(),
                // Intersect padding and inner box.
                PaddedBox(2.0, 8.0, 1.0, 4.0) to listOf(3 to 0.25, 6 to 0.5),
                // Intersect inner box and end in padding boundary.
                PaddedBox(2.5, 3.5, 0.5, 2.0) to listOf(9 to 0.75, 10 to 1.0),
                PaddedBox(6.0, 6.0, 1.1, 2.0) to listOf(2 to 1.0, 3 to 0.75, 7 to 0.5, 9 to 1.0),
                PaddedBox(7.0, 7.5, 1.0, 1.5) to listOf(7 to 0.25, 9 to 0.25),
                PaddedBox(6.0, 8.0, 0.5, 1.0) to emptyList(),
            )
        val strokePath = StrokePath(stroke(path))
        cases.forEachIndexed { i, (box, expected) ->
            val result = StrokeEraser.intersectWithPaddedBox(strokePath, box)
            assertEquals(expected.map { it.first }, result.map { it.index }, "box $i")
            expected.zip(result).forEach { (e, r) -> assertEquals(e.second, r.t, 1e-12, "box $i") }
        }
    }

    @Test
    fun keepsTheRemainingSections() {
        val closedPath = listOf(p(0, 0, 2), p(2, 2, 2.5), p(5, 2, 3), p(1, 4, 2.5), p(3, 6, 2), p(0, 0))
        val strokes =
            listOf(
                stroke(path),
                stroke(closedPath, fill = 123).copy(audio = AudioLink(0, "assets/bar.mp3")),
                stroke(closedPath, tool = StrokeTool.HIGHLIGHTER),
            )
        val closed = listOf(false, true, true)
        val erased =
            listOf(
                listOf(PathParameter(1, 0.5), PathParameter(2, 0.5), PathParameter(3, 0.5), PathParameter(4, 0.5)),
                listOf(PathParameter(1, 0.5), PathParameter(2, 0.5), PathParameter(3, 0.5), PathParameter(4, 0.5)),
                listOf(PathParameter(0, 0.0), PathParameter(2, 0.5), PathParameter(3, 0.5), PathParameter(4, 1.0)),
            )
        val expected =
            listOf(
                listOf(
                    listOf(p(0, 0, 2), p(2, 2, 2.5), p(3.5, 2)),
                    listOf(p(6, 3, 3), p(7, 4, 2.5), p(5, 5)),
                    listOf(
                        p(2.5, 7, 2),
                        p(2, 8, 1.5),
                        p(5, 11, 1),
                        p(7, 10, 1.5),
                        p(7, 6, 2),
                        p(6, 7, 2.5),
                        p(4, 4, 3),
                        p(1, 3),
                    ),
                ),
                listOf(
                    listOf(p(1.5, 3, 2), p(0, 0, 2), p(2, 2, 2.5), p(3.5, 2)),
                    listOf(p(3, 3, 3), p(1, 4, 2.5), p(2, 5)),
                ),
                listOf(listOf(p(3, 3, 3), p(1, 4, 2.5), p(2, 5))),
            )
        strokes.forEachIndexed { i, stroke ->
            val erasable = ErasableStroke(stroke)
            assertTrue(erasable.remove(erased[i]))
            assertEquals(closed[i], erasable.isClosed)
            val pieces = erasable.pieces()
            assertEquals(expected[i], pieces.map { it.asPoints() }, "stroke $i")
            pieces.forEach {
                assertEquals(stroke.copy(points = it.points, segmentWidths = it.segmentWidths), it, "keeps the style")
            }
        }
    }

    private val line = stroke(listOf(p(0, 0), p(10, 0), p(20, 0)), width = 1.0)

    @Test
    fun cutsAStrokeInTwo() {
        val erasable = ErasableStroke(line)
        assertTrue(erasable.untouched)
        assertTrue(erasable.erase(10.0, 0.0, 2.0))
        assertFalse(erasable.untouched)
        // The square of half side 2, padded by 0.4 × the width for round caps.
        assertEquals(
            listOf(listOf(p(0, 0), p(7.6, 0)), listOf(p(12.4, 0), p(20, 0))),
            erasable.pieces().map { it.roundedPoints() },
        )
        // Erasing where nothing is left changes nothing.
        assertFalse(erasable.erase(10.0, 0.0, 1.0))
        assertFalse(erasable.erase(10.0, 5.0, 1.0))
    }

    @Test
    fun keepsPressureWidthsAtTheCuts() {
        val erasable = ErasableStroke(stroke(listOf(p(0, 0, 1), p(10, 0, 2), p(20, 0, 3), p(30, 0)), width = 1.0))
        assertTrue(erasable.erase(15.0, 0.0, 1.0))
        assertEquals(
            listOf(listOf(p(0, 0, 1), p(10, 0, 2), p(13.6, 0)), listOf(p(16.4, 0, 2), p(20, 0, 3), p(30, 0))),
            erasable.pieces().map { it.roundedPoints() },
        )
    }

    @Test
    fun padsByCapStyle() {
        fun erasedLength(capStyle: CapStyle?): Double {
            val erasable = ErasableStroke(line.copy(width = 10.0, capStyle = capStyle))
            erasable.erase(10.0, 0.0, 2.0)
            val (left, right) = erasable.pieces()
            return right.points.x(0) - left.points.x(left.points.size - 1)
        }
        assertEquals(4.0 + 2 * 4.0, erasedLength(null), 1e-9)
        assertEquals(4.0 + 2 * 4.0, erasedLength(CapStyle.ROUND), 1e-9)
        assertEquals(4.0 + 2 * 0.1, erasedLength(CapStyle.BUTT), 1e-9)
        assertEquals(4.0 + 2 * 5.0, erasedLength(CapStyle.SQUARE), 1e-9)
    }

    @Test
    fun erasesWholeStrokesAndDots() {
        val erasable = ErasableStroke(line)
        assertTrue(erasable.erase(10.0, 0.0, 15.0))
        assertEquals(emptyList(), erasable.pieces())

        val dot = ErasableStroke(stroke(listOf(p(5, 5), p(5, 5)), width = 2.0))
        assertFalse(dot.erase(9.0, 9.0, 1.0))
        assertTrue(dot.erase(5.5, 5.0, 1.0))
        assertEquals(emptyList(), dot.pieces())
    }

    @Test
    fun erasesAlongAGesture() {
        // An eraser going down across the line at x = 5, then at x = 15.
        val erasable = ErasableStroke(line)
        for (y in -3..3) erasable.erase(5.0, y.toDouble(), 1.0)
        for (y in -3..3) erasable.erase(15.0, y.toDouble(), 1.0)
        val pieces = erasable.pieces().map { it.roundedPoints() }
        assertEquals(3, pieces.size)
        assertEquals(listOf(p(0, 0), p(3.6, 0)), pieces[0])
        assertEquals(listOf(p(6.4, 0), p(10, 0), p(13.6, 0)), pieces[1])
        assertEquals(listOf(p(16.4, 0), p(20, 0)), pieces[2])
    }

    @Test
    fun findsStrokesToDelete() {
        assertTrue(StrokeEraser.touches(line, 10.0, 0.5, 1.0))
        assertTrue(StrokeEraser.touches(line, 5.0, 0.9, 1.0))
        assertFalse(StrokeEraser.touches(line, 5.0, 3.0, 1.0))
        assertFalse(StrokeEraser.touches(line, 25.0, 0.0, 1.0))
        assertTrue(StrokeEraser.touches(line, 20.5, 0.0, 1.0))
    }
}
