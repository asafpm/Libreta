// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.FileDomain
import pm.asaf.inkpad.core.model.LineStyle
import pm.asaf.inkpad.core.model.PageStyle
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spot checks of Inkpad's own sample documents. */
class SampleContentTest {
    private fun load(path: String): Document = XoppReader.read(TestData.read(path)).document

    private fun Document.elements(): List<Element> = pages.flatMap { page -> page.layers.flatMap { it.elements } }

    @Test
    fun physics() {
        val document = load("physics.xopp")
        assertEquals("xournalpp 1.2.2", document.creator)
        assertEquals("Xournal++ document - see ", document.title) // trailing space kept
        assertEquals(8, document.pages.size)
        assertTrue(document.pages.all { (it.background as Background.Solid).style == PageStyle.LINED })
        val strokes = document.elements().filterIsInstance<Element.Stroke>()
        assertEquals(10541, strokes.size)
        assertEquals(1, strokes.count { it.lineStyle == LineStyle.DASH })
        assertEquals(4, document.elements().count { it is Element.Image })
    }

    @Test
    fun pdfAnnotation() {
        val document = load("pdf_annotation.xopp")
        assertEquals(
            Background.Pdf(pageNumber = 1, domain = FileDomain.ATTACH, filename = "bg.pdf"),
            document.pages[0].background,
        )
        val highlighters =
            document.elements().filterIsInstance<Element.Stroke>().filter { it.tool == StrokeTool.HIGHLIGHTER }
        assertEquals(5, highlighters.size)
        assertTrue(highlighters.all { it.color == Color(0xf8e45c7fu) && it.audio == null })
    }

    @Test
    fun noteTaking() {
        val elements = load("note-taking.xopp").elements()
        val tex = elements.filterIsInstance<Element.TexImage>().single()
        assertEquals("\\sin(\\arctan(m)) = \\frac{m}{\\sqrt{1+m^2}}", tex.latex)
        assertEquals("%PDF", tex.data.substring(0, 4).utf8())
        val image = elements.filterIsInstance<Element.Image>().single()
        assertEquals("PNG", image.data.substring(1, 4).utf8())
        assertEquals(2, elements.count { it is Element.Text })
        assertEquals(169, elements.count { it is Element.Stroke })
    }

    @Test
    fun pixmapBackgroundAndEraser() {
        val document = load("muzzy_pixmap_bg.xopp")
        assertEquals(
            Background.Pixmap(FileDomain.ABSOLUTE, "../../../bait/Photos/muzzy.jpg"),
            document.pages[0].background,
        )
        val erasers = document.elements().filterIsInstance<Element.Stroke>().filter { it.tool == StrokeTool.ERASER }
        assertEquals(2, erasers.size)
        assertTrue(erasers.all { it.color == Color.WHITE })
    }

    @Test
    fun xojExport() {
        val document = load("2026-10-13-Note-22-46.xoj")
        assertEquals(2, document.fileVersion)
        val stroke = document.elements().single() as Element.Stroke
        assertEquals(null, stroke.audio)
        assertEquals(null, stroke.capStyle)
    }
}
