// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

/**
 * Immutable list of doubles without boxing. Equality compares contents bit for bit
 * (NaN equals NaN with the same bits, `-0.0` differs from `0.0`), so values survive a save/load cycle exactly.
 */
class DoubleList private constructor(
    private val values: DoubleArray,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): Double = values[index]

    /** A copy of the values. */
    fun toDoubleArray(): DoubleArray = values.copyOf()

    override fun equals(other: Any?): Boolean = other is DoubleList && values.bitwiseEquals(other.values)

    override fun hashCode(): Int = values.bitwiseHashCode()

    override fun toString(): String = values.joinToString(prefix = "DoubleList[", postfix = "]")

    companion object {
        /** The array is copied. */
        fun fromArray(values: DoubleArray): DoubleList = DoubleList(values.copyOf())

        fun of(vararg values: Double): DoubleList = DoubleList(values.copyOf())
    }
}
