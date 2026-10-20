// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.files.PageThumbnails
import pm.asaf.libreta.viewer.PageRecolor
import kotlin.math.abs

/**
 * The note's pages as thumbnails (tap one to go there, or hold and drag it to move it, as a
 * hint says), and commands for the [current] one: insert a page after it, duplicate, delete,
 * and change its background.
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
    val spacing = with(LocalDensity.current) { SPACING_DP.dp.toPx() }
    val drag = remember(list, spacing) { PageDrag(list, spacing) }
    LaunchedEffect(current) {
        val shown = list.layoutInfo.visibleItemsInfo.map { it.index }
        if (current !in shown) list.scrollToItem(current)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.width(PANEL_DP.dp).fillMaxHeight(),
    ) {
        Column {
            PageCommands(pages.size, current, actions)
            if (pages.size > 1) {
                Text(
                    stringResource(R.string.drag_pages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
            HorizontalDivider()
            EdgeScrolling(drag)
            LazyColumn(
                state = list,
                verticalArrangement = Arrangement.spacedBy(SPACING_DP.dp),
                modifier =
                    Modifier
                        .padding(horizontal = THUMBNAIL_MARGIN_DP.dp)
                        .testTag("pages"),
            ) {
                itemsIndexed(pages) { index, page ->
                    // Asked again as thumbnails arrive.
                    val bitmap = rendered.let { thumbnails[page] }
                    Thumbnail(index, page, index == current, bitmap, drag, actions) { actions.goTo(index) }
                }
            }
        }
    }
}

/** The current page's commands. */
@Composable
private fun PageCommands(
    count: Int,
    current: Int,
    actions: PageActions,
) {
    FlowRow(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Action(AddPageIcon, R.string.insert_page) { actions.insertAfter(current) }
        Action(DuplicateIcon, R.string.duplicate_page) { actions.duplicate(current) }
        Action(Icons.Filled.Delete, R.string.delete_page, enabled = count > 1) { actions.delete(current) }
        Action(BackgroundIcon, R.string.page_background) { actions.background(current) }
    }
}

/**
 * A page held and dragged in [list]: which one, and where the finger is (in the list's
 * coordinates); it goes where the page under the finger is.
 */
private class PageDrag(
    private val list: LazyListState,
    /** Between pages in the list, in pixels. */
    private val spacing: Float,
) {
    var from by mutableStateOf<Int?>(null)
        private set
    private var y by mutableFloatStateOf(0f)

    /** How far below its top the page was picked up. */
    private var grab = 0f

    /** The room the dragged page takes in the list. */
    private var room = 0f

    /** Picks up page [index], held [at] (in the page's place); false if it isn't shown. */
    fun start(
        index: Int,
        at: Offset,
    ): Boolean {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
        from = index
        grab = at.y
        y = item.offset + at.y
        room = item.size + spacing
        return true
    }

    fun moveBy(dy: Float) {
        y += dy
    }

    /** Where the page would go: the place of the page nearest the finger. */
    val target: Int?
        get() {
            if (from == null) return null
            return list.layoutInfo.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2f - y) }
                ?.index
        }

    /**
     * How far page [index] is drawn from its place: the dragged one follows the finger, and
     * those between its place and where it would go step aside to make room.
     */
    fun translation(index: Int): Float {
        val from = from ?: return 0f
        val target = target ?: from
        return when (index) {
            from -> {
                y - grab -
                    (
                        list.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }
                            ?.offset ?: 0
                    ).toFloat()
            }

            in from + 1..target -> {
                -room
            }

            in target until from -> {
                room
            }

            else -> {
                0f
            }
        }
    }

    /** Scrolling speed near the list's ends, to drag beyond what is shown. */
    fun edgeSpeed(edge: Float): Float {
        val height = list.layoutInfo.viewportSize.height
        return when {
            from == null -> 0f
            y < edge -> -(edge - y) / EDGE_SLOWNESS
            y > height - edge -> (y - (height - edge)) / EDGE_SLOWNESS
            else -> 0f
        }
    }

    /** Drops the page; returns where from and to, if it moved. */
    fun end(): Pair<Int, Int>? {
        val move = from?.let { start -> target?.takeIf { it != start }?.let { start to it } }
        from = null
        return move
    }

    fun cancel() {
        from = null
    }

    /** Scrolls the list under the dragged page by [pixels]. */
    suspend fun scroll(pixels: Float) {
        list.scrollBy(pixels)
    }
}

/** Scrolls the list while a page is dragged near its ends. */
@Composable
private fun EdgeScrolling(drag: PageDrag) {
    LaunchedEffect(drag.from) {
        while (drag.from != null) {
            val speed = drag.edgeSpeed(EDGE_PX)
            if (speed != 0f) drag.scroll(speed)
            delay(FRAME_MS)
        }
    }
}

/**
 * Hold page [index] and drag it to move it ([onMove]). On the page itself, inside the list's
 * scrolling: once held, its moves are the page's, not the list's.
 */
@Composable
private fun Modifier.draggable(
    index: Int,
    drag: PageDrag,
    onMove: (Int, Int) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    // Kept by the list while held, even when scrolled out of view.
    val pinnable = LocalPinnableContainer.current
    return pointerInput(index, drag) {
        var pinned: PinnableContainer.PinnedHandle? = null
        try {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    if (drag.start(index, it)) {
                        pinned = pinnable?.pin()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    drag.moveBy(amount.y)
                },
                onDragEnd = {
                    pinned?.release()
                    drag.end()?.let { (from, to) -> move(from, to) }
                },
                onDragCancel = {
                    pinned?.release()
                    drag.cancel()
                },
            )
        } finally {
            // Gone mid-drag: the drag ends without a move.
            if (drag.from == index) {
                pinned?.release()
                drag.cancel()
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
    drag: PageDrag,
    actions: PageActions,
    onClick: () -> Unit,
) {
    val name = stringResource(R.string.page_number, index + 1)
    val ring = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val dragged = drag.from == index
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .zIndex(if (dragged) 1f else 0f)
                // Before the layer: the finger's moves are measured where the page was.
                .draggable(index, drag, actions.move)
                .graphicsLayer {
                    translationY = drag.translation(index)
                    if (dragged) {
                        shadowElevation = 8.dp.toPx()
                        alpha = DRAGGED_ALPHA
                    }
                }.padding(top = if (index == 0) 12.dp else 0.dp)
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
                .background(if (LocalDarkPages.current) Color(PageRecolor.PAPER) else Color.White),
        ) {
            bitmap?.let {
                // Rendered at the page's proportions: it fills the frame.
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = pageColorFilter(),
                    modifier = Modifier.fillMaxSize().testTag("thumbnail"),
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
    TooltipIconButton(icon, stringResource(label), enabled, onClick)
}

/** Wide enough for the commands in one row. */
private const val PANEL_DP = 200
private const val THUMBNAIL_MARGIN_DP = 16

/** How wide thumbnails are shown. */
internal const val THUMBNAIL_DP = PANEL_DP - 2 * THUMBNAIL_MARGIN_DP
private const val DRAGGED_ALPHA = 0.85f
private const val SPACING_DP = 12

/** From this far from the list's ends (pixels) a dragged page scrolls the list, faster nearer. */
private const val EDGE_PX = 120f
private const val EDGE_SLOWNESS = 6f
private const val FRAME_MS = 16L
