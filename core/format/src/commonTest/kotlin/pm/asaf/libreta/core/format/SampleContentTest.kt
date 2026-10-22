// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.ByteString.Companion.encodeUtf8
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spot checks of Libreta's own sample documents (written by WrittenSamplesTest and the like). */
class SampleContentTest {
    private fun load(path: String): Document = XoppReader.read(TestData.read(path)).document

    private fun Document.elements(): List<Element> = pages.flatMap { page -> page.layers.flatMap { it.elements } }

    @Test
    fun sample() {
        val document = load("libreta-sample.xopp")
        assertEquals("Libreta sample", document.creator)
        assertEquals(8, document.pages.size)
        assertTrue(document.pages.all { it.width == 612.0 && it.height == 792.0 })
        assertEquals(PageStyle.GRAPH, (document.pages[2].background as Background.Solid).style)
        val strokes = document.elements().filterIsInstance<Element.Stroke>()
        assertTrue(strokes.size > 4000, "${strokes.size} strokes")
        // Every other page is written with pressure.
        assertEquals(
            listOf(false, true, false, true, false, true, false, true),
            document.pages.map { page ->
                page.layers.single().elements.any {
                    (it as? Element.Stroke)?.segmentWidths !=
                        null
                }
            },
        )
        assertEquals(4, document.elements().count { it is Element.Image })
    }

    @Test
    fun pdfAnnotation() {
        val document = load("libreta-pdf.xopp")
        assertEquals(
            Background.Pdf(pageNumber = 1, domain = FileDomain.ATTACH, filename = "bg.pdf"),
            document.pages[0].background,
        )
        assertEquals(Background.Pdf(pageNumber = 2), document.pages[1].background)
        val highlighters =
            document.elements().filterIsInstance<Element.Stroke>().filter { it.tool == StrokeTool.HIGHLIGHTER }
        assertEquals(1, highlighters.size)
    }

    @Test
    fun notes() {
        val elements = load("libreta-notes.xopp").elements()
        val tex = elements.filterIsInstance<Element.TexImage>().single()
        assertEquals("{.\n}", tex.latex)
        assertEquals("%PDF", tex.data.substring(0, 4).utf8())
        val image = elements.filterIsInstance<Element.Image>().single()
        assertEquals("PNG", image.data.substring(1, 4).utf8())
        assertEquals(2, elements.count { it is Element.Text })
        assertTrue(
            elements.filterIsInstance<Element.Stroke>().all {
                it.tool != StrokeTool.PEN ||
                    it.segmentWidths != null
            },
        )
    }

    @Test
    fun whiteout() {
        val erasers =
            load("libreta-edits.xopp").elements().filterIsInstance<Element.Stroke>().filter {
                it.tool ==
                    StrokeTool.ERASER
            }
        assertEquals(1, erasers.size)
        assertTrue(erasers.all { it.color == Color.WHITE })
    }

    @Test
    fun pixmapBackgroundWithAPath() {
        val xml =
            """<?xml version="1.0" standalone="no"?>
            |<xournal creator="xournalpp 1.3.8" fileversion="4">
            |<page width="174" height="209">
            |<background type="pixmap" domain="absolute" filename="../../images/photo.jpg"/>
            |<layer/>
            |</page>
            |</xournal>
            |
            """.trimMargin()
        assertEquals(
            Background.Pixmap(FileDomain.ABSOLUTE, "../../images/photo.jpg"),
            XoppReader
                .read(xml.encodeUtf8())
                .document.pages[0]
                .background,
        )
    }

    @Test
    fun xojExport() {
        val document = load("xournalpp/preview-test2.xoj")
        assertEquals(2, document.fileVersion)
        val stroke = document.elements().single() as Element.Stroke
        assertEquals(null, stroke.audio)
        assertEquals(null, stroke.capStyle)
    }
}
