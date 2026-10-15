// SPDX-License-Identifier: GPL-2.0-or-later
// Container handling follows Xournal++ v1.3.8 src/core/control/xojfile/LoadHandler.cpp
// (openFile), © Xournal++ contributors.

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.BufferedSource
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

    /**
     * Reads a whole document from [source], which is consumed and closed. Gzip and plain XML
     * are parsed as they are read.
     *
     * @throws XoppFormatException if it is not a readable document.
     */
    fun read(source: Source): XoppReadResult =
        source.buffer().use { input ->
            when {
                input.startsWith(GZIP_MAGIC) -> decode(GzipSource(input).buffer(), XoppContainer.GZIP)
                input.startsWith(ZIP_MAGIC) -> readPackage(input.readByteString())
                else -> decode(input, XoppContainer.PLAIN_XML)
            }
        }

    /** @throws XoppFormatException if [bytes] is not a readable document. */
    fun read(bytes: ByteString): XoppReadResult = read(Buffer().write(bytes))

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
        return decode(Buffer().write(content), XoppContainer.ZIP, attachments, version)
    }

    private fun decode(
        xml: BufferedSource,
        container: XoppContainer,
        attachments: Map<String, ByteString> = emptyMap(),
        packageFileVersion: Int? = null,
    ): XoppReadResult {
        val context = DecodeContext(attachments, isPackage = container == XoppContainer.ZIP)
        val tree =
            try {
                XmlTree.parse(xml)
            } catch (e: IOException) {
                throw XoppFormatException("Corrupt ${container.name.lowercase()} data: ${e.message}", e)
            }
        val document = DocumentCodec.decode(tree, context, packageFileVersion)
        return XoppReadResult(document, context.warnings.toList(), container, attachments)
    }

    private fun BufferedSource.startsWith(prefix: ByteString): Boolean =
        request(prefix.size.toLong()) && rangeEquals(0, prefix)

    private val GZIP_MAGIC = ByteString.of(0x1f, 0x8b.toByte())
    private val ZIP_MAGIC = ByteString.of(0x50, 0x4b) // "PK"
}
