// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Bounds

/**
 * Pages stacked vertically for continuous scrolling, in layout units (points): each page
 * centred horizontally, [gap] between pages and around the whole document.
 */
class DocumentLayout(
    pageSizes: List<Size>,
    val gap: Double = DEFAULT_GAP,
) {
    val pageCount: Int = pageSizes.size
    val width: Double = (pageSizes.maxOfOrNull { it.width } ?: 0.0) + 2 * gap

    private val pages: List<Bounds> =
        buildList {
            var top = gap
            for (size in pageSizes) {
                val left = (width - size.width) / 2
                add(Bounds(left, top, left + size.width, top + size.height))
                top += size.height + gap
            }
        }

    val height: Double = (pages.lastOrNull()?.bottom ?: 0.0) + gap

    fun pageBounds(index: Int): Bounds = pages[index]

    /** Pages overlapping the vertical range [top, bottom], in order. */
    fun pagesIntersecting(
        top: Double,
        bottom: Double,
    ): IntRange {
        val first = firstPageEndingAfter(top)
        if (first >= pages.size || pages[first].top >= bottom) return IntRange.EMPTY
        var last = first
        while (last + 1 < pages.size && pages[last + 1].top < bottom) last++
        return first..last
    }

    /** The page containing the layout point (x, y), or null if it is in a gap. */
    fun pageAt(
        x: Double,
        y: Double,
    ): Int? {
        val index = firstPageEndingAfter(y)
        val page = pages.getOrNull(index) ?: return null
        return index.takeIf { y >= page.top && x >= page.left && x <= page.right }
    }

    /** Binary search: the first page whose bottom is below [y]. */
    private fun firstPageEndingAfter(y: Double): Int {
        var low = 0
        var high = pages.size
        while (low < high) {
            val middle = (low + high) / 2
            if (pages[middle].bottom <= y) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        const val DEFAULT_GAP = 12.0
    }
}
