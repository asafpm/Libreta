// SPDX-License-Identifier: GPL-2.0-or-later
// Container handling follows Xournal++ v1.3.8 src/core/control/xojfile/LoadHandler.cpp
// (openFile), © Xournal++ contributors.

package pm.asaf.inkpad.core.format

import okio.Buffer
import okio.ByteString
import okio.GzipSource
import okio.IOException
import okio.Source
import okio.buffer

/** Reads .xopp/.xoj documents (docs/xopp-format.md). */
object XoppReader {
    private const val CONTENT = "content.xml"
    private const val VERSION = "META-INF/version"
    private val versionPattern = Regex("""current=(\d+)\r?\nmin=(\d+)""")

    /** Reads a whole document from [source], which is consumed and closed. */
    fun read(source: Source): XoppReadResult = read(source.buffer().use { it.readByteString() })

    /** @throws XoppFormatException if [bytes] is not a readable document. */
    fun read(bytes: ByteString): XoppReadResult =
        when {
            bytes.startsWith(GZIP_MAGIC) -> decode(gunzip(bytes), XoppContainer.GZIP)
            bytes.startsWith(ZIP_MAGIC) -> readPackage(bytes)
            else -> decode(bytes.utf8(), XoppContainer.PLAIN_XML)
        }

    private fun readPackage(bytes: ByteString): XoppReadResult {
        val entries = ZipArchive.read(bytes)
        val content = entries[CONTENT] ?: throw XoppFormatException("Zip package without $CONTENT")
        val version =
            entries[VERSION]
                ?.utf8()
                ?.let { versionPattern.find(it) }
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: throw XoppFormatException("Zip package without a valid $VERSION")
        val attachments = entries.filterKeys { it != CONTENT }
        return decode(content.utf8(), XoppContainer.ZIP, attachments, version)
    }

    private fun decode(
        xml: String,
        container: XoppContainer,
        attachments: Map<String, ByteString> = emptyMap(),
        packageFileVersion: Int? = null,
    ): XoppReadResult {
        val context = DecodeContext(attachments)
        val document = DocumentCodec.decode(XmlTree.parse(xml), context, packageFileVersion)
        return XoppReadResult(document, context.warnings.toList(), container, attachments)
    }

    private fun gunzip(bytes: ByteString): String =
        try {
            GzipSource(Buffer().write(bytes)).buffer().use { it.readUtf8() }
        } catch (e: IOException) {
            throw XoppFormatException("Corrupt gzip data: ${e.message}", e)
        }

    private val GZIP_MAGIC = ByteString.of(0x1f, 0x8b.toByte())
    private val ZIP_MAGIC = ByteString.of(0x50, 0x4b) // "PK"
}
