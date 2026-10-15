// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/model/BackgroundConfig.cpp, © Xournal++ contributors.

package pm.asaf.libreta.core.geometry

/**
 * The `config` attribute of a solid background (docs/rendering.md §2.1): comma-separated
 * entries, each split at its last `=`. Values are read like a C++ stream reads them: the
 * leading number counts, and a value that does not start with a number reads as 0.
 */
class BackgroundConfig(
    config: String?,
) {
    private val values: Map<String, String> =
        config
            .orEmpty()
            .split(',')
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator < 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
            }.toMap()

    fun double(key: String): Double? = values[key]?.let { leading(DECIMAL, it)?.toDoubleOrNull() ?: 0.0 }

    fun int(key: String): Int? = values[key]?.let { leading(INTEGER, it)?.toIntOrNull() ?: 0 }

    /** A hexadecimal number (optionally `0x`-prefixed), as `0xAARRGGBB`; alpha is not used. */
    fun hex(key: String): UInt? =
        values[key]?.let { value ->
            leading(HEX, value)?.let { it.removePrefix("0x").removePrefix("0X").toUIntOrNull(radix = 16) }
                ?: 0u
        }

    private fun leading(
        pattern: Regex,
        value: String,
    ): String? =
        pattern
            .find(value)
            ?.value
            ?.trim()
            ?.removePrefix("+")

    private companion object {
        val DECIMAL = Regex("""^\s*[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")
        val INTEGER = Regex("""^\s*[+-]?\d+""")
        val HEX = Regex("""^\s*(0[xX])?[0-9a-fA-F]+""")
    }
}
