// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.content.ClipData
import android.graphics.Point
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.android.awaitFrame
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.LibraryEntry

/** Whether [entry] can be dropped on [target] to move it there: not where it is, nor into itself. */
fun canDrop(
    entry: LibraryEntry,
    target: FolderRef,
): Boolean = target.id != entry.parent.id && target.id != entry.storeId

/** The entry being dragged, if [event] is a card's drag. */
private fun draggedEntry(event: DragAndDropEvent): LibraryEntry? =
    event.toAndroidDragEvent().localState as? LibraryEntry

/**
 * Starts dragging [entry], within the app, from [view] (under the finger holding it): shown as
 * [look] draws its card, held by the middle. The drag carries the entry itself.
 */
fun startDrag(
    view: View,
    entry: LibraryEntry,
    look: GraphicsLayer,
    density: Density,
) {
    view.startDragAndDrop(ClipData.newPlainText(entry.name, entry.name), CardShadow(look, density), entry, 0)
}

/** A card as [look] draws it, for a drag. */
private class CardShadow(
    private val look: GraphicsLayer,
    private val density: Density,
) : View.DragShadowBuilder() {
    override fun onProvideShadowMetrics(
        outShadowSize: Point,
        outShadowTouchPoint: Point,
    ) {
        // A drag needs a shadow of some size.
        val width = look.size.width.coerceAtLeast(1)
        val height = look.size.height.coerceAtLeast(1)
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: android.graphics.Canvas) {
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(canvas), look.size.toSize()) { drawLayer(look) }
    }
}

/**
 * A place cards can be dropped to move them into [folder] ([onDrop]); [content] gets whether
 * one that can is held over it.
 */
@Composable
fun DropTarget(
    folder: FolderRef,
    onDrop: (LibraryEntry, FolderRef) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (over: Boolean) -> Unit,
) {
    var over by remember { mutableStateOf(false) }
    val drop by rememberUpdatedState(onDrop)
    val tracker = LocalDragTracker.current
    val target =
        remember(folder, tracker) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    over = true
                    tracker?.track(event)
                }

                override fun onMoved(event: DragAndDropEvent) {
                    tracker?.track(event)
                }

                override fun onExited(event: DragAndDropEvent) {
                    over = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    over = false
                    tracker?.y = null
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    over = false
                    val entry = draggedEntry(event) ?: return false
                    drop(entry, folder)
                    return true
                }
            }
        }
    Box(
        modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> draggedEntry(event)?.let { canDrop(it, folder) } == true },
            target = target,
        ),
    ) { content(over) }
}

/** [text] of a drop target (a heading, a breadcrumb), highlighted while a card is held [over] it. */
@Composable
fun DropLabel(
    text: String,
    over: Boolean,
    style: TextStyle,
    color: Color = Color.Unspecified,
) {
    val background = if (over) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Text(
        text,
        style = style,
        color = if (over) MaterialTheme.colorScheme.onPrimaryContainer else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                // Where the text would be without the highlight's room.
                .offset(x = -LABEL_PADDING)
                .background(background, MaterialTheme.shapes.small)
                .padding(horizontal = LABEL_PADDING, vertical = 4.dp),
    )
}

/**
 * Where a card is being dragged ([y] in the view; null if not over the grid), told by the
 * grid ([DragFollower]) and the drop targets in it, which take the events over them.
 */
class DragTracker {
    var y by mutableStateOf<Float?>(null)

    /** The grid's bounds in the view. */
    var area: Rect = Rect.Zero

    fun track(event: DragAndDropEvent) {
        y = event.toAndroidDragEvent().y
    }

    /** Which way to scroll for the drag: up (-1) near the top, down (1) near the bottom, or not (0). */
    fun direction(edge: Float): Int {
        val at = y ?: return 0
        return when {
            at < area.top + edge -> -1
            at > area.bottom - edge -> 1
            else -> 0
        }
    }
}

/** The tracker of the grid around, if any. */
val LocalDragTracker = staticCompositionLocalOf<DragTracker?> { null }

/** A grid's drop target for card drags between its drop targets: it tells [tracker] where they are. */
class DragFollower(
    private val tracker: DragTracker,
) : DragAndDropTarget {
    override fun onEntered(event: DragAndDropEvent) = tracker.track(event)

    override fun onMoved(event: DragAndDropEvent) = tracker.track(event)

    // Also when it goes over a drop target, which tells from there.
    override fun onExited(event: DragAndDropEvent) {
        tracker.y = null
    }

    override fun onEnded(event: DragAndDropEvent) {
        tracker.y = null
    }

    // Nothing is dropped between cards.
    override fun onDrop(event: DragAndDropEvent): Boolean = false

    companion object {
        fun accepts(event: DragAndDropEvent): Boolean = draggedEntry(event) != null
    }
}

/** Scrolls [state] while [tracker]'s drag is near the top or bottom of its grid. */
@Composable
fun ScrollWhileDragging(
    tracker: DragTracker,
    state: ScrollableState,
) {
    val edge = with(LocalDensity.current) { EDGE.toPx() }
    val direction = tracker.direction(edge)
    LaunchedEffect(direction) {
        while (direction != 0) {
            state.scrollBy(direction * SCROLL_PER_FRAME)
            awaitFrame()
        }
    }
}

private val EDGE = 56.dp
private val LABEL_PADDING = 8.dp
private const val SCROLL_PER_FRAME = 12f
