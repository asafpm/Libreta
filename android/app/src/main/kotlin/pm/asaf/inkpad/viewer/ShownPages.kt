// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import pm.asaf.inkpad.core.model.Page

/**
 * A page's layers as shown this session: the one written on, and those hidden. Like
 * Xournal++, this is not saved, not undone, and doesn't count as a change.
 */
data class LayerView(
    val selected: Int,
    val hidden: Set<Int> = emptySet(),
) {
    /** Within [page]'s layers, e.g. after a layer was removed. */
    fun fitting(page: Page): LayerView =
        LayerView(selected.coerceIn(0, page.layers.lastIndex), hidden.filterTo(HashSet()) { it in page.layers.indices })

    companion object {
        /** As a page opens: the top layer selected, all shown. */
        fun of(page: Page): LayerView = LayerView(page.layers.lastIndex)
    }
}

/** The document's pages in a view, with how each one's layers are shown; hidden layers aren't rendered. */
internal class ShownPages(
    pages: List<Page>,
    views: List<LayerView>,
) {
    private val pages = pages.toMutableList()
    private val views = MutableList(pages.size) { views.getOrNull(it)?.fitting(pages[it]) ?: LayerView.of(pages[it]) }

    val size: Int get() = pages.size

    /** The pages, as in the document. */
    val all: List<Page> get() = pages.toList()

    operator fun get(index: Int): Page = pages[index]

    fun view(index: Int): LayerView = views[index]

    /** Page [index] is now [page]. */
    operator fun set(
        index: Int,
        page: Page,
    ) {
        pages[index] = page
        views[index] = views[index].fitting(page)
    }

    fun setView(
        index: Int,
        view: LayerView,
    ) {
        views[index] = view.fitting(pages[index])
    }

    /** Page [index] as rendered: its hidden layers empty. */
    fun rendered(index: Int): Page {
        val hidden = views[index].hidden
        val page = pages[index]
        return if (hidden.isEmpty()) {
            page
        } else {
            page.copy(
                layers =
                    page.layers.mapIndexed { i, layer ->
                        if (i in
                            hidden
                        ) {
                            layer.copy(elements = emptyList())
                        } else {
                            layer
                        }
                    },
            )
        }
    }
}
