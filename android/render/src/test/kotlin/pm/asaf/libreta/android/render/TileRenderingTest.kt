// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.core.geometry.TileGrid
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.math.abs
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TileRenderingTest {
    private fun newRenderer() = PageRenderer(ApplicationProvider.getApplicationContext())

    private fun Page.allTiles(zoom: Float) =
        TileGrid.tilesCovering(width, height, zoom.toDouble(), Bounds(0.0, 0.0, width, height))

    /** Renders [page] tile by tile and pastes the tiles together. */
    private fun assembled(
        page: Page,
        zoom: Float,
    ): Bitmap {
        val renderer = newRenderer()
        val whole =
            Bitmap.createBitmap(
                ceil(page.width * zoom).toInt(),
                ceil(page.height * zoom).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(whole)
        for (tile in page.allTiles(zoom)) {
            val bitmap = Bitmap.createBitmap(TileGrid.SIZE, TileGrid.SIZE, Bitmap.Config.ARGB_8888)
            TileRenderer.renderTile(renderer, page, zoom, tile, bitmap)
            canvas.drawBitmap(bitmap, tile.x * TileGrid.SIZE.toFloat(), tile.y * TileGrid.SIZE.toFloat(), null)
        }
        return whole
    }

    /** The fraction of pixels where a colour channel differs by more than [tolerance]. */
    private fun differingFraction(
        a: Bitmap,
        b: Bitmap,
        tolerance: Int,
    ): Double {
        assertEquals(a.width to a.height, b.width to b.height)
        val pixelsA = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pixelsB = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        val differing =
            pixelsA.indices.count { i ->
                (0 until 32 step 8).any { shift ->
                    abs((pixelsA[i] ushr shift and 0xff) - (pixelsB[i] ushr shift and 0xff)) >
                        tolerance
                }
            }
        return differing.toDouble() / pixelsA.size
    }

    @Test
    fun tilesAssembleIntoTheWholePage() {
        // Zooms where tile edges fall between page units, on strokes, text and every ruling.
        val cases =
            listOf(
                Triple("physics.xopp", 0, 1.7f),
                Triple("note-taking.xopp", 0, 2.3f),
                Triple("xournalpp/load/pages.xoj", 2, 1.3f),
                Triple("xournalpp/load/pages.xoj", 4, 1.3f),
            )
        for ((path, index, zoom) in cases) {
            val page = TestDocuments.load(path).pages[index]
            // Not bit-exact: Skia's anti-aliasing of a path varies slightly with its extent and
            // clip, and background rulings are one path spanning the clip, so they differ between
            // a tile and the page (physics.xopp at 1.7: 99% of pixels equal, 0.12% off by more
            // than 2, as isolated specks along rulings; no visible seams).
            val fraction =
                differingFraction(newRenderer().renderToBitmap(page, zoom), assembled(page, zoom), tolerance = 2)
            assertTrue("$path page ${index + 1}: $fraction of pixels differ", fraction < 0.002)
        }
    }

    private fun bitmapOfBytes(bytes: Int): Bitmap = Bitmap.createBitmap(bytes / 4, 1, Bitmap.Config.ARGB_8888)

    @Test
    fun lruStaysWithinItsBudget() {
        val evicted = mutableListOf<Bitmap>()
        val lru = BitmapLru<Int>(maxBytes = 4000, onEvict = { evicted += it })
        val bitmaps = List(5) { bitmapOfBytes(1000) }
        for (i in 0 until 4) lru.put(i, bitmaps[i])
        assertEquals(4000L, lru.sizeBytes)
        // Using 0 makes 1 the least recently used.
        assertSame(bitmaps[0], lru[0])
        lru.put(4, bitmaps[4])
        assertEquals(4000L, lru.sizeBytes)
        assertNull(lru[1])
        assertEquals(listOf(bitmaps[1]), evicted)
        // Bigger than the whole budget: nothing stays.
        lru.put(5, bitmapOfBytes(8000))
        assertEquals(0L, lru.sizeBytes)
        assertEquals(0, lru.size)
    }

    @Test
    fun evictedTilesAreReused() {
        val tileBytes = TileGrid.SIZE * TileGrid.SIZE * 4L
        val cache = TileCache(maxBytes = 2 * tileBytes)
        val first = cache.obtain()
        cache.put(TileKey(0, 0, 1f, 0, 0), first)
        cache.put(TileKey(0, 0, 1f, 1, 0), cache.obtain())
        cache.put(TileKey(0, 0, 1f, 2, 0), cache.obtain())
        assertEquals(2 * tileBytes, cache.sizeBytes)
        assertSame(first, cache.obtain())
    }

    private fun TestScope.newTileRenderer(
        pages: List<Page>,
        caches: RenderCaches,
        onRendered: () -> Unit = {},
    ): TileRenderer {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TileRenderer(
            pages,
            caches,
            ::newRenderer,
            onRendered,
            RenderThreads(workers = 2, dispatcher, dispatcher),
        )
    }

    private fun caches() = RenderCaches(TileCache(Long.MAX_VALUE), Thumbnails(Long.MAX_VALUE))

    @Test
    fun rendersRequestedTilesAndThumbnails() =
        runTest {
            val page = TestDocuments.load("physics.xopp").pages[0]
            val caches = caches()
            var notified = 0
            val renderer = newTileRenderer(listOf(page, page), caches) { notified++ }
            val tiles = page.allTiles(1f).map { RenderJob.TileJob(1, 1f, it) }
            renderer.request(tiles + RenderJob.ThumbnailJob(0))
            testScheduler.advanceUntilIdle()
            assertEquals(tiles.size, caches.tiles.size)
            assertTrue(tiles.all { it.key(0) in caches.tiles })
            assertEquals(Thumbnails.WIDTH, caches.thumbnails[ThumbnailKey(0, 0)]?.width)
            assertEquals(tiles.size + 1, notified)
            // Done jobs are not rendered again.
            renderer.request(tiles)
            testScheduler.advanceUntilIdle()
            assertEquals(tiles.size + 1, notified)
            renderer.close()
        }

    @Test
    fun staleRequestsAreDropped() =
        runTest {
            val page = TestDocuments.load("physics.xopp").pages[0]
            val caches = caches()
            val renderer = newTileRenderer(listOf(page), caches)
            val atZoom1 = page.allTiles(1f).map { RenderJob.TileJob(0, 1f, it) }
            val atZoom2 = page.allTiles(2f).map { RenderJob.TileJob(0, 2f, it) }
            renderer.request(atZoom1)
            renderer.request(atZoom2 + RenderJob.TileJob(5, 2f, atZoom2[0].tile)) // no page 6: ignored
            testScheduler.advanceUntilIdle()
            assertEquals(
                atZoom2.map { it.key(0) }.toSet(),
                buildSet { caches.tiles.forEachOf(0) { key, _ -> add(key) } },
            )
            renderer.close()
        }

    @Test
    fun editsKeepTilesAwayFromTheChange() {
        val cache = TileCache(Long.MAX_VALUE)
        // 512 px tiles at zoom 1: tile (0, 0) covers 0-512 pt, tile (1, 0) 512-1024 pt.
        val near = TileKey(0, 0, 1f, 0, 0)
        val far = TileKey(0, 0, 1f, 1, 0)
        val elsewhere = TileKey(0, 0, 2f, 0, 0)
        for (key in listOf(near, far, elsewhere)) cache.put(key, cache.obtain())
        val changed = Bounds(100.0, 100.0, 120.0, 120.0)
        cache.pageChanged(0, version = 0, changed) { it.zoom == 1f }
        // Away from the change: same pixels, now the current version.
        assertTrue(far.at(1) in cache && far !in cache)
        // Touching it, on screen: stale, standing in for the new tile.
        assertEquals(near, cache.newest(near.at(1))?.first)
        assertTrue(cache.hasStale(0, 1, changed))
        // Touching it, not on screen: dropped.
        assertTrue(elsewhere !in cache && elsewhere.at(1) !in cache)
        // The re-rendered tile replaces the stale one.
        cache.put(near.at(1), cache.obtain())
        assertEquals(near.at(1), cache.newest(near.at(1))?.first)
        assertTrue(near !in cache)
        assertTrue(!cache.hasStale(0, 1, changed))
    }

    @Test
    fun anEditedPageReRendersOnlyTheTilesItChanged() =
        runTest {
            val page = TestDocuments.load("physics.xopp").pages[0]
            val caches = caches()
            var rendered = 0
            val renderer = newTileRenderer(listOf(page), caches) { rendered++ }
            val jobs = page.allTiles(1f).map { RenderJob.TileJob(0, 1f, it) }
            renderer.request(jobs)
            testScheduler.advanceUntilIdle()
            assertEquals(jobs.size, rendered)

            val dot = Element.Stroke(StrokeTool.PEN, Color.of(0, 0, 0), 2.0, PointList.of(100.0, 100.0, 101.0, 101.0))
            val version = renderer.updatePage(0, page.withElementAdded(dot), Bounds(98.0, 98.0, 103.0, 103.0)) { true }
            assertEquals(1, version)
            assertEquals(1, renderer.version(0))
            renderer.request(jobs)
            testScheduler.advanceUntilIdle()
            // Only tile (0, 0) touches the dot.
            assertEquals(jobs.size + 1, rendered)
            assertTrue(jobs.all { it.key(1) in caches.tiles })
            assertTrue(!caches.tiles.hasStale(0, 1, Bounds(0.0, 0.0, page.width, page.height)))
            renderer.close()
        }
}
