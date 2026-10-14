// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.GzipSink
import okio.Sink
import okio.buffer
import pm.asaf.inkpad.core.model.Document

/** Writes documents as gzip-compressed XML, the format Xournal++ 1.3 writes. */
object XoppWriter {
    /** Writes [document] to [sink] and closes it. */
    fun write(
        document: Document,
        sink: Sink,
    ) {
        GzipSink(sink).buffer().use { writeXml(document, it) }
    }

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
