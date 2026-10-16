// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.files.NoteClipboard
import pm.asaf.libreta.viewer.DocumentView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The selection's commands, floating just above [anchor] (the selection's box, in pixels of
 * the area this fills), or below it when there is no room above.
 */
@Composable
fun SelectionBar(
    anchor: RectF,
    actions: SelectionActions,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier =
            Modifier.layout { measurable, constraints ->
                val bar = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val gap = GAP_DP.dp.roundToPx()
                    val above = anchor.top - gap - bar.height
                    val y = if (above >= 0) above else anchor.bottom + gap
                    bar.place(
                        (anchor.centerX() - bar.width / 2f).roundToInt().coerceIn(
                            0,
                            max(
                                0,
                                constraints.maxWidth - bar.width,
                            ),
                        ),
                        y.roundToInt().coerceIn(0, max(0, constraints.maxHeight - bar.height)),
                    )
                }
            },
    ) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = actions.cut) { Text(stringResource(R.string.cut)) }
            TextButton(onClick = actions.copy) { Text(stringResource(R.string.copy)) }
            TextButton(onClick = actions.paste, enabled = actions.canPaste) { Text(stringResource(R.string.paste)) }
            TextButton(onClick = actions.duplicate) { Text(stringResource(R.string.duplicate)) }
            TextButton(onClick = actions.delete) { Text(stringResource(R.string.delete)) }
        }
    }
}

private const val GAP_DP = 12

/** The selection bar's commands on [view]'s selection; cut and copy put it on [clipboard]. */
internal fun selectionActions(
    view: () -> DocumentView?,
    clipboard: NoteClipboard?,
    /** Null when there is nothing to paste. */
    paste: (() -> Unit)?,
) = SelectionActions(
    cut = {
        view()?.selection?.let {
            clipboard?.copy(it.elements)
            it.delete()
        }
    },
    copy = { view()?.selection?.let { clipboard?.copy(it.elements) } },
    paste = paste ?: {},
    canPaste = paste != null,
    duplicate = { view()?.selection?.duplicate() },
    delete = { view()?.selection?.delete() },
)

/**
 * Pastes the clipboard's elements on the page in view, with the selection tool so that they
 * can be moved at once; null while there is nothing to paste.
 */
@Composable
internal fun rememberPaste(
    clipboard: NoteClipboard?,
    tools: ToolControls,
    page: () -> Int,
    insertImage: (Uri) -> Unit,
    view: () -> DocumentView?,
): (() -> Unit)? {
    val canPaste = clipboard?.canPaste?.collectAsState()?.value == true
    if (!canPaste) return null
    return {
        val elements = clipboard?.paste()
        if (elements != null) {
            tools.onTool(Tool.SELECT)
            view()?.selection?.paste(page(), elements)
        } else {
            // A picture copied in another app.
            clipboard?.image()?.let(insertImage)
        }
    }
}
