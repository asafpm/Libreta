// SPDX-License-Identifier: GPL-2.0-or-later
// Attribute names, order and value rules follow Xournal++ v1.3.8
// src/core/control/xojfile/LoadHandler.cpp (parseStroke, parserText) and SaveHandler.cpp
// (visitStroke, visitStrokeExtended), © Xournal++ contributors.

package pm.asaf.libreta.core.format

import pm.asaf.libreta.core.model.CapStyle
import pm.asaf.libreta.core.model.DoubleList
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.ForeignElement
import pm.asaf.libreta.core.model.ForeignText
import pm.asaf.libreta.core.model.LineStyle
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool

/** `<stroke>` (docs/xopp-format.md §5). */
internal object StrokeCodec {
    const val TAG = "stroke"

    /** Xournal++ rejects strokes with fewer than two points. */
    private const val MIN_COORDINATES = 4

    private val toolNames =
        mapOf(StrokeTool.PEN to "pen", StrokeTool.HIGHLIGHTER to "highlighter", StrokeTool.ERASER to "eraser")

    fun decode(element: ForeignElement): Element.Stroke {
        val attributes = AttributeReader(element.attributes)
        val toolName = attributes.requireString("tool")
        val tool =
            toolNames.entries.firstOrNull { it.value == toolName }?.key
                ?: throw DecodeException("unknown tool '$toolName'")
        val audio = attributes.audio()
        val color = attributes.requireColor("color", ColorCodec.Usage.ELEMENT)
        val widths = parseWidths(attributes.requireString("width"))
        return Element.Stroke(
            tool = tool,
            color = color,
            width = widths[0],
            points = parsePoints(element.textContent()),
            segmentWidths = if (widths.size > 1) DoubleList.fromArray(widths.copyOfRange(1, widths.size)) else null,
            fill = attributes.int("fill"),
            capStyle = attributes.string("capStyle")?.let(::CapStyle),
            lineStyle = attributes.string("style")?.let(::LineStyle),
            audio = audio,
            extraAttributes = attributes.extra(),
        )
    }

    /** Nominal width followed by optional per-segment widths. */
    private fun parseWidths(text: String): DoubleArray =
        NumberCodec.parseDoubleList(text)?.takeIf { it.isNotEmpty() } ?: throw DecodeException("invalid width list")

    private fun parsePoints(text: String): PointList {
        val coordinates = NumberCodec.parseDoubleList(text) ?: throw DecodeException("invalid coordinates")
        if (coordinates.size < MIN_COORDINATES || coordinates.size % 2 != 0) {
            throw DecodeException("invalid coordinate count ${coordinates.size}")
        }
        return PointList.fromInterleaved(coordinates)
    }

    fun encode(stroke: Element.Stroke): ForeignElement {
        val widths =
            buildString {
                NumberCodec.appendDouble(this, stroke.width)
                stroke.segmentWidths?.let { segments ->
                    for (index in 0 until segments.size) {
                        append(' ')
                        NumberCodec.appendDouble(this, segments[index])
                    }
                }
            }
        val coordinates =
            buildString {
                for (index in 0 until stroke.points.size) {
                    if (index > 0) append(' ')
                    NumberCodec.appendDouble(this, stroke.points.x(index))
                    append(' ')
                    NumberCodec.appendDouble(this, stroke.points.y(index))
                }
            }
        val attributes =
            AttributeWriter()
                .put("tool", toolNames.getValue(stroke.tool))
                .putAudio(stroke.audio)
                .put("color", ColorCodec.format(stroke.color))
                .put("width", widths)
                .put("fill", stroke.fill?.toLong())
                .put("capStyle", stroke.capStyle?.value)
                .put("style", stroke.lineStyle?.value)
                .putExtra(stroke.extraAttributes)
                .build()
        return ForeignElement(TAG, attributes, listOf(ForeignText(coordinates)))
    }
}
