// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/view/background/*BackgroundView.cpp and
// src/core/control/pagetype/PageTypeHandler.cpp, © Xournal++ contributors.

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.PageStyle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class LineCap { BUTT, ROUND, SQUARE }

/** One stroked path of a background pattern; joins are mitred (cairo's default). */
data class PatternStroke(
    val path: ShapePath,
    val width: Double,
    val cap: LineCap,
    val color: Color,
)

/** A solid background: the page filled with [fill], then [strokes] in order. */
data class BackgroundPattern(
    val fill: Color,
    val strokes: List<PatternStroke>,
)

/** Background ruling geometry (docs/rendering.md §2.1). */
object BackgroundPatterns {
    /**
     * The pattern of [background] on a page of the given size, limited to what can be seen in
     * [clip] (the whole page by default).
     */
    fun of(
        background: Background.Solid,
        pageWidth: Double,
        pageHeight: Double,
        clip: Bounds = Bounds(0.0, 0.0, pageWidth, pageHeight),
    ): BackgroundPattern {
        val fill = background.color.opaque()
        val page = PageArea(pageWidth, pageHeight, clip, fill.isLight(), BackgroundConfig(background.config))
        val strokes =
            when (background.style) {
                PageStyle.RULED -> Ruled.strokes(page)
                PageStyle.LINED -> Ruled.strokes(page) + Ruled.marginLine(page)
                PageStyle.STAVES -> Staves.strokes(page)
                PageStyle.GRAPH -> Graph.strokes(page)
                PageStyle.DOTTED -> Dotted.strokes(page)
                PageStyle.ISO_DOTTED -> Isometric.dots(page)
                PageStyle.ISO_GRAPH -> Isometric.lines(page)
                else -> emptyList() // plain, and unknown styles such as ":copy"
            }
        return BackgroundPattern(fill, strokes.filter { it.path.commands.isNotEmpty() })
    }

    internal class PageArea(
        val width: Double,
        val height: Double,
        val clip: Bounds,
        val light: Boolean,
        val config: BackgroundConfig,
    ) {
        /** Line width from `lw`, or [default]. */
        fun lineWidth(default: Double): Double = config.double("lw") ?: default

        /** Pattern colour: `f1` on light backgrounds, `af1` on dark ones. */
        fun color(
            light: Color,
            dark: Color,
            lightKey: String = "f1",
            darkKey: String = "af1",
        ): Color =
            if (this.light) {
                config.hex(lightKey)?.let(::fromArgb) ?: light
            } else {
                config.hex(darkKey)?.let(::fromArgb) ?: dark
            }
    }

    /** PlainBackgroundView::getIndexBounds. */
    internal fun indexBounds(
        min: Double,
        max: Double,
        step: Double,
        margin: Double,
        length: Double,
    ): IntRange = ceil(max(min, margin) / step).toInt()..floor(min(max, length - margin) / step).toInt()

    internal fun line(
        builder: ShapePathBuilder,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ) {
        builder.moveTo(x1, y1)
        builder.lineTo(x2, y2)
    }

    private fun Color.opaque(): Color = Color(rgba or OPAQUE)

    /** Xournal++ `Color::isLight`. */
    private fun Color.isLight(): Boolean = red + green + blue > LIGHTNESS_THRESHOLD

    private fun fromArgb(argb: UInt): Color = Color((argb shl BITS_PER_BYTE) or OPAQUE)

    private const val OPAQUE = 0xffu
    private const val BITS_PER_BYTE = 8
    private const val LIGHTNESS_THRESHOLD = 0x180

    // Colours (Xournal++ util/Color.h).
    internal val DODGER_BLUE = Color(0x40a0ffffu)
    internal val DARK_SLATE_GRAY = Color(0x434343ffu)
    internal val DEEP_PINK = Color(0xff0080ffu)
    internal val MIDNIGHT_BLUE = Color(0x220080ffu)
    internal val SILVER = Color(0xbdbdbdffu)
}
