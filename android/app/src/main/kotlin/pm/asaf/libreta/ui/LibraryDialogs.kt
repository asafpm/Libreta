// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import pm.asaf.libreta.R
import pm.asaf.libreta.library.EntryRequest
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.LibraryFiles
import pm.asaf.libreta.library.LibraryRoot

/**
 * The dialog [request] asks for: renaming, moving (to a folder of [roots], listed with
 * [onList] into [folders]) or deleting. [opened] is where notes of PDFs from other apps are.
 */
@Composable
fun EntryDialog(
    request: EntryRequest,
    roots: List<LibraryRoot.Local>,
    folders: Map<FolderRef, FolderState>,
    opened: FolderRef?,
    onList: (FolderRef) -> Unit,
    actions: EntryDialogActions,
) {
    when (request) {
        is EntryRequest.Rename -> RenameDialog(request.entry, actions.rename, actions.dismiss)
        is EntryRequest.Move -> MoveToFolderDialog(request.entry, roots, folders, onList, actions.move, actions.dismiss)
        is EntryRequest.Delete -> DeleteDialog(request, request.entry.parent == opened, actions.delete, actions.dismiss)
    }
}

/** A new name for [entry]: its [LibraryFiles.stem], the extensions shown after it. */
@Composable
private fun RenameDialog(
    entry: LibraryEntry,
    onRename: (stem: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val stem = LibraryFiles.stemOf(entry)
    var name by remember(entry) { mutableStateOf(TextFieldValue(stem, TextRange(0, stem.length))) }
    val good = LibraryFiles.isGoodName(name.text.trim()) && name.text.trim() != stem
    val rename = { if (good) onRename(name.text) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title, entry.name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                suffix =
                    entry.name
                        .drop(stem.length)
                        .takeIf { it.isNotEmpty() }
                        ?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { rename() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = rename, enabled = good) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    // Ready to type.
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Confirming [request]: with a choice to delete the PDF that goes with a note; [opened] for an opened PDF's note. */
@Composable
private fun DeleteDialog(
    request: EntryRequest.Delete,
    opened: Boolean,
    onDelete: (alsoPdf: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = request.entry
    var alsoPdf by rememberSaveable(entry.name) { mutableStateOf(false) }
    val text =
        when {
            opened -> R.string.delete_opened_text
            entry is LibraryEntry.Folder -> R.string.delete_folder_text
            else -> R.string.delete_file_text
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title, entry.name)) },
        text = {
            Column {
                Text(stringResource(text))
                request.pdf?.let { pdf ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.toggleable(value = alsoPdf, role = Role.Checkbox) { alsoPdf = it },
                    ) {
                        Checkbox(checked = alsoPdf, onCheckedChange = null)
                        Text(stringResource(R.string.also_delete, pdf))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(opened || alsoPdf) }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
