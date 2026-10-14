// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.PageStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundPatternTest {
    private val a4Width = 595.28
    private val a4Height = 841.89

    private fun pattern(
        style: PageStyle,
        config: String? = null,
        color: Color = Color.WHITE,
        width: Double = a4Width,
        height: Double = a4Height,
    ) = BackgroundPatterns.of(Background.Solid(color, style, config), width, height)

    /** (x1, y1, x2, y2) of each move/line pair. */
    private fun PatternStroke.segments(): List<List<Double>> =
        path.commands.chunked(2).map { (move, line) ->
            move as PathCommand.MoveTo
            line as PathCommand.LineTo
            listOf(move.x, move.y, line.x, line.y)
        }

    private fun assertClose(
        expected: Double,
        actual: Double,
    ) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, got $actual")

    @Test
    fun configIsParsedLikeXournalpp() {
        val config = BackgroundConfig("m1=40,rm=1,a=b=7,lw=abc,r1=12.5mm,f1=0xff00ff, bli=3")
        assertEquals(40.0, config.double("m1"))
        assertEquals(1, config.int("rm"))
        assertEquals(7.0, config.double("a=b")) // split at the last '='
        assertEquals(0.0, config.double("lw")) // not a number: reads as 0
        assertEquals(12.5, config.double("r1")) // the leading number counts
        assertEquals(0xff00ffu, config.hex("f1"))
        assertEquals(null, config.double(" bli")?.let { null }) // keys are not trimmed
        assertEquals(null, config.double("missing"))
    }

    @Test
    fun plainAndUnknownStylesOnlyFill() {
        for (style in listOf(PageStyle.PLAIN, PageStyle(":copy"), PageStyle("future"))) {
            assertEquals(BackgroundPattern(Color.WHITE, emptyList()), pattern(style))
        }
    }

    @Test
    fun backgroundAlphaIsIgnored() {
        assertEquals(Color(0xffff80ffu), pattern(PageStyle.PLAIN, color = Color(0xffff8000u)).fill)
    }

    @Test
    fun ruledA4() {
        val stroke = pattern(PageStyle.RULED).strokes.single()
        val lines = stroke.segments()
        assertEquals(30, lines.size)
        assertEquals(listOf(0.0, 80.0, a4Width, 80.0), lines.first())
        assertEquals(listOf(0.0, 776.0, a4Width, 776.0), lines.last())
        assertEquals(PatternStroke(stroke.path, 0.5, LineCap.BUTT, Color(0x40a0ffffu)), stroke)
    }

    @Test
    fun linedAddsAMarginLine() {
        val margin = pattern(PageStyle.LINED).strokes[1]
        assertEquals(listOf(listOf(72.0, 0.0, 72.0, a4Height)), margin.segments())
        assertEquals(Color(0xff0080ffu), margin.color)
        val right = pattern(PageStyle.LINED, config = "m1=-72").strokes[1]
        assertClose(a4Width - 72, right.segments().single()[0])
    }

    @Test
    fun darkBackgroundsUseAlternativeColours() {
        val strokes = pattern(PageStyle.LINED, color = Color(0x202020ffu)).strokes
        assertEquals(listOf(Color(0x434343ffu), Color(0x220080ffu)), strokes.map { it.color })
        val custom = pattern(PageStyle.RULED, config = "f1=123456,af1=654321", color = Color.BLACK).strokes.single()
        assertEquals(Color(0x654321ffu), custom.color)
    }

    @Test
    fun graphA4() {
        val stroke = pattern(PageStyle.GRAPH).strokes.single()
        val lines = stroke.segments()
        val vertical = lines.filter { it[0] == it[2] }
        val horizontal = lines.filter { it[1] == it[3] }
        assertEquals(41, vertical.size)
        assertEquals(59, horizontal.size)
        assertClose(14.17, vertical.first()[0])
        assertEquals(listOf(0.0, a4Height), listOf(vertical.first()[1], vertical.first()[3]))
        assertEquals(PatternStroke(stroke.path, 0.5, LineCap.SQUARE, Color(0xbdbdbdffu)), stroke)
    }

    @Test
    fun graphWithBorderSnapsToTheGrid() {
        val lines = pattern(PageStyle.GRAPH, config = "m1=40,rm=1").strokes.single().segments()
        val vertical = lines.filter { it[0] == it[2] }
        // Columns 3..39; lines run between the rounded margins (rows 3 .. floor((H-40)/14.17)).
        assertEquals((3..39).toList(), vertical.map { (it[0] / 14.17).let(Math::round).toInt() })
        assertClose(3 * 14.17, vertical.first()[1])
        assertClose(56 * 14.17, vertical.first()[3])
        val horizontal = lines.filter { it[1] == it[3] }
        assertClose(3 * 14.17, horizontal.first()[0])
        assertClose(39 * 14.17, horizontal.first()[2])
    }

    @Test
    fun graphBoldLines() {
        val (normal, bold) = pattern(PageStyle.GRAPH, config = "m1=20,rm=1,bli=5,blw=1").strokes
        assertEquals(1.0, bold.width)
        assertTrue(bold.segments().all { ((if (it[0] == it[2]) it[0] else it[1]) / 14.17).let(Math::round) % 5 == 0L })
        assertTrue(
            normal.segments().none {
                (
                    (
                        if (it[0] ==
                            it[2]
                        ) {
                            it[0]
                        } else {
                            it[1]
                        }
                    ) / 14.17
                ).let(Math::round) % 5 == 0L
            },
        )
    }

    @Test
    fun dotted() {
        val stroke = pattern(PageStyle.DOTTED).strokes.single()
        val dots = stroke.segments()
        assertEquals(41 * 59, dots.size)
        assertTrue(dots.all { it[0] == it[2] && it[1] == it[3] })
        assertEquals(LineCap.ROUND, stroke.cap)
        assertEquals(1.5, stroke.width)
    }

    @Test
    fun stavesA4() {
        val stroke = pattern(PageStyle.STAVES).strokes.single()
        val moves = stroke.path.commands.filterIsInstance<PathCommand.MoveTo>()
        // Per staff: 5 lines (the last continued by the right bar) plus the left bar.
        assertEquals(12 * 6, moves.size)
        assertEquals(PathCommand.MoveTo(50.0, 80.0), moves.first())
        val step = 40 + 20.5 + 2.0
        assertEquals(PathCommand.MoveTo(50.0, 80.0 + step), moves[6])
        assertEquals(LineCap.SQUARE, stroke.cap)
        assertEquals(Color.BLACK, stroke.color)
    }

    private fun PatternStroke.points(): List<Pair<Double, Double>> =
        path.commands.flatMap {
            when (it) {
                is PathCommand.MoveTo -> listOf(it.x to it.y)
                is PathCommand.LineTo -> listOf(it.x to it.y)
                else -> emptyList()
            }
        }

    @Test
    fun isometricGridsAreCentredHorizontally() {
        for (style in listOf(PageStyle.ISO_DOTTED, PageStyle.ISO_GRAPH)) {
            val points = pattern(style).strokes.single().points()
            assertClose(points.minOf { it.first }, a4Width - points.maxOf { it.first })
        }
    }

    @Test
    fun isographMatchesXournalppVertically() {
        // 114 rows fit on A4, centred with an offset of 17.1. Like Xournal++, the visible-row
        // computation floors 113.99999999999999 and drops the last row (floating point), so
        // the grid ends one row early.
        val ystep = 14.17 / 2
        val points = pattern(PageStyle.ISO_GRAPH).strokes.single().points()
        assertClose(17.1, points.minOf { it.second })
        assertClose(17.1 + 113 * ystep, points.maxOf { it.second })
    }

    @Test
    fun clippingLimitsTheLines() {
        val clip = Bounds(100.0, 300.0, 200.0, 400.0)
        val lines =
            BackgroundPatterns
                .of(
                    Background.Solid(Color.WHITE, PageStyle.RULED),
                    a4Width,
                    a4Height,
                    clip,
                ).strokes
                .single()
                .segments()
        // Lines at 320, 344, 368, 392 (80 + i*24) within [300, 400], spanning the clip horizontally.
        assertEquals(listOf(320.0, 344.0, 368.0, 392.0), lines.map { it[1] })
        assertTrue(lines.all { it[0] == 100.0 && it[2] == 200.0 })
    }
}
