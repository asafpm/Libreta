// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Layer
import pm.asaf.inkpad.core.model.LineStyle
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageEraserTest {
    /** A horizontal line at [y] from x = 0 to 100, width 1. */
    private fun line(y: Double) =
        Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 1.0, PointList.of(0.0, y, 50.0, y, 100.0, y))

    private val text = Element.Text("hi", "Sans", 12.0, 0.0, 0.0, Color.of(0, 0, 0))
    private val bottom = Layer(listOf(line(10.0)))
    private val top = Layer(listOf(line(10.0), text, line(30.0)))
    private val page = Page(200.0, 200.0, null, listOf(bottom, top))

    private fun Page.topElements() = layers.last().elements

    private fun Element.xs(): List<Double> {
        val points = (this as Element.Stroke).points
        return (0 until points.size).map { points.x(it) }
    }

    @Test
    fun cutsStrokesOnTheTopLayerInPlace() {
        val eraser = PageEraser(page, deleteStrokes = false)
        val changed = checkNotNull(eraser.erase(50.0, 10.0, 2.0))
        val erased = eraser.page
        // The bottom layer is untouched.
        assertSame(bottom, erased.layers[0])
        val elements = erased.topElements()
        assertEquals(4, elements.size)
        assertEquals(listOf(0.0, 47.6), elements[0].xs())
        assertEquals(listOf(52.4, 100.0), elements[1].xs())
        assertSame(text, elements[2])
        assertSame(top.elements[2], elements[3])
        // Around the eraser: the padded square and half the stroke's width.
        val reach = 2.0 + 0.4 + 0.5
        assertEquals(Bounds(50.0 - reach, 10.0 - 0.5, 50.0 + reach, 10.0 + 0.5), changed)
    }

    @Test
    fun keepsErasingTheSameStrokes() {
        val eraser = PageEraser(page, deleteStrokes = false)
        eraser.erase(20.0, 10.0, 2.0)
        val once = eraser.page
        // Where nothing is left: no change.
        assertNull(eraser.erase(20.0, 10.0, 2.0))
        assertSame(once, eraser.page)
        assertNull(eraser.erase(20.0, 50.0, 2.0))
        eraser.erase(80.0, 10.0, 2.0)
        assertEquals(5, eraser.page.topElements().size)
        assertSame(page, eraser.original)
    }

    @Test
    fun erasesAlongFastMoves() {
        val eraser = PageEraser(page, deleteStrokes = false)
        // Down across both lines in one move.
        val changed = checkNotNull(eraser.eraseAlong(40.0, 0.0, 40.0, 40.0, 2.0))
        val elements = eraser.page.topElements()
        assertEquals(5, elements.size)
        assertTrue(changed.top < 10.0 && changed.bottom > 30.0)
    }

    @Test
    fun deletesTouchedStrokes() {
        val eraser = PageEraser(page, deleteStrokes = true)
        assertNull(eraser.erase(50.0, 20.0, 2.0))
        val changed = checkNotNull(eraser.erase(50.0, 30.5, 1.0))
        assertEquals(listOf(top.elements[0], text), eraser.page.topElements())
        assertEquals(Bounds(-0.5, 29.5, 100.5, 30.5), changed)
    }

    @Test
    fun aDashedStrokeChangesAllAlong() {
        val dashed = line(10.0).copy(lineStyle = LineStyle.DASH)
        val eraser = PageEraser(page.copy(layers = listOf(Layer(listOf(dashed)))), deleteStrokes = false)
        assertEquals(Bounds(-0.5, 9.5, 100.5, 10.5), eraser.erase(50.0, 10.0, 2.0))
    }
}
