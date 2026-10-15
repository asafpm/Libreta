// SPDX-License-Identifier: GPL-2.0-or-later
// testStrokeWidthRecovery ported from Xournal++ v1.3.8 test/unit_tests/control/LoadHandlerTest.cpp
// (width values from test/files/packaged_xopp/stroke/width_recovery.xopp), © Xournal++ contributors.

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DoubleList
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PressureRepairTest {
    /** A stroke whose point i is at (i, 10 i), so pieces are easy to identify. */
    private fun stroke(
        pointCount: Int,
        vararg widths: Double,
    ) = Element.Stroke(
        tool = StrokeTool.PEN,
        color = Color.BLACK,
        width = 1.41,
        points =
            PointList.fromInterleaved(
                DoubleArray(2 * pointCount) {
                    if (it % 2 ==
                        0
                    ) {
                        it / 2.0
                    } else {
                        10.0 * (it / 2)
                    }
                },
            ),
        segmentWidths = if (widths.isEmpty()) null else DoubleList.of(*widths),
    )

    /** [widths] as written in the file, separated by spaces. */
    private fun stroke(
        pointCount: Int,
        widths: String,
    ) = stroke(pointCount, *widths.split(' ').map { if (it == "-NaN") -Double.NaN else it.toDouble() }.toDoubleArray())

    private fun StrokePiece.firstX(): Int = points.x(0).toInt()

    @Test
    fun strokeWithoutPressureIsOnePiece() {
        val pieces = PressureRepair.pieces(stroke(90))
        assertEquals(1, pieces.size)
        assertEquals(90, pieces[0].points.size)
        assertNull(pieces[0].segmentWidths)
    }

    @Test
    fun validPressuresAreKept() {
        val pieces = PressureRepair.pieces(stroke(3, 0.4, 0.5))
        assertEquals(listOf(StrokePiece(stroke(3).points, DoubleList.of(0.4, 0.5))), pieces)
    }

    @Test
    fun tooFewPressuresAreIgnored() {
        assertEquals(listOf(StrokePiece(stroke(4).points, null)), PressureRepair.pieces(stroke(4, 0.4, 0.5)))
    }

    @Test
    fun extraPressuresAreDropped() {
        assertEquals(
            DoubleList.of(0.4, 0.5),
            PressureRepair.pieces(stroke(3, 0.4, 0.5, 0.6, -1.0)).single().segmentWidths,
        )
    }

    /** The strokes of width_recovery.xopp (coordinates simplified). */
    private val widthRecoveryStrokes =
        listOf(
            stroke(90),
            stroke(8, "0.16 0.16 0.20 0.22 0.26 0.14 0.00"),
            stroke(8, "-0.16 -0.16 -0.20 -0.22 -0.26 -0.14 -0.00"),
            stroke(
                46,
                "0 0.16 0 0.28 0.30 0.34 0.22 0.18 -0.0 0.16 0.16 0.22 0.28 0.30 0.0 0.0000 -0.5 0.30 0.34 0.34 " +
                    "0.38 0.40 0.40 0.42 0.46 0.46 0.46 0.50 0.52 -0.58 0.56 0.56 0.58 0.60 0.56 0.40 0.32 0.18 " +
                    "0.12 0.16 0.16 0.20 0.22 0 0 0.00",
            ),
            stroke(10, "NaN 0.20 0.30 0.10 -NaN 0.25 0.30 0.40 NaN"),
        )

    // Xournal++: 9 strokes after loading; each piece has one more point than widths.
    private val widthRecoveryExpectedWidths =
        listOf(
            null,
            listOf(0.16, 0.16, 0.20, 0.22, 0.26, 0.14),
            listOf(0.16),
            listOf(0.28, 0.30, 0.34, 0.22, 0.18),
            listOf(0.16, 0.16, 0.22, 0.28, 0.30),
            listOf(0.30, 0.34, 0.34, 0.38, 0.40, 0.40, 0.42, 0.46, 0.46, 0.46, 0.50, 0.52),
            listOf(0.56, 0.56, 0.58, 0.60, 0.56, 0.40, 0.32, 0.18, 0.12, 0.16, 0.16, 0.20, 0.22),
            listOf(0.20, 0.30, 0.10),
            listOf(0.25, 0.30, 0.40),
        )

    @Test
    fun widthRecoveryMatchesXournalpp() {
        val pieces = widthRecoveryStrokes.flatMap { PressureRepair.pieces(it) }
        assertEquals(
            widthRecoveryExpectedWidths,
            pieces.map { piece -> piece.segmentWidths?.let { widths -> List(widths.size) { widths[it] } } },
        )
        for (piece in pieces.drop(1)) assertEquals(piece.segmentWidths?.size?.plus(1), piece.points.size)
        // The pieces start where the valid runs start.
        assertEquals(listOf(0, 0, 1, 3, 9, 17, 30, 1, 5), pieces.map { it.firstX() })
    }
}
