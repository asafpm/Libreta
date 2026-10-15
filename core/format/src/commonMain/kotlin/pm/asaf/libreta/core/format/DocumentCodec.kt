// SPDX-License-Identifier: GPL-2.0-or-later
// Structure follows Xournal++ v1.3.8 src/core/control/xojfile/LoadHandler.cpp (parseStart,
// parseContents, parsePage) and SaveHandler.cpp (prepareSave, visitPage), © Xournal++ contributors.

package pm.asaf.libreta.core.format

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.ForeignElement
import pm.asaf.libreta.core.model.ForeignNode
import pm.asaf.libreta.core.model.ForeignText
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page

/** `<xournal>`, `<page>` and `<layer>` (docs/xopp-format.md §4). */
internal object DocumentCodec {
    private const val ROOT = "xournal"

    /**
     * @param packageFileVersion the version from a zip package's `META-INF/version`, used when
     *   the root element has no `fileversion` attribute.
     */
    fun decode(
        root: ForeignElement,
        context: DecodeContext,
        packageFileVersion: Int? = null,
    ): Document {
        if (root.name != ROOT) throw XoppFormatException("Not a Xournal++ document (root element <${root.name}>)")
        val attributes = AttributeReader(root.attributes)
        val creator = attributes.string("creator")
        val fileVersion =
            try {
                attributes.int("fileversion")
            } catch (e: DecodeException) {
                context.warn("document", "file version kept as is: ${e.message}")
                attributes.keepAsExtra("fileversion")
                null
            }
        val content = RootContent(context)
        root.children.forEach(content::add)
        if (content.pages.isEmpty()) throw XoppFormatException("Document has no pages")
        return Document(
            pages = content.pages,
            creator = creator,
            fileVersion = fileVersion ?: packageFileVersion,
            title = content.title,
            preview = content.preview,
            extraAttributes = attributes.extra(),
            extraChildren = content.extraChildren,
        )
    }

    /** Collects the children of `<xournal>`; anything not understood is kept as is. */
    private class RootContent(
        private val context: DecodeContext,
    ) {
        var title: String? = null
        var preview: ByteString? = null
        val pages = mutableListOf<Page>()
        val extraChildren = mutableListOf<ForeignNode>()

        fun add(child: ForeignNode) {
            val element = child as? ForeignElement
            val understood =
                when (element?.name) {
                    "page" -> {
                        pages += decodePage(element, context, "page ${pages.size + 1}")
                        true
                    }

                    "title" -> {
                        title == null && element.isTextOnly() && decodeTitle(element)
                    }

                    "preview" -> {
                        preview == null && element.isTextOnly() && decodePreview(element)
                    }

                    // Declares an audio file of a zip package; the data is in the read result's
                    // attachments. Xournal++ crashes on <audio> outside zip packages, so it is
                    // never written back (docs/xopp-format.md §4).
                    "audio" -> {
                        if (!context.isPackage) context.warn("document", "<audio> dropped: only valid in zip packages")
                        true
                    }

                    else -> {
                        false
                    }
                }
            if (!understood) {
                if (element != null) context.warn("document", "<${element.name}> kept as is")
                extraChildren += child
            }
        }

        private fun decodeTitle(element: ForeignElement): Boolean {
            title = element.textContent()
            return true
        }

        private fun decodePreview(element: ForeignElement): Boolean {
            preview = element.textContent().decodeBase64()
            return preview != null
        }
    }

    private fun decodePage(
        element: ForeignElement,
        context: DecodeContext,
        location: String,
    ): Page {
        val attributes = AttributeReader(element.attributes)
        val (width, height) =
            try {
                attributes.requireDouble("width") to attributes.requireDouble("height")
            } catch (e: DecodeException) {
                throw XoppFormatException("Invalid size of $location: ${e.message}", e)
            }
        var background: Background? = null
        val layers = mutableListOf<Layer>()
        val extraChildren = mutableListOf<ForeignNode>()
        for (child in element.children) {
            when {
                child is ForeignElement && child.name == BackgroundCodec.TAG && background == null -> {
                    background = BackgroundCodec.decode(child, context, "$location › background")
                }

                child is ForeignElement && child.name == "layer" -> {
                    layers += decodeLayer(child, context, "$location › layer ${layers.size + 1}")
                }

                else -> {
                    if (child is ForeignElement) context.warn(location, "<${child.name}> kept as is")
                    extraChildren += child
                }
            }
        }
        return Page(
            width = width,
            height = height,
            background = background,
            // Xournal++ gives a page without layers one empty layer, on load and on save.
            layers = layers.ifEmpty { listOf(Layer(elements = emptyList())) },
            extraAttributes = attributes.extra(),
            extraChildren = extraChildren,
        )
    }

    private fun decodeLayer(
        element: ForeignElement,
        context: DecodeContext,
        location: String,
    ): Layer {
        val attributes = AttributeReader(element.attributes)
        val elements =
            element.children.mapIndexedNotNull { index, child ->
                when (child) {
                    is ForeignElement -> {
                        ElementCodec.decode(child, context, "$location › element ${index + 1}")
                    }

                    is ForeignText -> {
                        if (!child.text.all { it.isXmlSpace() }) context.warn(location, "stray text dropped")
                        null
                    }
                }
            }
        return Layer(elements = elements, name = attributes.string("name"), extraAttributes = attributes.extra())
    }

    private fun ForeignElement.isTextOnly(): Boolean = children.all { it is ForeignText }

    fun encode(document: Document): ForeignElement {
        val attributes =
            AttributeWriter()
                .put("creator", document.creator)
                .put("fileversion", document.fileVersion?.toLong())
                .putExtra(document.extraAttributes)
                .build()
        val children = mutableListOf<ForeignNode>()
        document.title?.let { children += ForeignElement("title", children = listOf(ForeignText(it))) }
        document.preview?.let { children += ForeignElement("preview", children = listOf(ForeignText(it.base64()))) }
        document.pages.mapTo(children, ::encodePage)
        children += document.extraChildren
        return ForeignElement(ROOT, attributes, children)
    }

    private fun encodePage(page: Page): ForeignElement {
        val attributes =
            AttributeWriter()
                .put("width", page.width)
                .put("height", page.height)
                .putExtra(page.extraAttributes)
                .build()
        val children = mutableListOf<ForeignNode>()
        page.background?.let { children += BackgroundCodec.encode(it) }
        page.layers.mapTo(children, ::encodeLayer)
        children += page.extraChildren
        return ForeignElement("page", attributes, children)
    }

    private fun encodeLayer(layer: Layer): ForeignElement =
        ForeignElement(
            name = "layer",
            attributes = AttributeWriter().put("name", layer.name).putExtra(layer.extraAttributes).build(),
            children = layer.elements.map(ElementCodec::encode),
        )
}
