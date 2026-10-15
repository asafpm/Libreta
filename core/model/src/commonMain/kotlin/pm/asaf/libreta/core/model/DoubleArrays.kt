// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

/** Bit-for-bit comparison: NaN equals NaN with the same bits, `-0.0` differs from `0.0`. */
internal fun DoubleArray.bitwiseEquals(other: DoubleArray): Boolean =
    size == other.size && indices.all { this[it].toRawBits() == other[it].toRawBits() }

internal fun DoubleArray.bitwiseHashCode(): Int =
    fold(1) { hash, value -> HASH_MULTIPLIER * hash + value.toRawBits().hashCode() }

private const val HASH_MULTIPLIER = 31
