// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong

private const val SIGNIFICANT_DIGITS = 8

/** 10^0 to 10^22: exactly representable as doubles. */
private val POWERS_OF_TEN =
    DoubleArray(23).also { powers ->
        powers[0] = 1.0
        for (i in 1 until powers.size) powers[i] = powers[i - 1] * 10
    }

/**
 * This value to 8 significant digits, the precision Xournal++ writes (`%.8g`), so that what
 * Inkpad creates is as short in files (docs/xopp-format.md §3). Far beyond what a pen can
 * place: 0.00001 pt for coordinates in the hundreds.
 */
internal fun Double.toEightDigits(): Double {
    val places = if (this == 0.0 || !isFinite()) -1 else SIGNIFICANT_DIGITS - 1 - floor(log10(abs(this))).toInt()
    // Zero, huge or tiny values (never coordinates) stay as they are.
    if (places !in POWERS_OF_TEN.indices) return this
    // Below 10^8, so the division is correctly rounded: the double of the 8-digit decimal.
    val rounded = (abs(this) * POWERS_OF_TEN[places]).roundToLong().toDouble() / POWERS_OF_TEN[places]
    return if (this < 0) -rounded else rounded
}
