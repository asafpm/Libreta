// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.GzipSource
import okio.buffer

/** The sample documents in `testdata/`, grouped by what they can be used to test. */
internal object SampleFiles {
    /** Libreta's own samples, written as Xournal++ writes. */
    val own: List<String> =
        listOf(
            "libreta-notes.xopp",
            "libreta-pdf.xopp",
            "libreta-sample.xopp",
        )

    /** Xournal++'s test files that are gzip-compressed or plain XML. */
    val xournalppGzipOrPlain: List<String> =
        listOf(
            "xournalpp/cjk/测试.unzipped.xopp",
            "xournalpp/cjk/测试.xopp",
            "xournalpp/development/README-Eraser-and-padded-box.xopp",
            "xournalpp/development/Setsquare_annotations.xopp",
            "xournalpp/load/layer.xml",
            "xournalpp/load/layer.xoj",
            "xournalpp/load/linebreaksLatex.xopp",
            "xournalpp/load/pages.xml",
            "xournalpp/load/pages.xoj",
            "xournalpp/load/relativePaths.xopp",
            "xournalpp/load/text.xml",
            "xournalpp/packaged_xopp/audioAttachment/old.xopp",
            "xournalpp/packaged_xopp/imgAttachment/doc_with_jpg.xopp",
            "xournalpp/packaged_xopp/imgAttachment/old.xopp",
            "xournalpp/packaged_xopp/imgBackground/old.xopp",
            "xournalpp/packaged_xopp/pdfBackground/old.xopp",
            "xournalpp/packaged_xopp/stroke/old.xopp",
            "xournalpp/packaged_xopp/suite.xopp",
            "xournalpp/packaged_xopp/suite_float_bw_compat.xopp",
            "xournalpp/pageTypeFormatCopy.xopp",
            "xournalpp/preview-test-no-preview.unzipped.xoj",
            "xournalpp/preview-test.unzipped.xoj",
            "xournalpp/preview-test.xoj",
            "xournalpp/preview-test2.xoj",
            "xournalpp/test1.unzipped.xoj",
            "xournalpp/test1.xoj",
        )

    /** Xournal++'s zip-packaged test files (written by Xournal++ 1.0.x). */
    val xournalppZip: List<String> =
        listOf(
            "xournalpp/packaged_xopp/audioAttachment/new.xopp",
            "xournalpp/packaged_xopp/imgAttachment/new.xopp",
            "xournalpp/packaged_xopp/imgBackground/new.xopp",
            "xournalpp/packaged_xopp/layer.xopp",
            "xournalpp/packaged_xopp/pages.xopp",
            "xournalpp/packaged_xopp/pdfBackground/new.xopp",
            "xournalpp/packaged_xopp/stroke/new.xopp",
            "xournalpp/packaged_xopp/stroke/width_recovery.xopp",
            "xournalpp/packaged_xopp/test.xopp",
            "xournalpp/packaged_xopp/testPreview.xopp",
            "xournalpp/packaged_xopp/testPreview2.xopp",
            "xournalpp/packaged_xopp/text.xopp",
        )

    /** Files written by the original Xournal (0.4.x), whose layout Libreta does not reproduce. */
    private val oldXournal: Set<String> =
        setOf("xournalpp/load/layer.xml", "xournalpp/load/layer.xoj", "xournalpp/load/text.xml")

    /** Gzip/plain files written by Libreta or Xournal++ (any version), whose XML layout we reproduce exactly. */
    val xournalppWrittenXml: List<String> = own + xournalppGzipOrPlain.filterNot { it in oldXournal }

    /** The XML of a gzip-compressed or plain sample. */
    fun xml(path: String): String {
        val bytes = TestData.read(path)
        val gzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        return if (gzip) {
            GzipSource(Buffer().write(bytes)).buffer().use { it.readUtf8() }
        } else {
            bytes.utf8()
        }
    }
}
