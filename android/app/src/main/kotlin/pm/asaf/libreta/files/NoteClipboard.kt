// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.libreta.core.format.ElementsXml
import pm.asaf.libreta.core.model.Element
import java.util.UUID

/**
 * Elements on the system clipboard, in Libreta's own format (.xopp XML, [ElementsXml]): copied in
 * one note, pasted in any, also after the note closed. Pictures copied in other apps paste too.
 * A copy too big for Android's clipboard (pictures, as base64) stays in the app, with a token
 * for it on the clipboard: it pastes in the app as long as nothing else is copied.
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

    /**
     * Looks at the clipboard again. Android lets an app read it only while the app has focus, so
     * what other apps copy while it is in the background shows only once it is back.
     */
    fun refresh() {
        pastable.value = holdsSomething()
    }

    fun copy(elements: List<Element>) {
        if (elements.isEmpty()) return
        val xml = ElementsXml.encode(elements)
        val text =
            if (xml.length <= MAX_CLIP_CHARS) {
                xml
            } else {
                val token = TOKEN + UUID.randomUUID()
                kept = token to elements
                token
            }
        val description = ClipDescription(LABEL, arrayOf(MIME_TYPE))
        pastable.value =
            try {
                clipboard.setPrimaryClip(ClipData(description, ClipData.Item(text)))
                true
            } catch (
                @Suppress("TooGenericExceptionCaught") e: RuntimeException,
            ) {
                // Refused after all: not copied, rather than a crash.
                Log.w(TAG, "Cannot put ${elements.size} elements on the clipboard", e)
                holdsSomething()
            }
    }

    /** The elements on the clipboard, or null if it holds none. */
    fun paste(): List<Element>? {
        val text =
            clipboard.primaryClip
                ?.takeIf { it.description.hasMimeType(MIME_TYPE) && it.itemCount > 0 }
                ?.getItemAt(0)
                ?.text
                ?.toString()
        val local = kept
        return when {
            text == null -> null
            text.startsWith(TOKEN) -> local?.takeIf { it.first == text }?.second
            else -> ElementsXml.decode(text)
        }
    }

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
        const val TAG = "NoteClipboard"

        /** What Android's clipboard takes, with room: its transactions hold about 1 MB (2 bytes a character). */
        const val MAX_CLIP_CHARS = 100_000

        /** How a copy kept in the app is named on the clipboard. */
        const val TOKEN = "libreta-elements:"

        /** The last copy kept in the app (by its token), for every note of the process. */
        @Volatile
        var kept: Pair<String, List<Element>>? = null

        const val LABEL = "Libreta elements"
        const val MIME_TYPE = "application/x-libreta-elements"
        const val IMAGES = "image/*"
    }
}
