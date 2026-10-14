// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

/**
 * Immutable stroke points in document coordinates (PDF points), stored as interleaved
 * `x0, y0, x1, y1, …` doubles. Equality compares contents bit for bit.
 */
class PointList private constructor(
    private val coordinates: DoubleArray,
) {
    val size: Int get() = coordinates.size / 2

    fun x(index: Int): Double = coordinates[2 * index]

    fun y(index: Int): Double = coordinates[2 * index + 1]

    /** A copy of the interleaved coordinates. */
    fun toInterleavedArray(): DoubleArray = coordinates.copyOf()

    override fun equals(other: Any?): Boolean = other is PointList && coordinates.bitwiseEquals(other.coordinates)

    override fun hashCode(): Int = coordinates.bitwiseHashCode()

    override fun toString(): String = "PointList(size=$size)"

    companion object {
        val EMPTY: PointList = PointList(DoubleArray(0))

        /** Points from interleaved `x, y` values; the array is copied. */
        fun fromInterleaved(coordinates: DoubleArray): PointList {
            require(coordinates.size % 2 == 0) { "odd number of coordinates: ${coordinates.size}" }
            return PointList(coordinates.copyOf())
        }

        fun of(vararg coordinates: Double): PointList = fromInterleaved(coordinates)
    }
}
