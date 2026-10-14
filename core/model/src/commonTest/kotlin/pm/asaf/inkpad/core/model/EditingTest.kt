// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EditingTest {
    private val dot = Element.Stroke(StrokeTool.PEN, Palette.DEFAULT_PEN, 1.41, PointList.of(1.0, 1.0, 1.0, 1.0))
    private val other = Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 2.0, PointList.of(0.0, 0.0, 5.0, 5.0))
    private val page = Page(100.0, 100.0, null, listOf(Layer(listOf(other), name = "bottom"), Layer(emptyList())))

    @Test
    fun elementsGoOnTopOfTheTopLayer() {
        val edited = page.withElementAdded(dot)
        assertEquals(listOf(other), edited.layers[0].elements)
        assertEquals(listOf<Element>(dot), edited.layers[1].elements)
        assertEquals(listOf(other, dot), page.withElementAdded(dot, layerIndex = 0).layers[0].elements)
        assertEquals("bottom", edited.layers[0].name)
        // The original is unchanged.
        assertEquals(emptyList(), page.layers[1].elements)
    }

    @Test
    fun replacesOnePage() {
        val document = Document(listOf(page, page), creator = "test")
        val edited = document.withPage(1, page.withElementAdded(dot))
        assertSame(page, edited.pages[0])
        assertEquals(listOf<Element>(dot), edited.pages[1].layers[1].elements)
        assertEquals("test", edited.creator)
    }

    @Test
    fun penDefaultsAreXournalpps() {
        assertEquals(PenSettings(Color.of(0x33, 0x33, 0xcc), PenSize.MEDIUM), PenSettings())
        assertEquals(listOf(0.42, 0.85, 1.41, 2.26, 5.67), PenSize.entries.map { it.width })
        assertEquals(11, Palette.XOURNALPP.size)
        assertEquals(Color.of(0, 0, 0), Palette.XOURNALPP[9])
    }
}
