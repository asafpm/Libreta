// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.files.PdfExportFlow
import pm.asaf.libreta.viewer.DocumentView

/** Exporting the note shown as a PDF: saved where the user chooses, or shared. */
class ExportActions(
    val save: () -> Unit,
    val share: () -> Unit,
)

/** [exports]' actions for the note [name] in [state], as [view] shows it (hidden layers left out). */
@Composable
fun rememberExportActions(
    exports: PdfExportFlow?,
    state: NoteState.Loaded?,
    name: String,
    view: () -> DocumentView?,
): ExportActions? {
    val context = LocalContext.current
    val saveAs =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_TYPE)) { uri ->
            if (uri != null && state != null) exports?.save(state.files, shown(state, view()), name, uri)
        }
    if (exports == null || state == null) return null
    return ExportActions(
        save = { saveAs.launch(NewNote.exportName(state.document, name)) },
        share = {
            exports.share(state.files, shown(state, view()), name, NewNote.exportName(state.document, name)) {
                context.startActivity(it)
            }
        },
    )
}

/** The pages of [state]'s document as [view] shows them: hidden layers left out. */
private fun shown(
    state: NoteState.Loaded,
    view: DocumentView?,
): List<Page> {
    val layers = view?.layerViews.orEmpty()
    return state.document.pages.mapIndexed { i, page ->
        val hidden = layers.getOrNull(i)?.hidden.orEmpty()
        if (hidden.isEmpty()) page else page.copy(layers = page.layers.filterIndexed { layer, _ -> layer !in hidden })
    }
}

/** How far an export is, with a button to stop it. */
@Composable
fun ExportProgress(exports: PdfExportFlow?) {
    val progress by (exports ?: return).progress.collectAsState()
    val done = progress ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.exporting_pdf)) },
        text = { Column { LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { TextButton(onClick = exports::cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val PDF_TYPE = "application/pdf"
