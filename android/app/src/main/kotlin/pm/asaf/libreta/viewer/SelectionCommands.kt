// SPDX-License-Identifier: GPL-2.0-or-later
// Pasting follows Xournal++ v1.3.8 src/core/control/Control.cpp (clipboardPaste…: centred on
// the visible part of the page, kept on it), © Xournal++ contributors.

package pm.asaf.libreta.viewer

import okio.ByteString
import pm.asaf.libreta.core.geometry.Selecting
import pm.asaf.libreta.core.geometry.SelectionTransform
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.SelectionOperations
import kotlin.math.max

/** What can be done with the selection of [tool]: each is one edit. */
internal class SelectionCommands(
    private val tool: SelectionTool,
    private val host: SelectionTool.Host,
) {
    fun delete() {
        val selected = tool.selected ?: return
        tool.clear()
        host.edit(SelectionOperations.delete(Document(host.pages), selected.page, selected.layer, selected.indices))
    }

    /** Copies, a little down and right, selected instead. */
    fun duplicate() {
        val selected = tool.selected ?: return
        val copies = selected.elements.map(SelectionTransform(dx = DUPLICATE_OFFSET, dy = DUPLICATE_OFFSET)::apply)
        add(selected.page, copies)
    }

    /** Recolours and resizes the selection (null: as it is). */
    fun restyle(
        color: Color?,
        size: PenSize?,
    ) {
        val selected = tool.selected ?: return
        val restyled =
            selected.elements.map { element ->
                val recolored = color?.let { SelectionOperations.recolored(element, it) } ?: element
                size?.let { SelectionOperations.resized(recolored, it) } ?: recolored
            }
        if (restyled == selected.elements) return
        val result =
            SelectionOperations.replace(
                Document(host.pages),
                selected.page,
                selected.layer,
                selected.indices,
                restyled,
            )
        host.edit(result.edit)
        tool.select(selected.page, result.indices)
    }

    /**
     * [elements] (e.g. from the clipboard) on page [page], centred on its visible part, kept on
     * the page; selected.
     */
    fun paste(
        page: Int,
        elements: List<Element>,
    ) {
        val placing = placement(page, elements) ?: return
        add(page, elements.map(placing::apply))
    }

    /**
     * A picture ([data], [width] × [height] pixels) added as an image, one pixel a point but no
     * larger than the part of the page in view, like Xournal++. It is centred on [at] (view
     * pixels) if given, on the page's visible part otherwise ([page]), kept on the page, and
     * selected.
     */
    fun insertImage(
        page: Int,
        data: ByteString,
        width: Int,
        height: Int,
        at: Pair<Float, Float>? = null,
    ) {
        val dropped = at?.let { (x, y) -> host.pageAt(x, y) }
        val index = dropped?.page ?: page
        val visible = host.visibleArea(index) ?: return
        val sheet = host.page(index)
        val scale =
            minOf(
                1.0,
                (visible.right - visible.left) / width,
                (visible.bottom - visible.top) / height,
                sheet.width / width,
                sheet.height / height,
            )
        val w = width * scale
        val h = height * scale
        val centerX = at?.let { dropped?.toPageX(it.first) } ?: ((visible.left + visible.right) / 2)
        val centerY = at?.let { dropped?.toPageY(it.second) } ?: ((visible.top + visible.bottom) / 2)
        val left = (centerX - w / 2).coerceIn(0.0, max(0.0, sheet.width - w))
        val top = (centerY - h / 2).coerceIn(0.0, max(0.0, sheet.height - h))
        add(index, listOf(Element.Image(Bounds(left, top, left + w, top + h), data)))
    }

    /** Moves [elements] to the middle of page [page]'s visible part, kept on the page; null if not shown. */
    private fun placement(
        page: Int,
        elements: List<Element>,
    ): SelectionTransform? {
        val measurer = host.renderer?.textMeasurer
        val bounds = measurer?.let { Selecting.bounds(elements, it) }
        val visible = host.visibleArea(page)
        if (bounds == null || visible == null) return null
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val left = max(0.0, (visible.left + visible.right - width) / 2)
        val top = max(0.0, (visible.top + visible.bottom - height) / 2)
        return SelectionTransform(dx = left - bounds.left, dy = top - bounds.top)
    }

    private fun add(
        page: Int,
        elements: List<Element>,
    ) {
        val result = SelectionOperations.add(Document(host.pages), page, host.layer(page), elements)
        tool.clear()
        host.edit(result.edit)
        tool.select(page, result.indices)
    }

    private companion object {
        /** How far down and right a duplicate goes, in points. */
        const val DUPLICATE_OFFSET = 10.0
    }
}
