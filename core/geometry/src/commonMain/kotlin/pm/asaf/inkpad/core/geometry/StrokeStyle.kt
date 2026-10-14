// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/model/StrokeStyle.cpp and src/core/view/StrokeView.cpp,
// © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.LineStyle
import pm.asaf.inkpad.core.model.StrokeTool

/** Dash patterns (docs/rendering.md §3.3): lengths in page units, not scaled by the stroke width. */
object DashPattern {
    private const val CUSTOM_PREFIX = "cust: "

    private val named =
        mapOf(
            LineStyle.DASH to doubleArrayOf(6.0, 3.0),
            LineStyle.DASH_DOT to doubleArrayOf(6.0, 3.0, 0.5, 3.0),
            LineStyle.DOT to doubleArrayOf(0.5, 3.0),
        )

    /** Alternating on/off lengths, or null for a solid stroke. */
    fun of(style: LineStyle?): DoubleArray? =
        when {
            style == null -> null
            style in named -> named.getValue(style).copyOf()
            style.value.startsWith(CUSTOM_PREFIX) -> parseCustom(style.value.removePrefix(CUSTOM_PREFIX))
            else -> null
        }

    /** Like the C++ stream Xournal++ uses: reads numbers until the first one that fails. */
    private fun parseCustom(lengths: String): DoubleArray? =
        lengths
            .split(' ', '\t', '\n')
            .filter { it.isNotEmpty() }
            .asSequence()
            .map { it.toDoubleOrNull() }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.toDoubleArray()
}

enum class Blend {
    /** Source over. */
    NORMAL,

    /** Cairo's MULTIPLY (Android `BlendMode.MULTIPLY`). */
    MULTIPLY,
}

/** How a stroke is painted (docs/rendering.md §3.2). Colour alpha from the file is ignored. */
data class StrokeAppearance(
    val blend: Blend,
    /** Opacity of the stroke itself. */
    val opacity: Double,
    /** Opacity of the enclosed area, or null if the stroke is not filled. */
    val fillOpacity: Double?,
    /** Fill and stroke are one shape painted once (filled highlighters), so they never double-darken. */
    val combineFill: Boolean,
) {
    companion object {
        const val HIGHLIGHTER_OPACITY = 0.47
        private const val MAX_FILL = 255.0

        fun of(stroke: Element.Stroke): StrokeAppearance {
            val fillOpacity = stroke.fill?.takeIf { it != -1 }?.let { it / MAX_FILL }
            return if (stroke.tool == StrokeTool.HIGHLIGHTER) {
                StrokeAppearance(
                    Blend.MULTIPLY,
                    fillOpacity ?: HIGHLIGHTER_OPACITY,
                    fillOpacity,
                    combineFill =
                        fillOpacity != null,
                )
            } else {
                StrokeAppearance(Blend.NORMAL, 1.0, fillOpacity, combineFill = false)
            }
        }
    }
}
