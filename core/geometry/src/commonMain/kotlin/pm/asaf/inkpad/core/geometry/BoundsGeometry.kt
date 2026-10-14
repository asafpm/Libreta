// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds

val Bounds.width: Double get() = right - left
val Bounds.height: Double get() = bottom - top

/** True when the overlap has a strictly positive area (Xournal++ Element.cpp:95-105). */
fun Bounds.intersects(other: Bounds): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

/** These bounds grown by [margin] on every side. */
fun Bounds.expandedBy(margin: Double): Bounds = Bounds(left - margin, top - margin, right + margin, bottom + margin)

fun Bounds.union(other: Bounds): Bounds =
    Bounds(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

/** Size in page units. */
data class Size(
    val width: Double,
    val height: Double,
)
