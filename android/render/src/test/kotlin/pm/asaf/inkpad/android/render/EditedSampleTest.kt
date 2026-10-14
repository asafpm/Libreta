// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import okio.buffer
import okio.sink
import org.junit.Assert.assertEquals
import org.junit.Test
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.geometry.InkSample
import pm.asaf.inkpad.core.geometry.PageEraser
import pm.asaf.inkpad.core.geometry.StrokeBuilder
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.EraserSettings
import pm.asaf.inkpad.core.model.HighlighterSettings
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.Palette
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.math.sin

/**
 * testdata/inkpad-edits.xopp: page 1 of physics.xopp edited the way Inkpad edits, with a pen
 * with pressure, overlapping highlighters, whiteout, the standard eraser and delete-stroke,
 * for screenshots and for scripts/render-crosscheck.sh (Xournal++ must show the same). The
 * file is written again when screenshots are recorded; otherwise it must match.
 */
class EditedSampleTest {
    @Test
    fun theSampleIsWhatInkpadEditingMakes() {
        val edited = edited()
        val file = TestDocuments.file(PATH)
        if (System.getProperty("inkpad.recordScreenshots").toBoolean()) {
            file.sink().buffer().use { XoppWriter.write(edited, it) }
        }
        assertEquals(edited, TestDocuments.load(PATH))
    }

    private fun edited(): Document {
        val physics = TestDocuments.load("physics.xopp")
        var page = physics.pages[0]
        // The standard eraser down through the equations, and delete-stroke across a line of them.
        page = erase(page, deleteStrokes = false, 300.0, 120.0, 300.0, 520.0)
        page = erase(page, deleteStrokes = true, 80.0, 610.0, 520.0, 610.0)
        // A pen line with rising pressure, overlapping highlighters, and whiteout over ink.
        page =
            page.withElementAdded(
                stroke(StrokeTool.PEN, Color.of(237, 83, 83), PenSize.THICK.width, 60.0, 700.0, pressure = true),
            )
        val highlighter = HighlighterSettings(Palette.HIGHLIGHTER, PenSize.THICK)
        page = page.withElementAdded(stroke(StrokeTool.HIGHLIGHTER, highlighter.color, highlighter.width, 60.0, 160.0))
        page =
            page.withElementAdded(
                stroke(StrokeTool.HIGHLIGHTER, Color.of(100, 186, 255), highlighter.width, 120.0, 172.0),
            )
        val whiteout = EraserSettings(size = PenSize.THICK)
        page = page.withElementAdded(stroke(StrokeTool.ERASER, Color.of(255, 255, 255), whiteout.width, 380.0, 280.0))
        return physics.copy(pages = listOf(page), creator = "Inkpad edits sample", preview = null)
    }

    private fun erase(
        page: Page,
        deleteStrokes: Boolean,
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
    ): Page {
        val eraser = PageEraser(page, deleteStrokes)
        eraser.eraseAlong(fromX, fromY, toX, toY, EraserSettings().width)
        return eraser.page
    }

    /** A wavy line 400 pt long from ([x], [y]), with pressure rising along it if [pressure]. */
    @Suppress("LongParameterList") // a stroke's settings
    private fun stroke(
        tool: StrokeTool,
        color: Color,
        width: Double,
        x: Double,
        y: Double,
        pressure: Boolean = false,
    ) = StrokeBuilder(tool, color, width).run {
        for (i in 0..STEPS) {
            add(
                InkSample(
                    x + LENGTH * i / STEPS,
                    y + WAVE * sin(i * 0.2),
                    if (pressure) {
                        0.2 +
                            0.8 * i / STEPS
                    } else {
                        null
                    },
                ),
            )
        }
        finish(null)
    }

    private companion object {
        const val PATH = "inkpad-edits.xopp"
        const val STEPS = 80
        const val LENGTH = 400.0
        const val WAVE = 6.0
    }
}
