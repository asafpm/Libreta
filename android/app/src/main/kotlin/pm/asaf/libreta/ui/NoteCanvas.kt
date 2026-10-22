// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.content.Context
import android.graphics.RectF
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.lowlatency.LowLatencyCanvasView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.R
import pm.asaf.libreta.SaveStatus
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.model.NewPageTemplate
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.files.NoteClipboard
import pm.asaf.libreta.files.NoteCommands
import pm.asaf.libreta.files.NoteSession
import pm.asaf.libreta.files.PageThumbnails
import pm.asaf.libreta.files.PdfExportFlow
import pm.asaf.libreta.viewer.DocumentView
import pm.asaf.libreta.viewer.LayerView
import pm.asaf.libreta.viewer.LowLatencyWetInk
import pm.asaf.libreta.viewer.scrollToPage

/**
 * Finishes the text being typed in [view] when the app leaves the screen: it is then in the
 * note, saved as the app stops (before it, as lifecycle observers hear of it first), and not
 * lost if the app is killed in the background.
 */
@Composable
private fun FinishTextOnStop(view: () -> DocumentView?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(view)
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) current()?.text?.finish()
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** The pages, with wet ink in a front buffer above them; [onView] gets the view (null when gone). */
@Composable
internal fun NoteCanvas(
    state: NoteState.Loaded,
    threads: RenderThreads,
    position: ViewerPosition,
    tools: ToolControls,
    panels: NotePanels,
    onView: (DocumentView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var document by remember { mutableStateOf<DocumentView?>(null) }
    val darkPages = LocalDarkPages.current
    val layers by state.session.layers.collectAsState()
    FinishTextOnStop { document }
    // Read for its changes: the page count is the session's.
    state.session.changes
        .collectAsState()
        .value
    Box(modifier) {
        NoteView(state, threads, position, tools, panels, onView, layers, darkPages) { document = it }
        PageAndZoom(panels.current, state.session.document.pages.size, panels.zoom) { move ->
            document?.let {
                it.navigate(move)
                // The page it shows now, even if it was the one in view already.
                panels.current = it.currentPage
            }
        }
    }
}

/** The pages in a [DocumentView], shown and edited; [onShown] gets the view (null when gone). */
@Suppress("LongParameterList") // the note, how it is shown, and where the view goes
@Composable
private fun NoteView(
    state: NoteState.Loaded,
    threads: RenderThreads,
    position: ViewerPosition,
    tools: ToolControls,
    panels: NotePanels,
    onView: (DocumentView?) -> Unit,
    layers: List<LayerView>,
    darkPages: Boolean,
    onShown: (DocumentView?) -> Unit,
) {
    var document by remember { mutableStateOf<DocumentView?>(null) }
    AndroidView(
        factory = { context ->
            val view =
                DocumentView(context).apply {
                    val session = state.session
                    setDocument(
                        session.document.pages,
                        state.caches,
                        state.files::newRenderer,
                        threads,
                        session.versions(),
                        session.layers.value,
                    )
                    onEdit = session::edited
                    onLayerView = session::setLayerView
                    onCurrentPage = { panels.current = it }
                    onZoom = { panels.zoom = it }
                    onSelection = { panels.selection = selection.bounds }
                    text.onStyle = { style -> tools.onChange { it.copy(text = style) } }
                    position.get()?.let { restore(it) }
                }
            document = view
            onShown(view)
            onView(view)
            view.stackedOver(context)
        },
        update = {
            document?.tools = tools.tools
            document?.fingerDraws = tools.fingerDraws
            document?.layerViews = layers
            document?.darkPages = darkPages
        },
        onRelease = {
            document?.let { released ->
                released.text.finish()
                position.set(released.anchor)
                state.session.keepVersions(released.versions)
                released.close()
            }
            document = null
            onShown(null)
            onView(null)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * This view with what goes over it: the field text is typed in (it takes its own touches), and
 * the front buffer wet ink is drawn in.
 */
private fun DocumentView.stackedOver(context: Context): FrameLayout {
    val ink = LowLatencyCanvasView(context)
    wetInk = LowLatencyWetInk(ink)
    val field = EditText(context)
    text.field = field
    val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
    return FrameLayout(context).apply {
        addView(this@stackedOver)
        addView(field, FrameLayout.LayoutParams(wrap, wrap))
        addView(ink)
    }
}
