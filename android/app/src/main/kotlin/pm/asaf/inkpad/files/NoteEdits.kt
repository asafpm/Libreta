// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.Page

/**
 * The open note as edited so far, kept across rotation: the document, whether it changed since
 * it was read or saved, and each page's edit version (matching its cached tiles).
 */
class NoteEdits(
    initial: Document,
) {
    @Volatile var document: Document = initial
        private set

    /** Changed since read or last saved. */
    @Volatile var dirty = false
        private set

    private val versions = IntArray(initial.pages.size)

    /** A copy of the pages' edit versions. */
    fun versions(): IntArray = synchronized(this) { versions.copyOf() }

    /** Page [index] is now [page], at edit [version]. */
    fun pageEdited(
        index: Int,
        page: Page,
        version: Int,
    ) = synchronized(this) {
        document = document.withPage(index, page)
        versions[index] = version
        dirty = true
    }

    /** [saved] is what was written: clean unless edited since. */
    fun saved(saved: Document) =
        synchronized(this) {
            if (document === saved) dirty = false
        }
}
