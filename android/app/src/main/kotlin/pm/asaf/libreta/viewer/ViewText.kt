// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.widget.EditText
import pm.asaf.libreta.core.model.TextSettings

/** Text typed or changed with the text tool in a [DocumentView]. */
class ViewText internal constructor(
    private val editing: TextEditing,
) {
    /** Where text is typed: an EditText over the view, in the same parent. */
    var field: EditText?
        get() = editing.field
        set(value) {
            editing.field = value
        }

    /** Called with the style of text taken to edit (its font, size and colour), for the toolbar to show. */
    var onStyle: ((TextSettings) -> Unit)?
        get() = editing.onStyle
        set(value) {
            editing.onStyle = value
        }

    /** Whether text is being edited. */
    val isEditing: Boolean get() = editing.isEditing

    /** Finishes the text being edited, if any (one edit). */
    fun finish() = editing.finish()
}
