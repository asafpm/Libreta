// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

/** The font families Inkpad bundles. */
enum class FontFamily {
    DEJAVU_SANS,
    DEJAVU_SERIF,
    DEJAVU_SANS_MONO,

    /** Metric-compatible with Arial / Helvetica. */
    LIBERATION_SANS,

    /** Metric-compatible with Times New Roman / Times. */
    LIBERATION_SERIF,

    /** Metric-compatible with Courier New / Courier. */
    LIBERATION_MONO,
}

/**
 * A Xournal++ font name reduced to a bundled font (docs/rendering.md §3.4).
 *
 * The name is a Pango font description: trailing style words (including "Roman") are
 * removed and the rest is the family, which is looked up like fontconfig does. So, like in
 * Xournal++, "Times New Roman" is the unknown family "Times New" and gets the default font.
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

        /** Families with a bundled equivalent, by exact (case-insensitive) name. */
        private val knownFamilies: Map<String, FontFamily> =
            buildMap {
                listOf("sans", "sans-serif", "dejavu sans").forEach { put(it, FontFamily.DEJAVU_SANS) }
                listOf("serif", "dejavu serif").forEach { put(it, FontFamily.DEJAVU_SERIF) }
                listOf("monospace", "mono", "dejavu sans mono").forEach { put(it, FontFamily.DEJAVU_SANS_MONO) }
                listOf("liberation serif", "times", "tinos", "thorndale", "nimbus roman no9 l", "tex gyre termes")
                    .forEach { put(it, FontFamily.LIBERATION_SERIF) }
                listOf(
                    "liberation sans",
                    "arial",
                    "helvetica",
                    "nimbus sans",
                    "nimbus sans l",
                    "arimo",
                    "albany",
                    "tex gyre heros",
                ).forEach { put(it, FontFamily.LIBERATION_SANS) }
                listOf(
                    "liberation mono",
                    "courier",
                    "courier new",
                    "nimbus mono",
                    "nimbus mono ps",
                    "nimbus mono l",
                    "cousine",
                    "cumberland",
                ).forEach { put(it, FontFamily.LIBERATION_MONO) }
            }

        private val monoHints =
            listOf("mono", "courier", "consol", "code", "typewriter", "inconsolata", "menlo", "hack")

        /** Serif families whose names do not say "serif" (e.g. URW's C059 = Century Schoolbook). */
        private val serifHints =
            listOf(
                "georgia",
                "palatino",
                "garamond",
                "cambria",
                "antiqua",
                "c059",
                "p052",
                "century",
                "schoolbook",
                "bookman",
                "libertine",
                "computer modern",
                "cmu",
                "baskerville",
                "caslon",
                "charter",
                "utopia",
                "didot",
                "bodoni",
            )

        fun parse(name: String): FontDescription {
            val words =
                name
                    .trim()
                    .split(' ', ',')
                    .filter { it.isNotEmpty() }
                    .map { it.lowercase() }
            // Pango reads style words (and an optional size) from the end of the description.
            val style = words.takeLastWhile { it.isStyleWord() }
            // A description made only of style words keeps them as its family.
            val familyWords = words.dropLast(style.size).ifEmpty { words }
            return FontDescription(
                family(familyWords.joinToString(" ")),
                bold = style.any { it in boldWords },
                italic = style.any { it in italicWords },
            )
        }

        private fun String.isStyleWord(): Boolean =
            this in boldWords || this in italicWords || this in otherStyleWords || toDoubleOrNull() != null

        /** Fontconfig falls back to the default (sans) font for families it does not know. */
        private fun family(name: String): FontFamily =
            knownFamilies[name]
                ?: when {
                    monoHints.any { it in name } -> FontFamily.DEJAVU_SANS_MONO

                    ("serif" in name && "sans" !in name) || name == "roman" ||
                        serifHints.any {
                            it in name
                        }
                    -> FontFamily.DEJAVU_SERIF

                    else -> FontFamily.DEJAVU_SANS
                }
    }
}
