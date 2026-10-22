// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Test
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.format.XoppWriter
import java.io.File

class NoteEncoderTest {
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))

    @Test
    fun encodesAsTheWriterDoes() {
        val document =
            File(testData, "libreta-sample.xopp")
                .source()
                .buffer()
                .use { XoppReader.read(it) }
                .document
        val encoded = NoteEncoder.encode(document)
        // The same XML, compressed the same way.
        assertEquals(XoppWriter.toByteString(document), encoded)
        assertEquals(document, XoppReader.read(encoded).document)
    }
}
