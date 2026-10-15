// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.RectF
import pm.asaf.inkpad.core.model.Element

/** What is selected in a [DocumentView], and where it is on screen. */
class ViewSelection internal constructor(
    private val tool: SelectionTool,
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
}
