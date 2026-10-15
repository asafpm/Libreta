// SPDX-License-Identifier: GPL-2.0-or-later
// Pages are exported as Xournal++ v1.3.8 exports them (src/core/pdf/base/XojCairoPdfExport.cpp
// exportPage): the page's size; a PDF background is the original PDF page, kept as vectors,
// drawn from the top left at its own size (docs/rendering.md §2.3); the layers on top. The
// title and creator are set as there. © Xournal++ contributors.

package pm.asaf.libreta.android.render

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream

/** A page of an exported PDF: its size in points, and the page of the original PDF under it (from 0), if any. */
data class ExportPage(
    val width: Double,
    val height: Double,
    val original: Int?,
)

/** An exported PDF's title and creator. */
data class PdfInfo(
    val title: String,
    val creator: String,
)

/** Puts the ink of an export over the pages of the PDF it annotates. */
object PdfMerge {
    /**
     * Writes [pages] to [output]: each the page of [overlay] in the same position (the ink, or
     * the whole page when it has no PDF background) over its [ExportPage.original] page of
     * [original], both as vectors and from the top left. [onPage] hears of each page done;
     * when [cancelled], it stops with an [InterruptedIOException].
     */
    @Suppress("LongParameterList") // what goes in, what comes out, and progress
    @Throws(IOException::class)
    fun merge(
        context: Context,
        overlay: File,
        original: File?,
        pages: List<ExportPage>,
        output: OutputStream,
        info: PdfInfo,
        onPage: (Int) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ) {
        PdfPages.init(context)
        // Large PDFs are buffered in temporary files rather than in memory.
        val memory = MemoryUsageSetting.setupTempFileOnly()
        PDDocument.load(overlay, memory).use { ink ->
            val source = original?.let { PDDocument.load(it, memory) }
            try {
                PDDocument(memory).use { merged ->
                    val layers = LayerUtility(merged)
                    // A PDF page shown on several pages is stored once.
                    val originals = HashMap<Int, PDFormXObject>()
                    pages.forEachIndexed { i, page ->
                        if (cancelled()) throw InterruptedIOException("Export cancelled")
                        val added = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                        merged.addPage(added)
                        PDPageContentStream(merged, added).use { content ->
                            val index = page.original?.takeIf { source != null && it in 0 until source.numberOfPages }
                            if (source != null && index != null) {
                                val form = originals.getOrPut(index) { layers.importPageAsForm(source, index) }
                                content.placeTopLeft(form, page.height)
                            }
                            content.placeTopLeft(layers.importPageAsForm(ink, i), page.height)
                        }
                        onPage(i)
                    }
                    merged.documentInformation =
                        PDDocumentInformation().apply {
                            title = info.title
                            creator = info.creator
                        }
                    merged.save(output)
                }
            } finally {
                source?.close()
            }
        }
    }

    /** Draws [form] with the top left of what it shows at the top left of a page [pageHeight] high. */
    private fun PDPageContentStream.placeTopLeft(
        form: PDFormXObject,
        pageHeight: Double,
    ) {
        // Where the form's box ends up, through its matrix (crop box, rotation).
        val box = form.bBox
        val matrix = form.matrix
        val corners =
            listOf(
                box.lowerLeftX to box.lowerLeftY,
                box.upperRightX to box.lowerLeftY,
                box.lowerLeftX to box.upperRightY,
                box.upperRightX to box.upperRightY,
            ).map { (x, y) -> matrix.transformPoint(x, y) }
        val left = corners.minOf { it.x }
        val top = corners.maxOf { it.y }
        saveGraphicsState()
        transform(Matrix.getTranslateInstance(-left, pageHeight.toFloat() - top))
        drawForm(form)
        restoreGraphicsState()
    }
}
