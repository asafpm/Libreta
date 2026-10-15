// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.viewer.LayerView

/**
 * The layers of [page] (number [number]), top first: which one ink goes on, which are shown,
 * their names, and commands for the selected one, as in Xournal++.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerSheet(
    page: Page,
    number: Int,
    view: LayerView,
    actions: LayerActions,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(
                "${stringResource(R.string.layers)} — ${stringResource(R.string.page_number, number)}",
                style = MaterialTheme.typography.titleMedium,
            )
            for (layer in page.layers.indices.reversed()) {
                val name = layerName(page, layer)
                val hidden = layer in view.hidden
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val write = stringResource(R.string.write_on_layer, name)
                    RadioButton(
                        selected = layer == view.selected,
                        onClick = { actions.select(layer) },
                        modifier = Modifier.semantics { contentDescription = write },
                    )
                    Text(
                        name,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable { renaming = layer }
                                .padding(vertical = 12.dp),
                    )
                    val toggle = stringResource(if (hidden) R.string.show_layer else R.string.hide_layer, name)
                    IconButton(onClick = { actions.setHidden(layer, !hidden) }) {
                        Icon(if (hidden) EyeOffIcon else EyeIcon, toggle)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val selected = view.selected
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Command(AddIcon, R.string.add_layer) { actions.add(false) }
                Command(AddIcon, R.string.add_layer_below) { actions.add(true) }
                Command(
                    Icons.Filled.KeyboardArrowUp,
                    R.string.move_layer_up,
                    selected < page.layers.lastIndex,
                ) { actions.move(true) }
                Command(Icons.Filled.KeyboardArrowDown, R.string.move_layer_down, selected > 0) { actions.move(false) }
                Command(MergeDownIcon, R.string.merge_layer_down, selected > 0) { actions.mergeDown() }
                Command(Icons.Filled.Delete, R.string.delete_layer, page.layers.size > 1) { actions.delete() }
            }
        }
    }
    renaming?.let { layer ->
        RenameDialog(
            initial = page.layers[layer].name.orEmpty(),
            onRename = {
                actions.rename(layer, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

/** A layer's name, or "Layer n" (numbered from the bottom) if it has none, as Xournal++ shows them. */
@Composable
fun layerName(
    page: Page,
    layer: Int,
): String = page.layers[layer].name ?: stringResource(R.string.layer_number, layer + 1)

@Composable
private fun Command(
    icon: ImageVector,
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val text = stringResource(label)
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null)
        Text(text, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_layer)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.layer_name)) },
                singleLine = true,
                modifier = Modifier.testTag("layer name"),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
