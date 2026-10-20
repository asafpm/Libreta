// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.geometry.TileGrid
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import java.io.File
import kotlin.math.abs
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Renders PDF content with Android's PdfRenderer on a device. The rendered pages are saved
 * with TestStorage for review (build/outputs/connected_android_test_additional_output).
 */
@RunWith(AndroidJUnit4::class)
class PdfRenderingDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun asset(path: String): ByteArray = assets.open(path).use { it.readBytes() }

    private fun document(path: String): Document = XoppReader.read(asset(path).toByteString()).document

    private fun save(
        bitmap: Bitmap,
        name: String,
    ) {
        TestStorage().openOutputFile(name).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Pixels darker than mid-grey inside [bounds] (page units) at [zoom]. */
    private fun darkPixels(
        bitmap: Bitmap,
        bounds: Bounds,
        zoom: Float,
    ): Int {
        var count = 0
        for (y in (bounds.top * zoom).toInt() until (bounds.bottom * zoom).toInt()) {
            for (x in (bounds.left * zoom).toInt() until (bounds.right * zoom).toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) < MID_GREY) count++
            }
        }
        return count
    }

    @Test
    fun pdfBackground() {
        val pdf =
            File(
                context.cacheDir,
                "pdf_annotation.xopp.bg.pdf",
            ).apply { writeBytes(asset("pdf_annotation.xopp.bg.pdf")) }
        PdfBackgroundSource(pdf).use { source ->
            val page = document("pdf_annotation.xopp").pages[0]
            val bitmap = PageRenderer(context, source).renderToBitmap(page, ZOOM)
            save(bitmap, "pdf_annotation_p1.png")
            // The PDF's own content (not just the annotations) is drawn: compare with a render
            // without the PDF, which shows only the "missing" placeholder.
            val withoutPdf = PageRenderer(context).renderToBitmap(page, ZOOM)
            val whole = Bounds(0.0, 0.0, page.width, page.height)
            assertTrue(darkPixels(bitmap, whole, ZOOM) > darkPixels(withoutPdf, whole, ZOOM) + MIN_PDF_PIXELS)
        }
    }

    @Test
    fun aPdfWithAPasswordFailsToOpenAsAnIOException() {
        // testdata/password.pdf: pdf_annotation.xopp.bg.pdf locked with qpdf (user password "secret").
        val pdf = File(context.cacheDir, "password.pdf").apply { writeBytes(asset("password.pdf")) }
        try {
            // PdfRenderer throws a SecurityException; callers handle files they can't read as IOExceptions.
            assertFailsWith<java.io.IOException> { PdfBackgroundSource(pdf) }
        } finally {
            pdf.delete()
        }
    }

    /** Dark pixels in the [size]-pixel square at ([left], [top]) of [bitmap], every other one. */
    private fun dark(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        size: Int,
    ): Int =
        (top until top + size step 2).sumOf { y ->
            (left until left + size step 2).count { x ->
                val pixel = bitmap.getPixel(x, y)
                Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) < MID_GREY
            }
        }

    /** Pixels of the [size]-pixel square of [a] at ([left], [top]) that differ from [b]'s, every other one. */
    private fun differing(
        a: Bitmap,
        left: Int,
        top: Int,
        b: Bitmap,
        size: Int,
    ): Int =
        (0 until size step 2).sumOf { y ->
            (0 until size step 2).count { x ->
                val expected = a.getPixel(left + x, top + y)
                val actual = b.getPixel(x, y)
                listOf(Color::red, Color::green, Color::blue).any { abs(it(expected) - it(actual)) > TOLERANCE }
            }
        }

    @Test
    fun aTileAtHighZoomShowsThePdfAsTheWholePageDoes() {
        val zoom = 6f
        val size = TileGrid.SIZE
        val pdf = File(context.cacheDir, "zoomed.pdf").apply { writeBytes(asset("pdf_annotation.xopp.bg.pdf")) }
        try {
            // What the PDF looks like at that zoom, rendered whole by PdfRenderer.
            val whole =
                PdfPageRenderer.open(pdf).use { pages ->
                    val (width, height) = pages.pageSize(0)
                    val bitmap =
                        Bitmap.createBitmap(
                            (width * zoom).toInt(),
                            (height * zoom).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                    bitmap.eraseColor(Color.WHITE)
                    pages.render(0, bitmap, Matrix().apply { setScale(zoom, zoom) })
                    bitmap
                }
            PdfBackgroundSource(pdf).use { source ->
                val (width, height) = checkNotNull(source.pageSize(1))
                val page =
                    Page(width, height, Background.Pdf(1, FileDomain.ABSOLUTE, "x.pdf"), listOf(Layer(emptyList())))
                // The tile with the most of the PDF's content, rendered as the viewer does.
                val tile =
                    TileGrid
                        .tilesCovering(width, height, zoom.toDouble(), Bounds(0.0, 0.0, width, height))
                        .filter { (it.x + 1) * size <= whole.width && (it.y + 1) * size <= whole.height }
                        .maxBy { dark(whole, it.x * size, it.y * size, size) }
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                TileRenderer.renderTile(PageRenderer(context, source), page, zoom, tile, bitmap)
                save(bitmap, "pdf_tile_zoomed.png")
                // Some of the PDF, not a blank part of the page, and as the whole page shows it.
                assertTrue(dark(bitmap, 0, 0, size) > size * size / 4 / 100, "a blank tile")
                val off = differing(whole, tile.x * size, tile.y * size, bitmap, size)
                assertTrue(off < size * size / 4 / 50, "$off pixels differ")
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun closingWhileDrawingIsSafe() {
        val pdf =
            File(context.cacheDir, "closing.pdf").apply { writeBytes(asset("pdf_annotation.xopp.bg.pdf")) }
        val source = PdfBackgroundSource(pdf)
        val canvas = android.graphics.Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        var failure: Throwable? = null
        // A render worker drawing at changing zooms, as tiles do, while the note closes.
        val worker =
            Thread {
                try {
                    var zoom = 0.2f
                    repeat(DRAWS) {
                        source.drawPdfPage(canvas, 1, zoom)
                        zoom += 0.05f
                    }
                } catch (e: Throwable) {
                    failure = e
                }
            }
        worker.start()
        Thread.sleep(CLOSE_AFTER_MS)
        source.close()
        worker.join()
        pdf.delete()
        assertTrue(failure == null, "drawing failed: $failure")
    }

    @Test
    fun latexFormulaStoredAsPdf() {
        val page = document("note-taking.xopp").pages[0]
        val tex =
            page.layers
                .flatMap { it.elements }
                .filterIsInstance<Element.TexImage>()
                .single()
        val rasterizer = AndroidPdfRasterizer(context.cacheDir)
        assertNotNull(rasterizer.render(tex.data, 0, 200, 50))
        val bitmap = PageRenderer(context, pdfRasterizer = rasterizer).renderToBitmap(page, ZOOM)
        save(bitmap, "note_taking_p1.png")
        assertTrue(darkPixels(bitmap, tex.bounds, ZOOM) > MIN_FORMULA_PIXELS, "formula not drawn")
    }

    private companion object {
        const val DRAWS = 40
        const val TOLERANCE = 48
        const val CLOSE_AFTER_MS = 50L
        const val ZOOM = 1.5f
        const val MID_GREY = 3 * 128
        const val MIN_PDF_PIXELS = 1000
        const val MIN_FORMULA_PIXELS = 200
    }
}
