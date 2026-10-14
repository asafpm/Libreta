// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import okio.Buffer
import pm.asaf.inkpad.core.model.ForeignElement
import pm.asaf.inkpad.core.model.ForeignText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XmlTreeTest {
    private fun parse(xml: String): ForeignElement = XmlTree.parse(xml)

    private fun write(root: ForeignElement): String = Buffer().also { XmlTree.write(root, it) }.readUtf8()

    private fun roundTrip(xml: String): String = write(parse(xml))

    @Test
    fun reproducesXournalLayoutExactly() {
        val xml =
            """
            <?xml version="1.0" standalone="no"?>
            <xournal creator="xournalpp 1.3.8" fileversion="4">
            <title>Xournal++ document - see https://xournalpp.github.io/</title>
            <page width="612" height="792">
            <background type="pdf" pageno="2"/>
            <layer/>
            <layer name="a">
            <stroke tool="pen" color="#000000ff" width="0.85">1 2 3 4</stroke>
            </layer>
            </page>
            </xournal>

            """.trimIndent()
        assertEquals(xml, roundTrip(xml))
    }

    @Test
    fun keepsAttributeOrder() {
        val root = parse("""<a z="1" b="2" m="3"/>""")
        assertEquals(listOf("z", "b", "m"), root.attributes.keys.toList())
    }

    @Test
    fun escapesAttributesLikeXournal() {
        val root = ForeignElement("t", mapOf("text" to "a&b\"c<d>e\nf\rg\th'i"))
        val xml = write(root)
        assertEquals(
            """<?xml version="1.0" standalone="no"?>""" + "\n" +
                """<t text="a&amp;b&quot;c&lt;d&gt;e&#10;f&#13;g&#9;h'i"/>""" + "\n",
            xml,
        )
        assertEquals(root, XmlTree.parse(xml))
    }

    @Test
    fun keepsLineBreaksEncodedInAttributes() {
        // Xournal++ test file load/linebreaksLatex.xopp stores LaTeX with these escapes.
        val root = parse("""<teximage text="{.&#10;}" b="{.&#13;}" c="{.&#13;&#10;}"/>""")
        assertEquals("{.\n}", root.attributes["text"])
        assertEquals("{.\r}", root.attributes["b"])
        assertEquals("{.\r\n}", root.attributes["c"])
    }

    @Test
    fun escapesTextAndKeepsItExactly() {
        val text = "a & b < c > d \"q\" 'q'\nline2\r\n测试 テスト 😀 trailing "
        val root = ForeignElement("text", children = listOf(ForeignText(text)))
        val xml = write(root)
        assertEquals(root, parse(xml))
    }

    @Test
    fun keepsWhitespaceOnlyLeafText() {
        val root = parse("<text> </text>")
        assertEquals(listOf(ForeignText(" ")), root.children)
        assertEquals("<text> </text>\n", write(root).substringAfter("?>\n"))
    }

    @Test
    fun dropsLayoutWhitespaceBetweenElements() {
        val root = parse("<page>\n  <layer/>\n\t<layer/>\n</page>")
        assertEquals(listOf(ForeignElement("layer"), ForeignElement("layer")), root.children)
    }

    @Test
    fun readsEntitiesAndCdataAsText() {
        val root = parse("<t>&amp;&lt;&gt;&quot;&apos;&#65;&#x42;<![CDATA[<raw>]]></t>")
        assertEquals(listOf(ForeignText("&<>\"'AB<raw>")), root.children)
    }

    @Test
    fun ignoresCommentsAndByteOrderMark() {
        val root = parse("\uFEFF<?xml version=\"1.0\"?><!-- c --><a><!-- d --><b/></a>")
        assertEquals(ForeignElement("a", children = listOf(ForeignElement("b"))), root)
    }

    @Test
    fun keepsOldXournalSelfClosingTags() {
        val root = parse("""<background type="solid" color="white" style="lined" />""")
        assertEquals(mapOf("type" to "solid", "color" to "white", "style" to "lined"), root.attributes)
    }

    @Test
    fun rejectsMalformedXml() {
        for (xml in listOf("", "not xml", "<a>", "<a></b>", "<a b=1/>")) {
            assertFailsWith<XoppFormatException>("should reject '$xml'") { parse(xml) }
        }
    }

    @Test
    fun reproducesEveryXournalppWrittenSampleByteForByte() {
        // Raw XML layer only (no typed model yet): parsing and re-serialising must not change
        // a single byte of files written by Xournal++. Old Xournal (0.4.x) files differ only
        // in layout ("<x />"), so they are excluded here.
        for (path in SampleFiles.xournalppWrittenXml) {
            val xml = SampleFiles.xml(path)
            assertEquals(xml, roundTrip(xml), path)
        }
    }
}
