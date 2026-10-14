// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.content.Context
import android.graphics.Typeface
import pm.asaf.inkpad.core.geometry.FontDescription
import pm.asaf.inkpad.core.geometry.FontFamily

/** Maps Xournal++ font names to the bundled DejaVu and Liberation fonts (docs/rendering.md §3.4). */
class FontResolver(
    context: Context,
) {
    private val resources = context.applicationContext.resources
    private val cache = HashMap<FontDescription, Typeface>()

    fun typeface(fontName: String): Typeface = typeface(FontDescription.parse(fontName))

    fun typeface(description: FontDescription): Typeface =
        synchronized(cache) { cache.getOrPut(description) { resources.getFont(resourceId(description)) } }

    private fun resourceId(description: FontDescription): Int {
        val styles = fonts.getValue(description.family)
        return when {
            description.bold && description.italic -> styles[BOLD_ITALIC]
            description.bold -> styles[BOLD]
            description.italic -> styles[ITALIC]
            else -> styles[REGULAR]
        }
    }

    private companion object {
        const val REGULAR = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        /** Regular, bold, italic, bold italic for each family. */
        val fonts =
            mapOf(
                FontFamily.DEJAVU_SANS to
                    intArrayOf(
                        R.font.dejavu_sans,
                        R.font.dejavu_sans_bold,
                        R.font.dejavu_sans_oblique,
                        R.font.dejavu_sans_bold_oblique,
                    ),
                FontFamily.DEJAVU_SERIF to
                    intArrayOf(
                        R.font.dejavu_serif,
                        R.font.dejavu_serif_bold,
                        R.font.dejavu_serif_italic,
                        R.font.dejavu_serif_bold_italic,
                    ),
                FontFamily.DEJAVU_SANS_MONO to
                    intArrayOf(
                        R.font.dejavu_sans_mono,
                        R.font.dejavu_sans_mono_bold,
                        R.font.dejavu_sans_mono_oblique,
                        R.font.dejavu_sans_mono_bold_oblique,
                    ),
                FontFamily.LIBERATION_SANS to
                    intArrayOf(
                        R.font.liberation_sans,
                        R.font.liberation_sans_bold,
                        R.font.liberation_sans_italic,
                        R.font.liberation_sans_bold_italic,
                    ),
                FontFamily.LIBERATION_SERIF to
                    intArrayOf(
                        R.font.liberation_serif,
                        R.font.liberation_serif_bold,
                        R.font.liberation_serif_italic,
                        R.font.liberation_serif_bold_italic,
                    ),
                FontFamily.LIBERATION_MONO to
                    intArrayOf(
                        R.font.liberation_mono,
                        R.font.liberation_mono_bold,
                        R.font.liberation_mono_italic,
                        R.font.liberation_mono_bold_italic,
                    ),
            )
    }
}
