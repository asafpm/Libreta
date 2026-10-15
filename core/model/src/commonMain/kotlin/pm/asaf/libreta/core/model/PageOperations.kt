// SPDX-License-Identifier: GPL-2.0-or-later
// Page and layer operations follow Xournal++ v1.3.8 src/core/control/Control.cpp (deletePage,
// duplicatePage, movePageTowards…) and src/core/control/layer/LayerController.cpp,
// © Xournal++ contributors.

package pm.asaf.libreta.core.model

/**
 * Page commands, each an [Edit] of [document] (null when it can't be done), so that they can
 * be undone. Page indices are 0-based.
 */
object PageOperations {
    /** Inserts [page] at [index]. */
    fun insertPage(
        document: Document,
        index: Int,
        page: Page,
    ): Edit = rearranged(document, document.pages.toMutableList().apply { add(index.coerceIn(0, size), page) })

    /** A copy of page [index] after it. */
    fun duplicatePage(
        document: Document,
        index: Int,
    ): Edit = insertPage(document, index + 1, document.pages[index].copy())

    /** Deletes page [index], unless it is the only one. */
    fun deletePage(
        document: Document,
        index: Int,
    ): Edit? =
        if (document.pages.size <= 1) {
            null
        } else {
            rearranged(document, document.pages.toMutableList().apply { removeAt(index) })
        }

    /** Moves page [from] to position [to]. */
    fun movePage(
        document: Document,
        from: Int,
        to: Int,
    ): Edit? {
        if (from == to || to !in document.pages.indices) return null
        return rearranged(document, document.pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    /**
     * Gives pages [indices] a solid [background]. A PDF named on one of them is named on the
     * next PDF page; images cloned from one keep the image.
     */
    fun setBackground(
        document: Document,
        indices: Collection<Int>,
        background: Background.Solid,
    ): Edit? {
        val pages = document.pages
        val changed = indices.toSet()
        val replaced =
            pages.mapIndexed { index, page ->
                if (index in changed && page.background != background) page.copy(background = background) else page
            }
        val fixed = BackgroundReferences.fixed(pages, replaced)
        val changes = pages.indices.filter { pages[it] !== fixed[it] }.map { PageChange(it, pages[it], fixed[it]) }
        return changes.takeIf { it.isNotEmpty() }?.let(::Edit)
    }

    /** [document]'s pages rearranged into [pages], with background references that still hold. */
    private fun rearranged(
        document: Document,
        pages: List<Page>,
    ): Edit = Edit(listOf(PagesChange(document.pages, BackgroundReferences.fixed(document.pages, pages))))
}

/**
 * Layer commands on page [index] of [document], each an [Edit] (null when it can't be done).
 * Layer indices are 0-based, bottom to top.
 */
object LayerOperations {
    /** Adds an empty layer at position [at] of page [index]. */
    fun addLayer(
        document: Document,
        index: Int,
        at: Int,
    ): Edit = changeLayers(document, index) { add(at.coerceIn(0, size), Layer(emptyList())) }

    /** Removes [layer] of page [index], unless it is the only one (a page needs one). */
    fun removeLayer(
        document: Document,
        index: Int,
        layer: Int,
    ): Edit? = if (document.pages[index].layers.size <= 1) null else changeLayers(document, index) { removeAt(layer) }

    /** Names [layer] of page [index]; a blank name removes it. */
    fun renameLayer(
        document: Document,
        index: Int,
        layer: Int,
        name: String,
    ): Edit = changeLayers(document, index) { this[layer] = this[layer].copy(name = name.takeIf { it.isNotBlank() }) }

    /** Moves [layer] of page [index] to position [to]. */
    fun moveLayer(
        document: Document,
        index: Int,
        layer: Int,
        to: Int,
    ): Edit? =
        if (layer == to || to !in document.pages[index].layers.indices) {
            null
        } else {
            changeLayers(document, index) { add(to, removeAt(layer)) }
        }

    /** Moves the elements of [layer] of page [index] on top of the layer below, and removes it. */
    fun mergeLayerDown(
        document: Document,
        index: Int,
        layer: Int,
    ): Edit? =
        if (layer == 0) {
            null
        } else {
            changeLayers(document, index) {
                val top = removeAt(layer)
                this[layer - 1] = this[layer - 1].copy(elements = this[layer - 1].elements + top.elements)
            }
        }

    private inline fun changeLayers(
        document: Document,
        index: Int,
        change: MutableList<Layer>.() -> Unit,
    ): Edit {
        val page = document.pages[index]
        return Edit(listOf(PageChange(index, page, page.copy(layers = page.layers.toMutableList().apply(change)))))
    }
}

/**
 * The page of [before] each page of [after] is (by identity), or -1 for a new page. A page
 * appearing twice maps once.
 */
fun pageMapping(
    before: List<Page>,
    after: List<Page>,
): IntArray {
    val unused = before.indices.toMutableList()
    return IntArray(after.size) { i ->
        val match = unused.firstOrNull { before[it] === after[i] } ?: -1
        if (match >= 0) unused.remove(match)
        match
    }
}

/**
 * Backgrounds that refer to other pages by position (docs/xopp-format.md §4): only the first
 * PDF page names the PDF, and a cloned image names the earlier page whose image it reuses.
 */
private object BackgroundReferences {
    /** [after] (a rearrangement of [before]) with those references made right again. */
    fun fixed(
        before: List<Page>,
        after: List<Page>,
    ): List<Page> = withPdfFile(before, withClones(before, after))

    /** Clones point at their source's new position, or take its image if it no longer comes first. */
    private fun withClones(
        before: List<Page>,
        after: List<Page>,
    ): List<Page> {
        val newIndex = IntArray(before.size) { -1 }
        pageMapping(before, after).forEachIndexed { i, old -> if (old >= 0) newIndex[old] = i }
        return after.mapIndexed { i, page ->
            val background = page.background as? Background.Pixmap
            val source = background?.takeIf { it.domain == FileDomain.CLONE }?.filename?.toIntOrNull()
            if (background == null || source == null || source !in before.indices) {
                page
            } else {
                val moved = newIndex[source]
                val resolved =
                    if (moved in 0 until i) {
                        background.copy(filename = moved.toString())
                    } else {
                        // The source's own image (it is never a clone of a later page).
                        before[source].background ?: background
                    }
                if (resolved == background) page else page.copy(background = resolved)
            }
        }
    }

    /** The PDF file named on the first PDF page, and on no other. */
    private fun withPdfFile(
        before: List<Page>,
        after: List<Page>,
    ): List<Page> {
        val named =
            before.firstNotNullOfOrNull {
                (it.background as? Background.Pdf)?.takeIf { pdf ->
                    pdf.filename !=
                        null
                }
            }
        val first = after.indexOfFirst { it.background is Background.Pdf }
        if (named == null || first < 0) return after
        return after.mapIndexed { i, page ->
            val pdf = page.background as? Background.Pdf
            val wanted =
                when {
                    pdf == null -> null
                    i == first -> pdf.copy(domain = named.domain, filename = named.filename)
                    else -> pdf.copy(domain = null, filename = null)
                }
            if (wanted == null || wanted == pdf) page else page.copy(background = wanted)
        }
    }
}
