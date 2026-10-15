// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import okio.buffer
import okio.sink
import org.junit.Assert.assertEquals
import org.junit.Test
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.LayerOperations
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.model.PageOperations
import pm.asaf.inkpad.core.model.PageStyle
import pm.asaf.inkpad.core.model.PaperSize
import pm.asaf.inkpad.core.model.applying

/**
 * testdata/inkpad-pages.xopp: physics.xopp's first pages rearranged with Inkpad's page and
 * layer commands (inserted, duplicated, moved and deleted pages, new backgrounds, layers
 * added, renamed, moved and merged), for scripts/render-crosscheck.sh (Xournal++ must open it
 * and show the same). Written again when screenshots are recorded; otherwise it must match.
 */
class PagesSampleTest {
    @Test
    fun theSampleIsWhatInkpadPageCommandsMake() {
        val edited = edited()
        val file = TestDocuments.file(PATH)
        if (System.getProperty("inkpad.recordScreenshots").toBoolean()) {
            file.sink().buffer().use { XoppWriter.write(edited, it) }
        }
        assertEquals(edited, TestDocuments.load(PATH))
    }

    private fun edited(): Document {
        val physics = TestDocuments.load("physics.xopp")
        var document = physics.copy(pages = physics.pages.take(3), creator = "Inkpad pages sample", preview = null)

        fun Document.then(edit: Edit?) = applying(checkNotNull(edit))
        // A copy of page 1's look after it, a template page (dotted, landscape Letter) at the end.
        document =
            document.then(PageOperations.insertPage(document, 1, NewPageTemplate().newPage(document.pages[0], 0)))
        val letter =
            NewPageTemplate(
                copyCurrent = false,
                PaperSize.LETTER,
                landscape = true,
                PageStyle.DOTTED,
                Color.of(255, 255, 128),
            )
        document = document.then(PageOperations.insertPage(document, document.pages.size, letter.newPage(null, -1)))
        // Page 3 duplicated, the last page moved first, page 2 deleted.
        document = document.then(PageOperations.duplicatePage(document, 2))
        document = document.then(PageOperations.movePage(document, document.pages.lastIndex, 0))
        document = document.then(PageOperations.deletePage(document, 2))
        // Graph paper in blue on two pages.
        document =
            document.then(
                PageOperations.setBackground(
                    document,
                    listOf(1, 2),
                    Background.Solid(Color.of(0xa0, 0xe8, 0xff), PageStyle.GRAPH),
                ),
            )
        // Layers on page 2: a new one above, renamed and moved below; then merged down on page 3.
        document = document.then(LayerOperations.addLayer(document, 1, 1))
        document = document.then(LayerOperations.renameLayer(document, 1, 1, "Notes"))
        document = document.then(LayerOperations.moveLayer(document, 1, 1, 0))
        document = document.then(LayerOperations.addLayer(document, 2, 0))
        document = document.then(LayerOperations.mergeLayerDown(document, 2, 1))
        return document
    }

    private companion object {
        const val PATH = "inkpad-pages.xopp"
    }
}
