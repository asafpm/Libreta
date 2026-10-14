// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.NewNoteRequest
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.model.PaperSize

/** Name, paper size and orientation of a new note, starting from [request]'s suggestions. */
@Composable
fun NewNoteDialog(
    request: NewNoteRequest,
    onCreate: (name: String, paper: PaperSize, landscape: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // The name is selected without its extension, ready to be typed over.
    var name by rememberSaveable(request, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(request.name, TextRange(0, request.name.substringBeforeLast('.').length)))
    }
    var paper by rememberSaveable(request) { mutableStateOf(request.paper) }
    var landscape by rememberSaveable(request) { mutableStateOf(request.landscape) }
    val create = { onCreate(name.text, paper, landscape) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.text.isNotBlank()) create() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.paper_size), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (size in PaperSize.entries) {
                        FilterChip(
                            selected = size == paper,
                            onClick = { paper = size },
                            label = { Text(stringResource(size.label)) },
                        )
                    }
                }
                Orientation(landscape) { landscape = it }
            }
        },
        confirmButton = {
            TextButton(onClick = create, enabled = name.text.isNotBlank()) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Orientation(
    landscape: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(R.string.portrait, R.string.landscape)
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = landscape == (index == 1),
                    onClick = { onChange(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

private val PaperSize.label: Int
    get() =
        when (this) {
            PaperSize.A4 -> R.string.paper_a4
            PaperSize.LETTER -> R.string.paper_letter
            PaperSize.A5 -> R.string.paper_a5
            PaperSize.LEGAL -> R.string.paper_legal
        }
