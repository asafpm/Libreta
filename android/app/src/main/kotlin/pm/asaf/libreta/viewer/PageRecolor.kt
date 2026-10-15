// SPDX-License-Identifier: GPL-2.0-or-later
// Xournal++ v1.3.8's recolouring (src/util/Recolor.cpp): the page is inverted, then its colours
// are squeezed between two colours, here with paper at the dark one (#303446) and black ink at
// the light one (#C6D0F5): Xournal++'s colours, as its dark-page setting arranges them.
// © Xournal++ contributors.

package pm.asaf.libreta.viewer

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/** Pages shown dark: white paper becomes [PAPER], black ink [INK], other colours inverted between them. */
object PageRecolor {
    /** What white becomes. */
    const val PAPER: Int = 0xFF303446.toInt()

    /** What black becomes. */
    const val INK: Int = 0xFFC6D0F5.toInt()

    /**
     * The recolouring as a 4 × 5 colour matrix (Android's and Compose's layout): each channel
     * c goes to ink + (paper − ink) · c / 255; alpha is kept.
     */
    val matrix: FloatArray =
        FloatArray(MATRIX_SIZE).apply {
            for (channel in 0 until CHANNELS) {
                val shift = (CHANNELS - 1 - channel) * BYTE
                val paper = (PAPER ushr shift) and MAX
                val ink = (INK ushr shift) and MAX
                this[channel * ROW + channel] = (paper - ink) / MAX.toFloat()
                this[channel * ROW + OFFSET] = ink.toFloat()
            }
            this[CHANNELS * ROW + CHANNELS] = 1f
        }

    /** Recolours what is drawn with it. */
    fun filter(): ColorMatrixColorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))

    /** [argb] recoloured. */
    fun map(argb: Int): Int {
        var result = argb and ALPHA
        for (channel in 0 until CHANNELS) {
            val shift = (CHANNELS - 1 - channel) * BYTE
            val value = (argb ushr shift) and MAX
            val mapped = matrix[channel * ROW + channel] * value + matrix[channel * ROW + OFFSET]
            result = result or (Math.round(mapped).coerceIn(0, MAX) shl shift)
        }
        return result
    }

    private const val CHANNELS = 3
    private const val ROW = 5
    private const val OFFSET = 4
    private const val MATRIX_SIZE = 20
    private const val BYTE = 8
    private const val MAX = 255
    private const val ALPHA = 0xFF000000.toInt()
}
