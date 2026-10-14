// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.android.render.TileKey
import pm.asaf.inkpad.core.geometry.DocumentLayout
import pm.asaf.inkpad.core.geometry.TileGrid
import pm.asaf.inkpad.core.geometry.Viewport
import pm.asaf.inkpad.core.model.Bounds
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Draws the visible pages from cached bitmaps: each page's thumbnail, then tiles left over
 * from other zooms (scaled, the furthest from the current zoom first), then tiles at the
 * current zoom. Nothing is rendered here. Use from the UI thread only.
 */
internal class DocumentPainter {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val source = Rect()
    private val target = RectF()
    private val otherZooms = ArrayList<Pair<TileKey, Bitmap>>()

    /** Where a page is drawn: its size in points, its top-left view pixel, its visible part. */
    private class Placement(
        val width: Double,
        val height: Double,
        val left: Float,
        val top: Float,
        val zoom: Double,
        /** Visible part of the page, in page units. */
        val clip: Bounds,
    )

    fun draw(
        canvas: Canvas,
        layout: DocumentLayout,
        caches: RenderCaches,
        viewport: Viewport,
    ) {
        canvas.drawColor(BACKGROUND)
        val visible = viewport.visible
        for (index in layout.pagesIntersecting(visible.top, visible.bottom)) {
            val bounds = layout.pageBounds(index)
            val placement =
                Placement(
                    width = bounds.right - bounds.left,
                    height = bounds.bottom - bounds.top,
                    // Whole pixels, so that tiles at the current zoom are copied without resampling.
                    left = ((bounds.left - viewport.offsetX) * viewport.zoom).roundToInt().toFloat(),
                    top = ((bounds.top - viewport.offsetY) * viewport.zoom).roundToInt().toFloat(),
                    zoom = viewport.zoom,
                    clip =
                        Bounds(
                            visible.left - bounds.left,
                            visible.top - bounds.top,
                            visible.right - bounds.left,
                            visible.bottom - bounds.top,
                        ),
                )
            drawPage(canvas, caches, index, placement)
        }
    }

    private fun drawPage(
        canvas: Canvas,
        caches: RenderCaches,
        index: Int,
        page: Placement,
    ) {
        target.set(
            page.left,
            page.top,
            page.left + (page.width * page.zoom).toFloat(),
            page.top + (page.height * page.zoom).toFloat(),
        )
        canvas.drawRect(target, pagePaint)
        caches.thumbnails[index]?.let { canvas.drawBitmap(it, null, target, bitmapPaint) }

        val zoom = page.zoom.toFloat()
        otherZooms.clear()
        caches.tiles.forEachOf(index) { key, bitmap -> if (key.zoom != zoom) otherZooms += key to bitmap }
        otherZooms.sortByDescending { (key, _) -> abs(ln(key.zoom / zoom)) }
        for ((key, bitmap) in otherZooms) drawTile(canvas, key, bitmap, page)
        otherZooms.clear()

        for (tile in TileGrid.tilesCovering(page.width, page.height, page.zoom, page.clip)) {
            val key = TileKey(index, zoom, tile.x, tile.y)
            caches.tiles[key]?.let { drawTile(canvas, key, it, page) }
        }
    }

    private fun drawTile(
        canvas: Canvas,
        key: TileKey,
        bitmap: Bitmap,
        page: Placement,
    ) {
        val tileZoom = key.zoom.toDouble()
        val size = TileGrid.SIZE
        val tileLeft = key.x * size
        val tileTop = key.y * size
        // Edge tiles only use part of the bitmap.
        val pixelsX = (ceil(page.width * tileZoom).toInt() - tileLeft).coerceIn(1, size)
        val pixelsY = (ceil(page.height * tileZoom).toInt() - tileTop).coerceIn(1, size)
        val shown =
            tileLeft / tileZoom < page.clip.right &&
                (tileLeft + pixelsX) / tileZoom > page.clip.left &&
                tileTop / tileZoom < page.clip.bottom &&
                (tileTop + pixelsY) / tileZoom > page.clip.top
        if (shown) {
            val scale = page.zoom / tileZoom
            source.set(0, 0, pixelsX, pixelsY)
            target.set(
                page.left + (tileLeft * scale).toFloat(),
                page.top + (tileTop * scale).toFloat(),
                page.left + ((tileLeft + pixelsX) * scale).toFloat(),
                page.top + ((tileTop + pixelsY) * scale).toFloat(),
            )
            canvas.drawBitmap(bitmap, source, target, bitmapPaint)
        }
    }

    private companion object {
        val BACKGROUND = Color.rgb(0xE4, 0xE4, 0xE7)
    }
}
