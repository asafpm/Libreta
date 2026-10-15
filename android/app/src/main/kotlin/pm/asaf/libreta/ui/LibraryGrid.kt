// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.PreviewCache

/** Cards of folders and notes, wrapping to the width; [content] adds the items. */
@Composable
fun LibraryGrid(
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD_WIDTH),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** A heading across the grid, with "New folder" and "New note" buttons. */
fun LazyGridScope.sectionHeader(
    key: String,
    title: String,
    onNewFolder: () -> Unit,
    onNewNote: () -> Unit,
) {
    item(key = "header:$key", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
        OutlinedIconButton(onClick = onClick) { Icon(NewFolderIcon, contentDescription = label) }
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
            items(state.entries, key = { "$key/${it.name}" }) { entry ->
                when (entry) {
                    is LibraryEntry.Folder -> FolderCard(entry) { actions.openFolder(entry) }
                    is LibraryEntry.Document -> DocumentCard(entry, previews) { actions.openDocument(entry) }
                    is LibraryEntry.Pdf -> DocumentCard(entry, previews) { actions.openPdf(entry) }
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun FolderCard(
    entry: LibraryEntry.Folder,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(FolderIcon, contentDescription = stringResource(R.string.folder), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                entry.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** A note or a PDF (labelled so), with a preview of its first page. */
@Composable
private fun DocumentCard(
    entry: LibraryEntry.File,
    previews: PreviewCache,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(PREVIEW_ASPECT)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
            ) {
                PreviewImage(entry, previews)
                if (entry is LibraryEntry.Pdf) PdfLabel(Modifier.align(Alignment.TopEnd))
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    entry.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (entry.modified > 0) {
                    val context = LocalContext.current
                    val date =
                        DateUtils.formatDateTime(
                            context,
                            entry.modified,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                        )
                    Text(
                        date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfLabel(modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.pdf),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PreviewImage(
    entry: LibraryEntry.File,
    previews: PreviewCache,
) {
    val bitmap: Bitmap? by produceState(previews.cached(entry), entry) {
        previews.previews(entry).collect { value = it }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.border(1.dp, Color(BORDER)),
        )
    }
}

private val CARD_WIDTH = 180.dp

// Room for a portrait A4 page, with the padding.
private const val PREVIEW_ASPECT = 0.75f
private const val BORDER = 0x22000000
