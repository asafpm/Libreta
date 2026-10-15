// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.libreta.core.model.Change
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.EditHistory
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.PagesChange
import pm.asaf.libreta.core.model.applying
import pm.asaf.libreta.core.model.pageMapping
import pm.asaf.libreta.core.model.reverting
import pm.asaf.libreta.viewer.LayerView

/** Whether there is something to undo or redo. */
data class UndoState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * The open note as edited so far, kept across rotation: the document, its undo history,
 * whether it changed since it was read or saved, how each page's layers are shown, and each
 * page's version in the view's tiles. Edited on the main thread; read by saving in the
 * background.
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
    private val layerFlow = MutableStateFlow(initial.pages.map(LayerView::of))

    val undoState: StateFlow<UndoState> = undoFlow.asStateFlow()

    /** Counts the changes to the document (edits, undos and redos). */
    val changes: StateFlow<Long> = changeCount.asStateFlow()

    /** How each page's layers are shown, by page index. */
    val layers: StateFlow<List<LayerView>> = layerFlow.asStateFlow()

    /** Changed since read or last saved. */
    val dirty: Boolean get() = synchronized(this) { history.dirty }

    /** The pages' versions when the view was last closed (matching the cached tiles). */
    fun versions(): IntArray = synchronized(this) { versions.copyOf() }

    /** Keeps the view's page [versions] for when it shows the note again. */
    fun keepVersions(versions: IntArray) = synchronized(this) { this.versions = versions.copyOf() }

    /** [edit] was done. */
    fun edited(edit: Edit) =
        update {
            history = history.record(edit)
            show(document.applying(edit), edit.changes)
        }

    /** Undoes the last edit; returns the pages to show, or null if there was none. */
    fun undo(): List<Page>? =
        update {
            history.undo()?.let { (undone, edit) ->
                history = undone
                show(document.reverting(edit), edit.changes.asReversed().map { it.reversed() })
                document.pages
            }
        }

    /** Redoes the last undone edit; returns the pages to show, or null if there was none. */
    fun redo(): List<Page>? =
        update {
            history.redo()?.let { (redone, edit) ->
                history = redone
                show(document.applying(edit), edit.changes)
                document.pages
            }
        }

    /** Shows page [index]'s layers as [view]. */
    fun setLayerView(
        index: Int,
        view: LayerView,
    ) = synchronized(this) {
        layerFlow.value = layerFlow.value.toMutableList().also { it[index] = view.fitting(document.pages[index]) }
    }

    /** [saved] is what was written: clean unless edited since. */
    fun saved(saved: Document) =
        update {
            if (document === saved) history = history.saved()
        }

    /** The document is now [next], after [changes]: the layer views follow the pages. */
    private fun show(
        next: Document,
        changes: List<Change>,
    ) {
        var views = layerFlow.value
        for (change in changes) {
            if (change is PagesChange) {
                val mapping = pageMapping(change.before, change.after)
                views =
                    change.after.mapIndexed { i, page ->
                        mapping[i].takeIf { it >= 0 }?.let(views::get)
                            ?: LayerView.of(page)
                    }
            }
        }
        document = next
        layerFlow.value = next.pages.mapIndexed { i, page -> views.getOrNull(i)?.fitting(page) ?: LayerView.of(page) }
        changeCount.value++
    }

    private fun Change.reversed(): Change =
        when (this) {
            is PageChange -> PageChange(index, after, before)
            is PagesChange -> PagesChange(after, before)
        }

    private inline fun <T> update(change: () -> T): T =
        synchronized(this) {
            val result = change()
            undoFlow.value = UndoState(history.canUndo, history.canRedo)
            result
        }
}
