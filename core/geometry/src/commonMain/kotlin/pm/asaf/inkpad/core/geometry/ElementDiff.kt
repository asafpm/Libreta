// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Layer
import pm.asaf.inkpad.core.model.Page

/** Where an edited page changed, to redraw only that. */
object ElementDiff {
    /**
     * The area (page units) where [after] differs from [before], or null if they look the
     * same. Elements are compared by identity: edits add elements or replace some, keeping the
     * others; anything else (background, size, layers) is the whole page.
     */
    fun changedBounds(
        before: Page,
        after: Page,
        textMeasurer: TextMeasurer,
    ): Bounds? {
        val whole = Bounds(0.0, 0.0, maxOf(before.width, after.width), maxOf(before.height, after.height))
        val sameFrame =
            before.width == after.width &&
                before.height == after.height &&
                before.background == after.background &&
                before.layers.size == after.layers.size &&
                before.layers.zip(after.layers).all { (old, new) ->
                    old.copy(elements = emptyList()) ==
                        new.copy(elements = emptyList())
                }
        return if (sameFrame) {
            before.layers
                .zip(after.layers)
                .mapNotNull { (old, new) -> changedBounds(old, new, textMeasurer) }
                .reduceOrNull(Bounds::union)
        } else {
            whole
        }
    }

    private fun changedBounds(
        before: Layer,
        after: Layer,
        textMeasurer: TextMeasurer,
    ): Bounds? {
        val old = before.elements
        val new = after.elements
        var start = 0
        while (start < old.size && start < new.size && old[start] === new[start]) start++
        var end = 0
        while (end < old.size - start && end < new.size - start &&
            old[old.size - 1 - end] === new[new.size - 1 - end]
        ) {
            end++
        }
        val differing: List<Element> = old.subList(start, old.size - end) + new.subList(start, new.size - end)
        return differing.mapNotNull { ElementGeometry.bounds(it, textMeasurer) }.reduceOrNull(Bounds::union)
    }
}
