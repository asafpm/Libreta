// SPDX-License-Identifier: GPL-2.0-or-later
// Pen widths and default colour from Xournal++ v1.3.8 src/core/control/ToolHandler.cpp, the
// palette from palettes/xournalpp.gpl, © Xournal++ contributors.

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

object Palette {
    /** Xournal++'s default pen colour. */
    val DEFAULT_PEN: Color = Color(0x3333ccffu)

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
