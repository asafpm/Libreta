// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

/** The generic font families Inkpad bundles (DejaVu Sans, Serif and Sans Mono). */
enum class FontFamily { SANS, SERIF, MONO }

/**
 * A Xournal++ font name (a Pango font description such as `Liberation Serif Bold`) reduced
 * to what Inkpad can draw (docs/rendering.md §3.4).
 */
data class FontDescription(
    val family: FontFamily,
    val bold: Boolean,
    val italic: Boolean,
) {
    companion object {
        private val boldWords =
            setOf(
                "bold",
                "semibold",
                "semi-bold",
                "demibold",
                "demi-bold",
                "extrabold",
                "extra-bold",
                "ultrabold",
                "ultra-bold",
                "heavy",
                "black",
            )
        private val italicWords = setOf("italic", "oblique")
        private val otherStyleWords =
            setOf(
                "regular",
                "normal",
                "book",
                "medium",
                "light",
                "thin",
                "ultralight",
                "extralight",
                "semilight",
                "condensed",
                "semi-condensed",
                "expanded",
                "semi-expanded",
                "roman",
                "small-caps",
            )
        private val monoHints = listOf("mono", "courier", "consol", "code", "typewriter")
        private val serifHints = listOf("roman", "times", "georgia", "palatino", "garamond", "cambria", "antiqua")

        fun parse(name: String): FontDescription {
            val words =
                name
                    .trim()
                    .split(' ', ',')
                    .filter { it.isNotEmpty() }
                    .map { it.lowercase() }
            // Pango reads style words (and an optional size) from the end of the description.
            val style =
                words.takeLastWhile {
                    it in boldWords || it in italicWords || it in otherStyleWords ||
                        it.toDoubleOrNull() != null
                }
            val lowerName = name.lowercase()
            val family =
                when {
                    monoHints.any { it in lowerName } -> FontFamily.MONO

                    ("serif" in lowerName && "sans" !in lowerName) ||
                        serifHints.any {
                            it in lowerName
                        }
                    -> FontFamily.SERIF

                    else -> FontFamily.SANS
                }
            return FontDescription(
                family,
                bold = style.any { it in boldWords },
                italic = style.any { it in italicWords },
            )
        }
    }
}
