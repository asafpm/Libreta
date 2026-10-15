// SPDX-License-Identifier: GPL-2.0-or-later

// Field offsets of the zip file format (APPNOTE.TXT) are clearer as literals.
@file:Suppress("MagicNumber")

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.ByteString
import okio.Inflater
import okio.InflaterSource
import okio.buffer

/**
 * Minimal reader for zip archives held in memory, enough for Xournal++ 1.0.x packages:
 * stored and deflated entries, no encryption, no zip64.
 */
internal object ZipArchive {
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
    private const val CENTRAL_DIRECTORY_ENTRY = 0x02014b50
    private const val LOCAL_FILE_HEADER = 0x04034b50
    private const val END_RECORD_SIZE = 22
    private const val MAX_COMMENT_LENGTH = 0xffff
    private const val CENTRAL_ENTRY_SIZE = 46
    private const val LOCAL_HEADER_SIZE = 30
    private const val STORED = 0
    private const val DEFLATED = 8

    /** Entries by path, excluding directories. */
    fun read(zip: ByteString): Map<String, ByteString> {
        val end = findEndRecord(zip)
        val entryCount = zip.u16(end + 10)
        var offset = zip.u32(end + 16)
        val entries = LinkedHashMap<String, ByteString>()
        repeat(entryCount) {
            if (zip.u32(offset) != CENTRAL_DIRECTORY_ENTRY) corrupt("bad central directory entry")
            val method = zip.u16(offset + 10)
            val compressedSize = zip.u32(offset + 20)
            val size = zip.u32(offset + 24)
            val nameLength = zip.u16(offset + 28)
            val extraLength = zip.u16(offset + 30)
            val commentLength = zip.u16(offset + 32)
            val localHeader = zip.u32(offset + 42)
            val name = zip.substring(offset + CENTRAL_ENTRY_SIZE, offset + CENTRAL_ENTRY_SIZE + nameLength).utf8()
            if (!name.endsWith('/')) entries[name] = entryData(zip, localHeader, method, compressedSize, size, name)
            offset += CENTRAL_ENTRY_SIZE + nameLength + extraLength + commentLength
        }
        return entries
    }

    private fun findEndRecord(zip: ByteString): Int {
        val last = zip.size - END_RECORD_SIZE
        val first = maxOf(0, last - MAX_COMMENT_LENGTH)
        return (last downTo first).firstOrNull { zip.u32(it) == END_OF_CENTRAL_DIRECTORY }
            ?: corrupt("end of central directory not found")
    }

    private fun entryData(
        zip: ByteString,
        localHeader: Int,
        method: Int,
        compressedSize: Int,
        size: Int,
        name: String,
    ): ByteString {
        if (zip.u32(localHeader) != LOCAL_FILE_HEADER) corrupt("bad local header for $name")
        val start = localHeader + LOCAL_HEADER_SIZE + zip.u16(localHeader + 26) + zip.u16(localHeader + 28)
        if (start + compressedSize > zip.size) corrupt("truncated entry $name")
        val compressed = zip.substring(start, start + compressedSize)
        val data =
            when (method) {
                STORED -> {
                    compressed
                }

                DEFLATED -> {
                    InflaterSource(
                        Buffer().write(compressed),
                        Inflater(true),
                    ).buffer().use { it.readByteString() }
                }

                else -> {
                    corrupt("unsupported compression method $method for $name")
                }
            }
        if (data.size != size) corrupt("size mismatch for $name")
        return data
    }

    private fun ByteString.u16(index: Int): Int {
        if (index < 0 || index + 2 > size) corrupt("truncated archive")
        return (this[index].toInt() and 0xff) or ((this[index + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteString.u32(index: Int): Int = u16(index) or (u16(index + 2) shl 16)

    private fun corrupt(reason: String): Nothing = throw XoppFormatException("Corrupt zip package: $reason")
}
