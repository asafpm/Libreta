// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import pm.asaf.inkpad.core.geometry.TileGrid

/** A tile of page [page] (0-based) rendered at [zoom] px per point, in column [x] and row [y]. */
data class TileKey(
    val page: Int,
    val zoom: Float,
    val x: Int,
    val y: Int,
)

/**
 * Rendered tiles, all [TileGrid.SIZE] pixels square (edge tiles leave the rest unused), within a
 * byte budget. Evicted bitmaps are kept for reuse by [obtain].
 *
 * Insert and evict on the thread that draws (see [TileRenderer]): a bitmap is reused only
 * after it has left the cache, so it is never redrawn while being overwritten.
 */
class TileCache(
    maxBytes: Long,
) {
    private val pool = ArrayDeque<Bitmap>()
    private val tiles = BitmapLru<TileKey>(maxBytes, ::recycle)

    val maxBytes: Long get() = tiles.maxBytes
    val sizeBytes: Long get() = tiles.sizeBytes
    val size: Int get() = tiles.size

    operator fun get(key: TileKey): Bitmap? = tiles[key]

    operator fun contains(key: TileKey): Boolean = key in tiles

    fun put(
        key: TileKey,
        bitmap: Bitmap,
    ) = tiles.put(key, bitmap)

    /** Calls [block] for each cached tile of [page], at any zoom. */
    fun forEachOf(
        page: Int,
        block: (TileKey, Bitmap) -> Unit,
    ) = tiles.forEach({ it.page == page }, block)

    /** Drops the tiles of [page], e.g. after it changes. */
    fun invalidate(page: Int) = tiles.removeIf { it.page == page }

    fun clear() = tiles.clear()

    /** A cleared tile bitmap, reused if possible. */
    fun obtain(): Bitmap =
        synchronized(pool) { pool.removeLastOrNull() }?.apply { eraseColor(0) }
            ?: Bitmap.createBitmap(TileGrid.SIZE, TileGrid.SIZE, Bitmap.Config.ARGB_8888)

    /** Returns an unused bitmap from [obtain]. */
    fun recycle(bitmap: Bitmap) {
        synchronized(pool) { if (pool.size < POOL_SIZE) pool.addLast(bitmap) }
    }

    private companion object {
        const val POOL_SIZE = 8
    }
}

/** Whole pages at [WIDTH] pixels wide, drawn while tiles at the current zoom are missing. */
class Thumbnails(
    maxBytes: Long,
) {
    private val pages = BitmapLru<Int>(maxBytes)

    val maxBytes: Long get() = pages.maxBytes
    val sizeBytes: Long get() = pages.sizeBytes

    operator fun get(page: Int): Bitmap? = pages[page]

    operator fun contains(page: Int): Boolean = page in pages

    fun put(
        page: Int,
        bitmap: Bitmap,
    ) = pages.put(page, bitmap)

    fun invalidate(page: Int) = pages.removeIf { it == page }

    fun clear() = pages.clear()

    companion object {
        const val WIDTH = 384

        /** The zoom of the thumbnail of a page [pageWidth] points wide. */
        fun zoomFor(pageWidth: Double): Float = (WIDTH / pageWidth).toFloat()
    }
}

/** The caches of one document view. */
class RenderCaches(
    val tiles: TileCache,
    val thumbnails: Thumbnails,
) {
    fun clear() {
        tiles.clear()
        thumbnails.clear()
    }

    companion object {
        private const val MB = 1024L * 1024
        private const val MIN_TILE_MB = 64L
        private const val MAX_TILE_MB = 256L
        private const val MIN_THUMBNAIL_MB = 16L
        private const val MAX_THUMBNAIL_MB = 64L
        private const val TILE_SHARE = 3 // a third of the heap
        private const val THUMBNAIL_SHARE = 8 // an eighth

        /**
         * Budgets from the app's heap size: a third for tiles (64–256 MB), an eighth for
         * thumbnails (16–64 MB).
         */
        fun forDevice(context: Context): RenderCaches {
            val heapMb = checkNotNull(context.getSystemService(ActivityManager::class.java)).memoryClass.toLong()
            return RenderCaches(
                TileCache((heapMb / TILE_SHARE).coerceIn(MIN_TILE_MB, MAX_TILE_MB) * MB),
                Thumbnails((heapMb / THUMBNAIL_SHARE).coerceIn(MIN_THUMBNAIL_MB, MAX_THUMBNAIL_MB) * MB),
            )
        }
    }
}
