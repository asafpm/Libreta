// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.HighlighterSettings
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.StrokeTool
import java.io.ByteArrayOutputStream
import kotlin.math.sin

/**
 * The handwritten samples in testdata/, made up by [Handwriting] so they hold nobody's notes:
 * - libreta-sample.xopp: eight Letter pages of lecture notes, with equations, figures and a
 *   highlighter, half of them written with pressure: a long, dense document;
 * - libreta-notes.xopp: one A4 landscape page with pressure, text, a figure and a LaTeX formula;
 * - libreta-pdf.xopp: notes on the two pages of Xournal++'s test PDF
 *   (packaged_xopp/pdfBackground/old.xopp.bg.pdf), attached as libreta-pdf.xopp.bg.pdf.
 * Written again when screenshots are recorded; otherwise they must match.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WrittenSamplesTest {
    @Test
    fun theSamplesAreWhatTheyWrite() {
        val recording = System.getProperty("libreta.recordScreenshots").toBoolean()
        for ((path, document) in listOf(SAMPLE to sample(), NOTES to notes(), PDF to pdf())) {
            val saved = XoppReader.read(XoppWriter.toByteString(document)).document
            if (recording) {
                TestDocuments
                    .file(path)
                    .sink()
                    .buffer()
                    .use { XoppWriter.write(saved, it) }
            }
            assertEquals(path, saved, TestDocuments.load(path))
        }
        val pdf = TestDocuments.file(PDF_SOURCE)
        if (recording) pdf.copyTo(TestDocuments.file("$PDF.bg.pdf"), overwrite = true)
        assertArrayEquals(pdf.readBytes(), TestDocuments.file("$PDF.bg.pdf").readBytes())
    }

    private fun sample(): Document {
        val pages =
            List(SAMPLE_PAGES) { index ->
                val elements = ArrayList<Element>()
                val pen = Handwriting(index, INK[index % INK.size], PenSize.MEDIUM.width, pressure = index % 2 == 1)
                val heading = Handwriting(SAMPLE_PAGES + index, RED, PenSize.THICK.width, pressure = false)
                elements += heading.line(MARGIN, MARGIN, LETTER_WIDTH / 2, xHeight = 9.0)
                elements += heading.straight(MARGIN, MARGIN + 6, LETTER_WIDTH / 2, MARGIN + 7)
                // A figure on every other page, the lines beside it shorter.
                val figure = if (index % 2 == 0) Bounds(340.0, 250.0, 540.0, 383.0) else null
                var baseline = MARGIN + 2 * LINE
                var line = 0
                while (baseline < LETTER_HEIGHT - MARGIN) {
                    val beside = figure != null && baseline > figure.top - LINE && baseline < figure.bottom + LINE
                    val right = if (beside) figure.left - 16 else LETTER_WIDTH - MARGIN
                    elements +=
                        if (line % EQUATION_EVERY == 3) equation(pen, baseline) else pen.line(MARGIN, baseline, right)
                    if (index % 3 == 0 && line == 6) elements += highlight(MARGIN - 4, baseline - 4, right)
                    baseline += LINE
                    line++
                }
                figure?.let { elements += Element.Image(it, figure(index)) }
                val style = if (index == 2) PageStyle.GRAPH else PageStyle.PLAIN
                Page(LETTER_WIDTH, LETTER_HEIGHT, Background.Solid(Color.WHITE, style), listOf(Layer(elements)))
            }
        return Document(pages, creator = "Libreta sample", fileVersion = 4)
    }

    /** Words, an equals sign, and a fraction. */
    private fun equation(
        pen: Handwriting,
        baseline: Double,
    ): List<Element.Stroke> {
        val left = MARGIN + 60
        return pen.line(left, baseline, left + 90) +
            pen.straight(left + 100, baseline - 5, left + 112, baseline - 5) +
            pen.straight(left + 100, baseline - 2, left + 112, baseline - 2) +
            pen.line(left + 124, baseline - 8, left + 200) +
            pen.straight(left + 120, baseline - 3, left + 204, baseline - 3) +
            pen.line(left + 130, baseline + 9, left + 190)
    }

    private fun highlight(
        left: Double,
        y: Double,
        right: Double,
    ): Element.Stroke {
        val highlighter = HighlighterSettings()
        return Handwriting(0, highlighter.color, highlighter.width, pressure = false)
            .straight(left, y, right, y)
            .copy(tool = StrokeTool.HIGHLIGHTER)
    }

    /** The handwriting and the formula on the bottom layer; the text and the figure, with arrows on it, above. */
    private fun notes(): Document {
        val ink = ArrayList<Element>()
        val pen = Handwriting(100, BLUE, PenSize.MEDIUM.width, pressure = true)
        for (line in 0 until 16) {
            val baseline = NOTES_TOP + line * LINE
            ink += if (line % 5 == 2) equation(pen, baseline) else pen.line(50.0, baseline, 520.0)
        }
        ink += highlight(46.0, NOTES_TOP + 4 * LINE - 4, 400.0)
        val red = Handwriting(101, RED, PenSize.THICK.width, pressure = true)
        ink += red.ellipse(40.0, NOTES_TOP + 8 * LINE - 14, 200.0, NOTES_TOP + 8 * LINE + 6)
        ink += formula()
        val extras = ArrayList<Element>()
        extras += Element.Text("Waves on a string", "Sans Bold", 20.0, 50.0, 30.0, Color.BLACK)
        extras += Element.Image(Bounds(560.0, 90.0, 790.0, 243.0), figure(100))
        extras += red.straight(600.0, 230.0, 660.0, 180.0)
        extras += red.straight(660.0, 180.0, 648.0, 182.0)
        extras += red.straight(660.0, 180.0, 657.0, 192.0)
        extras +=
            Element.Text("Exercise: what changes\nwhen the string is twice as long?", "Serif", 12.0, 560.0, 420.0, RED)
        val page =
            Page(
                A4_HEIGHT,
                A4_WIDTH,
                Background.Solid(Color.WHITE, PageStyle.LINED),
                listOf(Layer(ink, name = "Ink"), Layer(extras, name = "Figures")),
            )
        return Document(listOf(page), creator = "Libreta notes sample", fileVersion = 4)
    }

    private fun pdf(): Document {
        val red = Handwriting(200, RED, PenSize.MEDIUM.width, pressure = true)
        // Xournal++'s test PDF has one line on each page, at the top: "Test PDF for Xournal++", "Page 2".
        val first =
            listOf(highlight(56.0, 65.0, 182.0), red.ellipse(120.0, 52.0, 188.0, 78.0)) +
                red.straight(190.0, 66.0, 260.0, 110.0) +
                (0 until 6).flatMap { red.line(240.0, 124.0 + it * LINE, 540.0) }
        val blue = Handwriting(201, BLUE, PenSize.MEDIUM.width, pressure = true)
        val second =
            blue.line(56.0, 110.0, 300.0) + blue.straight(56.0, 74.0, 96.0, 74.0) +
                (0 until 10).flatMap { blue.line(56.0, 160.0 + it * LINE, 540.0) }
        return Document(
            listOf(
                Page(LETTER_WIDTH, LETTER_HEIGHT, Background.Pdf(1, FileDomain.ATTACH, "bg.pdf"), listOf(Layer(first))),
                Page(LETTER_WIDTH, LETTER_HEIGHT, Background.Pdf(2), listOf(Layer(second))),
            ),
            creator = "Libreta PDF sample",
            fileVersion = 4,
        )
    }

    /** A PNG figure, 300 × 200 pixels: axes, a curve and some points, different for each [seed]. */
    private fun figure(seed: Int): ByteString {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val axes = Paint().apply { strokeWidth = 2f }
        canvas.drawLine(20f, 180f, 290f, 180f, axes)
        canvas.drawLine(20f, 180f, 20f, 10f, axes)
        val curve =
            Paint().apply {
                color = android.graphics.Color.rgb(51, 51, 204)
                strokeWidth = 3f
            }
        var previous = 20f to 95f
        for (x in 21..290) {
            val y = (95 - 70 * sin((x - 20) / (25.0 + 5 * seed % 20))).toFloat()
            canvas.drawLine(previous.first, previous.second, x.toFloat(), y, curve)
            previous = x.toFloat() to y
        }
        val dots = Paint().apply { color = android.graphics.Color.rgb(204, 0, 0) }
        for (i in 0 until 8) canvas.drawCircle(40f + i * 32, 95f - 60 * sin(i + seed.toDouble()).toFloat(), 4f, dots)
        return ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
            .toByteString()
    }

    /** "E = mc²" as a LaTeX image holds it: a PDF of the formula alone, in Times. */
    private fun formula(): Element.TexImage {
        PdfPages.init(ApplicationProvider.getApplicationContext())
        val pdf =
            PDDocument().use { document ->
                val page = PDPage(PDRectangle(0f, 0f, 70f, 16f))
                document.addPage(page)
                PDPageContentStream(document, page).use { text ->
                    text.beginText()
                    text.setFont(PDType1Font.TIMES_ITALIC, 12f)
                    text.newLineAtOffset(2f, 4f)
                    text.showText("E = mc")
                    text.setFont(PDType1Font.TIMES_ROMAN, 8f)
                    text.newLineAtOffset(36f, 5f)
                    text.showText("2")
                    text.endText()
                }
                // A fixed file ID: otherwise each save makes a new one.
                val id = COSString(ByteArray(16))
                document.document.trailer.setItem(
                    COSName.ID,
                    COSArray().apply {
                        add(id)
                        add(id)
                    },
                )
                ByteArrayOutputStream().also(document::save).toByteArray()
            }
        return Element.TexImage("E = mc^2", Bounds(300.0, 500.0, 405.0, 524.0), pdf.toByteString())
    }

    private companion object {
        const val SAMPLE = "libreta-sample.xopp"
        const val NOTES = "libreta-notes.xopp"
        const val PDF = "libreta-pdf.xopp"
        const val PDF_SOURCE = "xournalpp/packaged_xopp/pdfBackground/old.xopp.bg.pdf"
        const val SAMPLE_PAGES = 8
        const val LETTER_WIDTH = 612.0
        const val LETTER_HEIGHT = 792.0
        const val A4_WIDTH = 595.28
        const val A4_HEIGHT = 841.89
        const val MARGIN = 60.0
        const val LINE = 24.0
        const val EQUATION_EVERY = 6

        /** The first ruled line of a lined page. */
        const val NOTES_TOP = 104.0
        val RED = Color.of(204, 0, 0)
        val BLUE = Color.of(51, 51, 204)
        val INK = listOf(Color.BLACK, BLUE)
    }
}
