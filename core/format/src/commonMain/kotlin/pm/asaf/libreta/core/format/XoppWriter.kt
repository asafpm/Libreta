// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.GzipSink
import okio.Sink
import okio.buffer
import pm.asaf.libreta.core.model.Document

/** Writes documents as gzip-compressed XML, the format Xournal++ 1.3 writes. */
object XoppWriter {
    /**
     * The zlib level: gzip's default (6) takes four times as long on a tablet, for files 10%
     * smaller (docs/xopp-format.md §1). Any level reads the same.
     */
    const val COMPRESSION_LEVEL = 2

    /** Writes [document] to [sink] and closes it. */
    fun write(
        document: Document,
        sink: Sink,
    ) {
        gzip(sink).buffer().use { writeXml(document, it) }
    }

    /** A sink compressing into [sink] as [write] does. */
    fun gzip(sink: Sink): Sink = GzipSink(sink).apply { deflater.setLevel(COMPRESSION_LEVEL) }

    fun toByteString(document: Document): ByteString = Buffer().also { write(document, it) }.readByteString()

    /** Writes the uncompressed XML of [document]. */
    fun writeXml(
        document: Document,
        sink: BufferedSink,
    ) {
        XmlTree.write(DocumentCodec.encode(document), sink)
    }

    fun toXml(document: Document): String = Buffer().also { writeXml(document, it) }.readUtf8()
}
