// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionOperationsTest {
    private fun stroke(
        x: Double,
        tool: StrokeTool = StrokeTool.PEN,
        width: Double = 1.0,
    ) = Element.Stroke(tool, Color.of(0, 0, 0), width, PointList.of(x, 0.0, x, 1.0))

    private val a = stroke(1.0)
    private val b = stroke(2.0)
    private val c = stroke(3.0)

    private fun page(vararg elements: Element) =
        Page(100.0, 100.0, null, listOf(Layer(emptyList()), Layer(elements.toList())))

    private val document = Document(listOf(page(a, b, c), page()))

    private fun Document.top(page: Int) = pages[page].layers[1].elements

    private fun checkUndo(edit: Edit): Document {
        val after = document.applying(edit)
        assertEquals(document, after.reverting(edit))
        return after
    }

    @Test
    fun replacesInPlaceKeepingTheOrder() {
        val moved = SelectionOperations.replace(document, 0, 1, listOf(0, 2), listOf(stroke(10.0), stroke(30.0)))
        assertEquals(listOf(stroke(10.0), b, stroke(30.0)), checkUndo(moved.edit).top(0))
        assertEquals(listOf(0, 2), moved.indices)
    }

    @Test
    fun deletesAddsAndMovesToAnotherPage() {
        assertEquals(listOf(b), checkUndo(SelectionOperations.delete(document, 0, 1, listOf(2, 0))).top(0))
        val added = SelectionOperations.add(document, 0, 1, listOf(stroke(9.0)))
        assertEquals(listOf(a, b, c, stroke(9.0)), checkUndo(added.edit).top(0))
        assertEquals(listOf(3), added.indices)

        val moved = SelectionOperations.moveToPage(document, 0, 1, listOf(0, 2), 1, 1, listOf(stroke(5.0), stroke(6.0)))
        val after = checkUndo(moved.edit)
        assertEquals(listOf(b), after.top(0))
        assertEquals(listOf(stroke(5.0), stroke(6.0)), after.top(1))
        assertEquals(SelectionEdit(moved.edit, 1, 1, listOf(0, 1)), moved)
    }

    @Test
    fun restylesLikeXournalpp() {
        val red = Color.of(255, 0, 0)
        val text = Element.Text("t", "Sans", 12.0, 0.0, 0.0, Color.of(0, 0, 0))
        assertEquals(text.copy(color = red), SelectionOperations.recolored(text, red))
        assertEquals(
            stroke(1.0, StrokeTool.HIGHLIGHTER).copy(color = red),
            SelectionOperations.recolored(stroke(1.0, StrokeTool.HIGHLIGHTER), red),
        )
        // Each tool's own width for the size, pressure widths scaled alike.
        val pen = stroke(1.0, width = 1.41).copy(segmentWidths = DoubleList.of(0.705))
        val thick = SelectionOperations.resized(pen, PenSize.THICK) as Element.Stroke
        assertEquals(2.26, thick.width)
        assertEquals(1.13, checkNotNull(thick.segmentWidths)[0], 1e-9)
        assertEquals(
            19.84,
            (SelectionOperations.resized(stroke(1.0, StrokeTool.HIGHLIGHTER), PenSize.THICK) as Element.Stroke).width,
        )
        assertEquals(
            12.0,
            (SelectionOperations.resized(stroke(1.0, StrokeTool.ERASER), PenSize.THICK) as Element.Stroke).width,
        )
        assertEquals(text, SelectionOperations.resized(text, PenSize.THICK))
    }
}
