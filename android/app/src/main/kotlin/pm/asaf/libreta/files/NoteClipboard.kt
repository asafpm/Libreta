// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.libreta.core.format.ElementsXml
import pm.asaf.libreta.core.model.Element

/**
 * Elements on the system clipboard, in Libreta's own format (.xopp XML, [ElementsXml]): copied in
 * one note, pasted in any, also after the note closed. Pictures copied in other apps paste too.
 */
class NoteClipboard(
    context: Context,
) {
    private val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
    private val pastable = MutableStateFlow(holdsSomething())

    /** Whether the clipboard holds elements or a picture to paste. */
    val canPaste: StateFlow<Boolean> = pastable.asStateFlow()

    init {
        clipboard.addPrimaryClipChangedListener { pastable.value = holdsSomething() }
    }

    fun copy(elements: List<Element>) {
        if (elements.isEmpty()) return
        val description = ClipDescription(LABEL, arrayOf(MIME_TYPE))
        clipboard.setPrimaryClip(ClipData(description, ClipData.Item(ElementsXml.encode(elements))))
        pastable.value = true
    }

    /** The elements on the clipboard, or null if it holds none. */
    fun paste(): List<Element>? =
        clipboard.primaryClip
            ?.takeIf { it.description.hasMimeType(MIME_TYPE) && it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
            ?.let(ElementsXml::decode)

    /** A picture copied in another app, or null if the clipboard holds none. */
    fun image(): Uri? =
        clipboard.primaryClip
            ?.takeIf { it.description.hasMimeType(IMAGES) && it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri

    private fun holdsSomething(): Boolean {
        val description = clipboard.primaryClipDescription ?: return false
        return description.hasMimeType(MIME_TYPE) || description.hasMimeType(IMAGES)
    }

    private companion object {
        const val LABEL = "Libreta elements"
        const val MIME_TYPE = "application/x-libreta-elements"
        const val IMAGES = "image/*"
    }
}
