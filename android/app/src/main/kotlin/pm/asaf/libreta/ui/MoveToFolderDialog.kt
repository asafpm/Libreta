// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.LibraryRoot

/**
 * Chooses a library folder to move [note] to: the roots, then their subfolders (listed with
 * [onList] into [folders]). [onMove] gets the folder chosen.
 */
@Composable
fun MoveToFolderDialog(
    note: LibraryEntry.Document,
    roots: List<LibraryRoot.Local>,
    folders: Map<FolderRef, FolderState>,
    onList: (FolderRef) -> Unit,
    onMove: (FolderRef) -> Unit,
    onDismiss: () -> Unit,
) {
    // From a root down to the folder shown; empty: the roots.
    var path by remember { mutableStateOf(emptyList<FolderRef>()) }
    val current = path.lastOrNull()
    LaunchedEffect(current) { current?.let(onList) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column {
                Text(
                    note.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    if (current != null) {
                        IconButton(onClick = { path = path.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                    Text(
                        current?.name ?: stringResource(R.string.library_folders),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                val choices =
                    if (current == null) {
                        roots.map { it.folder }
                    } else {
                        (folders[current] as? FolderState.Loaded)
                            ?.entries
                            ?.filterIsInstance<LibraryEntry.Folder>()
                            ?.map { it.folder }
                    }
                FolderChoices(choices) { path = path + it }
            }
        },
        confirmButton = {
            TextButton(onClick = { current?.let(onMove) }, enabled = current != null) {
                Text(stringResource(R.string.move_here))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** [folders] to choose from (null while they are listed). */
@Composable
private fun FolderChoices(
    folders: List<FolderRef>?,
    onChoose: (FolderRef) -> Unit,
) {
    if (folders == null) {
        CircularProgressIndicator(Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.heightIn(max = 320.dp)) {
        items(folders, key = { it.id.value }) { folder ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(folder) }
                        .padding(vertical = 12.dp),
            ) {
                Icon(FolderIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
