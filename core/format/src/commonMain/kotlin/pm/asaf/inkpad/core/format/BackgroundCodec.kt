// SPDX-License-Identifier: GPL-2.0-or-later
// Attribute names and order follow Xournal++ v1.3.8 src/core/control/xojfile/SaveHandler.cpp
// (visitPage, writeSolidBackground), © Xournal++ contributors.

package pm.asaf.inkpad.core.format

import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.FileDomain
import pm.asaf.inkpad.core.model.ForeignElement
import pm.asaf.inkpad.core.model.PageStyle

/** `<background>` (docs/xopp-format.md §4). */
internal object BackgroundCodec {
    const val TAG = "background"

    fun decode(
        element: ForeignElement,
        context: DecodeContext,
        location: String,
    ): Background =
        try {
            val attributes = AttributeReader(element.attributes)
            when (val type = attributes.string("type")) {
                "solid" -> decodeSolid(attributes)
                "pdf" -> decodePdf(attributes)
                "pixmap" -> decodePixmap(attributes)
                else -> throw DecodeException("unknown background type '$type'")
            }.also { if (element.children.isNotEmpty()) throw DecodeException("unexpected content") }
        } catch (e: DecodeException) {
            context.warn(location, "background kept as is: ${e.message}")
            Background.Unknown(element)
        }

    private fun decodeSolid(attributes: AttributeReader): Background.Solid {
        val name = attributes.string("name")
        return Background.Solid(
            color = attributes.requireColor("color", ColorCodec.Usage.BACKGROUND),
            style = attributes.string("style")?.let(::PageStyle),
            config = attributes.string("config"),
            name = name,
            extraAttributes = attributes.extra(),
        )
    }

    private fun decodePdf(attributes: AttributeReader): Background.Pdf {
        val name = attributes.string("name")
        return Background.Pdf(
            domain = attributes.domain(),
            filename = attributes.string("filename"),
            pageNumber = attributes.long("pageno"),
            name = name,
            extraAttributes = attributes.extra(),
        )
    }

    private fun decodePixmap(attributes: AttributeReader): Background.Pixmap {
        val name = attributes.string("name")
        return Background.Pixmap(
            domain = attributes.domain(),
            filename = attributes.string("filename"),
            name = name,
            extraAttributes = attributes.extra(),
        )
    }

    private fun AttributeReader.domain(): FileDomain? =
        string("domain")?.let { FileDomain.fromXmlName(it) ?: throw DecodeException("unknown domain '$it'") }

    fun encode(background: Background): ForeignElement {
        val attributes = AttributeWriter()
        when (background) {
            is Background.Unknown -> {
                return background.element
            }

            is Background.Solid -> {
                attributes
                    .put("name", background.name)
                    .put("type", "solid")
                    .put("color", ColorCodec.format(background.color))
                    .put("style", background.style?.value)
                    .put("config", background.config)
                    .putExtra(background.extraAttributes)
            }

            // Original Xournal needs this attribute order (SaveHandler.cpp:231-234).
            is Background.Pdf -> {
                attributes
                    .put("name", background.name)
                    .put("type", "pdf")
                    .put("domain", background.domain?.xmlName)
                    .put("filename", background.filename)
                    .put("pageno", background.pageNumber)
                    .putExtra(background.extraAttributes)
            }

            is Background.Pixmap -> {
                attributes
                    .put("name", background.name)
                    .put("type", "pixmap")
                    .put("domain", background.domain?.xmlName)
                    .put("filename", background.filename)
                    .putExtra(background.extraAttributes)
            }
        }
        return ForeignElement(TAG, attributes.build())
    }
}
