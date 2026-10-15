// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.RectF
import okio.ByteString
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PenSize

/** What is selected in a [DocumentView], where it is on screen, and what can be done with it. */
class ViewSelection internal constructor(
    private val tool: SelectionTool,
    private val commands: SelectionCommands,
) {
    /** The page of the selection, or null if nothing is selected. */
    val page: Int? get() = tool.selected?.page

    /** The selected elements, in their layer's order. */
    val elements: List<Element> get() = tool.selected?.elements.orEmpty()

    /** Their indices in the page's selected layer. */
    val indices: List<Int> get() = tool.selected?.indices.orEmpty()

    /** The selection's box in view pixels, or null if nothing is selected. */
    val bounds: RectF? get() = tool.screenBounds()

    fun clear() = tool.clear()

    /** Deletes the selection (one edit). */
    fun delete() = commands.delete()

    /** Moves the selection by ([dx], [dy]) points (one edit). */
    fun moveBy(
        dx: Double,
        dy: Double,
    ) = commands.moveBy(dx, dy)

    /** Copies of the selection, a little down and right, become the selection (one edit). */
    fun duplicate() = commands.duplicate()

    /** Recolours ([color]) and resizes ([size]) the selection; null leaves it (one edit). */
    fun restyle(
        color: Color? = null,
        size: PenSize? = null,
    ) = commands.restyle(color, size)

    /**
     * A picture ([data], [width] × [height] pixels) added as an image on page [page], centred
     * on its visible part (or on [at], view pixels, on the page there), no larger than what is
     * in view; selected (one edit).
     */
    fun insertImage(
        page: Int,
        data: ByteString,
        width: Int,
        height: Int,
        at: Pair<Float, Float>? = null,
    ) = commands.insertImage(page, data, width, height, at)

    /** [elements] centred on the visible part of page [page], selected (one edit). */
    fun paste(
        page: Int,
        elements: List<Element>,
    ) = commands.paste(page, elements)
}
