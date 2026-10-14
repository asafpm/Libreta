// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import okio.ByteString
import pm.asaf.inkpad.core.model.Document

/** Result of [XoppReader.read]. */
data class XoppReadResult(
    val document: Document,
    /** Content that could not be interpreted; it is kept verbatim in the document. */
    val warnings: List<FormatWarning>,
    val container: XoppContainer,
    /**
     * Files stored next to `content.xml` in a zip package (e.g. `attachments/bg.pdf`),
     * keyed by path. Empty for other containers. Images are already resolved into the
     * document.
     */
    val attachments: Map<String, ByteString> = emptyMap(),
)

data class FormatWarning(
    /** Where in the document, e.g. `page 2 › layer 1 › element 5 <stroke>`. */
    val location: String,
    val message: String,
)

/** How the document was stored (docs/xopp-format.md §1). */
enum class XoppContainer {
    /** Gzip-compressed XML: what Xournal++ writes. */
    GZIP,

    /** Uncompressed XML. */
    PLAIN_XML,

    /** Zip package written by Xournal++ 1.0.x (`content.xml` plus attachments). */
    ZIP,
}
