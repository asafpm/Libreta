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

    /** A document with one white, lined page. */
    fun document(
        paper: PaperSize,
        landscape: Boolean,
        creator: String,
    ): Document {
        val (width, height) = if (landscape) paper.height to paper.width else paper.width to paper.height
        val page =
            Page(
                width = width,
                height = height,
                background = Background.Solid(Color.WHITE, PageStyle.LINED),
                layers = listOf(Layer(emptyList())),
            )
        return Document(
            pages = listOf(page),
            creator = creator,
            fileVersion = CURRENT_FILE_VERSION,
            title = "Inkpad document",
        )
    }

    private fun Int.pad(length: Int): String = toString().padStart(length, '0')
}
