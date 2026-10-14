// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import okio.ByteString
import kotlin.jvm.JvmInline

/** Content of a layer (docs/xopp-format.md §5). */
sealed interface Element {
    data class Stroke(
        val tool: StrokeTool,
        val color: Color,
        /** Nominal width in points. */
        val width: Double,
        val points: PointList,
        /**
         * Pressure-dependent width of each segment, one per point except the last, or null
         * for constant width. Kept exactly as stored: files can contain NaN or non-positive
         * values, which renderers must handle.
         */
        val segmentWidths: DoubleList? = null,
        val capStyle: CapStyle? = null,
        val lineStyle: LineStyle? = null,
        /** Fill opacity (0–255) of the area enclosed by the stroke. */
        val fill: Int? = null,
        val audio: AudioLink? = null,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Element

    data class Text(
        val text: String,
        val font: String,
        /** Font size in points. */
        val size: Double,
        /** Top-left corner. */
        val x: Double,
        val y: Double,
        val color: Color,
        val audio: AudioLink? = null,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Element

    data class Image(
        val bounds: Bounds,
        /** Encoded image (PNG, JPEG, …), exactly as stored. */
        val data: ByteString,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Element

    data class TexImage(
        /** LaTeX source. */
        val latex: String,
        val bounds: Bounds,
        /** Rendered formula: PDF (current files) or PNG (old files). */
        val data: ByteString,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Element

    /** An element Inkpad does not know (or could not read), kept verbatim. */
    data class Unknown(
        val element: ForeignElement,
    ) : Element
}

enum class StrokeTool { PEN, HIGHLIGHTER, ERASER }

/** Axis-aligned rectangle in points. */
data class Bounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/**
 * Link from an element to an audio recording. Both parts are stored independently in the
 * file (`ts` and `fn` attributes); either may be absent.
 */
data class AudioLink(
    /** Position in the recording, in milliseconds (seconds in files older than version 4). */
    val timestamp: Long?,
    val filename: String?,
)

@JvmInline
value class CapStyle(
    val value: String,
) {
    companion object {
        val BUTT: CapStyle = CapStyle("butt")
        val ROUND: CapStyle = CapStyle("round")
        val SQUARE: CapStyle = CapStyle("square")
    }
}

/** Dash pattern: `dash`, `dashdot`, `dot`, or `cust: <lengths>`. Solid strokes have none. */
@JvmInline
value class LineStyle(
    val value: String,
) {
    companion object {
        val DASH: LineStyle = LineStyle("dash")
        val DASH_DOT: LineStyle = LineStyle("dashdot")
        val DOT: LineStyle = LineStyle("dot")
    }
}
