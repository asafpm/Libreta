// SPDX-License-Identifier: GPL-2.0-or-later
// Pen, highlighter and eraser widths, default colours and eraser types from Xournal++ v1.3.8
// src/core/control/ToolHandler.cpp, the palette from palettes/xournalpp.gpl, © Xournal++
// contributors.

package pm.asaf.inkpad.core.model

/** Xournal++'s pen widths, in points. */
enum class PenSize(
    val width: Double,
) {
    VERY_FINE(0.42),
    FINE(0.85),
    MEDIUM(1.41),
    THICK(2.26),
    VERY_THICK(5.67),
}

/** The pen strokes are drawn with. Xournal++'s default pen is royal blue at medium width. */
data class PenSettings(
    val color: Color = Palette.DEFAULT_PEN,
    val size: PenSize = PenSize.MEDIUM,
)

/** What the eraser does (Xournal++'s eraser types). */
enum class EraserType {
    /** Erases what it goes over, cutting strokes. */
    STANDARD,

    /** Paints white strokes over the page. */
    WHITEOUT,

    /** Deletes the strokes it touches. */
    DELETE_STROKE,
}

/** The eraser: Xournal++'s defaults are the standard eraser at medium width. */
data class EraserSettings(
    val type: EraserType = EraserType.STANDARD,
    val size: PenSize = PenSize.MEDIUM,
) {
    /** In points: half the side of the square erased, and the width of whiteout strokes. */
    val width: Double get() = ERASER_WIDTHS[size.ordinal]

    private companion object {
        val ERASER_WIDTHS = listOf(1.0, 2.83, 8.5, 12.0, 18.0)
    }
}

/** The highlighter: Xournal++'s default is yellow at medium width. Highlighters ignore pressure. */
data class HighlighterSettings(
    val color: Color = Palette.HIGHLIGHTER,
    val size: PenSize = PenSize.MEDIUM,
) {
    /** In points. */
    val width: Double get() = HIGHLIGHTER_WIDTHS[size.ordinal]

    private companion object {
        val HIGHLIGHTER_WIDTHS = listOf(1.0, 2.83, 8.5, 19.84, 30.0)
    }
}

/** The tool chosen in the toolbar. */
enum class Tool { PEN, HIGHLIGHTER, ERASER }

/** What the stylus does while its side button is held. */
enum class StylusButton { ERASER, HIGHLIGHTER, NONE }

/** The tools and their settings: what the stylus (or a finger) does. */
data class ToolSettings(
    val tool: Tool = Tool.PEN,
    val pen: PenSettings = PenSettings(),
    val highlighter: HighlighterSettings = HighlighterSettings(),
    val eraser: EraserSettings = EraserSettings(),
    val button: StylusButton = StylusButton.ERASER,
)

object Palette {
    /** Xournal++'s default pen colour. */
    val DEFAULT_PEN: Color = Color(0x3333ccffu)

    /** Xournal++'s default highlighter colour. */
    val HIGHLIGHTER: Color = Color.of(255, 255, 0)

    /** The colours a toolbar offers by default: black, the default pen, red, dark green and the default highlighter. */
    val DEFAULT_FIXED: List<Color>
        get() = listOf(Color.of(0, 0, 0), DEFAULT_PEN, Color.of(237, 83, 83), Color.of(58, 145, 4), HIGHLIGHTER)

    /** Xournal++'s default palette, `palettes/xournalpp.gpl`, in its order. */
    val XOURNALPP: List<Color> =
        listOf(
            Color.of(255, 225, 107), // yellow
            Color.of(255, 161, 84), // orange
            Color.of(205, 158, 247), // purple
            Color.of(155, 219, 77), // light green
            Color.of(100, 186, 255), // light blue
            Color.of(128, 128, 128), // grey
            Color.of(58, 145, 4), // dark green
            Color.of(237, 83, 83), // red
            Color.of(0, 46, 153), // dark blue
            Color.of(0, 0, 0), // black
            Color.of(255, 255, 255), // white
        )
}
