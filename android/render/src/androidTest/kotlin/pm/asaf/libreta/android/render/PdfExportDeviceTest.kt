// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Document
import java.io.File

/**
 * Exports sample notes as PDF on a device (Android's PdfDocument draws the ink; PdfBox puts it
 * over the original PDF). The PDFs are saved with TestStorage, for scripts/pdf-export-check.sh
 * to compare with Xournal++'s exports; pdf-export-report.txt has the times.
 */
@RunWith(AndroidJUnit4::class)
class PdfExportDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun asset(path: String): ByteArray = assets.open(path).use { it.readBytes() }

    private fun document(path: String): Document = XoppReader.read(asset(path).toByteString()).document

    /** Exports [document] (annotating the PDF asset [pdf]) to a local file; returns it and the milliseconds taken. */
    private fun export(
        document: Document,
        name: String,
        pdf: String? = null,
    ): Pair<File, Long> {
        val original = pdf?.let { File(context.cacheDir, "original-$name.pdf").apply { writeBytes(asset(it)) } }
        val source = original?.let(::PdfBackgroundSource)
        val renderer =
            PageRenderer(context, source ?: BackgroundSource.None, AndroidPdfRasterizer(File(context.cacheDir, "tex")))
        val output = File(context.cacheDir, "$name.pdf")
        val start = SystemClock.elapsedRealtime()
        output.outputStream().use {
            PdfExport.write(context, document.pages, renderer, original, it, PdfInfo(name, "Libreta test"))
        }
        val took = SystemClock.elapsedRealtime() - start
        source?.close()
        return output to took
    }

    /** Saves [file] for the host, as [name]. */
    private fun keep(
        file: File,
        name: String,
    ) = TestStorage().openOutputFile(name).use { out -> file.inputStream().use { it.copyTo(out) } }

    /** [file]'s pages: how many, each one's size, and page [index] drawn at [zoom]. */
    private fun pages(
        file: File,
        index: Int,
        zoom: Float,
    ): Triple<Int, List<Pair<Int, Int>>, Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val sizes = (0 until renderer.pageCount).map { i -> renderer.openPage(i).use { it.width to it.height } }
                val bitmap =
                    renderer.openPage(index).use { page ->
                        Bitmap
                            .createBitmap(
                                (page.width * zoom).toInt(),
                                (page.height * zoom).toInt(),
                                Bitmap.Config.ARGB_8888,
                            ).apply {
                                eraseColor(Color.WHITE)
                                page.render(
                                    this,
                                    null,
                                    Matrix().apply { setScale(zoom, zoom) },
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                            }
                    }
                Triple(renderer.pageCount, sizes, bitmap)
            }
        }

    private fun darkPixels(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) + Color.green(it) + Color.blue(it) < MID_GREY }
    }

    @Test
    fun samplesExportAsTheyAreShown() {
        val report = StringBuilder()
        val samples =
            listOf(
                Triple("pdf_annotation.xopp", "pdf_annotation", "pdf_annotation.xopp.bg.pdf"),
                Triple("note-taking.xopp", "note-taking", null),
                Triple("physics.xopp", "physics", null),
                Triple("libreta-edits.xopp", "libreta-edits", null),
                Triple("libreta-selection.xopp", "libreta-selection", null),
            )
        for ((path, name, pdf) in samples) {
            val document = document(path)
            val (file, took) = export(document, name, pdf)
            val (count, sizes, first) = pages(file, 0, ZOOM)
            assertEquals(name, document.pages.size, count)
            // PdfRenderer gives whole points; the page sizes are kept.
            document.pages.zip(sizes).forEach { (page, size) ->
                assertEquals(name, page.width, size.first.toDouble(), 1.0)
                assertEquals(name, page.height, size.second.toDouble(), 1.0)
            }
            keep(file, "export-$name.pdf")
            TestStorage()
                .openOutputFile(
                    "export-$name-p1.png",
                ).use { first.compress(Bitmap.CompressFormat.PNG, 100, it) }
            report.appendLine("$name: ${document.pages.size} pages, ${file.length() / 1024} KB, $took ms")
        }
        // The PDF's own content is in the export, not only the ink.
        val note = document("pdf_annotation.xopp")
        val (withPdf, _) = export(note, "with-pdf", "pdf_annotation.xopp.bg.pdf")
        val inkOnly = PageRenderer(context).renderToBitmap(note.pages[0].copy(background = null), ZOOM)
        assertTrue(darkPixels(pages(withPdf, 0, ZOOM).third) > darkPixels(inkOnly) + MIN_PDF_PIXELS)

        // The 104-page note.
        val physics = document("physics.xopp")
        val large = physics.copy(pages = List(REPEATS) { physics.pages }.flatten())
        val (file, took) = export(large, "large")
        report.appendLine("104-page note: ${file.length() / 1024} KB, $took ms (${took / large.pages.size} ms a page)")
        Log.i(TAG, report.toString())
        TestStorage().openOutputFile("pdf-export-report.txt").use { it.write(report.toString().toByteArray()) }
    }

    private companion object {
        const val TAG = "PdfExportDeviceTest"
        const val ZOOM = 1.5f
        const val MID_GREY = 384
        const val MIN_PDF_PIXELS = 1000
        const val REPEATS = 13
    }
}
