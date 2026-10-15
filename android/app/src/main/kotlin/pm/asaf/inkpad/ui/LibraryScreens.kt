// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.FolderState
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.library.PreviewCache

/** The home screen: each library root with its folders and notes, or a prompt to add one. */
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { padding ->
        val locals = roots.filterIsInstance<LibraryRoot.Local>()
        if (locals.isEmpty()) {
            NoFolders(onAddFolder, Modifier.padding(padding))
        } else {
            LibraryGrid(Modifier.padding(padding)) {
                for (root in locals) {
                    sectionHeader(root.id, root.name, { onNewFolder(root.folder) }) { onNewNote(root.folder) }
                    folderItems(root.id, folders[root.folder], previews, actions)
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Breadcrumbs(path, onBreadcrumb) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    NewFolderButton(onNewFolder)
                    Spacer(Modifier.width(8.dp))
                    NewNoteButton(onNewNote)
                },
            )
        },
    ) { padding ->
        LibraryGrid(Modifier.padding(padding)) {
            folderItems(path.last().id.value, state, previews, actions)
        }
    }
}

/** Folder names from the root down; tapping one goes back to it ([onClick] gets its index). */
@Composable
private fun Breadcrumbs(
    path: List<FolderRef>,
    onClick: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        path.forEachIndexed { index, folder ->
            if (index > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            val last = index == path.lastIndex
            Text(
                folder.name,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = if (last) Modifier else Modifier.clickable { onClick(index) },
            )
        }
    }
}
