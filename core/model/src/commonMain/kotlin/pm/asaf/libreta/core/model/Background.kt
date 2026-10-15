// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

import kotlin.jvm.JvmInline

/** A page background (docs/xopp-format.md §4). */
sealed interface Background {
    data class Solid(
        val color: Color,
        val style: PageStyle?,
        /** Pattern parameters, e.g. line spacing; format defined by Xournal++. */
        val config: String? = null,
        val name: String? = null,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Background

    /**
     * A page of a PDF file. Only the first PDF page of a document names the file;
     * later ones carry just [pageNumber].
     */
    data class Pdf(
        /** 1-based page number in the PDF. */
        val pageNumber: Long?,
        val domain: FileDomain? = null,
        val filename: String? = null,
        val name: String? = null,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Background

    /** An image background; for [FileDomain.CLONE], [filename] is the index of the page to copy. */
    data class Pixmap(
        val domain: FileDomain?,
        val filename: String?,
        val name: String? = null,
        val extraAttributes: Map<String, String> = emptyMap(),
    ) : Background

    /** A background type Libreta does not know, kept verbatim. */
    data class Unknown(
        val element: ForeignElement,
    ) : Background
}

/** Where a background file lives. */
enum class FileDomain(
    val xmlName: String,
) {
    /** A path, absolute or relative to the document's directory. */
    ABSOLUTE("absolute"),

    /** Stored next to the document (`<doc>.<filename>`) or, in zip packages, inside it. */
    ATTACH("attach"),

    /** Reuses another page's background image. */
    CLONE("clone"),
    ;

    companion object {
        fun fromXmlName(name: String): FileDomain? = entries.firstOrNull { it.xmlName == name }
    }
}

/** Ruling of a solid background. Unknown styles (e.g. `:copy`) are kept as they are. */
@JvmInline
value class PageStyle(
    val value: String,
) {
    companion object {
        val PLAIN: PageStyle = PageStyle("plain")
        val RULED: PageStyle = PageStyle("ruled")
        val LINED: PageStyle = PageStyle("lined")
        val STAVES: PageStyle = PageStyle("staves")
        val GRAPH: PageStyle = PageStyle("graph")
        val DOTTED: PageStyle = PageStyle("dotted")
        val ISO_DOTTED: PageStyle = PageStyle("isodotted")
        val ISO_GRAPH: PageStyle = PageStyle("isograph")
    }
}
