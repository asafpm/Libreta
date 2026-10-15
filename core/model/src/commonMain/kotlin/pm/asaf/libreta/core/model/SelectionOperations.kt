// SPDX-License-Identifier: GPL-2.0-or-later
// Restyling follows Xournal++ v1.3.8 src/core/control/tools/EditSelectionContents.cpp
// (updateColor, updateSize), © Xournal++ contributors.

package pm.asaf.libreta.core.model

/** An edit made on a selection, and where the selected elements are afterwards (in its layer). */
data class SelectionEdit(
    val edit: Edit,
    val page: Int,
    val layer: Int,
    val indices: List<Int>,
)

/**
 * Commands on selected elements: [indices] of layer [layer] of page [page], each an [Edit] of
 * [document]. Elements keep their place in the layer (z-order) unless they move to another
 * page; added ones go on top.
 */
object SelectionOperations {
    /** Elements [indices] replaced by [replacements] (one each, in order), in place. */
    fun replace(
        document: Document,
        page: Int,
        layer: Int,
        indices: List<Int>,
        replacements: List<Element>,
    ): SelectionEdit {
        require(indices.size == replacements.size) { "one replacement per element" }
        val edit =
            changeElements(document, page, layer) {
                indices.forEachIndexed { i, index -> this[index] = replacements[i] }
            }
        return SelectionEdit(edit, page, layer, indices)
    }

    /** Elements [indices] removed. */
    fun delete(
        document: Document,
        page: Int,
        layer: Int,
        indices: List<Int>,
    ): Edit = changeElements(document, page, layer) { indices.sortedDescending().forEach { removeAt(it) } }

    /** [elements] added on top of layer [layer] of page [page]. */
    fun add(
        document: Document,
        page: Int,
        layer: Int,
        elements: List<Element>,
    ): SelectionEdit {
        val start =
            document.pages[page]
                .layers[layer]
                .elements.size
        val edit = changeElements(document, page, layer) { addAll(elements) }
        return SelectionEdit(edit, page, layer, List(elements.size) { start + it })
    }

    /** Elements [indices] taken from their page to page [toPage], on top of its layer [toLayer], as [moved]. */
    @Suppress("LongParameterList") // where from, where to, and what
    fun moveToPage(
        document: Document,
        page: Int,
        layer: Int,
        indices: List<Int>,
        toPage: Int,
        toLayer: Int,
        moved: List<Element>,
    ): SelectionEdit {
        val removed = delete(document, page, layer, indices)
        val added = add(document.applying(removed), toPage, toLayer, moved)
        return added.copy(edit = removed.followedBy(added.edit))
    }

    /** [element] in [color]: strokes (highlighters too) and text; others as they are. */
    fun recolored(
        element: Element,
        color: Color,
    ): Element =
        when (element) {
            is Element.Stroke -> element.copy(color = color)
            is Element.Text -> element.copy(color = color)
            else -> element
        }

    /**
     * [element] at [size]: strokes take their own tool's width for it (pen, highlighter or
     * eraser), pressure widths scaled alike; others as they are.
     */
    fun resized(
        element: Element,
        size: PenSize,
    ): Element {
        if (element !is Element.Stroke || element.width <= 0.0) return element
        val width =
            when (element.tool) {
                StrokeTool.PEN -> size.width
                StrokeTool.HIGHLIGHTER -> HighlighterSettings(size = size).width
                StrokeTool.ERASER -> EraserSettings(size = size).width
            }
        val factor = width / element.width
        val widths = element.segmentWidths?.let { w -> DoubleList.fromArray(DoubleArray(w.size) { w[it] * factor }) }
        return element.copy(width = width, segmentWidths = widths)
    }

    private inline fun changeElements(
        document: Document,
        page: Int,
        layer: Int,
        change: MutableList<Element>.() -> Unit,
    ): Edit {
        val before = document.pages[page]
        val layers = before.layers.toMutableList()
        layers[layer] = layers[layer].copy(elements = layers[layer].elements.toMutableList().apply(change))
        return Edit(listOf(PageChange(page, before, before.copy(layers = layers))))
    }
}

/** This edit, then [other] (made on the document after this one): undone together. */
fun Edit.followedBy(other: Edit): Edit = Edit(changes + other.changes)
