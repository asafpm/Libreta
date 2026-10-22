// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.ByteString.Companion.decodeBase64
import pm.asaf.libreta.core.model.ForeignElement
import pm.asaf.libreta.core.model.ForeignNode
import pm.asaf.libreta.core.model.ForeignText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Read → write → read over every sample document in `testdata/`. */
class RoundTripTest {
    private val allSamples = SampleFiles.own + SampleFiles.xournalppGzipOrPlain + SampleFiles.xournalppZip

    @Test
    fun everySampleRoundTripsToAnEqualDocument() {
        for (path in allSamples) {
            val original = XoppReader.read(TestData.read(path)).document
            val reread = XoppReader.read(XoppWriter.toByteString(original)).document
            assertEquals(original, reread, path)
        }
    }

    @Test
    fun writingIsIdempotent() {
        for (path in allSamples) {
            val once = XoppWriter.toXml(XoppReader.read(TestData.read(path)).document)
            val twice =
                XoppWriter.toXml(
                    XoppReader.read(XoppWriter.toByteString(XoppReader.read(TestData.read(path)).document)).document,
                )
            assertEquals(once, twice, path)
        }
    }

    @Test
    fun ownSamplesReadWithoutWarnings() {
        for (path in SampleFiles.own) {
            assertEquals(emptyList(), XoppReader.read(TestData.read(path)).warnings, path)
        }
    }

    @Test
    fun writtenXmlKeepsEveryElementAndAttributeOfTheOriginal() {
        // Compares the XML trees with values normalised (numbers by value, "ll" suffixes,
        // colour names, base64 encoding). Anything the decoder dropped would show up here.
        for (path in SampleFiles.own + SampleFiles.xournalppGzipOrPlain) {
            val original = XmlTree.parse(SampleFiles.xml(path))
            val written = XmlTree.parse(XoppWriter.toXml(XoppReader.read(TestData.read(path)).document))
            assertEquals(Canonical.of(original), Canonical.of(written), path)
        }
    }

    @Test
    fun filesFromCurrentXournalppAreReproducedByteForByte() {
        // Xournal++ >= 1.2 writes numbers with at most 8 significant digits, which our
        // shortest-exact formatting reproduces; older versions padded decimals ("1.41000000").
        val byteExact =
            listOf(
                "libreta-sample.xopp",
                "xournalpp/cjk/测试.xopp",
                "xournalpp/load/linebreaksLatex.xopp",
                "xournalpp/load/relativePaths.xopp",
                "xournalpp/packaged_xopp/pdfBackground/old.xopp",
            )
        for (path in byteExact) {
            val xml = SampleFiles.xml(path)
            assertTrue(XoppWriter.toXml(XoppReader.read(TestData.read(path)).document) == xml, "$path differs")
        }
    }

    /** XML tree with values normalised so that equal content compares equal. */
    private object Canonical {
        fun of(element: ForeignElement): ForeignElement =
            ForeignElement(
                name = element.name,
                attributes = element.attributes.mapValues { (name, value) -> attribute(element.name, name, value) },
                children =
                    element.children
                        .filterNot { it is ForeignText && element.name !in textBodies && it.text.isBlank() }
                        .map { child(element.name, it) },
            )

        /** Elements whose text is content; elsewhere whitespace-only text is layout (`<layer>\n</layer>`). */
        private val textBodies = setOf("text", "title")

        private fun child(
            parent: String,
            node: ForeignNode,
        ): ForeignNode =
            when (node) {
                is ForeignElement -> {
                    of(node)
                }

                is ForeignText -> {
                    when (parent) {
                        "stroke" -> ForeignText(numbers(node.text) ?: node.text)
                        "image", "teximage", "preview" -> ForeignText(node.text.decodeBase64()?.hex() ?: node.text)
                        else -> node
                    }
                }
            }

        private fun attribute(
            element: String,
            name: String,
            value: String,
        ): String =
            when (name) {
                "color" -> color(element, value) ?: value
                in textAttributes -> value
                else -> numbers(value) ?: value
            }

        private fun color(
            element: String,
            value: String,
        ): String? {
            val usage = if (element == "background") ColorCodec.Usage.BACKGROUND else ColorCodec.Usage.ELEMENT
            return ColorCodec.parse(value, usage)?.let(ColorCodec::format)
        }

        /** Attributes whose values are text even when they look like numbers. */
        private val textAttributes =
            setOf("text", "name", "font", "filename", "fn", "creator", "title", "style", "config")

        /** Space-separated numbers in canonical form, or null if [text] is not a number list. */
        private fun numbers(text: String): String? {
            val tokens = text.split(' ', '\n', '\t', '\r').filter { it.isNotEmpty() }.map { it.removeSuffix("ll") }
            val values = tokens.mapNotNull { NumberCodec.parseDouble(it) }
            val allNumbers = tokens.isNotEmpty() && values.size == tokens.size
            return if (allNumbers) values.joinToString(" ") { NumberCodec.formatDouble(it) } else null
        }
    }
}
