// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Bounds
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionBoxTest {
    // Contents 100 x 50 at (112, 112): the box is (100, 100) to (236, 186).
    private val selection = SelectionBox(Bounds(112.0, 112.0, 224.0, 174.0))

    @Test
    fun findsHandlesCornersFirst() {
        assertEquals(Bounds(100.0, 100.0, 236.0, 186.0), selection.box)
        assertEquals(SelectionHandle.TOP_LEFT, selection.handleAt(102.0, 101.0, 5.0, 20.0))
        assertEquals(SelectionHandle.RIGHT, selection.handleAt(236.0, 143.0, 5.0, 20.0))
        assertEquals(SelectionHandle.ROTATE, selection.handleAt(256.0, 143.0, 5.0, 20.0))
        assertEquals(SelectionHandle.MOVE, selection.handleAt(150.0, 150.0, 5.0, 20.0))
        assertNull(selection.handleAt(50.0, 50.0, 5.0, 20.0))
        // With text: no edges, no rotation.
        val text = selection.copy(canStretch = false, canRotate = false)
        assertEquals(SelectionHandle.MOVE, text.handleAt(236.0, 143.0, 5.0, 20.0))
        assertNull(text.handleAt(256.0, 143.0, 5.0, 20.0))
        assertEquals(4, text.handles.size)
    }

    @Test
    fun cornersScaleEvenlyAboutTheOppositeOne() {
        // The bottom-right corner dragged to twice the size along the diagonal.
        val t = selection.transform(SelectionHandle.BOTTOM_RIGHT, 236.0, 186.0, 372.0, 272.0, 5.0)
        assertEquals(SelectionTransform(fx = 2.0, fy = 2.0, originX = 100.0, originY = 100.0), t)
        // Off the diagonal: projected onto it.
        val off = selection.transform(SelectionHandle.BOTTOM_RIGHT, 236.0, 186.0, 372.0, 186.0, 5.0)
        assertEquals(off.fx, off.fy)
    }

    @Test
    fun edgesStretchOneWay() {
        val t = selection.transform(SelectionHandle.LEFT, 100.0, 143.0, 168.0, 0.0, 5.0)
        assertEquals(SelectionTransform(fx = 0.5, fy = 1.0, originX = 236.0, originY = 143.0), t)
    }

    @Test
    fun keepsAMinimumSizeAndMirrorsOnlyWhenAllowed() {
        // Past the opposite side.
        val mirrored = selection.transform(SelectionHandle.RIGHT, 236.0, 143.0, 32.0, 143.0, 5.0)
        assertEquals(-0.5, mirrored.fx, 1e-12)
        val boxed = selection.copy(canMirror = false).transform(SelectionHandle.RIGHT, 236.0, 143.0, 32.0, 143.0, 5.0)
        assertEquals(5.0 / 136, boxed.fx, 1e-12)
        val tiny = selection.transform(SelectionHandle.BOTTOM, 0.0, 186.0, 0.0, 101.0, 5.0)
        assertEquals(5.0 / 86, tiny.fy, 1e-12)
    }

    @Test
    fun rotatesAboutTheCentreSnapping() {
        val down = selection.transform(SelectionHandle.ROTATE, 256.0, 143.0, 168.0, 300.0, 5.0)
        assertEquals(SelectionTransform(angle = PI / 2, centerX = 168.0, centerY = 143.0), down)
        val moved = selection.transform(SelectionHandle.MOVE, 150.0, 150.0, 160.0, 145.0, 5.0)
        assertEquals(SelectionTransform(dx = 10.0, dy = -5.0), moved)
    }
}
