// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.asaf.libreta.R
import pm.asaf.libreta.library.FolderState
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.PreviewCache

/**
 * A folder, with a menu if [actions] has one; then it can also be dragged, and cards dropped
 * on it move into it.
 */
@Composable
internal fun FolderCard(
    entry: LibraryEntry.Folder,
    actions: LibraryActions,
    onClick: () -> Unit,
) {
    val manage = actions.manage
    if (manage == null) {
        FolderCardContent(entry, actions, over = false, onClick)
    } else {
        DropTarget(entry.folder, manage.drop) { over -> FolderCardContent(entry, actions, over, onClick) }
    }
}

/** A folder's card, highlighted while a card is held [over] it. */
@Composable
private fun FolderCardContent(
    entry: LibraryEntry.Folder,
    actions: LibraryActions,
    over: Boolean,
    onClick: () -> Unit,
) {
    EntryCard(
        entry.takeIf { actions.manage != null },
        onClick,
        colors =
            if (over) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            },
        border = if (over) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Icon(FolderIcon, contentDescription = stringResource(R.string.folder), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                entry.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
            )
            actions.manage?.let { CardMenu(entry, it, Modifier) } ?: Spacer(Modifier.width(16.dp))
        }
    }
}

/**
 * A note or a PDF (labelled so), with a preview of its first page, and a menu if [actions] has
 * one; [place] says where it is (e.g. found by a search).
 */
@Composable
internal fun DocumentCard(
    entry: LibraryEntry.File,
    previews: PreviewCache,
    actions: LibraryActions,
    place: String? = null,
    onClick: () -> Unit,
) {
    EntryCard(entry.takeIf { actions.manage != null }, onClick) {
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
                if (entry is LibraryEntry.Pdf) PdfLabel(Modifier.align(Alignment.TopStart))
                actions.manage?.let { CardMenu(entry, it, Modifier.align(Alignment.TopEnd)) }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    entry.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                place?.let {
                    Text(
                        it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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

/**
 * A card, which can be picked up with a long press and dragged if [draggable] is given (the
 * entry it shows); the whole card is shown while it is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    draggable: LibraryEntry?,
    onClick: () -> Unit,
    colors: CardColors = CardDefaults.cardColors(),
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val look = rememberGraphicsLayer()
    val view = LocalView.current
    val density = LocalDensity.current
    Card(
        colors = colors,
        border = border,
        modifier =
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    look.record { this@drawWithContent.drawContent() }
                    drawLayer(look)
                }.clip(CardDefaults.shape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = draggable?.let { stringResource(R.string.drag_to_move) },
                    onLongClick = draggable?.let { { startDrag(view, it, look, density) } },
                ),
    ) { content() }
}

/** Renaming, moving and deleting [entry]. */
@Composable
private fun CardMenu(
    entry: LibraryEntry,
    actions: ManageActions,
    modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions_for, entry.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val items =
                listOf(
                    R.string.rename to actions.rename,
                    R.string.move_to_folder to actions.move,
                    R.string.delete to actions.delete,
                )
            for ((label, action) in items) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action(entry)
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
