// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/pdf/base/XojCairoPdfExport.cpp (exportPage): pages on the
// PDF keep the original page, with the layers drawn over it; other pages are drawn whole.
// © Xournal++ contributors.

package pm.asaf.libreta.android.render

import android.content.Context
import android.graphics.pdf.PdfDocument
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Page
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import kotlin.math.ceil

/** Writes documents as PDF files, keeping the PDF they annotate as it is, as vectors. */
object PdfExport {
    /**
     * Writes [pages] (as shown: hidden layers left out) to [output]. [renderer] draws them,
     * with the document's background files; [original] is the PDF the document annotates (a
     * local copy), if it was found. [progress] hears how much is done, from 0 to 1; when
     * [cancelled], it stops with an [InterruptedIOException]. Blocking.
     */
    @Suppress("LongParameterList") // what is drawn, with what, where to, and progress
    @Throws(IOException::class)
    fun write(
        context: Context,
        pages: List<Page>,
        renderer: PageRenderer,
        original: File?,
        output: OutputStream,
        info: PdfInfo,
        progress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ) {
        val steps = 2f * pages.size
        val overlay = File.createTempFile("export", ".pdf", context.cacheDir)
        try {
            drawOverlay(pages, renderer, original != null, overlay, { progress((it + 1) / steps) }, cancelled)
            val exported =
                pages.map { page ->
                    val pdf = (page.background as? Background.Pdf)?.takeIf { original != null }
                    ExportPage(page.width, page.height, pdf?.pageNumber?.let { (it - 1).toInt() })
                }
            PdfMerge.merge(context, overlay, original, exported, output, info, {
                progress((pages.size + it + 1) / steps)
            }, cancelled)
        } finally {
            overlay.delete()
        }
    }

    /**
     * One page per page in [file], the size of the page rounded up to whole points: the
     * layers alone where the page shows the PDF ([onPdf]), the whole page otherwise.
     */
    @Suppress("LongParameterList") // what is drawn, with what, where to, and progress
    private fun drawOverlay(
        pages: List<Page>,
        renderer: PageRenderer,
        onPdf: Boolean,
        file: File,
        onPage: (Int) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { i, page ->
                if (cancelled()) throw InterruptedIOException("Export cancelled")
                val info =
                    PdfDocument.PageInfo
                        .Builder(
                            ceil(page.width).toInt(),
                            ceil(page.height).toInt(),
                            i + 1,
                        ).create()
                val drawn = document.startPage(info)
                if (onPdf && page.background is Background.Pdf) {
                    renderer.renderElements(page.layers.flatMap { it.elements }, drawn.canvas, 1f)
                } else {
                    renderer.render(page, drawn.canvas, 1f)
                }
                document.finishPage(drawn)
                onPage(i)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
