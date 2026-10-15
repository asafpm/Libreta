// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.InterruptedIOException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class PdfMergeTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A page of [box], rotated by [rotation], with [text] at ([x], [y]) in its own (unrotated) coordinates. */
    private class Spec(
        val box: PDRectangle,
        val text: String,
        val x: Float,
        val y: Float,
        val rotation: Int = 0,
        val crop: PDRectangle? = null,
    )

    private fun pdf(
        name: String,
        vararg pages: Spec,
    ): File {
        PdfPages.init(context)
        val file = temporary.newFile(name)
        PDDocument().use { document ->
            for (spec in pages) {
                val page = PDPage(spec.box).apply { rotation = spec.rotation }
                spec.crop?.let { page.cropBox = it }
                document.addPage(page)
                PDPageContentStream(document, page).use {
                    it.beginText()
                    it.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                    it.newLineAtOffset(spec.x, spec.y)
                    it.showText(spec.text)
                    it.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private val letter = PDRectangle(0f, 0f, 612f, 792f)

    /**
     * The text in [area] (points, from the top left as shown) of [page] of [file], without
     * spaces or line breaks (text turned with the page comes letter by letter).
     */
    private fun textIn(
        file: File,
        page: Int,
        area: RectF,
    ): String =
        PDDocument.load(file).use { document ->
            PDFTextStripperByArea().run {
                addRegion("area", area)
                extractRegions(document.getPage(page))
                getTextForRegion("area").filterNot(Char::isWhitespace)
            }
        }

    @Test
    fun originalPagesKeepTheirContentWithTheInkOnTop() {
        val original =
            pdf(
                "original.pdf",
                // Text near the top left of each page as shown.
                Spec(letter, "ORIGINAL0", 20f, 760f),
                Spec(letter, "ORIGINAL1", 70f, 580f, crop = PDRectangle(50f, 100f, 400f, 500f)),
                Spec(letter, "ORIGINAL2", 20f, 20f, rotation = 90),
            )
        // The ink, drawn at whole-point page sizes by Android's PdfDocument.
        val overlay =
            pdf(
                "overlay.pdf",
                Spec(PDRectangle(0f, 0f, 612f, 792f), "INK0", 20f, 700f),
                Spec(PDRectangle(0f, 0f, 400f, 500f), "INK1", 20f, 440f),
                Spec(PDRectangle(0f, 0f, 792f, 612f), "INK2", 20f, 550f),
                Spec(PDRectangle(0f, 0f, 596f, 842f), "INK3", 20f, 780f),
            )
        val pages =
            listOf(
                ExportPage(612.0, 792.0, original = 0),
                ExportPage(400.0, 500.0, original = 1),
                ExportPage(792.0, 612.0, original = 2),
                // Not on the PDF, and not a whole number of points.
                ExportPage(595.28, 841.89, original = null),
            )
        val output = temporary.newFile("output.pdf")
        val done = ArrayList<Int>()
        output.outputStream().use {
            PdfMerge.merge(context, overlay, original, pages, it, PdfInfo("Lecture", "Libreta test"), done::add)
        }
        assertEquals(listOf(0, 1, 2, 3), done)

        PDDocument.load(output).use { document ->
            assertEquals(4, document.numberOfPages)
            for ((i, page) in pages.withIndex()) {
                val box = document.getPage(i).mediaBox
                assertEquals(page.width, box.width.toDouble(), 0.01)
                assertEquals(page.height, box.height.toDouble(), 0.01)
            }
            assertEquals("Lecture", document.documentInformation.title)
            assertEquals("Libreta test", document.documentInformation.creator)
            val text = PDFTextStripper().getText(document).filterNot(Char::isWhitespace)
            for (word in listOf("ORIGINAL0", "ORIGINAL1", "ORIGINAL2", "INK0", "INK1", "INK2", "INK3")) {
                assertTrue("$word in $text", word in text)
            }
        }
        // Everything top-left aligned, as the note shows it.
        val topLeft = RectF(0f, 0f, 250f, 120f)
        assertTrue(textIn(output, 0, topLeft).contains("ORIGINAL0"))
        assertTrue(textIn(output, 0, RectF(0f, 60f, 250f, 120f)).contains("INK0"))
        assertTrue(textIn(output, 1, topLeft).contains("ORIGINAL1"))
        assertTrue(textIn(output, 1, topLeft).contains("INK1"))
        assertTrue(textIn(output, 2, RectF(0f, 0f, 120f, 250f)).contains("ORIGINAL2"))
        assertTrue(textIn(output, 2, topLeft).contains("INK2"))
        assertTrue(textIn(output, 3, topLeft).contains("INK3"))
        assertFalse(textIn(output, 3, topLeft).contains("ORIGINAL"))
    }

    @Test
    fun aPageOfThePdfShownTwiceIsStoredOnce() {
        val original = pdf("original.pdf", Spec(letter, "ORIGINAL0", 20f, 760f))
        val overlay = pdf("overlay.pdf", Spec(letter, "A", 20f, 700f), Spec(letter, "B", 20f, 700f))
        val pages = listOf(ExportPage(612.0, 792.0, 0), ExportPage(612.0, 792.0, 0))
        val once = temporary.newFile("once.pdf")
        once.outputStream().use { PdfMerge.merge(context, overlay, original, pages.take(1), it, PdfInfo("x", "y")) }
        val twice = temporary.newFile("twice.pdf")
        twice.outputStream().use { PdfMerge.merge(context, overlay, original, pages, it, PdfInfo("x", "y")) }
        // Only the second page's ink is added.
        assertTrue("${once.length()} then ${twice.length()}", twice.length() - once.length() < once.length() / 2)
    }

    @Test
    fun mergingStopsWhenCancelled() {
        val overlay = pdf("overlay.pdf", Spec(letter, "A", 20f, 700f), Spec(letter, "B", 20f, 700f))
        val pages = listOf(ExportPage(612.0, 792.0, null), ExportPage(612.0, 792.0, null))
        assertFailsWith<InterruptedIOException> {
            temporary.newFile("output.pdf").outputStream().use {
                PdfMerge.merge(context, overlay, null, pages, it, PdfInfo("x", "y"), cancelled = { true })
            }
        }
    }

    private companion object {
        const val FONT_SIZE = 12f
    }
}
