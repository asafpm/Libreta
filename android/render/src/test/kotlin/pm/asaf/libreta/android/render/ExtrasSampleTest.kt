// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.geometry.ShapeGrid
import pm.asaf.libreta.core.geometry.ShapeModifiers
import pm.asaf.libreta.core.geometry.ShapePoint
import pm.asaf.libreta.core.geometry.Shapes
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.HighlighterSettings
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import java.io.ByteArrayOutputStream

/**
 * testdata/libreta-extras.xopp: what Libreta's shapes, text tool and inserted pictures make
 * (every drawing type in pen and highlighter, text in the three families with bold and
 * italic, a JPEG), for scripts/render-crosscheck.sh (Xournal++ must show the same). Written
 * again when screenshots are recorded; otherwise it must match.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExtrasSampleTest {
    @Test
    fun theSampleIsWhatLibretaMakes() {
        val saved = XoppReader.read(XoppWriter.toByteString(sample())).document
        val file = TestDocuments.file(PATH)
        if (System.getProperty("libreta.recordScreenshots").toBoolean()) {
            file.sink().buffer().use { XoppWriter.write(saved, it) }
        }
        assertEquals(saved, TestDocuments.load(PATH))
    }

    private fun sample(): Document {
        val page = Page(595.28, 841.89, Background.Solid(Color.WHITE, PageStyle.GRAPH), listOf(Layer(emptyList())))
        val grid = ShapeGrid.of(page)
        val elements = ArrayList<Element>()
        // Each shape twice: pen, then highlighter, a column each.
        val types = DrawingType.entries - DrawingType.FREEHAND
        types.forEachIndexed { row, type ->
            val top = 40.0 + row * 90
            elements += shape(type, ShapePoint(60.0, top), ShapePoint(180.0, top + 70), StrokeTool.PEN, grid)
            elements += shape(type, ShapePoint(220.0, top), ShapePoint(340.0, top + 70), StrokeTool.HIGHLIGHTER, grid)
        }
        // A square and a circle (Shift), from the centre (Ctrl).
        val square = ShapeModifiers(square = true, fromCenter = true)
        elements +=
            shape(
                DrawingType.RECTANGLE,
                ShapePoint(450.0, 80.0),
                ShapePoint(490.0, 100.0),
                StrokeTool.PEN,
                grid,
                square,
            )
        elements +=
            shape(DrawingType.ELLIPSE, ShapePoint(450.0, 200.0), ShapePoint(490.0, 210.0), StrokeTool.PEN, grid, square)
        // Text as the text tool makes it.
        val fonts = listOf("Sans", "Serif Bold", "Monospace Italic", "Sans Bold Italic")
        fonts.forEachIndexed { i, font ->
            elements +=
                Element.Text(
                    "$font ${10 + 4 * i} pt\nsecond line",
                    font,
                    10.0 + 4 * i,
                    380.0,
                    300.0 + i * 70,
                    TEXT_COLORS[i],
                )
        }
        elements += Element.Image(Bounds(380.0, 600.0, 540.0, 720.0), picture())
        return Document(
            listOf(page.copy(layers = listOf(Layer(elements)))),
            creator = "Libreta extras sample",
            fileVersion = 4,
        )
    }

    private fun shape(
        type: DrawingType,
        start: ShapePoint,
        end: ShapePoint,
        tool: StrokeTool,
        grid: ShapeGrid,
        modifiers: ShapeModifiers = ShapeModifiers(),
    ): Element.Stroke {
        val width = if (tool == StrokeTool.PEN) PenSize.THICK.width else HighlighterSettings().width
        val color = if (tool == StrokeTool.PEN) Color.of(51, 51, 204) else Color.of(255, 255, 0)
        val points = Shapes.points(type, grid.snap(start), end, width, grid, modifiers)
        return Element.Stroke(tool, color, width, PointList.of(*points.flatMap { listOf(it.x, it.y) }.toDoubleArray()))
    }

    /** A JPEG picture, 160 × 120 pixels: a gradient with a stripe. */
    private fun picture() =
        Bitmap
            .createBitmap(160, 120, Bitmap.Config.ARGB_8888)
            .apply {
                val canvas = Canvas(this)
                for (x in 0 until 160) {
                    canvas.drawLine(
                        x.toFloat(),
                        0f,
                        x.toFloat(),
                        120f,
                        Paint().apply {
                            color =
                                android.graphics.Color.rgb(x, 100, 255 - x)
                        },
                    )
                }
                canvas.drawRect(0f, 50f, 160f, 70f, Paint().apply { color = android.graphics.Color.WHITE })
            }.let { bitmap -> ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            .toByteArray()
            .toByteString()

    private companion object {
        const val PATH = "libreta-extras.xopp"
        val TEXT_COLORS = listOf(Color.BLACK, Color.of(204, 0, 0), Color.of(0, 128, 0), Color.of(51, 51, 204))
    }
}
