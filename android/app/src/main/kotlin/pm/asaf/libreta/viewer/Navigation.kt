// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

/** Ways to move around a [DocumentView], e.g. from the keyboard or a mouse wheel. */
sealed interface Navigation {
    /** Zoom to fit the page width, the top of the view kept in place. */
    data object FitWidth : Navigation

    /** The top of page [index], at the same zoom. */
    data class ToPage(
        val index: Int,
    ) : Navigation

    /** [delta] pages on from the current one (back if negative). */
    data class PageBy(
        val delta: Int,
    ) : Navigation

    /** The zoom times [factor], keeping the view point ([focusX], [focusY]) in place (the centre if null). */
    data class ZoomBy(
        val factor: Double,
        val focusX: Float? = null,
        val focusY: Float? = null,
    ) : Navigation

    /** Scrolled by ([dx], [dy]) view pixels (positive towards the end). */
    data class ScrollBy(
        val dx: Float,
        val dy: Float,
    ) : Navigation

    /** Down (up if negative) [screens] views' heights, less a little that stays in view, like Xournal++'s Space. */
    data class ScreenBy(
        val screens: Int,
    ) : Navigation
}

/** Zooms to fit the page width, keeping the top of the view in place. */
fun DocumentView.fitWidth() = navigate(Navigation.FitWidth)

/** Scrolls to the top of page [index], at the same zoom. */
fun DocumentView.scrollToPage(index: Int) = navigate(Navigation.ToPage(index))
