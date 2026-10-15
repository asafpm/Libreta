// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/util/XojPreviewExtractor.cpp, © Xournal++ contributors.

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.GzipSource
import okio.IOException
import okio.Source
import okio.buffer

/**
 * Reads the preview image (PNG) of a document without reading the whole document, for
 * file browsers. Xournal++ writes `<preview>` right after `<title>`, before any page.
 */
object XoppPreview {
    private val PREVIEW_START = "<preview>".encodeUtf8()
    private val PREVIEW_END = "</preview>".encodeUtf8()
    private val PAGE_START = "<page".encodeUtf8()
    private const val THUMBNAIL = "thumbnails/thumbnail.png"

    /** The preview must start within this many bytes of XML. */
    private const val HEADER_LIMIT = 64L * 1024

    /** Longest preview read. */
    private const val PREVIEW_LIMIT = 8L * 1024 * 1024

    /** The embedded preview, or null if there is none or the input is not a readable document. Closes [source]. */
    fun read(source: Source): ByteString? =
        runCatching {
            source.buffer().use { input ->
                when {
                    input.startsWith(ZIP_MAGIC) -> ZipArchive.read(input.readByteString())[THUMBNAIL]
                    input.startsWith(GZIP_MAGIC) -> GzipSource(input).buffer().use { previewFromXml(it) }
                    else -> previewFromXml(input)
                }
            }
        }.getOrElse { e ->
            // Not gzip after all, truncated, corrupt zip package, …
            if (e is IOException || e is XoppFormatException) null else throw e
        }

    private fun previewFromXml(xml: BufferedSource): ByteString? {
        val start = xml.indexOf(PREVIEW_START, 0, HEADER_LIMIT)
        val contentStart = start + PREVIEW_START.size
        val end =
            when {
                start < 0 -> -1L

                xml.indexOf(PAGE_START, 0, start) >= 0 -> -1L

                // the preview belongs before the pages
                else -> xml.indexOf(PREVIEW_END, contentStart, contentStart + PREVIEW_LIMIT)
            }
        return if (end < 0) {
            null
        } else {
            val content = Buffer()
            xml.skip(contentStart)
            xml.read(content, end - contentStart)
            content
                .readUtf8()
                .trim()
                .decodeBase64()
                ?.takeIf { it.size > 0 }
        }
    }

    private fun BufferedSource.startsWith(prefix: ByteString): Boolean =
        request(prefix.size.toLong()) && rangeEquals(0, prefix)

    private val GZIP_MAGIC = ByteString.of(0x1f, 0x8b.toByte())
    private val ZIP_MAGIC = ByteString.of(0x50, 0x4b)
}
