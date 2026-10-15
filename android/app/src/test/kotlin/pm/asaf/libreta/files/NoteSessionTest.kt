// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.LayerOperations
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.PageOperations
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.core.model.applying
import pm.asaf.libreta.viewer.LayerView

class NoteSessionTest {
    private val blank = Page(100.0, 100.0, null, listOf(Layer(emptyList())))
    private val session = NoteSession(Document(listOf(blank, blank)))

    private fun stroke(x: Double) = Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 1.0, PointList.of(x, 0.0, x, 1.0))

    /** Adds a stroke to [indices] of the session's document, as one edit. */
    private fun write(vararg indices: Int) {
        var document = session.document
        val changes =
            indices.map { index ->
                val before = document.pages[index]
                val after = before.withElementAdded(stroke(index.toDouble()))
                document = document.withPage(index, after)
                PageChange(index, before, after)
            }
        session.edited(Edit(changes))
    }

    @Test
    fun undoAndRedoGiveThePagesToShow() {
        assertNull(session.undo())
        write(0)
        val one = session.document
        // One edit changing page 1 twice and page 0 once.
        write(1, 1, 0)
        val two = session.document
        assertEquals(UndoState(canUndo = true, canRedo = false), session.undoState.value)

        val undone = checkNotNull(session.undo())
        assertSame(one.pages[0], undone[0])
        assertSame(blank, undone[1])
        assertEquals(one, session.document)
        assertEquals(UndoState(canUndo = true, canRedo = true), session.undoState.value)

        val redone = checkNotNull(session.redo())
        assertSame(two.pages[0], redone[0])
        assertSame(two.pages[1], redone[1])
        assertEquals(two, session.document)
        assertNull(session.redo())
    }

    @Test
    fun knowsWhatIsSaved() {
        assertFalse(session.dirty)
        write(0)
        assertTrue(session.dirty)
        val saved = session.document
        // Edited while saving: still dirty.
        write(1)
        session.saved(saved)
        assertTrue(session.dirty)
        session.saved(session.document)
        assertFalse(session.dirty)
        session.undo()
        assertTrue(session.dirty)
        session.redo()
        assertFalse(session.dirty)
    }

    @Test
    fun layerViewsFollowThePages() {
        val twoLayers = session.document.applying(LayerOperations.addLayer(session.document, 1, 0))
        session.edited(LayerOperations.addLayer(session.document, 1, 0))
        assertEquals(twoLayers, session.document)
        session.setLayerView(1, LayerView(selected = 0, hidden = setOf(1)))
        // Not a change to the note.
        assertEquals(1L, session.changes.value)
        session.saved(session.document)
        session.setLayerView(0, LayerView(0, setOf(0)))
        assertFalse(session.dirty)

        // Page 1 moves first: its view goes with it; an inserted page starts as opened.
        session.edited(checkNotNull(PageOperations.movePage(session.document, 1, 0)))
        session.edited(PageOperations.insertPage(session.document, 0, blank.copy()))
        assertEquals(listOf(LayerView(0), LayerView(0, setOf(1)), LayerView(0, setOf(0))), session.layers.value)
        session.undo()
        assertEquals(listOf(LayerView(0, setOf(1)), LayerView(0, setOf(0))), session.layers.value)
        session.undo()
        assertEquals(listOf(LayerView(0, setOf(0)), LayerView(0, setOf(1))), session.layers.value)
        // Undoing the new layer: the view fits the page again.
        session.undo()
        assertEquals(LayerView(0), session.layers.value[1])
    }

    @Test
    fun keepsTheViewsVersions() {
        assertEquals(listOf(0, 0), session.versions().toList())
        val versions = intArrayOf(3, 1)
        session.keepVersions(versions)
        versions[0] = 9
        assertEquals(listOf(3, 1), session.versions().toList())
    }
}
