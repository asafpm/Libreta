// SPDX-License-Identifier: GPL-2.0-or-later
// Page sizes as Xournal++ v1.3.8 takes them for a note on a PDF (src/core/model/Document.cpp
// readPdf, through poppler_page_get_size: the crop box, turned by the page's rotation),
// © Xournal++ contributors.

package pm.asaf.libreta.android.render

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.IOException
import java.io.InputStream

/** Reading PDFs with PdfBox. */
object PdfPages {
    /** Sets PdfBox up (its resources); done once. */
    fun init(context: Context) {
        if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context.applicationContext)
    }

    /**
     * The sizes (points, width to height) of [pdf]'s pages as readers show them: the crop box,
     * turned by the page's rotation. Fails with an [IOException] for a PDF that isn't one, or
     * that needs a password.
     */
    @Throws(IOException::class)
    fun sizes(
        context: Context,
        pdf: InputStream,
    ): List<Pair<Double, Double>> {
        init(context)
        // Large PDFs are buffered in a temporary file rather than in memory.
        return PDDocument.load(pdf, MemoryUsageSetting.setupTempFileOnly()).use { document ->
            document.pages.map { page ->
                val box = page.cropBox
                val width = box.width.toDouble()
                val height = box.height.toDouble()
                if (Math.floorMod(page.rotation, HALF_TURN) == QUARTER_TURN) height to width else width to height
            }
        }
    }

    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180
}
