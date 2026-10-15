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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.PreviewCache

@Composable
internal fun FolderCard(
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

/** A note or a PDF (labelled so), with a preview of its first page, and a menu if [actions] has one. */
@Composable
internal fun DocumentCard(
    entry: LibraryEntry.File,
    previews: PreviewCache,
    actions: LibraryActions,
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
                if (entry is LibraryEntry.Document) CardMenu(entry, actions, Modifier.align(Alignment.TopEnd))
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

/** Moving and deleting [entry], when [actions] does them. */
@Composable
private fun CardMenu(
    entry: LibraryEntry.Document,
    actions: LibraryActions,
    modifier: Modifier,
) {
    val move = actions.move
    val delete = actions.delete
    if (move == null && delete == null) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions_for, entry.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            move?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_folder)) },
                    onClick = {
                        open = false
                        it(entry)
                    },
                )
            }
            delete?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        open = false
                        it(entry)
                    },
                )
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
            colorFilter = pageColorFilter(),
            modifier = Modifier.border(1.dp, Color(BORDER)),
        )
    }
}

// Room for a portrait A4 page, with the padding.
private const val PREVIEW_ASPECT = 0.75f
private const val BORDER = 0x22000000
