// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import pm.asaf.libreta.core.model.ForeignElement
import pm.asaf.libreta.core.model.ForeignText

/** State shared while decoding one document. */
internal class DecodeContext(
    /** Zip package entries by path; empty for gzip/plain files. */
    val attachments: Map<String, ByteString>,
    /** Whether the document comes from a zip package. */
    val isPackage: Boolean = false,
) {
    val warnings = mutableListOf<FormatWarning>()

    fun warn(
        location: String,
        message: String,
    ) {
        warnings += FormatWarning(location, message)
    }

    /**
     * Binary content of `<image>`/`<teximage>`: a base64 body, or (zip packages) an
     * `<attachment path="…"/>` child.
     */
    fun binaryContent(element: ForeignElement): ByteString {
        val attachment = element.children.filterIsInstance<ForeignElement>().singleOrNull { it.name == "attachment" }
        val data = if (attachment != null) attachmentContent(attachment) else base64Content(element)
        return data.takeIf { it.size > 0 } ?: throw DecodeException("no data")
    }

    private fun attachmentContent(attachment: ForeignElement): ByteString {
        val path = AttributeReader(attachment.attributes).requireString("path")
        return attachments[path] ?: throw DecodeException("attachment '$path' not found in the package")
    }

    private fun base64Content(element: ForeignElement): ByteString =
        element.textContent().decodeBase64() ?: throw DecodeException("invalid base64 data")
}

/** Text of an element that must contain only character data. */
internal fun ForeignElement.textContent(): String {
    if (children.any { it is ForeignElement }) throw DecodeException("unexpected child elements in <$name>")
    return children.joinToString(separator = "") { (it as ForeignText).text }
}
