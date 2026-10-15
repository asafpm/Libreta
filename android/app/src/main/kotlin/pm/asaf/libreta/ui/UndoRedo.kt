// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.files.NoteSession
import pm.asaf.libreta.viewer.DocumentView

/**
 * Undoes the last change to [session]'s note, shown in [view]. Text being typed is finished
 * first: it is a step of its own.
 */
internal fun undo(
    session: NoteSession,
    view: DocumentView?,
) {
    view?.text?.finish()
    session.undo()?.let { view?.show(it, session) }
}

/** Redoes the last change undone in [session]'s note, shown in [view]. */
internal fun redo(
    session: NoteSession,
    view: DocumentView?,
) {
    view?.text?.finish()
    session.redo()?.let { view?.show(it, session) }
}

/** Shows [pages] after an undo or redo, with the layers as [session] shows them. */
private fun DocumentView.show(
    pages: List<Page>,
    session: NoteSession,
) {
    showDocument(pages)
    layerViews = session.layers.value
}
