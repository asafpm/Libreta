// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

/** Part of an edit. */
sealed interface Change

/** Page [index] replaced: [before] by [after]. */
data class PageChange(
    val index: Int,
    val before: Page,
    val after: Page,
) : Change

/**
 * The pages rearranged (inserted, deleted, moved): [before] became [after]. Pages are shared,
 * not copied; [pageMapping] tells which page went where.
 */
data class PagesChange(
    val before: List<Page>,
    val after: List<Page>,
) : Change

/** One user action (a stroke, an erasure, a new page, …), undone and redone as a whole. */
data class Edit(
    val changes: List<Change>,
)

/** This document with [edit] done. */
fun Document.applying(edit: Edit): Document =
    edit.changes.fold(this) { document, change ->
        when (change) {
            is PageChange -> document.withPage(change.index, change.after)
            is PagesChange -> document.copy(pages = change.after)
        }
    }

/** This document with [edit] undone. */
fun Document.reverting(edit: Edit): Document =
    edit.changes.asReversed().fold(this) { document, change ->
        when (change) {
            is PageChange -> document.withPage(change.index, change.before)
            is PagesChange -> document.copy(pages = change.before)
        }
    }

/**
 * Undo and redo: the [limit] most recent edits. Unsaved changes are tracked by which edit is
 * on top when saved, so undoing back to the saved state makes the document clean again.
 */
class EditHistory private constructor(
    private val undoable: List<Edit>,
    private val redoable: List<Edit>,
    /** The edit on top when last saved (null: none, i.e. as opened). */
    private val savedTop: Edit?,
    /** False once the saved state can't be reached by undo or redo any more. */
    private val savedReachable: Boolean,
    private val limit: Int,
) {
    constructor(limit: Int = DEFAULT_LIMIT) : this(emptyList(), emptyList(), null, true, limit)

    val canUndo: Boolean get() = undoable.isNotEmpty()
    val canRedo: Boolean get() = redoable.isNotEmpty()

    /** Whether the document differs from what was last saved (or opened). */
    val dirty: Boolean get() = !savedReachable || undoable.lastOrNull() !== savedTop

    /** Adds [edit], which was just done; the redos go. */
    fun record(edit: Edit): EditHistory {
        val kept = (undoable + edit).takeLast(limit)
        // A saved state among the dropped edits, or among the redos, can't come back.
        val droppedSaved = undoable.size + 1 > limit && kept.none { it === savedTop }
        val savedLost = droppedSaved || redoable.any { it === savedTop }
        return EditHistory(kept, emptyList(), savedTop, savedReachable && !savedLost, limit)
    }

    /** The history after undoing, and the edit to revert; null if there is nothing to undo. */
    fun undo(): Pair<EditHistory, Edit>? {
        val edit = undoable.lastOrNull() ?: return null
        return EditHistory(undoable.dropLast(1), redoable + edit, savedTop, savedReachable, limit) to edit
    }

    /** The history after redoing, and the edit to apply again; null if there is nothing to redo. */
    fun redo(): Pair<EditHistory, Edit>? {
        val edit = redoable.lastOrNull() ?: return null
        return EditHistory(undoable + edit, redoable.dropLast(1), savedTop, savedReachable, limit) to edit
    }

    /** The current state was saved. */
    fun saved(): EditHistory = EditHistory(undoable, redoable, undoable.lastOrNull(), true, limit)

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}
