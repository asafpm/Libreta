// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.R

/** What the user should know about the note: saving fails ([onRetrySave] given), or files are missing. */
@Composable
internal fun Notices(
    state: NoteState.Loaded,
    onAddFolder: () -> Unit,
    onRetrySave: (() -> Unit)?,
) {
    onRetrySave?.let { NotSavedNotice(it) }
    FilesNotice(state, onAddFolder)
}

/** Saving failed: the changes are only in the app until it works. */
@Composable
private fun NotSavedNotice(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.not_saved),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Why backgrounds are missing, if they are. */
@Composable
private fun FilesNotice(
    state: NoteState.Loaded,
    onAddFolder: () -> Unit,
) {
    val missing = state.files.missing
    if (!state.folderNeeded && missing.isEmpty() && state.file.writable) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val message =
                when {
                    !state.file.writable -> stringResource(R.string.read_only_package)
                    state.folderNeeded -> stringResource(R.string.folder_needed)
                    else -> stringResource(R.string.missing_files, missing.joinToString())
                }
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (state.folderNeeded) {
                TextButton(onClick = onAddFolder) { Text(stringResource(R.string.add_this_folder)) }
            }
        }
    }
}
