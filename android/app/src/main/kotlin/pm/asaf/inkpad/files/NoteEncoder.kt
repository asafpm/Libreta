// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import okio.Buffer
import okio.ByteString
import okio.Pipe
import okio.buffer
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.model.Document
import kotlin.concurrent.thread

/**
 * Encodes notes as .xopp files ([XoppWriter]), compressing on a second thread while the XML
 * is written: for a large note, saving takes about as long as the slower of the two.
 */
object NoteEncoder {
    private const val PIPE_SIZE = 1L shl 20

    fun encode(document: Document): ByteString {
        val pipe = Pipe(PIPE_SIZE)
        val compressed = Buffer()
        var failure: Throwable? = null
        val compressor =
            thread(name = "xopp-gzip") {
                try {
                    XoppWriter.gzip(compressed).buffer().use { it.writeAll(pipe.source) }
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Throwable,
                ) {
                    failure = e
                    // The writer must not wait for room in the pipe.
                    pipe.cancel()
                }
            }
        try {
            pipe.sink.buffer().use { XoppWriter.writeXml(document, it) }
        } finally {
            compressor.join()
        }
        failure?.let { throw it }
        return compressed.readByteString()
    }
}
