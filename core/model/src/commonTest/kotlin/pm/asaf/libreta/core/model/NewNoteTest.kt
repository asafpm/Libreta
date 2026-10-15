// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewNoteTest {
    @Test
    fun suggestsXournalStyleNames() {
        assertEquals("2026-10-14-Note-14-30.xopp", NewNote.suggestedName(2026, 10, 14, 14, 30, existing = emptySet()))
        assertEquals("2026-01-02-Note-03-04.xopp", NewNote.suggestedName(2026, 1, 2, 3, 4, existing = emptySet()))
    }

    @Test
    fun avoidsExistingNames() {
        val existing = setOf("2026-10-14-Note-14-30.xopp", "2026-10-14-Note-14-30 (2).xopp")
        assertEquals("2026-10-14-Note-14-30 (3).xopp", NewNote.suggestedName(2026, 10, 14, 14, 30, existing))
    }

    @Test
    fun createsOneLinedPage() {
        val document = NewNote.document(PaperSize.A4, landscape = false, creator = "Libreta 0.1.0")
        val page = document.pages.single()
        assertEquals(595.28, page.width)
        assertEquals(841.89, page.height)
        assertEquals(Background.Solid(Color.WHITE, PageStyle.LINED), page.background)
        assertEquals(listOf(Layer(emptyList())), page.layers)
        assertEquals("Libreta 0.1.0", document.creator)
        assertEquals(4, document.fileVersion)
    }

    @Test
    fun landscapeSwapsTheSides() {
        val page = NewNote.document(PaperSize.LETTER, landscape = true, creator = "x").pages.single()
        assertEquals(792.0, page.width)
        assertEquals(612.0, page.height)
    }

    @Test
    fun aPdfGivesOnePagePerPdfPageOnlyTheFirstNamingIt() {
        val sizes = listOf(612.0 to 792.0, 792.0 to 612.0, 595.28 to 841.89)
        val document = NewNote.forPdf(sizes, FileDomain.ABSOLUTE, "paper.pdf", creator = "Libreta 0.1.0")
        assertEquals(sizes, document.pages.map { it.width to it.height })
        assertEquals(
            listOf(
                Background.Pdf(1, FileDomain.ABSOLUTE, "paper.pdf"),
                Background.Pdf(2),
                Background.Pdf(3),
            ),
            document.pages.map { it.background },
        )
        assertTrue(document.pages.all { it.layers == listOf(Layer(emptyList())) })
        assertEquals("Libreta 0.1.0", document.creator)
        assertEquals(4, document.fileVersion)
    }

    @Test
    fun aPdfsNotesAreLookedForUnderXournalNames() {
        assertEquals(
            listOf("paper.xopp", "paper.xoj", "paper.pdf.xopp", "paper.pdf.xoj"),
            NewNote.notesForPdf("paper.pdf"),
        )
        assertEquals(
            NewNote.notesForPdf("paper.pdf"),
            NewNote.notesForPdf("paper.PDF").map { it.replace("PDF", "pdf") },
        )
        assertEquals(listOf("a.b.xopp", "a.b.xoj", "a.b.pdf.xopp", "a.b.pdf.xoj"), NewNote.notesForPdf("a.b.pdf"))
    }
}
