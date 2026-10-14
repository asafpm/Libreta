// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import pm.asaf.inkpad.core.geometry.TileGrid
import pm.asaf.inkpad.core.geometry.expandedBy
import pm.asaf.inkpad.core.geometry.intersects
import pm.asaf.inkpad.core.model.Bounds

/**
 * A tile of page [page] (0-based) as it was at [version], rendered at [zoom] px per point, in
 * column [x] and row [y].
 */
data class TileKey(
    val page: Int,
    val version: Int,
    val zoom: Float,
    val x: Int,
    val y: Int,
) {
    /** The part of the page it shows, in page units (to the page edge and beyond). */
    val bounds: Bounds
        get() {
            val units = TileGrid.SIZE / zoom.toDouble()
            return Bounds(x * units, y * units, (x + 1) * units, (y + 1) * units)
        }

    /** The same tile at another version. */
    fun at(version: Int): TileKey = copy(version = version)
}

/**
 * Rendered tiles, all [TileGrid.SIZE] pixels square (edge tiles leave the rest unused), within a
 * byte budget. Evicted bitmaps are kept for reuse by [obtain].
 *
 * Pages have versions that grow with each edit. A tile of an older version is *stale*: it can
 * stand in for a missing current one (without the latest edits) until that is rendered.
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

    /** Stores [bitmap], which replaces older versions of the same tile. */
    fun put(
        key: TileKey,
        bitmap: Bitmap,
    ) {
        for (version in key.version - 1 downTo maxOf(0, key.version - MAX_STALE_VERSIONS)) {
            tiles.take(key.at(version))?.let(::recycle)
        }
        tiles.put(key, bitmap)
    }

    /** The tile of [key], or else its newest older version (stale), with the key found. */
    fun newest(key: TileKey): Pair<TileKey, Bitmap>? {
        for (version in key.version downTo maxOf(0, key.version - MAX_STALE_VERSIONS)) {
            val found = key.at(version)
            tiles[found]?.let { return found to it }
        }
        return null
    }

    /** Calls [block] for each cached tile of [page], at any zoom and version. */
    fun forEachOf(
        page: Int,
        block: (TileKey, Bitmap) -> Unit,
    ) = tiles.forEach({ it.page == page }, block)

    /**
     * After [page] changed from [version] to [version] + 1 within [changed] (page units; null:
     * nowhere):
     * its tiles away from the change are still right and move to the new version; those
     * touching it become stale if [keep] (e.g. on screen, to be drawn until re-rendered) and
     * are dropped otherwise.
     */
    fun pageChanged(
        page: Int,
        version: Int,
        changed: Bounds?,
        keep: (TileKey) -> Boolean,
    ) {
        for (key in tiles.keys { it.page == page && it.version == version }) {
            val touched = changed != null && key.bounds.expandedBy(1.0 / key.zoom).intersects(changed)
            when {
                !touched -> tiles.take(key)?.let { tiles.put(key.at(version + 1), it) }
                !keep(key) -> tiles.take(key)?.let(::recycle)
                else -> Unit
            }
        }
    }

    /** Whether a tile of [page] older than [version] is cached within [bounds]. */
    fun hasStale(
        page: Int,
        version: Int,
        bounds: Bounds,
    ): Boolean = tiles.keys { it.page == page && it.version < version && it.bounds.intersects(bounds) }.isNotEmpty()

    /** Drops the tiles of [page]. */
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

    companion object {
        private const val POOL_SIZE = 8

        /** How many versions back a stale tile is looked for. */
        const val MAX_STALE_VERSIONS = 16
    }
}

/** A page's thumbnail as it was at [version]. */
data class ThumbnailKey(
    val page: Int,
    val version: Int,
)

/**
 * Whole pages at [WIDTH] pixels wide, drawn while tiles at the current zoom are missing. An
 * older version stands in until the current one is rendered.
 */
class Thumbnails(
    maxBytes: Long,
) {
    private val pages = BitmapLru<ThumbnailKey>(maxBytes)

    val maxBytes: Long get() = pages.maxBytes
    val sizeBytes: Long get() = pages.sizeBytes

    operator fun get(key: ThumbnailKey): Bitmap? = pages[key]

    operator fun contains(key: ThumbnailKey): Boolean = key in pages

    /** The thumbnail of [key], or else its newest older version. */
    fun newest(key: ThumbnailKey): Bitmap? =
        (key.version downTo maxOf(0, key.version - TileCache.MAX_STALE_VERSIONS))
            .firstNotNullOfOrNull { pages[key.copy(version = it)] }

    /** Stores [bitmap], which replaces older versions of the page's thumbnail. */
    fun put(
        key: ThumbnailKey,
        bitmap: Bitmap,
    ) {
        pages.removeIf { it.page == key.page && it.version < key.version }
        pages.put(key, bitmap)
    }

    fun invalidate(page: Int) = pages.removeIf { it.page == page }

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
