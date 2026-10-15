// SPDX-License-Identifier: GPL-2.0-or-later
// Attribute names, order and value rules follow Xournal++ v1.3.8
// src/core/control/xojfile/LoadHandler.cpp (parseText, parseImage, parseTexImage) and
// SaveHandler.cpp (visitLayer), © Xournal++ contributors.

package pm.asaf.libreta.core.format

import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.ForeignElement
import pm.asaf.libreta.core.model.ForeignText

/** Layer content (docs/xopp-format.md §5); strokes are in [StrokeCodec]. */
internal object ElementCodec {
    /** Decodes a layer child; anything unknown or malformed is kept verbatim. */
    fun decode(
        element: ForeignElement,
        context: DecodeContext,
        location: String,
    ): Element =
        try {
            when (element.name) {
                StrokeCodec.TAG -> StrokeCodec.decode(element)
                "text" -> decodeText(element)
                "image" -> decodeImage(element, context)
                "teximage" -> decodeTexImage(element, context)
                else -> throw DecodeException("unknown element")
            }
        } catch (e: DecodeException) {
            context.warn(location, "<${element.name}> kept as is: ${e.message}")
            Element.Unknown(element)
        }

    private fun decodeText(element: ForeignElement): Element.Text {
        val attributes = AttributeReader(element.attributes)
        return Element.Text(
            font = attributes.requireString("font"),
            size = attributes.requireDouble("size"),
            x = attributes.requireDouble("x"),
            y = attributes.requireDouble("y"),
            color = attributes.requireColor("color", ColorCodec.Usage.ELEMENT),
            audio = attributes.audio(),
            text = element.textContent(),
            extraAttributes = attributes.extra(),
        )
    }

    private fun decodeImage(
        element: ForeignElement,
        context: DecodeContext,
    ): Element.Image {
        val attributes = AttributeReader(element.attributes)
        return Element.Image(
            bounds = attributes.bounds(),
            data = context.binaryContent(element),
            extraAttributes = attributes.extra(),
        )
    }

    private fun decodeTexImage(
        element: ForeignElement,
        context: DecodeContext,
    ): Element.TexImage {
        val attributes = AttributeReader(element.attributes)
        return Element.TexImage(
            latex = attributes.requireString("text"),
            bounds = attributes.bounds(),
            data = context.binaryContent(element),
            extraAttributes = attributes.extra(),
        )
    }

    private fun AttributeReader.bounds(): Bounds =
        Bounds(
            left = requireDouble("left"),
            top = requireDouble("top"),
            right = requireDouble("right"),
            bottom = requireDouble("bottom"),
        )

    fun encode(element: Element): ForeignElement =
        when (element) {
            is Element.Unknown -> element.element
            is Element.Stroke -> StrokeCodec.encode(element)
            is Element.Text -> encodeText(element)
            is Element.Image -> encodeImage(element)
            is Element.TexImage -> encodeTexImage(element)
        }

    private fun encodeText(text: Element.Text): ForeignElement {
        val attributes =
            AttributeWriter()
                .put("font", text.font)
                .put("size", text.size)
                .put("x", text.x)
                .put("y", text.y)
                .put("color", ColorCodec.format(text.color))
                .putAudio(text.audio)
                .putExtra(text.extraAttributes)
                .build()
        return ForeignElement("text", attributes, listOf(ForeignText(text.text)))
    }

    private fun encodeImage(image: Element.Image): ForeignElement {
        val attributes = AttributeWriter().putBounds(image.bounds).putExtra(image.extraAttributes).build()
        return ForeignElement("image", attributes, listOf(ForeignText(image.data.base64())))
    }

    private fun encodeTexImage(image: Element.TexImage): ForeignElement {
        val attributes =
            AttributeWriter()
                .put("text", image.latex)
                .putBounds(image.bounds)
                .putExtra(image.extraAttributes)
                .build()
        return ForeignElement("teximage", attributes, listOf(ForeignText(image.data.base64())))
    }

    private fun AttributeWriter.putBounds(bounds: Bounds): AttributeWriter =
        put("left", bounds.left).put("top", bounds.top).put("right", bounds.right).put("bottom", bounds.bottom)
}
