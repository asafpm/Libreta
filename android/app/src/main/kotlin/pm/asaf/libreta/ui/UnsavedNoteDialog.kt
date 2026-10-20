// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R

/**
 * The note [name] was closed but couldn't be saved: its changes are kept until it is saved,
 * copied elsewhere or reopened, or they are discarded. It can't be dismissed without a choice.
 */
@Composable
fun UnsavedNoteDialog(
    name: String,
    actions: UnsavedNoteActions,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.unsaved_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.unsaved_text), style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = actions.retry,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.retry)) }
                OutlinedButton(onClick = actions.saveCopy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_copy))
                }
                OutlinedButton(onClick = actions.keepEditing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = actions.discard) {
                Text(stringResource(R.string.discard_changes), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
