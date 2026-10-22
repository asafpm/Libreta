// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.Found
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.PreviewCache
import pm.asaf.libreta.library.SearchResults

/** Cards of folders and notes, wrapping to the width; [content] adds the items. */
@Composable
fun LibraryGrid(
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    val state = rememberLazyGridState()
    val tracker = remember { DragTracker() }
    val follower = remember(tracker) { DragFollower(tracker) }
    ScrollWhileDragging(tracker, state)
    CompositionLocalProvider(LocalDragTracker provides tracker) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD_WIDTH),
            state = state,
            modifier =
                modifier
                    .fillMaxSize()
                    .onGloballyPositioned { tracker.area = it.boundsInRoot() }
                    .dragAndDropTarget(DragFollower::accepts, follower),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** A heading across the grid. */
fun LazyGridScope.sectionTitle(
    key: String,
    title: String,
) = item(key = "header:$key", span = { GridItemSpan(maxLineSpan) }) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

/**
 * The heading of [folder] across the grid, with "New folder" and "New note" buttons; cards
 * dropped on it move there ([onDrop]).
 */
fun LazyGridScope.sectionHeader(
    folder: FolderRef,
    title: String,
    onDrop: ((LibraryEntry, FolderRef) -> Unit)?,
    onNewFolder: () -> Unit,
    onNewNote: () -> Unit,
) {
    item(key = "header:${folder.id.value}", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (onDrop == null) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                } else {
                    DropTarget(folder, onDrop) { over -> DropLabel(title, over, MaterialTheme.typography.titleLarge) }
                }
            }
            NewFolderButton(onNewFolder)
            NewNoteButton(onNewNote)
        }
    }
}

/** "New folder"; just its icon on narrow screens, where the title needs the room. */
@Composable
fun NewFolderButton(onClick: () -> Unit) {
    val label = stringResource(R.string.new_folder)
    if (LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP) {
        OutlinedButton(onClick = onClick) {
            Icon(NewFolderIcon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    } else {
        WithTooltip(label) { OutlinedIconButton(onClick = onClick) { Icon(NewFolderIcon, contentDescription = label) } }
    }
}

@Composable
fun NewNoteButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.new_note))
    }
}

/** The notes opened last ([entries]), opened again by [onOpen]; nothing if there are none. */
fun LazyGridScope.recentItems(
    title: String,
    entries: List<LibraryEntry.Document>,
    previews: PreviewCache,
    onOpen: (LibraryEntry.Document) -> Unit,
) {
    if (entries.isEmpty()) return
    sectionTitle("recent", title)
    // Changed where they are, not here: no menus.
    val actions = LibraryActions({}, {}, {})
    items(
        entries,
        key = { "recent/${it.id.value}" },
    ) { entry -> DocumentCard(entry, previews, actions) { onOpen(entry) } }
}

/** What a search found ([results]), each with where it is, opened by [onOpen]. */
fun LazyGridScope.searchItems(
    results: SearchResults,
    previews: PreviewCache,
    onOpen: (Found) -> Unit,
) {
    if (!results.done) fullWidth("searching") { CircularProgressIndicator(Modifier.padding(16.dp)) }
    if (results.done && results.found.isEmpty()) {
        fullWidth(
            "nothing",
        ) { Text(stringResource(R.string.nothing_found), style = MaterialTheme.typography.bodyMedium) }
    }
    // Found here, changed where they are: no menus.
    val actions = LibraryActions({}, {}, {})
    items(results.found, key = { "found/${it.entry.storeId.value}" }) { found ->
        val place = found.path.joinToString(" › ") { it.name }
        when (val entry = found.entry) {
            is LibraryEntry.Folder -> FolderCard(entry, actions) { onOpen(found) }
            is LibraryEntry.File -> DocumentCard(entry, previews, actions, place) { onOpen(found) }
        }
    }
}

/** The contents of a folder, or its loading or error state, as grid items. */
fun LazyGridScope.folderItems(
    key: String,
    state: FolderState?,
    previews: PreviewCache,
    actions: LibraryActions,
) {
    when (state) {
        null, FolderState.Loading -> {
            fullWidth("loading:$key") { CircularProgressIndicator(Modifier.padding(16.dp)) }
        }

        is FolderState.Failed -> {
            fullWidth("failed:$key") {
                Text(
                    stringResource(R.string.folder_unavailable, state.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is FolderState.Loaded -> {
            if (state.entries.isEmpty()) {
                fullWidth(
                    "empty:$key",
                ) { Text(stringResource(R.string.empty_folder), style = MaterialTheme.typography.bodyMedium) }
            }
            // By document: names can repeat (e.g. Nextcloud lists a file twice while uploading it).
            items(state.entries, key = { "$key/${it.storeId.value}" }) { entry ->
                when (entry) {
                    is LibraryEntry.Folder -> FolderCard(entry, actions) { actions.openFolder(entry) }
                    is LibraryEntry.Document -> DocumentCard(entry, previews, actions) { actions.openDocument(entry) }
                    is LibraryEntry.Pdf -> DocumentCard(entry, previews, actions) { actions.openPdf(entry) }
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private val CARD_WIDTH = 180.dp
