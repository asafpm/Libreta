// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

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
import pm.asaf.inkpad.core.geometry.Tile
import pm.asaf.inkpad.core.geometry.TileGrid
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Page
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
        val key: TileKey get() = TileKey(page, zoom, tile.x, tile.y)
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
 * [request]ed, calling [onRendered] on the results thread after each one is stored.
 *
 * Each worker has its own [PageRenderer] from [newRenderer] (renderers are not thread-safe),
 * so the [BackgroundSource] they share must be.
 */
class TileRenderer(
    private val pages: List<Page>,
    private val caches: RenderCaches,
    newRenderer: () -> PageRenderer,
    private val onRendered: () -> Unit,
    private val threads: RenderThreads = RenderThreads(),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + threads.results)
    private val wakeups = Channel<Unit>(threads.workers)
    private val inFlight = mutableSetOf<RenderJob>()
    private var queue = ArrayDeque<RenderJob>()

    init {
        repeat(threads.workers) {
            scope.launch(threads.render) { work(newRenderer()) }
        }
    }

    /**
     * Replaces the pending work with [jobs], most important first. Pending jobs not in [jobs]
     * are dropped; jobs already rendered or being rendered are skipped.
     */
    fun request(jobs: List<RenderJob>) {
        val pending = jobs.filterTo(ArrayDeque()) { it.page in pages.indices && !isDone(it) }
        synchronized(this) {
            pending.removeAll(inFlight)
            queue = pending
        }
        repeat(threads.workers) { wakeups.trySend(Unit) }
    }

    /** Stops rendering; results still in progress are discarded. */
    override fun close() = scope.cancel()

    private suspend fun work(renderer: PageRenderer) {
        while (true) {
            val job = next()
            if (job == null) {
                wakeups.receive()
            } else {
                val bitmap = render(renderer, job)
                withContext(threads.results) {
                    store(job, bitmap)
                    synchronized(this@TileRenderer) { inFlight.remove(job) }
                    onRendered()
                }
            }
        }
    }

    @Synchronized
    private fun next(): RenderJob? {
        while (true) {
            val job = queue.removeFirstOrNull() ?: return null
            if (!isDone(job)) return job.also { inFlight.add(it) }
        }
    }

    private fun isDone(job: RenderJob): Boolean =
        when (job) {
            is RenderJob.TileJob -> job.key in caches.tiles
            is RenderJob.ThumbnailJob -> job.page in caches.thumbnails
        }

    private fun render(
        renderer: PageRenderer,
        job: RenderJob,
    ): Bitmap {
        val page = pages[job.page]
        return when (job) {
            is RenderJob.TileJob -> caches.tiles.obtain().also { renderTile(renderer, page, job.zoom, job.tile, it) }
            is RenderJob.ThumbnailJob -> renderThumbnail(renderer, page)
        }
    }

    private fun store(
        job: RenderJob,
        bitmap: Bitmap,
    ) {
        when (job) {
            is RenderJob.TileJob -> caches.tiles.put(job.key, bitmap)
            is RenderJob.ThumbnailJob -> caches.thumbnails.put(job.page, bitmap)
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
