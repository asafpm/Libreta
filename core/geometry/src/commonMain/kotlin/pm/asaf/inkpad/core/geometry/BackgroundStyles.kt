// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/view/background/{Ruled,Lined,Staves,Graph,Dotted,
// BaseIsometric,IsoDotted,IsoGraph}BackgroundView.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.geometry.BackgroundPatterns.DARK_SLATE_GRAY
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.DEEP_PINK
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.DODGER_BLUE
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.MIDNIGHT_BLUE
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.PageArea
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.SILVER
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.indexBounds
import pm.asaf.inkpad.core.geometry.BackgroundPatterns.line
import pm.asaf.inkpad.core.model.Color
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object Ruled {
    private const val HEADER = 80.0
    private const val FOOTER = 60.0
    private const val DEFAULT_SPACING = 24.0
    private const val DEFAULT_LINE_WIDTH = 0.5
    private const val DEFAULT_MARGIN = 72.0

    fun strokes(page: PageArea): List<PatternStroke> {
        val lineWidth = page.lineWidth(DEFAULT_LINE_WIDTH)
        val spacing = page.config.double("r1") ?: DEFAULT_SPACING
        val clip = page.clip
        val indices =
            indexBounds(
                clip.top - HEADER - lineWidth / 2,
                clip.bottom - HEADER + lineWidth / 2,
                spacing,
                0.0,
                page.height - HEADER - FOOTER,
            )
        val path = ShapePathBuilder()
        for (index in indices) line(path, clip.left, HEADER + index * spacing, clip.right, HEADER + index * spacing)
        return listOf(PatternStroke(path.build(), lineWidth, LineCap.BUTT, page.color(DODGER_BLUE, DARK_SLATE_GRAY)))
    }

    /** The vertical margin line of `lined` pages; a negative margin counts from the right. */
    fun marginLine(page: PageArea): PatternStroke {
        val margin = (page.config.double("m1") ?: DEFAULT_MARGIN).let { if (it < 0) it + page.width else it }
        val path = ShapePathBuilder()
        line(path, margin, 0.0, margin, page.height)
        val color = page.color(DEEP_PINK, MIDNIGHT_BLUE, lightKey = "f2", darkKey = "af2")
        return PatternStroke(path.build(), page.lineWidth(DEFAULT_LINE_WIDTH), LineCap.BUTT, color)
    }
}

internal object Staves {
    private const val HEADER = 80.0
    private const val FOOTER = 60.0
    private const val MARGIN = 50.0
    private const val STAVES_SPACING = 40.0
    private const val LINES_SPACING = 5.0
    private const val LINES = 5
    private const val DEFAULT_LINE_WIDTH = 0.5

    fun strokes(page: PageArea): List<PatternStroke> {
        val lineWidth = page.lineWidth(DEFAULT_LINE_WIDTH)
        val staffHeight = lineWidth + (LINES - 1) * LINES_SPACING
        // "+ 4 * lineWidth" is a legacy Xournal++ keeps so that existing documents look the same.
        val step = STAVES_SPACING + staffHeight + (LINES - 1) * lineWidth
        val clip = page.clip
        val indices =
            indexBounds(
                clip.top - HEADER - staffHeight,
                clip.bottom + lineWidth / 2 - HEADER,
                step,
                0.0,
                page.height - HEADER - FOOTER,
            )
        val path = ShapePathBuilder()
        val right = page.width - MARGIN
        for (index in indices) {
            val top = HEADER + index * step
            for (lineIndex in 0 until LINES) {
                line(
                    path,
                    MARGIN,
                    top + lineIndex * LINES_SPACING,
                    right,
                    top + lineIndex * LINES_SPACING,
                )
            }
            // Continues from the end of the last line: the right bar is joined to it.
            path.lineTo(right, top)
            line(path, MARGIN, top, MARGIN, top + (LINES - 1) * LINES_SPACING)
        }
        return listOf(PatternStroke(path.build(), lineWidth, LineCap.SQUARE, page.color(Color.BLACK, Color.WHITE)))
    }
}

internal object Graph {
    private const val DEFAULT_SQUARE = 14.17 // 5 mm
    private const val DEFAULT_LINE_WIDTH = 0.5
    private const val DEFAULT_BOLD_LINE_WIDTH = 1.5

    private class Settings(
        page: PageArea,
    ) {
        val lineWidth = page.lineWidth(DEFAULT_LINE_WIDTH)
        val square = page.config.double("r1") ?: DEFAULT_SQUARE
        val margin = page.config.double("m1") ?: 0.0
        val boldInterval = page.config.int("bli") ?: 0
        val boldWidth = page.config.double("blw") ?: DEFAULT_BOLD_LINE_WIDTH
        val roundMargin = margin > 0.0 && (page.config.int("rm") ?: 0) != 0
        val halfWidth = (if (boldInterval > 0) boldWidth else lineWidth) / 2
        val clearance = max(margin, halfWidth)
    }

    /** The area lines are drawn in. */
    private data class Extents(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double,
    )

    fun strokes(page: PageArea): List<PatternStroke> {
        val settings = Settings(page)
        val extents = extents(page, settings)
        val columns =
            indexBounds(
                extents.minX - settings.halfWidth,
                extents.maxX + settings.halfWidth,
                settings.square,
                settings.clearance,
                page.width,
            )
        val rows =
            indexBounds(
                extents.minY - settings.halfWidth,
                extents.maxY + settings.halfWidth,
                settings.square,
                settings.clearance,
                page.height,
            )
        val lines = if (settings.roundMargin) roundToGrid(extents, page, settings) else extents
        val color = page.color(SILVER, DARK_SLATE_GRAY)

        fun grid(include: (Int) -> Boolean): ShapePath {
            val path = ShapePathBuilder()
            val square = settings.square
            if (lines.minY < lines.maxY) {
                for (index in columns) {
                    if (include(
                            index,
                        )
                    ) {
                        line(path, index * square, lines.minY, index * square, lines.maxY)
                    }
                }
            }
            if (lines.minX < lines.maxX) {
                for (index in rows) {
                    if (include(index)) {
                        line(
                            path,
                            lines.minX,
                            index * square,
                            lines.maxX,
                            index * square,
                        )
                    }
                }
            }
            return path.build()
        }
        val interval = settings.boldInterval
        return if (interval > 0) {
            listOf(
                PatternStroke(grid { it % interval != 0 }, settings.lineWidth, LineCap.SQUARE, color),
                PatternStroke(grid { it % interval == 0 }, settings.boldWidth, LineCap.SQUARE, color),
            )
        } else {
            listOf(PatternStroke(grid { true }, settings.lineWidth, LineCap.SQUARE, color))
        }
    }

    /** The clip, narrowed to the margins. */
    private fun extents(
        page: PageArea,
        settings: Settings,
    ): Extents {
        val clip = page.clip
        val margin = settings.margin
        return if (margin > 0.0) {
            Extents(
                max(clip.left, margin),
                min(clip.right, page.width - margin),
                max(clip.top, margin),
                min(
                    clip.bottom,
                    page.height - margin,
                ),
            )
        } else {
            Extents(clip.left, clip.right, clip.top, clip.bottom)
        }
    }

    /** `rm`: line ends snap to the first and last grid lines inside the margins. */
    private fun roundToGrid(
        extents: Extents,
        page: PageArea,
        settings: Settings,
    ): Extents {
        val half = settings.halfWidth
        val square = settings.square
        val margin = settings.margin
        val pageColumns = indexBounds(margin - half, page.width - margin + half, square, settings.clearance, page.width)
        val pageRows = indexBounds(margin - half, page.height - margin + half, square, settings.clearance, page.height)
        return Extents(
            max(extents.minX, square * pageColumns.first),
            min(extents.maxX, square * pageColumns.last),
            max(extents.minY, square * pageRows.first),
            min(extents.maxY, square * pageRows.last),
        )
    }
}

internal object Dotted {
    private const val DEFAULT_SQUARE = 14.17
    private const val DEFAULT_LINE_WIDTH = 1.5

    fun strokes(page: PageArea): List<PatternStroke> {
        val lineWidth = page.lineWidth(DEFAULT_LINE_WIDTH)
        val square = page.config.double("r1") ?: DEFAULT_SQUARE
        val half = lineWidth / 2
        val clip = page.clip
        val columns = indexBounds(clip.left - half, clip.right + half, square, half, page.width)
        val rows = indexBounds(clip.top - half, clip.bottom + half, square, half, page.height)
        val path = ShapePathBuilder()
        // Zero-length segments with round caps are dots.
        for (column in columns) {
            for (row in rows) {
                line(
                    path,
                    column * square,
                    row * square,
                    column * square,
                    row * square,
                )
            }
        }
        return listOf(PatternStroke(path.build(), lineWidth, LineCap.ROUND, page.color(SILVER, DARK_SLATE_GRAY)))
    }
}

internal object Isometric {
    private const val DEFAULT_TRIANGLE = 14.17
    private const val DOTS_LINE_WIDTH = 1.5
    private const val LINES_LINE_WIDTH = 1.0

    /** The visible part of the centred triangle grid (BaseIsometricBackgroundView::draw). */
    private class Grid(
        val cols: Int,
        val rows: Int,
        val xStep: Double,
        val yStep: Double,
        val xOffset: Double,
        val yOffset: Double,
    )

    private fun grid(
        page: PageArea,
        lineWidth: Double,
    ): Grid {
        val triangle = page.config.double("r1") ?: DEFAULT_TRIANGLE
        val xStep = sqrt(3.0) / 2.0 * triangle
        val yStep = triangle / 2.0
        val cols = floor((page.width - 2 * triangle) / xStep).toInt()
        val rows = floor((page.height - 2 * triangle) / yStep).toInt()
        val contentWidth = cols * xStep
        val contentHeight = rows * yStep
        var xOffset = (page.width - contentWidth) / 2
        var yOffset = (page.height - contentHeight) / 2
        val half = lineWidth / 2
        val clip = page.clip
        val xIndices =
            indexBounds(
                clip.left - half - xStep - xOffset,
                clip.right + half + xStep - xOffset,
                xStep,
                0.0,
                contentWidth,
            )
        val yIndices =
            indexBounds(
                clip.top - half - yStep - yOffset,
                clip.bottom + half + yStep - yOffset,
                yStep,
                0.0,
                contentHeight,
            )
        var minX = xIndices.first
        var minY = yIndices.first
        if ((minX + minY) % 2 != 0) {
            // Start the painted area with "/\/\" rather than "\/\/".
            if (minX != 0) minX-- else minY--
        }
        xOffset += minX * xStep
        yOffset += minY * yStep
        return Grid(xIndices.last - minX, yIndices.last - minY, xStep, yStep, xOffset, yOffset)
    }

    fun dots(page: PageArea): List<PatternStroke> {
        val lineWidth = page.lineWidth(DOTS_LINE_WIDTH)
        val grid = grid(page, lineWidth)
        val path = ShapePathBuilder()
        for (col in 0..grid.cols) {
            val x = grid.xOffset + col * grid.xStep
            val even = col % 2 == 0
            val start = if (even) grid.yStep else 0.0
            val rowsInCol = if (even) grid.rows - 2 else grid.rows
            for (row in 0..rowsInCol step 2) {
                val y = grid.yOffset + start + row * grid.yStep
                line(path, x, y, x, y)
            }
        }
        return listOf(PatternStroke(path.build(), lineWidth, LineCap.ROUND, page.color(SILVER, DARK_SLATE_GRAY)))
    }

    fun lines(page: PageArea): List<PatternStroke> {
        val lineWidth = page.lineWidth(LINES_LINE_WIDTH)
        val grid = grid(page, lineWidth)
        val path = ShapePathBuilder()
        IsoGraphLines(grid.cols, grid.rows, grid.xStep, grid.yStep) { x1, y1, x2, y2 ->
            line(path, grid.xOffset + x1, grid.yOffset + y1, grid.xOffset + x2, grid.yOffset + y2)
        }.draw()
        return listOf(PatternStroke(path.build(), lineWidth, LineCap.ROUND, page.color(SILVER, DARK_SLATE_GRAY)))
    }

    /** IsoGraphBackgroundView::paintGrid, verbatim. */
    private class IsoGraphLines(
        private val cols: Int,
        private val rows: Int,
        private val xStep: Double,
        private val yStep: Double,
        private val drawLine: (Double, Double, Double, Double) -> Unit,
    ) {
        private val contentWidth = cols * xStep
        private val contentHeight = rows * yStep
        private val hDiags = cols / 2
        private val vDiags = rows / 2
        private val diags = hDiags + vDiags
        private val hCorr = cols - 2 * hDiags
        private val vCorr = rows - 2 * vDiags

        fun draw() {
            drawLine(0.0, 0.0, contentWidth, 0.0)
            drawLine(0.0, contentHeight, contentWidth, contentHeight)
            for (col in 0..cols) drawLine(col * xStep, 0.0, col * xStep, contentHeight)
            for (d in 0 until diags + hCorr * vCorr) leftDown(d)
            for (d in 0 until diags) rightDown(d)
        }

        /** Diagonals starting in the top left corner. */
        private fun leftDown(d: Int) {
            var x1 = contentWidth
            var y1 = 0.0
            if (d < hDiags) x1 = xStep + d * 2 * xStep else y1 = yStep + (d - hDiags) * 2 * yStep - hCorr * yStep
            var x2 = 0.0
            var y2 = contentHeight
            if (d < vDiags) y2 = yStep + d * 2 * yStep else x2 = xStep + (d - vDiags) * 2 * xStep - vCorr * xStep
            drawLine(x1, y1, x2, y2)
        }

        /** Diagonals starting in the top right corner. */
        private fun rightDown(d: Int) {
            var x1 = 0.0
            var y1 = 0.0
            if (d <
                hDiags
            ) {
                x1 = contentWidth - (xStep + d * 2 * xStep) - hCorr * xStep
            } else {
                y1 =
                    yStep + (d - hDiags) * 2 * yStep
            }
            var x2 = contentWidth
            var y2 = contentHeight
            if (d < vDiags) {
                y2 = yStep + d * 2 * yStep + hCorr * yStep
            } else {
                x2 = contentWidth - (xStep + (d - vDiags) * 2 * xStep) + (vCorr - hCorr) * xStep
            }
            drawLine(x1, y1, x2, y2)
        }
    }
}
