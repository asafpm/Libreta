// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.files.PageThumbnails

/**
 * The note's pages as thumbnails (tap one to go there), and commands for the [current] one:
 * insert a page after it, duplicate, delete, move, and change its background.
 */
@Composable
fun PagePanel(
    pages: List<Page>,
    current: Int,
    thumbnails: PageThumbnails,
    actions: PageActions,
    modifier: Modifier = Modifier,
) {
    // Thumbnails arriving redraw the list.
    val rendered by thumbnails.rendered.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(current) {
        val shown = list.layoutInfo.visibleItemsInfo.map { it.index }
        if (current !in shown) list.scrollToItem(current)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.width(PANEL_WIDTH.dp).fillMaxHeight(),
    ) {
        Column {
            FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Action(AddPageIcon, R.string.insert_page) { actions.insertAfter(current) }
                Action(DuplicateIcon, R.string.duplicate_page) { actions.duplicate(current) }
                Action(Icons.Filled.Delete, R.string.delete_page, enabled = pages.size > 1) { actions.delete(current) }
                Action(Icons.Filled.KeyboardArrowUp, R.string.move_page_up, enabled = current > 0) {
                    actions.move(current, current - 1)
                }
                Action(Icons.Filled.KeyboardArrowDown, R.string.move_page_down, enabled = current < pages.lastIndex) {
                    actions.move(current, current + 1)
                }
                Action(BackgroundIcon, R.string.page_background) { actions.background(current) }
            }
            HorizontalDivider()
            LazyColumn(
                state = list,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                itemsIndexed(pages) { index, page ->
                    // Asked again as thumbnails arrive.
                    val bitmap = rendered.let { thumbnails[page] }
                    Thumbnail(index, page, index == current, bitmap) { actions.goTo(index) }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(
    index: Int,
    page: Page,
    current: Boolean,
    /** Its thumbnail, once rendered. */
    bitmap: Bitmap?,
    onClick: () -> Unit,
) {
    val name = stringResource(R.string.page_number, index + 1)
    val ring = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = if (index == 0) 12.dp else 0.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    selected = current
                },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio((page.width / page.height).toFloat())
                .border(if (current) 3.dp else 1.dp, ring)
                .background(Color.White),
        ) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            (index + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, stringResource(label)) }
}

private const val PANEL_WIDTH = 200
