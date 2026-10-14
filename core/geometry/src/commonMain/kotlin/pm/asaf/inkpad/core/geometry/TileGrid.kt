// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import kotlin.math.ceil
import kotlin.math.floor

/** One square of a page rendered at a given zoom. */
data class Tile(
    val x: Int,
    val y: Int,
    /** The part of the page it shows, in page units (edge tiles stop at the page edge). */
    val bounds: Bounds,
)

/** Splits pages into [SIZE] x [SIZE] pixel tiles for rendering at a zoom. */
object TileGrid {
    const val SIZE = 512

    /** The tiles of a page of the given size at [zoom] (px per point) that overlap [clip] (page units). */
    fun tilesCovering(
        pageWidth: Double,
        pageHeight: Double,
        zoom: Double,
        clip: Bounds,
    ): List<Tile> {
        val left = maxOf(clip.left, 0.0)
        val top = maxOf(clip.top, 0.0)
        val right = minOf(clip.right, pageWidth)
        val bottom = minOf(clip.bottom, pageHeight)
        if (left >= right || top >= bottom) return emptyList()
        val tileUnits = SIZE / zoom
        val columns = floor(left / tileUnits).toInt() until ceil(right / tileUnits).toInt()
        val rows = floor(top / tileUnits).toInt() until ceil(bottom / tileUnits).toInt()
        return rows.flatMap { y ->
            columns.map { x ->
                Tile(
                    x,
                    y,
                    Bounds(
                        x * tileUnits,
                        y * tileUnits,
                        minOf((x + 1) * tileUnits, pageWidth),
                        minOf((y + 1) * tileUnits, pageHeight),
                    ),
                )
            }
        }
    }
}
