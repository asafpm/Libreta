// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pm.asaf.libreta.R
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.PreviewCache
import pm.asaf.libreta.library.SortOrder

/**
 * The home screen: each library root with its folders and notes, or a prompt to add one; and
 * the notes of PDFs opened from other apps ([opened]), if there are any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    roots: List<LibraryRoot>,
    folders: Map<FolderRef, FolderState>,
    previews: PreviewCache,
    actions: LibraryActions,
    onNewFolder: (FolderRef) -> Unit,
    onNewNote: (FolderRef) -> Unit,
    onAddFolder: () -> Unit,
    onSettings: () -> Unit,
    opened: FolderRef? = null,
    /** The notes opened last, and how to open one again. */
    recent: List<LibraryEntry.Document> = emptyList(),
    onOpenRecent: (LibraryEntry.Document) -> Unit = {},
    /** How notes are sorted, and changing it. */
    sorting: Sorting? = null,
    /** Lists the library again (pulled down), returning once it has. */
    onRefresh: suspend () -> Unit = {},
) {
    val locals = roots.filterIsInstance<LibraryRoot.Local>()
    val recentTitle = stringResource(R.string.recent)
    val openedState = opened?.let { folders[it] }
    val anyOpened = (openedState as? FolderState.Loaded)?.entries?.isNotEmpty() == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    sorting?.let { SortMenu(it) }
                    HelpButton(HelpTopic.LIBRARY)
                    TooltipIconButton(Icons.Filled.Settings, stringResource(R.string.settings), onClick = onSettings)
                },
            )
        },
    ) { padding ->
        if (locals.isEmpty() && !anyOpened) {
            NoFolders(onAddFolder, Modifier.padding(padding))
        } else {
            RefreshableGrid(onRefresh, Modifier.padding(padding)) {
                if (locals.isEmpty()) item(key = "add", span = { GridItemSpan(maxLineSpan) }) { NoFolders(onAddFolder) }
                recentItems(recentTitle, recent, previews, onOpenRecent)
                for (root in locals) {
                    val drop = actions.manage?.drop
                    sectionHeader(root.folder, root.name, drop, { onNewFolder(root.folder) }) { onNewNote(root.folder) }
                    folderItems(root.id, folders[root.folder], previews, actions)
                }
                if (opened != null && anyOpened) {
                    sectionTitle("opened", opened.name)
                    folderItems("opened", openedState, previews, actions)
                }
            }
        }
    }
}

@Composable
private fun NoFolders(
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
        ) {
            Text(
                stringResource(R.string.no_folders),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onAddFolder) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_folder), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A folder below a root, with a breadcrumb trail from the root ([path]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    path: List<FolderRef>,
    state: FolderState?,
    previews: PreviewCache,
    actions: LibraryActions,
    onNewFolder: () -> Unit,
    onNewNote: () -> Unit,
    onBreadcrumb: (Int) -> Unit,
    onBack: () -> Unit,
    /** Lists the folder again (pulled down), returning once it has. */
    onRefresh: suspend () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Breadcrumbs(path, actions.manage?.drop, onBreadcrumb) },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    NewFolderButton(onNewFolder)
                    Spacer(Modifier.width(8.dp))
                    NewNoteButton(onNewNote)
                    HelpButton(HelpTopic.LIBRARY)
                },
            )
        },
    ) { padding ->
        RefreshableGrid(onRefresh, Modifier.padding(padding)) {
            folderItems(path.last().id.value, state, previews, actions)
        }
    }
}

/**
 * Folder names from the root down; tapping one goes back to it ([onClick] gets its index),
 * and cards dropped on one move up into it ([onDrop]).
 */
@Composable
private fun Breadcrumbs(
    path: List<FolderRef>,
    onDrop: ((LibraryEntry, FolderRef) -> Unit)?,
    onClick: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        path.forEachIndexed { index, folder ->
            if (index > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            if (index == path.lastIndex) {
                Text(folder.name, color = MaterialTheme.colorScheme.onSurface)
            } else {
                val style = LocalTextStyle.current
                val color = MaterialTheme.colorScheme.primary
                Box(Modifier.clickable { onClick(index) }) {
                    if (onDrop == null) {
                        DropLabel(folder.name, over = false, style, color)
                    } else {
                        DropTarget(folder, onDrop) { over -> DropLabel(folder.name, over, style, color) }
                    }
                }
            }
        }
    }
}

/** How notes are sorted ([order]), and changing it ([onSort]). */
class Sorting(
    val order: SortOrder,
    val onSort: (SortOrder) -> Unit,
)

/** The sort order, as a button opening the orders. */
@Composable
private fun SortMenu(sorting: Sorting) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(SortIcon, stringResource(R.string.sort_by)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((order, label) in listOf(
                SortOrder.MODIFIED to R.string.sort_modified,
                SortOrder.NAME to R.string.sort_name,
            )) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    trailingIcon = { if (order == sorting.order) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        sorting.onSort(order)
                    },
                )
            }
        }
    }
}

/** A [LibraryGrid] that is listed again ([onRefresh]) when pulled down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableGrid(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    onRefresh()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
    ) { LibraryGrid(content = content) }
}
