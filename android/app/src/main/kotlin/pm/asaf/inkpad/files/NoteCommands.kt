// SPDX-License-Identifier: GPL-2.0-or-later
// Which layer is selected after a layer command follows Xournal++ v1.3.8
// src/core/control/layer/LayerController.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.files

import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.LayerOperations
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.model.PageOperations
import pm.asaf.inkpad.viewer.DocumentView
import pm.asaf.inkpad.viewer.LayerView

/** Edits of the note in [session], shown in the [view] if there is one. */
class NoteEditor(
    val session: NoteSession,
    val view: () -> DocumentView?,
) {
    val document get() = session.document

    /** Does [edit], if any, with page [index]'s layers then shown as [layers] (if given). */
    fun apply(
        edit: Edit?,
        index: Int = -1,
        layers: LayerView? = null,
    ) {
        if (edit == null) return
        session.edited(edit)
        layers?.let { session.setLayerView(index, it) }
        show()
    }

    /** Shows page [index]'s layers as [change] makes them. */
    inline fun setView(
        index: Int,
        change: (LayerView) -> LayerView,
    ) {
        session.setLayerView(index, change(session.layers.value[index]))
        show()
    }

    fun show() {
        view()?.let { view ->
            view.showDocument(document.pages)
            view.layerViews = session.layers.value
        }
    }
}

/** Page commands: each an edit (undoable). New pages follow [template]. */
class PageCommands(
    private val editor: NoteEditor,
    private val template: () -> NewPageTemplate,
) {
    /** A new page after page [index], shown. */
    fun insertPageAfter(index: Int) {
        val page = template().newPage(editor.document.pages[index], index)
        editor.apply(PageOperations.insertPage(editor.document, index + 1, page))
        editor.view()?.scrollToPage(index + 1)
    }

    fun duplicatePage(index: Int) = editor.apply(PageOperations.duplicatePage(editor.document, index))

    fun deletePage(index: Int) = editor.apply(PageOperations.deletePage(editor.document, index))

    fun movePage(
        index: Int,
        to: Int,
    ) = editor.apply(PageOperations.movePage(editor.document, index, to))

    fun setBackground(
        indices: Collection<Int>,
        background: Background.Solid,
    ) = editor.apply(PageOperations.setBackground(editor.document, indices, background))
}

/** Layer commands on the layers of a page, and how they are shown (not edits, like Xournal++). */
class LayerCommands(
    private val editor: NoteEditor,
) {
    /** A new layer above the selected one of page [index] (or below), selected. */
    fun addLayer(
        index: Int,
        below: Boolean = false,
    ) {
        val view = editor.session.layers.value[index]
        val at = if (below) view.selected else view.selected + 1
        val shifted = view.hidden.map { if (it >= at) it + 1 else it }.toSet()
        editor.apply(LayerOperations.addLayer(editor.document, index, at), index, LayerView(at, shifted))
    }

    /** Removes the selected layer of page [index]; the one below is selected. */
    fun removeSelectedLayer(index: Int) {
        val view = editor.session.layers.value[index]
        val layer = view.selected
        editor.apply(
            LayerOperations.removeLayer(editor.document, index, layer),
            index,
            LayerView(maxOf(layer - 1, 0), view.hidden.removing(layer)),
        )
    }

    /** Merges the selected layer of page [index] into the one below, which is selected. */
    fun mergeSelectedLayerDown(index: Int) {
        val view = editor.session.layers.value[index]
        val layer = view.selected
        editor.apply(
            LayerOperations.mergeLayerDown(editor.document, index, layer),
            index,
            LayerView(layer - 1, view.hidden.removing(layer)),
        )
    }

    /** Moves the selected layer of page [index] up (towards the top) or down, still selected. */
    fun moveSelectedLayer(
        index: Int,
        up: Boolean,
    ) {
        val view = editor.session.layers.value[index]
        val from = view.selected
        val to = if (up) from + 1 else from - 1
        val swapped =
            view.hidden.mapTo(HashSet()) {
                if (it == from) {
                    to
                } else if (it == to) {
                    from
                } else {
                    it
                }
            }
        editor.apply(LayerOperations.moveLayer(editor.document, index, from, to), index, LayerView(to, swapped))
    }

    fun renameLayer(
        index: Int,
        layer: Int,
        name: String,
    ) = editor.apply(LayerOperations.renameLayer(editor.document, index, layer, name))

    /** Writes on [layer] of page [index] from now on. */
    fun selectLayer(
        index: Int,
        layer: Int,
    ) = editor.setView(index) { it.copy(selected = layer) }

    /** Hides or shows [layer] of page [index] (not an edit: like Xournal++, not saved). */
    fun setLayerHidden(
        index: Int,
        layer: Int,
        hidden: Boolean,
    ) = editor.setView(index) { it.copy(hidden = if (hidden) it.hidden + layer else it.hidden - layer) }

    /** Hidden layers after [removed] is gone. */
    private fun Set<Int>.removing(removed: Int): Set<Int> =
        filter { it != removed }
            .map {
                if (it >
                    removed
                ) {
                    it - 1
                } else {
                    it
                }
            }.toSet()
}

/** The note's page and layer commands. */
class NoteCommands(
    session: NoteSession,
    view: () -> DocumentView?,
    template: () -> NewPageTemplate,
) {
    private val editor = NoteEditor(session, view)
    val pages = PageCommands(editor, template)
    val layers = LayerCommands(editor)
}
