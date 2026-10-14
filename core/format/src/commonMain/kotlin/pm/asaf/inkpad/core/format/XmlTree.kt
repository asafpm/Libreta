// SPDX-License-Identifier: GPL-2.0-or-later
// Output layout and escaping follow Xournal++ v1.3.8 src/core/control/xml/XmlNode.cpp,
// TextAttribute.cpp and XmlTextNode.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.core.format

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import okio.BufferedSink
import pm.asaf.inkpad.core.model.ForeignElement
import pm.asaf.inkpad.core.model.ForeignNode
import pm.asaf.inkpad.core.model.ForeignText

/**
 * Converts between XML text and a [ForeignElement] tree (docs/xopp-format.md §2).
 *
 * Reading keeps attribute order and the exact text of leaf elements; whitespace between
 * child elements is layout and is dropped, as are comments and processing instructions.
 * Writing reproduces Xournal++'s layout: no indentation, a newline after every element,
 * text bodies inline.
 */
internal object XmlTree {
    const val DECLARATION = "<?xml version=\"1.0\" standalone=\"no\"?>\n"
    private const val BYTE_ORDER_MARK = '\uFEFF'

    fun parse(xml: String): ForeignElement {
        val text = xml.removePrefix(BYTE_ORDER_MARK.toString())
        // xmlutil reports malformed input with XmlException, but also with
        // IllegalStateException/IllegalArgumentException (e.g. "Unexpected EOF").
        return runCatching { TreeBuilder(xmlStreaming.newGenericReader(text, expandEntities = true)).build() }
            .getOrElse { error ->
                val malformed =
                    error is XmlException || error is IllegalStateException || error is IllegalArgumentException
                throw if (malformed) XoppFormatException("Malformed XML: ${error.message}", error) else error
            }
    }

    /** Writes the XML declaration and [root]. */
    fun write(
        root: ForeignElement,
        sink: BufferedSink,
    ) {
        sink.writeUtf8(DECLARATION)
        writeElement(root, sink)
    }

    fun writeElement(
        element: ForeignElement,
        sink: BufferedSink,
    ) {
        sink.writeUtf8("<").writeUtf8(element.name)
        for ((name, value) in element.attributes) {
            sink.writeUtf8(" ").writeUtf8(name).writeUtf8("=\"")
            writeEscaped(value, sink, inAttribute = true)
            sink.writeUtf8("\"")
        }
        val children = element.children
        when {
            children.isEmpty() -> {
                sink.writeUtf8("/>\n")
            }

            children.all { it is ForeignText } -> {
                sink.writeUtf8(">")
                children.forEach { writeEscaped((it as ForeignText).text, sink, inAttribute = false) }
                sink.writeUtf8("</").writeUtf8(element.name).writeUtf8(">\n")
            }

            else -> {
                sink.writeUtf8(">\n")
                for (child in children) {
                    when (child) {
                        is ForeignElement -> writeElement(child, sink)
                        is ForeignText -> writeEscaped(child.text, sink, inAttribute = false)
                    }
                }
                sink.writeUtf8("</").writeUtf8(element.name).writeUtf8(">\n")
            }
        }
    }

    /**
     * Xournal++ escapes `& < > " \n \r` in attributes and `& < >` in text. We additionally
     * escape tabs in attributes and carriage returns in text, which XML parsers would
     * otherwise turn into a space or a newline.
     */
    private fun writeEscaped(
        text: String,
        sink: BufferedSink,
        inAttribute: Boolean,
    ) {
        var plainStart = 0
        for (index in text.indices) {
            val replacement = escape(text[index], inAttribute) ?: continue
            sink.writeUtf8(text, plainStart, index).writeUtf8(replacement)
            plainStart = index + 1
        }
        sink.writeUtf8(text, plainStart, text.length)
    }

    private fun escape(
        char: Char,
        inAttribute: Boolean,
    ): String? =
        when (char) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '\r' -> "&#13;"
            '"' -> if (inAttribute) "&quot;" else null
            '\n' -> if (inAttribute) "&#10;" else null
            '\t' -> if (inAttribute) "&#9;" else null
            else -> null
        }

    /** Builds the tree from reader events. */
    private class TreeBuilder(
        private val reader: XmlReader,
    ) {
        private class Open(
            val name: String,
            val attributes: Map<String, String>,
        ) {
            val children = mutableListOf<ForeignNode>()
            val pendingText = StringBuilder()

            fun flushText() {
                if (pendingText.isNotEmpty()) {
                    children += ForeignText(pendingText.toString())
                    pendingText.clear()
                }
            }

            fun close(): ForeignElement {
                flushText()
                val hasElementChildren = children.any { it is ForeignElement }
                val kept =
                    if (hasElementChildren) {
                        children.filterNot { it is ForeignText && it.text.all { char -> char.isXmlSpace() } }
                    } else {
                        children.toList()
                    }
                return ForeignElement(name, attributes, kept)
            }
        }

        private val stack = ArrayDeque<Open>()
        private var root: ForeignElement? = null

        fun build(): ForeignElement {
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        startElement()
                    }

                    EventType.END_ELEMENT -> {
                        endElement()
                    }

                    EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE, EventType.ENTITY_REF -> {
                        stack.lastOrNull()?.pendingText?.append(reader.text)
                    }

                    else -> {
                        // XML declaration, comments, processing instructions: not document content.
                    }
                }
            }
            if (stack.isNotEmpty()) {
                throw XoppFormatException("Unexpected end of document inside <${stack.last().name}>")
            }
            return root ?: throw XoppFormatException("No root element")
        }

        private fun startElement() {
            if (root != null && stack.isEmpty()) throw XoppFormatException("More than one root element")
            stack.lastOrNull()?.flushText()
            val attributes = LinkedHashMap<String, String>()
            for (namespace in reader.namespaceDecls) {
                val name = if (namespace.prefix.isEmpty()) "xmlns" else "xmlns:${namespace.prefix}"
                attributes[name] = namespace.namespaceURI
            }
            for (index in 0 until reader.attributeCount) {
                attributes[qualified(reader.getAttributePrefix(index), reader.getAttributeLocalName(index))] =
                    reader.getAttributeValue(index)
            }
            stack.addLast(Open(qualified(reader.prefix, reader.localName), attributes))
        }

        private fun endElement() {
            val element = stack.removeLast().close()
            val parent = stack.lastOrNull()
            if (parent == null) root = element else parent.children += element
        }

        private fun qualified(
            prefix: String,
            localName: String,
        ): String = if (prefix.isEmpty()) localName else "$prefix:$localName"
    }
}
