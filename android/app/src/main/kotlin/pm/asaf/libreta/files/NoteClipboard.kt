// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.libreta.core.format.ElementsXml
import pm.asaf.libreta.core.model.Element

/**
 * Elements on the system clipboard, in Libreta's own format (.xopp XML, [ElementsXml]): copied in
 * one note, pasted in any, also after the note closed.
 */
class NoteClipboard(
    context: Context,
) {
    private val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
    private val elementsState = MutableStateFlow(holdsElements())

    /** Whether the clipboard holds elements to paste. */
    val hasElements: StateFlow<Boolean> = elementsState.asStateFlow()

    init {
        clipboard.addPrimaryClipChangedListener { elementsState.value = holdsElements() }
    }

    fun copy(elements: List<Element>) {
        if (elements.isEmpty()) return
        val description = ClipDescription(LABEL, arrayOf(MIME_TYPE))
        clipboard.setPrimaryClip(ClipData(description, ClipData.Item(ElementsXml.encode(elements))))
        elementsState.value = true
    }

    /** The elements on the clipboard, or null if it holds none. */
    fun paste(): List<Element>? =
        clipboard.primaryClip
            ?.takeIf { it.description.hasMimeType(MIME_TYPE) && it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
            ?.let(ElementsXml::decode)

    private fun holdsElements(): Boolean = clipboard.primaryClipDescription?.hasMimeType(MIME_TYPE) == true

    private companion object {
        const val LABEL = "Libreta elements"
        const val MIME_TYPE = "application/x-libreta-elements"
    }
}
