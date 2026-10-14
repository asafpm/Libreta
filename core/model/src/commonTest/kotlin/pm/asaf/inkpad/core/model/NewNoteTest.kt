// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

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
        val document = NewNote.document(PaperSize.A4, landscape = false, creator = "Inkpad 0.1.0")
        val page = document.pages.single()
        assertEquals(595.28, page.width)
        assertEquals(841.89, page.height)
        assertEquals(Background.Solid(Color.WHITE, PageStyle.LINED), page.background)
        assertEquals(listOf(Layer(emptyList())), page.layers)
        assertEquals("Inkpad 0.1.0", document.creator)
        assertEquals(4, document.fileVersion)
    }

    @Test
    fun landscapeSwapsTheSides() {
        val page = NewNote.document(PaperSize.LETTER, landscape = true, creator = "x").pages.single()
        assertEquals(792.0, page.width)
        assertEquals(612.0, page.height)
    }
}
