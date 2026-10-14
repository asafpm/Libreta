// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap

/**
 * Bitmaps by key, bounded by their total size in bytes; the least recently used go first,
 * handed to [onEvict]. Thread-safe.
 */
class BitmapLru<K : Any>(
    val maxBytes: Long,
    private val onEvict: (Bitmap) -> Unit = {},
) {
    // Access order: iteration starts at the least recently used entry.
    private val entries = LinkedHashMap<K, Bitmap>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var bytes = 0L

    /** Total size of the cached bitmaps. */
    val sizeBytes: Long @Synchronized get() = bytes

    val size: Int @Synchronized get() = entries.size

    @Synchronized
    operator fun get(key: K): Bitmap? = entries[key]

    @Synchronized
    operator fun contains(key: K): Boolean = entries.containsKey(key)

    @Synchronized
    fun put(
        key: K,
        bitmap: Bitmap,
    ) {
        entries.put(key, bitmap)?.let { old ->
            bytes -= old.allocationByteCount
            if (old !== bitmap) onEvict(old)
        }
        bytes += bitmap.allocationByteCount
        trim()
    }

    /** Calls [block] for each entry whose key matches [filter], without changing the order. */
    @Synchronized
    fun forEach(
        filter: (K) -> Boolean,
        block: (K, Bitmap) -> Unit,
    ) {
        for ((key, bitmap) in entries) if (filter(key)) block(key, bitmap)
    }

    /** Takes the bitmap of [key] out of the cache without evicting it (e.g. to store it under another key). */
    @Synchronized
    fun take(key: K): Bitmap? = entries.remove(key)?.also { bytes -= it.allocationByteCount }

    /** The keys matching [filter]. */
    @Synchronized
    fun keys(filter: (K) -> Boolean): List<K> = entries.keys.filter(filter)

    /** Removes the entries whose key matches [filter]. */
    @Synchronized
    fun removeIf(filter: (K) -> Boolean) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (key, bitmap) = iterator.next()
            if (filter(key)) {
                iterator.remove()
                bytes -= bitmap.allocationByteCount
                onEvict(bitmap)
            }
        }
    }

    @Synchronized
    fun clear() = removeIf { true }

    private fun trim() {
        val iterator = entries.values.iterator()
        while (bytes > maxBytes && iterator.hasNext()) {
            val bitmap = iterator.next()
            iterator.remove()
            bytes -= bitmap.allocationByteCount
            onEvict(bitmap)
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
