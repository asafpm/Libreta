// SPDX-License-Identifier: GPL-2.0-or-later
// Ported from Xournal++ v1.3.8 test/unit_tests/control/LoadHandlerTest.cpp, © Xournal++ contributors.
// Where Libreta deliberately differs from Xournal++ (docs/xopp-format.md §7) the expectation
// is adapted and the difference noted.

package pm.asaf.libreta.core.format

import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XournalppLoadTest {
    private fun load(path: String): Document = XoppReader.read(TestData.read("xournalpp/$path")).document

    private fun Document.texts(): List<String> =
        pages.flatMap { page -> page.layers.flatMap { it.elements } }.filterIsInstance<Element.Text>().map { it.text }

    private fun assertSingleText(
        path: String,
        expected: String,
    ) {
        val document = load(path)
        assertEquals(1, document.pages.size, path)
        val layer = document.pages[0].layers.single()
        assertEquals(expected, assertIs<Element.Text>(layer.elements.first()).text, path)
    }

    @Test
    fun testLoad() = assertSingleText("test1.xoj", "12345")

    @Test
    fun testLoadZipped() = assertSingleText("packaged_xopp/test.xopp", "12345")

    @Test
    fun testLoadUnzipped() = assertSingleText("test1.unzipped.xoj", "12345")

    @Test
    fun testPageType() {
        for (path in listOf("load/pages.xoj", "packaged_xopp/pages.xopp")) {
            val document = load(path)
            assertEquals(6, document.pages.size, path)
            val styles = document.pages.take(5).map { (it.background as Background.Solid).style }
            assertEquals(
                listOf(PageStyle.PLAIN, PageStyle.RULED, PageStyle.LINED, PageStyle.STAVES, PageStyle.GRAPH),
                styles,
                path,
            )
            assertIs<Background.Pixmap>(document.pages[5].background, path)
            assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p6"), document.texts(), path)
        }
    }

    @Test
    fun testPageTypeFormatCopyFix() {
        // Xournal++ turns the unknown style ":copy" into "plain"; Libreta keeps it.
        val document = load("pageTypeFormatCopy.xopp")
        assertEquals(
            listOf(PageStyle.LINED, PageStyle(":copy"), PageStyle.PLAIN),
            document.pages.map { (it.background as Background.Solid).style },
        )
        assertEquals(listOf("p1", "p2", "p3"), document.texts())
    }

    @Test
    fun testLayer() {
        for (path in listOf("load/layer.xoj", "packaged_xopp/layer.xopp")) {
            val page = load(path).pages.single()
            assertEquals(
                listOf("l1", "l2", "l3"),
                page.layers.map { (it.elements.first() as Element.Text).text },
                path,
            )
        }
    }

    @Test
    fun testText() {
        for (path in listOf("load/text.xml", "packaged_xopp/text.xopp")) {
            val texts =
                load(path)
                    .pages
                    .single()
                    .layers
                    .single()
                    .elements
                    .map { it as Element.Text }
            assertEquals(listOf("red", "blue", "green"), texts.map { it.text }, path)
            // Xournal++ compares RGB only (it drops the alpha byte when loading).
            assertEquals(listOf(0xff0000, 0x3333cc, 0x00f000), texts.map { (it.color.rgba shr 8).toInt() }, path)
        }
    }

    @Test
    fun testImageZipped() {
        val layer =
            load("packaged_xopp/imgAttachment/new.xopp")
                .pages
                .single()
                .layers
                .single()
        assertIs<Element.Image>(layer.elements.single())
    }

    @Test
    fun imageLoadJpeg() {
        val image =
            assertIs<Element.Image>(
                load("packaged_xopp/imgAttachment/doc_with_jpg.xopp")
                    .pages
                    .single()
                    .layers
                    .single()
                    .elements
                    .single(),
            )
        // JPEG magic. Xournal++ re-encodes it as PNG on save; Libreta keeps the original bytes.
        assertEquals(0xff.toByte(), image.data[0])
        assertEquals(0xd8.toByte(), image.data[1])
        val reread = XoppReader.read(XoppWriter.toByteString(load("packaged_xopp/imgAttachment/doc_with_jpg.xopp")))
        assertEquals(
            image,
            reread.document.pages[0]
                .layers[0]
                .elements[0],
        )
    }

    @Test
    fun linebreaksLatex() {
        val expected = listOf("{.\n}", "{.\r}", "{.\r\n}")
        val document = load("load/linebreaksLatex.xopp")
        val saved = XoppReader.read(XoppWriter.toByteString(document)).document
        for (doc in listOf(document, saved)) {
            assertEquals(
                expected,
                doc.pages[0]
                    .layers[0]
                    .elements
                    .map { (it as Element.TexImage).latex },
            )
        }
    }

    @Test
    fun testLoadStoreLoad() {
        // Xournal++ allows a tolerance after saving; Libreta must reproduce values exactly.
        for (path in listOf("packaged_xopp/suite.xopp", "packaged_xopp/suite_float_bw_compat.xopp")) {
            val document = load(path)
            val elements =
                document.pages
                    .single()
                    .layers
                    .single()
                    .elements
            assertEquals(8, elements.size, path)
            assertEquals(
                listOf(true, true, true, true, true, false, true, true),
                elements.map { it is Element.Stroke },
                path,
            )
            assertIs<Element.Text>(elements[5], path)
            assertEquals(document, XoppReader.read(XoppWriter.toByteString(document)).document, path)
        }
    }

    @Test
    fun testStrokeWidthRecovery() {
        // Xournal++ repairs non-positive/NaN pressure values by splitting strokes (9 elements).
        // Libreta keeps the 5 strokes exactly as stored; repairing is a rendering concern.
        val strokes =
            load("packaged_xopp/stroke/width_recovery.xopp")
                .pages
                .single()
                .layers
                .single()
                .elements
                .map { it as Element.Stroke }
        assertEquals(5, strokes.size)
        assertNull(strokes[0].segmentWidths)
        for (stroke in strokes.drop(1)) {
            assertEquals(0x00ff00ffu, stroke.color.rgba)
            assertEquals(1.41, stroke.width)
        }
        assertEquals(0.16, strokes[1].segmentWidths?.get(0))
        assertEquals(-0.16, strokes[2].segmentWidths?.get(0))
        assertTrue(strokes[4].segmentWidths?.get(0)?.isNaN() == true)
    }

    @Test
    fun testLoadStoreCJK() {
        val document = load("cjk/测试.xopp")
        assertEquals(2, document.pages.size)
        assertEquals(
            listOf("Test", "测试", "テスト"),
            document.pages[0]
                .layers
                .single()
                .elements
                .map { (it as Element.Text).text },
        )
        val background = assertIs<Background.Pdf>(document.pages[0].background)
        assertTrue(background.filename?.endsWith("测试.pdf") == true)
        assertEquals(document, XoppReader.read(XoppWriter.toByteString(document)).document)
    }

    @Test
    fun testRelativePath() {
        val pages = load("load/relativePaths.xopp").pages
        assertEquals(
            Background.Pdf(pageNumber = 1, domain = FileDomain.ABSOLUTE, filename = "./././../cjk/测试.pdf"),
            pages[0].background,
        )
        // Only the first PDF page names the file.
        assertEquals(Background.Pdf(pageNumber = 2), pages[1].background)
    }

    @Test
    fun audioAttachment() {
        val result = XoppReader.read(TestData.read("xournalpp/packaged_xopp/audioAttachment/new.xopp"))
        val stroke =
            assertIs<Element.Stroke>(
                result.document.pages[0]
                    .layers[0]
                    .elements
                    .single(),
            )
        assertEquals("attachments/test.ogg", stroke.audio?.filename)
        assertEquals(StrokeTool.PEN, stroke.tool)
        assertTrue("attachments/test.ogg" in result.attachments)
    }
}
