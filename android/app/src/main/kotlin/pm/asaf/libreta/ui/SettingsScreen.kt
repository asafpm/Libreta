// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.NewPageTemplate
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.StylusButton
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.SortOrder

/**
 * The library folders (add, remove, reorder), how notes are sorted, input, the toolbar's
 * colours and widths, new pages, and the stylus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    roots: List<LibraryRoot>,
    sortOrder: SortOrder,
    fingerButton: Boolean,
    stylusButton: StylusButton,
    toolbar: ToolbarSettings,
    newPages: NewPageTemplate,
    actions: SettingsActions,
    onBack: () -> Unit,
    appearance: AppearanceSettings? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    BackButton(onBack)
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
            SortChoices(sortOrder, actions.sortBy)
            appearance?.let {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Heading(stringResource(R.string.appearance))
                AppearanceSection(it)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.input))
            ListItem(
                headlineContent = { Text(stringResource(R.string.finger_button)) },
                supportingContent = { Text(stringResource(R.string.finger_button_summary)) },
                trailingContent = { Switch(checked = fingerButton, onCheckedChange = null) },
                modifier =
                    Modifier.toggleable(
                        value = fingerButton,
                        role = Role.Switch,
                        onValueChange = actions.fingerButton,
                    ),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.colors))
            ColorSettingsSection(toolbar, actions)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.stroke_widths))
            WidthChoices(toolbar.sizes, actions.sizeVisible)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.new_pages))
            NewPagesSection(newPages, actions.newPages)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Heading(stringResource(R.string.stylus))
            ButtonChoices(stylusButton, actions.stylusButton)
        }
    }
}

/** How library folders are sorted. */
@Composable
private fun SortChoices(
    sortOrder: SortOrder,
    onSort: (SortOrder) -> Unit,
) {
    RadioOption(stringResource(R.string.sort_modified), sortOrder == SortOrder.MODIFIED) { onSort(SortOrder.MODIFIED) }
    RadioOption(stringResource(R.string.sort_name), sortOrder == SortOrder.NAME) { onSort(SortOrder.NAME) }
}

/** What holding the stylus button does. */
@Composable
private fun ButtonChoices(
    button: StylusButton,
    onChange: (StylusButton) -> Unit,
) {
    Text(
        stringResource(R.string.stylus_button),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    for ((choice, label) in STYLUS_BUTTONS) {
        RadioOption(stringResource(label), button == choice) { onChange(choice) }
    }
}

/** A checkbox per width: whether the toolbar offers it. */
@Composable
private fun WidthChoices(
    sizes: Set<PenSize>,
    onChange: (PenSize, Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        for (size in PenSize.entries) {
            val shown = size in sizes
            // At least one width stays.
            val enabled = !shown || sizes.size > 1
            val label = stringResource(size.label)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .toggleable(value = shown, enabled = enabled, role = Role.Checkbox) { onChange(size, it) }
                        .semantics { contentDescription = label },
            ) {
                Checkbox(checked = shown, onCheckedChange = null, enabled = enabled)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(DOT_BOX.dp), contentAlignment = Alignment.Center) {
                    WidthDot(size, MaterialTheme.colorScheme.onSurface)
                }
            }
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
                TooltipIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up), index > 0) {
                    actions.move(root, -1)
                }
                TooltipIconButton(
                    Icons.Filled.KeyboardArrowDown,
                    stringResource(R.string.move_down),
                    index < count - 1,
                ) {
                    actions.move(root, 1)
                }
                TooltipIconButton(Icons.Filled.Delete, stringResource(R.string.remove)) { actions.remove(root) }
            }
        },
    )
}

@Composable
private fun RadioOption(
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

private val STYLUS_BUTTONS =
    listOf(
        StylusButton.ERASER to R.string.button_erases,
        StylusButton.HIGHLIGHTER to R.string.button_highlights,
        StylusButton.NONE to R.string.button_nothing,
    )

/** Room for the largest width dot. */
private const val DOT_BOX = 24
