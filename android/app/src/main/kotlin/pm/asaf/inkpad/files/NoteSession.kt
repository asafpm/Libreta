// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.EditHistory
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.applying
import pm.asaf.inkpad.core.model.reverting

/** Whether there is something to undo or redo. */
data class UndoState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * The open note as edited so far, kept across rotation: the document, its undo history,
 * whether it changed since it was read or saved, and each page's version in the view's tiles.
 * Edited on the main thread; read by saving in the background.
 */
class NoteSession(
    initial: Document,
) {
    @Volatile var document: Document = initial
        private set

    private var history = EditHistory()
    private var versions = IntArray(initial.pages.size)
    private val undoFlow = MutableStateFlow(UndoState())
    private val changeCount = MutableStateFlow(0L)

    val undoState: StateFlow<UndoState> = undoFlow.asStateFlow()

    /** Counts the changes to the document (edits, undos and redos). */
    val changes: StateFlow<Long> = changeCount.asStateFlow()

    /** Changed since read or last saved. */
    val dirty: Boolean get() = synchronized(this) { history.dirty }

    /** The pages' versions when the view was last closed (matching the cached tiles). */
    fun versions(): IntArray = synchronized(this) { versions.copyOf() }

    /** Keeps the view's page [versions] for when it shows the note again. */
    fun keepVersions(versions: IntArray) = synchronized(this) { this.versions = versions.copyOf() }

    /** [edit] was done. */
    fun edited(edit: Edit) =
        update {
            document = document.applying(edit)
            history = history.record(edit)
            changeCount.value++
        }

    /** Undoes the last edit; returns the pages to show (by index), or null if there was none. */
    fun undo(): Map<Int, Page>? =
        update {
            history.undo()?.let { (undone, edit) ->
                history = undone
                document = document.reverting(edit)
                changeCount.value++
                // The earliest change of each page has what it was before.
                edit.changes.asReversed().associate { it.index to it.before }
            }
        }

    /** Redoes the last undone edit; returns the pages to show (by index), or null if there was none. */
    fun redo(): Map<Int, Page>? =
        update {
            history.redo()?.let { (redone, edit) ->
                history = redone
                document = document.applying(edit)
                changeCount.value++
                edit.changes.associate { it.index to it.after }
            }
        }

    /** [saved] is what was written: clean unless edited since. */
    fun saved(saved: Document) =
        update {
            if (document === saved) history = history.saved()
        }

    private inline fun <T> update(change: () -> T): T =
        synchronized(this) {
            val result = change()
            undoFlow.value = UndoState(history.canUndo, history.canRedo)
            result
        }
}
