// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.library.SortOrder

/** The library folders (add, remove, reorder) and how notes are sorted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    roots: List<LibraryRoot>,
    sortOrder: SortOrder,
    fingerDraws: Boolean,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Heading(stringResource(R.string.library_folders))
            roots.forEachIndexed { index, root -> RootItem(root, index, roots.size, actions) }
            TextButton(onClick = actions.addFolder, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_folder), modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.sort_by))
            SortOption(stringResource(R.string.sort_modified), sortOrder == SortOrder.MODIFIED) {
                actions.sortBy(SortOrder.MODIFIED)
            }
            SortOption(
                stringResource(R.string.sort_name),
                sortOrder == SortOrder.NAME,
            ) { actions.sortBy(SortOrder.NAME) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.input))
            ListItem(
                headlineContent = { Text(stringResource(R.string.draw_with_finger)) },
                supportingContent = { Text(stringResource(R.string.draw_with_finger_summary)) },
                trailingContent = { Switch(checked = fingerDraws, onCheckedChange = null) },
                modifier =
                    Modifier.toggleable(
                        value = fingerDraws,
                        role = Role.Switch,
                        onValueChange = actions.fingerDraws,
                    ),
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun RootItem(
    root: LibraryRoot,
    index: Int,
    count: Int,
    actions: SettingsActions,
) {
    val location =
        when (root) {
            is LibraryRoot.Local -> root.treeUri.lastPathSegment.orEmpty()
        }
    ListItem(
        headlineContent = { Text(root.name) },
        supportingContent = { Text(location) },
        trailingContent = {
            Row {
                IconButton(onClick = { actions.move(root, -1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                }
                IconButton(onClick = { actions.move(root, 1) }, enabled = index < count - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                }
                IconButton(
                    onClick = { actions.remove(root) },
                ) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
            }
        },
    )
}

@Composable
private fun SortOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}
