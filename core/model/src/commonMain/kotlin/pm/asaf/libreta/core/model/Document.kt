// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

import okio.ByteString

/**
 * A Xournal++ document. Coordinates are PDF points.
 *
 * `extraAttributes` and `extraChildren` (here and on pages, layers, elements and
 * backgrounds) hold file content Libreta does not interpret, so it is saved unchanged.
 */
data class Document(
    val pages: List<Page>,
    /** Application that wrote the file, e.g. `xournalpp 1.3.8`. */
    val creator: String? = null,
    /** Xournal++ file format version (4 for current files, 2 for .xoj exports). */
    val fileVersion: Int? = null,
    val title: String? = null,
    /** Encoded PNG thumbnail of the first page. */
    val preview: ByteString? = null,
    val extraAttributes: Map<String, String> = emptyMap(),
    /** Uninterpreted root-level children, written after the pages. */
    val extraChildren: List<ForeignNode> = emptyList(),
) {
    /** This document with page [index] replaced by [page]. */
    fun withPage(
        index: Int,
        page: Page,
    ): Document = copy(pages = pages.toMutableList().also { it[index] = page })
}

data class Page(
    val width: Double,
    val height: Double,
    val background: Background?,
    /** Bottom to top. Never empty: Xournal++ requires at least one layer per page. */
    val layers: List<Layer>,
    val extraAttributes: Map<String, String> = emptyMap(),
    /** Uninterpreted children, written after the layers. */
    val extraChildren: List<ForeignNode> = emptyList(),
) {
    init {
        require(layers.isNotEmpty()) { "a page needs at least one layer" }
    }

    /**
     * This page with [element] added above everything else in layer [layerIndex]; by
     * default the top layer, where Xournal++ draws unless another layer is selected.
     */
    fun withElementAdded(
        element: Element,
        layerIndex: Int = layers.lastIndex,
    ): Page {
        val layer = layers[layerIndex]
        return copy(
            layers =
                layers.toMutableList().also {
                    it[layerIndex] =
                        layer.copy(elements = layer.elements + element)
                },
        )
    }
}

/** Layer visibility is not part of the file format (docs/xopp-format.md §4). */
data class Layer(
    val elements: List<Element>,
    val name: String? = null,
    val extraAttributes: Map<String, String> = emptyMap(),
)
