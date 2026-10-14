// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Layer
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PageStyle
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElementDiffTest {
    private val measurer = TextMeasurer { Size(10.0, 5.0) }

    /** A width-2 stroke from (x, y) to (x + 10, y). */
    private fun stroke(
        x: Double,
        y: Double,
    ) = Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 2.0, PointList.of(x, y, x + 10, y))

    private val a = stroke(10.0, 10.0)
    private val b = stroke(50.0, 50.0)
    private val c = stroke(80.0, 20.0)
    private val page =
        Page(200.0, 300.0, Background.Solid(Color.of(255, 255, 255), PageStyle.PLAIN), listOf(Layer(listOf(a, b, c))))

    private fun changed(after: Page) = ElementDiff.changedBounds(page, after, measurer)

    @Test
    fun theSamePageDidNotChange() {
        assertNull(changed(page))
        assertNull(changed(page.copy()))
    }

    @Test
    fun anAddedStroke() {
        val d = stroke(100.0, 100.0)
        assertEquals(StrokeGeometry.bounds(d), changed(page.withElementAdded(d)))
    }

    @Test
    fun aReplacedStrokeCoversBothVersions() {
        // Erasing replaces b with its two remaining pieces.
        val left = stroke(50.0, 50.0).copy(points = PointList.of(50.0, 50.0, 53.0, 50.0))
        val right = stroke(50.0, 50.0).copy(points = PointList.of(57.0, 50.0, 60.0, 50.0))
        val after = page.copy(layers = listOf(Layer(listOf(a, left, right, c))))
        assertEquals(StrokeGeometry.bounds(b), changed(after))
        // Removed entirely.
        assertEquals(StrokeGeometry.bounds(b), changed(page.copy(layers = listOf(Layer(listOf(a, c))))))
    }

    @Test
    fun aDifferentBackgroundIsTheWholePage() {
        val lined = page.copy(background = Background.Solid(Color.of(255, 255, 255), PageStyle.LINED))
        assertEquals(Bounds(0.0, 0.0, 200.0, 300.0), changed(lined))
        assertEquals(Bounds(0.0, 0.0, 200.0, 300.0), changed(page.copy(layers = page.layers + Layer(emptyList()))))
    }
}
