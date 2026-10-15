// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.GzipSource
import okio.buffer
import pm.asaf.libreta.core.model.AudioLink
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.CapStyle
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DoubleList
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.ForeignElement
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.LineStyle
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XoppCodecTest {
    @Test
    fun notesForPdfsAreWrittenAsXournalWritesThem() {
        val note = NewNote.forPdf(listOf(612.0 to 792.0, 612.0 to 792.0), FileDomain.ABSOLUTE, "paper.pdf", "Libreta")
        val xml = Buffer().also { XoppWriter.writeXml(note, it) }.readUtf8()
        // The first PDF page names the file, relative to the note; the others give their page.
        assertTrue("""<background type="pdf" domain="absolute" filename="paper.pdf" pageno="1"/>""" in xml, xml)
        assertTrue("""<background type="pdf" pageno="2"/>""" in xml, xml)
        assertEquals(note, XoppReader.read(XoppWriter.toByteString(note)).document)
    }

    private fun read(xml: String): XoppReadResult = XoppReader.read(xml.encodeUtf8())

    private fun document(
        pageContent: String,
        rootAttributes: String = """creator="xournalpp 1.3.8" fileversion="4"""",
    ): String =
        listOf(
            """<?xml version="1.0" standalone="no"?>""",
            "<xournal $rootAttributes>",
            "<title>T</title>",
            """<page width="612" height="792">""",
            pageContent,
            "</page>",
            "</xournal>",
        ).joinToString("\n")

    private fun layerOf(vararg elements: String): String =
        "<background type=\"solid\" color=\"#ffffffff\" style=\"lined\"/>\n<layer>\n" + elements.joinToString("\n") +
            "\n</layer>"

    private fun elementsOf(xml: String): List<Element> =
        read(xml)
            .document.pages
            .single()
            .layers
            .single()
            .elements

    /** Read → write → read must give the same document. */
    private fun assertRoundTrips(xml: String) {
        val first = read(xml).document
        assertEquals(first, XoppReader.read(XoppWriter.toByteString(first)).document)
    }

    @Test
    fun decodesStrokes() {
        val stroke =
            elementsOf(
                document(
                    layerOf(
                        """<stroke tool="pen" ts="0ll" fn="" color="#3333ccff" width="1.41 0.4 0.5" fill="128" """ +
                            """capStyle="round" style="dash">1 2 3 4 5 6</stroke>""",
                    ),
                ),
            ).single()
        assertEquals(
            Element.Stroke(
                tool = StrokeTool.PEN,
                color = Color(0x3333ccffu),
                width = 1.41,
                points = PointList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0),
                segmentWidths = DoubleList.of(0.4, 0.5),
                capStyle = CapStyle.ROUND,
                lineStyle = LineStyle.DASH,
                fill = 128,
                audio = AudioLink(timestamp = 0, filename = ""),
            ),
            stroke,
        )
    }

    @Test
    fun strokeWithoutPressureOrAudio() {
        val stroke =
            assertIs<Element.Stroke>(
                elementsOf(
                    document(layerOf("""<stroke tool="highlighter" color="#f8e45c7f" width="8.5">1 2 3 4</stroke>""")),
                ).single(),
            )
        assertNull(stroke.segmentWidths)
        assertNull(stroke.audio)
        assertEquals(Color(0xf8e45c7fu), stroke.color) // alpha kept, unlike Xournal++
    }

    @Test
    fun keepsNonFinitePressureValues() {
        // From Xournal++ test file packaged_xopp/stroke/width_recovery.xopp.
        val xml =
            document(
                layerOf("""<stroke tool="pen" color="#00ff00ff" width="1.41 nan 0.2 -nan">1 2 3 4 5 6 7 8</stroke>"""),
            )
        val stroke = assertIs<Element.Stroke>(elementsOf(xml).single())
        assertEquals(3, stroke.segmentWidths?.size)
        assertTrue(stroke.segmentWidths?.get(0)?.isNaN() == true)
        assertTrue(XoppWriter.toXml(read(xml).document).contains("""width="1.41 nan 0.2 -nan""""))
        assertRoundTrips(xml)
    }

    @Test
    fun decodesTextImageAndTexImage() {
        val elements =
            elementsOf(
                document(
                    layerOf(
                        """<text font="Sans" size="12" x="10.5" y="20" color="#000000ff">a &amp; b
            second line</text>""",
                        """<image left="1" top="2" right="3" bottom="4">${"PNG".encodeUtf8().base64()}</image>""",
                        """<teximage text="{.&#10;}" left="1" top="2" right="3" bottom="4">""" +
                            """${"%PDF".encodeUtf8().base64()}</teximage>""",
                    ),
                ),
            )
        assertEquals(
            listOf(
                Element.Text(
                    text = "a & b\n            second line",
                    font = "Sans",
                    size = 12.0,
                    x = 10.5,
                    y = 20.0,
                    color = Color.BLACK,
                ),
                Element.Image(Bounds(1.0, 2.0, 3.0, 4.0), "PNG".encodeUtf8()),
                Element.TexImage("{.\n}", Bounds(1.0, 2.0, 3.0, 4.0), "%PDF".encodeUtf8()),
            ),
            elements,
        )
    }

    @Test
    fun decodesBackgrounds() {
        val xml =
            """
            <xournal creator="x" fileversion="4">
            <page width="1" height="1"><background name="n" type="solid" color="pink" style=":copy" config="a=1"/><layer/></page>
            <page width="1" height="1"><background type="pdf" domain="attach" filename="bg.pdf" pageno="1ll"/><layer/></page>
            <page width="1" height="1"><background type="pdf" pageno="2"/><layer/></page>
            <page width="1" height="1"><background type="pixmap" domain="clone" filename="0"/><layer/></page>
            <page width="1" height="1"><background type="video" src="x"/><layer/></page>
            </xournal>
            """.trimIndent()
        val result = read(xml)
        assertEquals(
            listOf(
                Background.Solid(Color(0xffc0d4ffu), PageStyle(":copy"), config = "a=1", name = "n"),
                Background.Pdf(pageNumber = 1, domain = FileDomain.ATTACH, filename = "bg.pdf"),
                Background.Pdf(pageNumber = 2),
                Background.Pixmap(FileDomain.CLONE, "0"),
                Background.Unknown(ForeignElement("background", mapOf("type" to "video", "src" to "x"))),
            ),
            result.document.pages.map { it.background },
        )
        assertEquals(1, result.warnings.size)
        assertRoundTrips(xml)
    }

    @Test
    fun keepsUnknownContentAtEveryLevel() {
        val xml =
            """
            <xournal creator="x" fileversion="4" future="1">
            <title>T</title>
            <bookmark page="1"/>
            <page width="1" height="1" rotation="90">
            <background type="solid" color="#ffffffff" style="plain" tint="3"/>
            <layer name="L" locked="true">
            <stroke tool="pen" color="#000000ff" width="1" pressureKind="x">1 2 3 4</stroke>
            <timestamp ts="5" fn="b.ogg"/>
            <text font="Sans" size="12" x="0" y="0" color="#000000ff">t</text>
            </layer>
            <marker id="m"/>
            </page>
            </xournal>
            """.trimIndent()
        val result = read(xml)
        val document = result.document
        assertEquals(mapOf("future" to "1"), document.extraAttributes)
        assertEquals(listOf(ForeignElement("bookmark", mapOf("page" to "1"))), document.extraChildren)
        val page = document.pages.single()
        assertEquals(mapOf("rotation" to "90"), page.extraAttributes)
        assertEquals(listOf(ForeignElement("marker", mapOf("id" to "m"))), page.extraChildren)
        assertEquals(mapOf("tint" to "3"), (page.background as Background.Solid).extraAttributes)
        val layer = page.layers.single()
        assertEquals(mapOf("locked" to "true"), layer.extraAttributes)
        assertEquals(mapOf("pressureKind" to "x"), (layer.elements[0] as Element.Stroke).extraAttributes)
        // The unknown element keeps its position between the stroke and the text.
        assertEquals(
            Element.Unknown(ForeignElement("timestamp", mapOf("ts" to "5", "fn" to "b.ogg"))),
            layer.elements[1],
        )
        assertIs<Element.Text>(layer.elements[2])

        val written = XoppWriter.toXml(document)
        for (fragment in listOf(
            "future=\"1\"",
            "<bookmark page=\"1\"/>",
            "rotation=\"90\"",
            "tint=\"3\"",
            "locked=\"true\"",
            "pressureKind=\"x\"",
            "<timestamp ts=\"5\" fn=\"b.ogg\"/>",
            "<marker id=\"m\"/>",
        )) {
            assertTrue(fragment in written, "missing $fragment in\n$written")
        }
        assertRoundTrips(xml)
    }

    @Test
    fun audioDeclarationsAreNotKeptBecauseXournalppCrashesOnThemOutsideZipPackages() {
        val zip = XoppReader.read(TestData.read("xournalpp/packaged_xopp/audioAttachment/new.xopp"))
        assertEquals(emptyList(), zip.document.extraChildren)
        assertEquals(emptyList(), zip.warnings)
        assertTrue("<audio" !in XoppWriter.toXml(zip.document))

        val withAudio = document(layerOf()).replace("<title>T</title>", "<audio fn=\"a.ogg\"/>")
        val gzip = read(withAudio)
        assertEquals(emptyList(), gzip.document.extraChildren)
        assertEquals(1, gzip.warnings.size)
    }

    @Test
    fun keepsMalformedElementsVerbatimWithAWarning() {
        val malformed =
            listOf(
                """<stroke tool="pen" color="#000000ff" width="1">1 2 3</stroke>""",
                """<stroke tool="pen" color="#000000ff" width="1">1 2</stroke>""",
                """<stroke tool="pen" color="purple" width="1">1 2 3 4</stroke>""",
                """<stroke tool="brush" color="#000000ff" width="1">1 2 3 4</stroke>""",
                """<stroke tool="pen" color="#000000ff" width="">1 2 3 4</stroke>""",
                """<stroke tool="pen" color="#000000ff" width="1" ts="soon">1 2 3 4</stroke>""",
                """<text font="Sans" size="big" x="0" y="0" color="#000000ff">t</text>""",
                """<image left="0" top="0" right="1" bottom="1">not base64!</image>""",
            )
        val result = read(document(layerOf(*malformed.toTypedArray())))
        val elements =
            result.document.pages[0]
                .layers[0]
                .elements
        assertTrue(elements.all { it is Element.Unknown }, elements.toString())
        assertEquals(malformed.size, result.warnings.size)
        val written = XoppWriter.toXml(result.document)
        for (element in malformed) assertTrue(element in written, "not written verbatim: $element")
    }

    @Test
    fun readsLegacyXournalNamesAndRules() {
        // From Xournal++ test file load/text.xml (original Xournal 0.4.7).
        val xml =
            """
            <?xml version="1.0" standalone="no"?>
            <xournal version="0.4.7">
            <page width="612.00" height="792.00">
            <background type="solid" color="blue" style="lined" />
            <layer>
            <text font="Sans" size="12.00" x="130.50" y="96.75" color="red">red</text>
            <text font="Sans" size="12.00" x="258.75" y="87.00" color="blue">blue</text>
            </layer>
            </page>
            </xournal>
            """.trimIndent()
        val document = read(xml).document
        assertNull(document.fileVersion)
        assertEquals(mapOf("version" to "0.4.7"), document.extraAttributes)
        assertEquals(Color(0xa0e8ffffu), (document.pages[0].background as Background.Solid).color)
        assertEquals(
            listOf(Color(0xff0000ffu), Color(0x3333ccffu)),
            document.pages[0].layers[0].elements.map {
                (it as Element.Text).color
            },
        )
        assertRoundTrips(xml)
    }

    @Test
    fun pageWithoutLayersGetsAnEmptyOne() {
        val document = read(document("""<background type="solid" color="#ffffffff" style="plain"/>""")).document
        assertEquals(listOf(Layer(elements = emptyList())), document.pages.single().layers)
    }

    @Test
    fun writesXournalLayout() {
        val xml =
            """
            <?xml version="1.0" standalone="no"?>
            <xournal creator="xournalpp 1.3.8" fileversion="4">
            <title>Xournal++ document - see https://xournalpp.github.io/</title>
            <page width="612" height="792">
            <background type="solid" color="#ffffffff" style="lined"/>
            <layer/>
            <layer name="Notes">
            <stroke tool="pen" color="#000000ff" width="0.85" capStyle="round">114.7171 292.66517 113.57111 290.94428</stroke>
            <text font="Sans" size="12" x="10" y="20" color="#3333ccff">Hi &amp; bye</text>
            </layer>
            </page>
            </xournal>

            """.trimIndent()
        assertEquals(xml, XoppWriter.toXml(read(xml).document))
    }

    @Test
    fun writesGzip() {
        val document = read(document(layerOf())).document
        val bytes = XoppWriter.toByteString(document)
        assertEquals(0x1f.toByte(), bytes[0])
        val xml = GzipSource(Buffer().write(bytes)).buffer().use { it.readUtf8() }
        assertEquals(XoppWriter.toXml(document), xml)
        assertEquals(XoppContainer.GZIP, XoppReader.read(bytes).container)
    }

    @Test
    fun readsZipPackages() {
        // Xournal++ test file packaged_xopp/imgAttachment/new.xopp: image stored as an attachment.
        val result = XoppReader.read(TestData.read("xournalpp/packaged_xopp/imgAttachment/new.xopp"))
        assertEquals(XoppContainer.ZIP, result.container)
        assertEquals(4, result.document.fileVersion) // from META-INF/version
        val image =
            assertIs<Element.Image>(
                result.document.pages[0]
                    .layers[0]
                    .elements
                    .single(),
            )
        assertEquals(result.attachments.getValue("attachments/avatar.png"), image.data)
        assertTrue(
            image.data.startsWith(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())),
        )
    }

    @Test
    fun rejectsNonDocuments() {
        val inputs =
            listOf(
                "",
                "hello",
                "<MrWriter version=\"1\"><page width=\"1\" height=\"1\"/></MrWriter>",
                "<xournal/>",
                "<xournal><page width=\"wide\" height=\"1\"/></xournal>",
            )
        for (input in inputs) {
            assertFailsWith<XoppFormatException>("should reject '$input'") { read(input) }
        }
        assertFailsWith<XoppFormatException> { XoppReader.read(TestData.read("xournalpp/preview-test-invalid.xoj")) }
        assertFailsWith<XoppFormatException> {
            XoppReader.read(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3).let { okio.ByteString.of(*it) })
        }
        assertFailsWith<XoppFormatException> {
            XoppReader.read(
                byteArrayOf(0x50, 0x4b, 3, 4).let { okio.ByteString.of(*it) },
            )
        }
    }
}
