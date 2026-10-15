// SPDX-License-Identifier: GPL-2.0-or-later
// Default name pattern from Xournal++ v1.3.8 src/core/control/settings/Settings.cpp (`%F-Note-%H-%M`),
// © Xournal++ contributors.

package pm.asaf.inkpad.core.model

/** Paper sizes for new notes, portrait, in points. */
enum class PaperSize(
    val width: Double,
    val height: Double,
) {
    A4(595.28, 841.89),
    LETTER(612.0, 792.0),
    A5(419.53, 595.28),
    LEGAL(612.0, 1008.0),
}

/** New notes: Xournal++-style names and a first page. */
object NewNote {
    const val EXTENSION = ".xopp"
    private const val CURRENT_FILE_VERSION = 4

    /**
     * `yyyy-MM-dd-Note-HH-mm.xopp` (the pattern Xournal++ uses), with " (2)", " (3)", …
     * added when a file of that name is in [existing].
     */
    fun suggestedName(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        existing: Set<String>,
    ): String {
        val base = "${year.pad(4)}-${month.pad(2)}-${day.pad(2)}-Note-${hour.pad(2)}-${minute.pad(2)}"
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) "$base$EXTENSION" else "$base ($it)$EXTENSION" }
            .first { it !in existing }
    }

    /** A document with one page of [paper] size, by default white and lined. */
    fun document(
        paper: PaperSize,
        landscape: Boolean,
        creator: String,
        background: Background.Solid = Background.Solid(Color.WHITE, PageStyle.LINED),
    ): Document {
        val template =
            NewPageTemplate(
                copyCurrent = false,
                paper,
                landscape,
                background.style ?: PageStyle.LINED,
                background.color,
            )
        return Document(
            pages = listOf(template.newPage(null, -1)),
            creator = creator,
            fileVersion = CURRENT_FILE_VERSION,
            title = "Inkpad document",
        )
    }

    private fun Int.pad(length: Int): String = toString().padStart(length, '0')
}

/**
 * How new pages look (Xournal++'s page template): by default like the page they follow, size
 * and background ([copyCurrent], Xournal++'s "copy last page settings"); otherwise, or for a
 * note's first page, [paper] in the given orientation with a solid [style] background.
 */
data class NewPageTemplate(
    val copyCurrent: Boolean = true,
    val paper: PaperSize = PaperSize.A4,
    val landscape: Boolean = false,
    val style: PageStyle = PageStyle.LINED,
    val color: Color = Color.WHITE,
) {
    /** A page with one empty layer, to follow [current] (at [currentIndex]), or to start a note. */
    fun newPage(
        current: Page?,
        currentIndex: Int,
    ): Page {
        if (copyCurrent && current != null) {
            return Page(
                current.width,
                current.height,
                copiedBackground(current, currentIndex),
                listOf(Layer(emptyList())),
            )
        }
        val (width, height) = if (landscape) paper.height to paper.width else paper.width to paper.height
        return Page(width, height, Background.Solid(color, style), listOf(Layer(emptyList())))
    }

    /** Like Xournal++: the same PDF page, the same image (as a clone of it), or the same ruling and colour. */
    private fun copiedBackground(
        current: Page,
        currentIndex: Int,
    ): Background? =
        when (val background = current.background) {
            is Background.Pdf -> {
                background.copy(domain = null, filename = null)
            }

            is Background.Pixmap -> {
                if (background.domain ==
                    FileDomain.CLONE
                ) {
                    background
                } else {
                    Background.Pixmap(FileDomain.CLONE, currentIndex.toString())
                }
            }

            is Background.Solid -> {
                Background.Solid(background.color, background.style, background.config)
            }

            else -> {
                background
            }
        }
}
