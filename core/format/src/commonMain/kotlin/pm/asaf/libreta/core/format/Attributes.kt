// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import pm.asaf.libreta.core.model.AudioLink
import pm.asaf.libreta.core.model.Color

/** A known element could not be interpreted; the decoder keeps it verbatim instead. */
internal class DecodeException(
    message: String,
) : Exception(message)

/**
 * Typed access to an element's attributes. Every attribute that is read counts as known;
 * [extra] returns the rest, in their original order, for `extraAttributes`.
 */
internal class AttributeReader(
    private val attributes: Map<String, String>,
) {
    private val known = mutableSetOf<String>()

    fun string(name: String): String? {
        known += name
        return attributes[name]
    }

    fun requireString(name: String): String = string(name) ?: throw DecodeException("missing attribute '$name'")

    fun double(name: String): Double? = string(name)?.let { NumberCodec.parseDouble(it) ?: invalid(name, it) }

    fun requireDouble(name: String): Double = double(name) ?: throw DecodeException("missing attribute '$name'")

    fun long(name: String): Long? = string(name)?.let { NumberCodec.parseLong(it) ?: invalid(name, it) }

    fun int(name: String): Int? =
        long(name)?.let {
            if (it in
                Int.MIN_VALUE..Int.MAX_VALUE
            ) {
                it.toInt()
            } else {
                invalid(name, it)
            }
        }

    fun requireColor(
        name: String,
        usage: ColorCodec.Usage,
    ): Color {
        val text = requireString(name)
        return ColorCodec.parse(text, usage) ?: invalid(name, text)
    }

    /** Treats [name] as unknown again, e.g. because its value could not be interpreted. */
    fun keepAsExtra(name: String) {
        known -= name
    }

    fun extra(): Map<String, String> = attributes.filterKeys { it !in known }

    private fun invalid(
        name: String,
        value: Any,
    ): Nothing = throw DecodeException("invalid value $name=\"$value\"")
}

/** Builds an ordered attribute map: known attributes first (nulls skipped), then extras. */
internal class AttributeWriter {
    private val attributes = LinkedHashMap<String, String>()

    fun put(
        name: String,
        value: String?,
    ): AttributeWriter {
        if (value != null) attributes[name] = value
        return this
    }

    fun put(
        name: String,
        value: Double?,
    ): AttributeWriter = put(name, value?.let(NumberCodec::formatDouble))

    fun put(
        name: String,
        value: Long?,
    ): AttributeWriter = put(name, value?.toString())

    /** Extra attributes never override known ones. */
    fun putExtra(extra: Map<String, String>): AttributeWriter {
        for ((name, value) in extra) attributes.getOrPut(name) { value }
        return this
    }

    fun build(): Map<String, String> = attributes
}

/** The `ts`/`fn` audio link of strokes and texts; null when neither attribute is present. */
internal fun AttributeReader.audio(): AudioLink? {
    val timestamp = long("ts")
    val filename = string("fn")
    return if (timestamp == null && filename == null) null else AudioLink(timestamp, filename)
}

internal fun AttributeWriter.putAudio(audio: AudioLink?): AttributeWriter =
    put("ts", audio?.timestamp).put("fn", audio?.filename)
