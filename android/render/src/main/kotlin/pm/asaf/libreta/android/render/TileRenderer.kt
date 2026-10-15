// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.core.geometry.Tile
import pm.asaf.libreta.core.geometry.TileGrid
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Page
import java.io.Closeable
import kotlin.math.roundToInt

/** Something to render for a page (0-based). */
sealed interface RenderJob {
    val page: Int

    /** One tile of [page] at [zoom] px per point. */
    data class TileJob(
        override val page: Int,
        val zoom: Float,
        val tile: Tile,
    ) : RenderJob {
        /** Its tile at [version] of the page. */
        fun key(version: Int): TileKey = TileKey(page, version, zoom, tile.x, tile.y)
    }

    /** The whole of [page] at [Thumbnails.WIDTH] pixels wide. */
    data class ThumbnailJob(
        override val page: Int,
    ) : RenderJob
}

/** Where rendering runs, and where its results are stored (the thread that draws). */
data class RenderThreads(
    val workers: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_WORKERS),
    val render: CoroutineDispatcher = Dispatchers.Default,
    val results: CoroutineDispatcher = Dispatchers.Main,
) {
    private companion object {
        const val MAX_WORKERS = 4
    }
}

/**
 * Renders tiles and thumbnails of [pages] into [caches] in the background, in the order last
 * [request]ed, calling [onRendered] on the results thread after each one is stored. Pages can
 * be replaced as they are edited ([updatePage]); each replacement is a new version.
 *
 * Each worker has its own [PageRenderer] from [newRenderer] (renderers are not thread-safe),
 * so the [BackgroundSource] they share must be.
 */
class TileRenderer(
    pages: List<Page>,
    private val caches: RenderCaches,
    newRenderer: () -> PageRenderer,
    private val onRendered: () -> Unit,
    private val threads: RenderThreads = RenderThreads(),
    /** The pages' versions, when [caches] already hold tiles of earlier ones (e.g. after rotation). */
    versions: IntArray = IntArray(pages.size),
) : Closeable {
    /** A job with the page as it was when the job started. */
    private class Work(
        val job: RenderJob,
        val page: Page,
        val version: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + threads.results)
    private val wakeups = Channel<Unit>(threads.workers)
    private val inFlight = mutableSetOf<RenderJob>()
    private var queue = ArrayDeque<RenderJob>()
    private val pages = pages.toMutableList()
    private val versions = versions.copyOf()

    val pageCount: Int get() = pages.size

    init {
        repeat(threads.workers) {
            scope.launch(threads.render) { work(newRenderer()) }
        }
    }

    /** The current version of page [index]; it grows by one with each [updatePage]. */
    @Synchronized
    fun version(index: Int): Int = versions[index]

    @Synchronized
    fun page(index: Int): Page = pages[index]

    /**
     * Replaces page [index], which changed within [changed] (page units; null: it looks the
     * same). Tiles away from the change are kept; those touching it are stale until
     * re-rendered, and kept meanwhile only if [keepStale] (see [TileCache.pageChanged]). Call
     * on the results thread. Returns the new version.
     */
    fun updatePage(
        index: Int,
        page: Page,
        changed: Bounds?,
        keepStale: (TileKey) -> Boolean,
    ): Int {
        val old =
            synchronized(this) {
                pages[index] = page
                versions[index]++
            }
        caches.tiles.pageChanged(index, old, changed, keepStale)
        return old + 1
    }

    /**
     * Replaces the pending work with [jobs], most important first. Pending jobs not in [jobs]
     * are dropped; jobs already rendered or being rendered are skipped.
     */
    fun request(jobs: List<RenderJob>) {
        synchronized(this) {
            val pending = jobs.filterTo(ArrayDeque()) { it.page in pages.indices && !isDone(it) }
            pending.removeAll(inFlight)
            queue = pending
        }
        repeat(threads.workers) { wakeups.trySend(Unit) }
    }

    /** Stops rendering; results still in progress are discarded. */
    override fun close() = scope.cancel()

    private suspend fun work(renderer: PageRenderer) {
        while (true) {
            val work = next()
            if (work == null) {
                wakeups.receive()
            } else {
                val bitmap = render(renderer, work)
                withContext(threads.results) {
                    store(work, bitmap)
                    synchronized(this@TileRenderer) {
                        inFlight.remove(work.job)
                        // Edited meanwhile: what was stored is stale already, render it again.
                        if (!isDone(work.job)) queue.addFirst(work.job)
                    }
                    onRendered()
                }
            }
        }
    }

    @Synchronized
    private fun next(): Work? {
        while (true) {
            val job = queue.removeFirstOrNull() ?: return null
            if (!isDone(job)) {
                inFlight.add(job)
                return Work(job, pages[job.page], versions[job.page])
            }
        }
    }

    /** Whether [job] is rendered at the page's current version. Call while synchronized. */
    private fun isDone(job: RenderJob): Boolean =
        when (job) {
            is RenderJob.TileJob -> job.key(versions[job.page]) in caches.tiles
            is RenderJob.ThumbnailJob -> ThumbnailKey(job.page, versions[job.page]) in caches.thumbnails
        }

    private fun render(
        renderer: PageRenderer,
        work: Work,
    ): Bitmap =
        when (val job = work.job) {
            is RenderJob.TileJob -> {
                caches.tiles.obtain().also {
                    renderTile(
                        renderer,
                        work.page,
                        job.zoom,
                        job.tile,
                        it,
                    )
                }
            }

            is RenderJob.ThumbnailJob -> {
                renderThumbnail(renderer, work.page)
            }
        }

    private fun store(
        work: Work,
        bitmap: Bitmap,
    ) {
        when (val job = work.job) {
            is RenderJob.TileJob -> caches.tiles.put(job.key(work.version), bitmap)
            is RenderJob.ThumbnailJob -> caches.thumbnails.put(ThumbnailKey(job.page, work.version), bitmap)
        }
    }

    companion object {
        /** [page] at [Thumbnails.WIDTH] pixels wide. */
        fun renderThumbnail(
            renderer: PageRenderer,
            page: Page,
        ): Bitmap {
            val zoom = Thumbnails.zoomFor(page.width)
            // Rounded, not rounded up: width × zoom can come out a hair above WIDTH.
            val height = (page.height * zoom).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(Thumbnails.WIDTH, height, Bitmap.Config.ARGB_8888)
            renderer.render(page, Canvas(bitmap), zoom)
            return bitmap
        }

        /**
         * Draws [tile] of [page] at [zoom] into [bitmap] ([TileGrid.SIZE] pixels square), with
         * the same pixels as the matching part of a whole-page render.
         */
        fun renderTile(
            renderer: PageRenderer,
            page: Page,
            zoom: Float,
            tile: Tile,
            bitmap: Bitmap,
        ) {
            val canvas = Canvas(bitmap)
            canvas.translate(-tile.x * TileGrid.SIZE.toFloat(), -tile.y * TileGrid.SIZE.toFloat())
            // The bitmap edge clips; a clip a pixel larger keeps anti-aliased fills from
            // fading out along tile edges inside the page.
            val margin = 1.0 / zoom
            val bounds = tile.bounds
            val clip =
                Bounds(
                    maxOf(bounds.left - margin, 0.0),
                    maxOf(bounds.top - margin, 0.0),
                    minOf(bounds.right + margin, page.width),
                    minOf(bounds.bottom + margin, page.height),
                )
            renderer.render(page, canvas, zoom, clip)
        }
    }
}
