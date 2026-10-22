// SPDX-License-Identifier: GPL-2.0-or-later
// Expectations ported from Xournal++ v1.3.8 test/unit_tests/util/XojPreviewExtractorTest.cpp,
// © Xournal++ contributors.

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XoppPreviewTest {
    private fun preview(path: String): ByteString? = XoppPreview.read(Buffer().write(TestData.read(path)))

    private fun ByteString.isPng() = size > 8 && substring(1, 4).utf8() == "PNG"

    @Test
    fun readsTheEmbeddedPreviewOfGzipDocuments() {
        for (path in listOf(
            "xournalpp/packaged_xopp/suite.xopp",
            "xournalpp/development/Setsquare_annotations.xopp",
            "xournalpp/packaged_xopp/pdfBackground/old.xopp",
            "xournalpp/preview-test2.xoj",
        )) {
            val preview = assertNotNull(preview(path), path)
            assertTrue(preview.isPng(), path)
            // Same bytes as the full reader gets.
            assertEquals(XoppReader.read(TestData.read(path)).document.preview, preview, path)
        }
        assertEquals(2856, preview("xournalpp/preview-test2.xoj")?.size)
        // Xournal++'s fixture holds text, not a PNG.
        assertEquals("CppUnitTestString".encodeUtf8(), preview("xournalpp/preview-test.xoj"))
    }

    @Test
    fun readsPlainXml() {
        assertEquals("CppUnitTestString".encodeUtf8(), preview("xournalpp/preview-test.unzipped.xoj"))
    }

    @Test
    fun readsTheThumbnailOfZipPackages() {
        // The entry is returned as is.
        assertEquals("CppUnitTestString \n".encodeUtf8(), preview("xournalpp/packaged_xopp/testPreview.xopp"))
        val png = assertNotNull(preview("xournalpp/packaged_xopp/testPreview2.xopp"))
        assertEquals(804, png.size)
        assertTrue(png.isPng())
    }

    @Test
    fun nullWithoutAPreview() {
        assertNull(preview("xournalpp/preview-test-no-preview.unzipped.xoj"))
        assertNull(preview("xournalpp/load/text.xml"))
    }

    @Test
    fun nullForInvalidInput() {
        assertNull(preview("xournalpp/preview-test-invalid.xoj"))
        assertNull(XoppPreview.read(Buffer().write("not a document".encodeUtf8())))
        assertNull(XoppPreview.read(Buffer().write(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3))))
    }

    @Test
    fun stopsAtThePages() {
        // A preview after the first page is not a document preview (and is never searched for).
        val preview = "<preview>${"PNG".encodeUtf8().base64()}</preview>"
        val xml = "<xournal><page width=\"1\" height=\"1\"><layer/></page>$preview</xournal>"
        assertNull(XoppPreview.read(Buffer().writeUtf8(xml)))
    }
}
