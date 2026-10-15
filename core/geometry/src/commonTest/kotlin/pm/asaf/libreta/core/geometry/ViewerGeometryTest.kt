// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Bounds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewerGeometryTest {
    private val a4 = Size(595.28, 841.89)
    private val letterLandscape = Size(792.0, 612.0)
    private val layout = DocumentLayout(listOf(a4, letterLandscape, a4), gap = 10.0)

    private fun assertClose(
        expected: Double,
        actual: Double,
        message: String = "",
    ) = assertTrue(abs(expected - actual) < 1e-6, "$message expected $expected, got $actual")

    @Test
    fun pagesAreStackedAndCentred() {
        assertEquals(792.0 + 20.0, layout.width)
        assertEquals(841.89 * 2 + 612.0 + 40.0, layout.height)
        val first = layout.pageBounds(0)
        assertClose((812.0 - 595.28) / 2, first.left)
        assertEquals(10.0, first.top)
        val second = layout.pageBounds(1)
        assertEquals(listOf(10.0, 802.0), listOf(second.left, second.right))
        assertClose(861.89, second.top)
        assertClose(1473.89, second.bottom)
        assertClose(1473.89 + 10.0, layout.pageBounds(2).top)
    }

    @Test
    fun findsPagesInARange() {
        assertEquals(0..0, layout.pagesIntersecting(0.0, 100.0))
        assertEquals(0..1, layout.pagesIntersecting(800.0, 900.0))
        assertEquals(1..1, layout.pagesIntersecting(900.0, 1000.0))
        assertEquals(IntRange.EMPTY, layout.pagesIntersecting(852.0, 860.0)) // only the gap
        assertEquals(0..2, layout.pagesIntersecting(-100.0, 1e6))
        assertEquals(2, layout.pageAt(400.0, 1500.0))
        assertEquals(null, layout.pageAt(400.0, 855.0))
    }

    private val navigator = ViewportNavigator(layout)

    @Test
    fun startsAtFitWidthAtTheTop() {
        val viewport = navigator.initial(viewWidth = 1624.0, viewHeight = 2000.0)
        assertEquals(2.0, viewport.zoom)
        assertEquals(0.0, viewport.offsetX)
        assertEquals(0.0, viewport.offsetY)
    }

    @Test
    fun scrollingIsClamped() {
        val start = navigator.initial(1624.0, 2000.0)
        assertEquals(0.0, navigator.scrolledBy(start, 0.0, -500.0).offsetY)
        val end = navigator.scrolledBy(start, 0.0, 1e9)
        assertClose(layout.height - 2000.0 / 2.0, end.offsetY)
        // At fit width there is nothing to scroll horizontally.
        assertEquals(0.0, navigator.scrolledBy(start, 300.0, 0.0).offsetX)
    }

    @Test
    fun narrowContentIsCentred() {
        val zoomedOut = navigator.zoomedAround(navigator.initial(1624.0, 2000.0), 812.0, 0.0, 0.25)
        assertClose(0.5, zoomedOut.zoom)
        // 812 pt at 0.5 px/pt is 406 px wide in a 1624 px view.
        assertClose(-(1624.0 / 0.5 - layout.width) / 2, zoomedOut.offsetX)
        assertClose(-(2000.0 / 0.5 - layout.height) / 2, zoomedOut.offsetY)
    }

    @Test
    fun zoomKeepsTheFocalPointFixed() {
        val start = navigator.scrolledBy(navigator.initial(1624.0, 2000.0), 0.0, 1000.0)
        val focusX = 700.0
        val focusY = 900.0
        val before = start.viewToLayout(focusX, focusY)
        val zoomed = navigator.zoomedAround(start, focusX, focusY, 3.0)
        assertEquals(6.0, zoomed.zoom)
        val after = zoomed.viewToLayout(focusX, focusY)
        assertClose(before.first, after.first, "x")
        assertClose(before.second, after.second, "y")
    }

    @Test
    fun zoomIsLimited() {
        val start = navigator.initial(1624.0, 2000.0)
        assertEquals(2.0 * ViewportNavigator.MAX_ZOOM_FACTOR, navigator.zoomedAround(start, 0.0, 0.0, 1000.0).zoom)
        assertEquals(2.0 * ViewportNavigator.MIN_ZOOM_FACTOR, navigator.zoomedAround(start, 0.0, 0.0, 0.001).zoom)
    }

    @Test
    fun visibleAreaAndTransforms() {
        val viewport = Viewport(zoom = 2.0, offsetX = 10.0, offsetY = 100.0, viewWidth = 400.0, viewHeight = 600.0)
        assertEquals(Bounds(10.0, 100.0, 210.0, 400.0), viewport.visible)
        assertEquals(Pair(20.0, 40.0), viewport.layoutToView(20.0, 120.0))
        assertEquals(Pair(20.0, 120.0), viewport.viewToLayout(20.0, 40.0))
    }

    @Test
    fun anchorSurvivesRotation() {
        val portrait =
            navigator.zoomedAround(
                navigator.scrolledBy(navigator.initial(1624.0, 2000.0), 0.0, 2400.0),
                800.0,
                1000.0,
                1.5,
            )
        val anchor = navigator.anchorOf(portrait)
        assertEquals(1, anchor.page)
        val landscape = navigator.restore(anchor, viewWidth = 2000.0, viewHeight = 1624.0)
        // Same zoom relative to fit width, and the same page point at the top of the view.
        assertClose(portrait.zoom / navigator.fitWidthZoom(1624.0), landscape.zoom / navigator.fitWidthZoom(2000.0))
        assertEquals(1, navigator.anchorOf(landscape).page)
        assertClose(anchor.pageY, navigator.anchorOf(landscape).pageY)
    }

    @Test
    fun tilesCoverTheClip() {
        // A4 at 2 px/pt is 1191 x 1684 px: 3 x 4 tiles of 512 px.
        val all = TileGrid.tilesCovering(a4.width, a4.height, 2.0, Bounds(0.0, 0.0, a4.width, a4.height))
        assertEquals(12, all.size)
        assertEquals(Bounds(0.0, 0.0, 256.0, 256.0), all.first().bounds)
        // The last tile ends at the page edge.
        val last = all.last()
        assertEquals(Pair(2, 3), last.x to last.y)
        assertEquals(a4.width, last.bounds.right)
        assertEquals(a4.height, last.bounds.bottom)
        // A clip in the middle of the page needs only the tiles it touches.
        val some = TileGrid.tilesCovering(a4.width, a4.height, 2.0, Bounds(250.0, 250.0, 260.0, 260.0))
        assertEquals(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), some.map { it.x to it.y })
        assertEquals(emptyList(), TileGrid.tilesCovering(a4.width, a4.height, 2.0, Bounds(700.0, 0.0, 800.0, 10.0)))
    }
}
