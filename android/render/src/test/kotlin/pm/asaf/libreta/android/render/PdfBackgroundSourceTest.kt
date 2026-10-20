// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.core.geometry.TileGrid
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfBackgroundSourceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val real = PdfPageRenderer.open

    /**
     * Robolectric can't run PdfRenderer: a page of A4 with a red square at (100, 100)–(140, 140)
     * points, drawn through the transform as PdfRenderer does, that fails once closed.
     */
    private class Pages : PdfPageRenderer {
        var closed = false

        /** The most pixels asked for at once. */
        var largest = 0L

        override val pageCount = 1

        override fun pageSize(index: Int) = 595.0 to 842.0

        override fun render(
            index: Int,
            bitmap: Bitmap,
            transform: Matrix,
        ) {
            check(!closed) { "Already closed" }
            largest = maxOf(largest, bitmap.width.toLong() * bitmap.height)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            Canvas(bitmap).apply { concat(transform) }.drawRect(100f, 100f, 140f, 140f, Paint().apply { color = RED })
        }

        override fun close() {
            closed = true
        }
    }

    private lateinit var pages: Pages

    @Before
    fun setUp() {
        pages = Pages()
        PdfPageRenderer.open = { pages }
    }

    @After
    fun tearDown() {
        PdfPageRenderer.open = real
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
    }

    private fun canvas() = Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

    @Test
    fun aTileDeepInAPageNeedsOnlyItsPartOfThePdf() {
        val zoom = 20f
        val page =
            Page(
                595.0,
                842.0,
                Background.Pdf(1, FileDomain.ABSOLUTE, "paper.pdf"),
                listOf(Layer(emptyList())),
            )
        PdfBackgroundSource(temporary.newFile("paper.pdf")).use { source ->
            val renderer = PageRenderer(ApplicationProvider.getApplicationContext(), source)
            // The tile with page point (135, 135): inside the square, near its corner at (140, 140).
            val tile =
                TileGrid
                    .tilesCovering(
                        page.width,
                        page.height,
                        zoom.toDouble(),
                        Bounds(135.0, 135.0, 136.0, 136.0),
                    ).single()
            val bitmap = Bitmap.createBitmap(TileGrid.SIZE, TileGrid.SIZE, Bitmap.Config.ARGB_8888)
            TileRenderer.renderTile(renderer, page, zoom, tile, bitmap)

            fun at(x: Double) = (x * zoom - tile.x * TileGrid.SIZE).toInt()
            assertEquals(RED, bitmap.getPixel(at(135.0), at(135.0)))
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(at(145.0), at(145.0)))
            // Not the whole page at 20 pixels a point (200 megapixels): about the tile.
            assertTrue("${pages.largest} pixels", pages.largest <= 2L * TileGrid.SIZE * TileGrid.SIZE)
        }
    }

    @Test
    fun aClosedPdfDrawsNothing() {
        val source = PdfBackgroundSource(temporary.newFile("paper.pdf"))
        assertTrue(source.drawPdfPage(canvas(), 1, 1f))
        source.close()
        // A render still running when its note closed: nothing, rather than an exception.
        assertFalse(source.drawPdfPage(canvas(), 1, 2f))
    }
}
