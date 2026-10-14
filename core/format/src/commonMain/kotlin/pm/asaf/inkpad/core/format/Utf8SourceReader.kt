// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import nl.adaptivity.xmlutil.core.impl.multiplatform.Reader
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8

/**
 * Characters of the UTF-8 text in [source], decoded a chunk at a time, so that a document
 * is parsed while it is decompressed and never held whole as one string. A leading byte
 * order mark is skipped.
 */
internal class Utf8SourceReader(
    private val source: BufferedSource,
) : Reader() {
    private var chunk = ""
    private var position = 0

    init {
        if (source.rangeEquals(0, BYTE_ORDER_MARK)) source.skip(BYTE_ORDER_MARK.size.toLong())
    }

    override fun read(
        buf: CharArray,
        offset: Int,
        len: Int,
    ): Int {
        if (position == chunk.length && !fill()) return -1
        val count = minOf(len, chunk.length - position)
        chunk.toCharArray(buf, offset, position, position + count)
        position += count
        return count
    }

    override fun close() = source.close()

    private fun fill(): Boolean {
        if (!source.request(1)) return false
        source.request(CHUNK_BYTES.toLong())
        val available = source.buffer.size
        val size = if (available > CHUNK_BYTES) completeSequences(CHUNK_BYTES) else available.toInt()
        chunk = source.readUtf8(size.toLong())
        position = 0
        return chunk.isNotEmpty()
    }

    /** At most [limit] bytes of the buffer, not ending inside a multi-byte sequence. */
    private fun completeSequences(limit: Int): Int {
        val buffer = source.buffer
        var start = limit - 1
        // Back up to the first byte of the last sequence.
        while (start > 0 && buffer[start.toLong()].toInt() and CONTINUATION_MASK == CONTINUATION) start--
        val lead = buffer[start.toLong()].toInt() and BYTE
        val length =
            when {
                lead < 0x80 -> 1
                lead >= 0xF0 -> 4
                lead >= 0xE0 -> 3
                else -> 2
            }
        return if (start + length <= limit) limit else start
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val CONTINUATION_MASK = 0xC0
        const val CONTINUATION = 0x80
        const val BYTE = 0xFF
        val BYTE_ORDER_MARK = "﻿".encodeUtf8()
    }
}
