// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditHistoryTest {
    private fun stroke(x: Double) =
        Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 1.0, PointList.of(x, 0.0, x + 1, 1.0))

    private val blank = Page(100.0, 100.0, null, listOf(Layer(emptyList())))
    private val document = Document(listOf(blank, blank))

    /** Adds a stroke at [x] to page [index] of [document], as an edit. */
    private fun add(
        document: Document,
        index: Int,
        x: Double,
    ): Pair<Document, Edit> {
        val after = document.pages[index].withElementAdded(stroke(x))
        return document.withPage(index, after) to Edit(listOf(PageChange(index, document.pages[index], after)))
    }

    @Test
    fun undoAndRedoRestorePages() {
        val (one, first) = add(document, 0, 1.0)
        val (two, second) = add(one, 1, 2.0)
        var history = EditHistory().record(first).record(second)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        val (afterUndo, undone) = checkNotNull(history.undo())
        history = afterUndo
        val reverted = two.reverting(undone)
        assertEquals(one, reverted)
        assertSame(one.pages[1], reverted.pages[1])
        assertTrue(history.canRedo)

        val (afterRedo, redone) = checkNotNull(history.redo())
        assertEquals(two, reverted.applying(redone))
        assertFalse(afterRedo.canRedo)
    }

    @Test
    fun aNewEditDropsTheRedos() {
        val (one, first) = add(document, 0, 1.0)
        val (_, second) = add(one, 0, 2.0)
        val (_, third) = add(one, 0, 3.0)
        val history = checkNotNull(EditHistory().record(first).record(second).undo()).first.record(third)
        assertFalse(history.canRedo)
        assertSame(third, checkNotNull(history.undo()).second)
    }

    @Test
    fun knowsWhetherThereAreUnsavedEdits() {
        val (one, first) = add(document, 0, 1.0)
        val (_, second) = add(one, 0, 2.0)
        val (_, other) = add(one, 0, 3.0)
        var history = EditHistory()
        assertFalse(history.dirty)
        history = history.record(first).saved()
        assertFalse(history.dirty)
        history = history.record(second)
        assertTrue(history.dirty)
        // Back to what was saved.
        history = checkNotNull(history.undo()).first
        assertFalse(history.dirty)
        // As deep as the saved state, but different.
        history = checkNotNull(history.undo()).first.record(other)
        assertTrue(history.dirty)
    }

    @Test
    fun keepsTheNewestEdits() {
        var doc = document
        var history = EditHistory(limit = 3)
        repeat(5) {
            val (next, edit) = add(doc, 0, it.toDouble())
            doc = next
            history = history.record(edit)
        }
        var undos = 0
        while (true) {
            history = history.undo()?.first ?: break
            undos++
        }
        assertEquals(3, undos)
        assertNull(EditHistory().undo())
    }
}
