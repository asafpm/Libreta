// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page

/**
 * Elements as .xopp XML, e.g. for the clipboard: a one-page document with one layer, written
 * and read like any note, so nothing is lost.
 */
object ElementsXml {
    /** The creator written, by which [decode] knows the text is elements. */
    private const val CREATOR = "Libreta elements"

    fun encode(elements: List<Element>): String =
        XoppWriter.toXml(Document(listOf(Page(0.0, 0.0, null, listOf(Layer(elements)))), creator = CREATOR))

    /** The elements in [text], or null if it isn't what [encode] writes. */
    @Suppress("SwallowedException") // any other text simply isn't elements
    fun decode(text: String): List<Element>? =
        try {
            XoppReader
                .read(Buffer().write(text.encodeUtf8()))
                .document
                .takeIf { it.creator == CREATOR }
                ?.pages
                ?.singleOrNull()
                ?.layers
                ?.singleOrNull()
                ?.elements
        } catch (e: XoppFormatException) {
            null
        }
}
