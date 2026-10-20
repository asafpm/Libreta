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
import pm.asaf.libreta.viewer.fitWidth
import pm.asaf.libreta.viewer.scrollToPage

/**
 * A note, shown in a [DocumentView], with a notice when its background files could not be
 * found ([onAddFolder] asks for the note's folder when it is outside the library), a page
 * panel and a layer sheet. New pages follow [template].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    name: String,
    state: NoteState?,
    threads: RenderThreads,
    position: ViewerPosition,
    tools: ToolControls,
    onAddFolder: () -> Unit,
    onBack: () -> Unit,
    template: NewPageTemplate = NewPageTemplate(),
    clipboard: NoteClipboard? = null,
    exports: PdfExportFlow? = null,
    /** Saves again; given while saving fails, to say so until it works. */
    onRetrySave: (() -> Unit)? = null,
) {
    var view by remember { mutableStateOf<DocumentView?>(null) }
    val loaded = state as? NoteState.Loaded
    val currentTemplate by rememberUpdatedState(template)
    val commands = loaded?.let { remember(it.session) { NoteCommands(it.session, { view }, { currentTemplate }) } }
    val panels = remember { NotePanels() }
    val images = rememberImageActions({ view }, { panels.current }) { tools.onTool(Tool.SELECT) }
    val paste = rememberPaste(clipboard, tools, { panels.current }, images.insertFrom) { view }
    val export = rememberExportActions(exports, loaded, name) { view }
    ExportProgress(exports)
    val more = TopBarActions(paste, export, images)
    if (loaded != null && commands != null) {
        NoteShortcuts(loaded, commands, panels, tools, more, selectionActions({ view }, clipboard, paste)) { view }
    }
    Scaffold(
        topBar = {
            NoteTopBar(name, onBack) {
                if (loaded != null && commands != null) UndoButtons(loaded.session, view)
                NoteActions(panels, commands, view, more)
                HelpButton(HelpTopic.NOTE)
            }
        },
    ) { padding ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                null, NoteState.Loading -> {
                    CircularProgressIndicator()
                }

                is NoteState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                }

                is NoteState.Loaded -> {
                    Row(Modifier.fillMaxSize()) {
                        if (panels.pages && commands != null) {
                            NotePagePanel(state, panels, commands) { view?.scrollToPage(it) }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ToolBar(
                                tools,
                                Modifier.fillMaxWidth(),
                            ) { color, size -> view?.selection?.restyle(color, size) }
                            Notices(state, onAddFolder, onRetrySave)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                val canvas = Modifier.fillMaxSize()
                                NoteCanvas(state, threads, position, tools, panels, { shown ->
                                    view = shown
                                    shown?.onImageDropped = images.drop
                                }, canvas)
                                panels.selection?.let { anchor ->
                                    SelectionBar(anchor, selectionActions({ view }, clipboard, paste))
                                }
                            }
                        }
                    }
                    if (commands != null) NoteDialogs(state, panels, commands)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteTopBar(
    name: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            BackButton(onBack)
        },
        actions = actions,
    )
}

/**
 * The top bar's commands that depend on more than the note: pasting (null: nothing to paste),
 * exporting and pictures.
 */
private class TopBarActions(
    val paste: (() -> Unit)?,
    val export: ExportActions?,
    val images: ImageActions,
)

/** Keyboard shortcuts for the note in [state], shown by [view]. */
@Composable
private fun NoteShortcuts(
    state: NoteState.Loaded,
    commands: NoteCommands,
    panels: NotePanels,
    tools: ToolControls,
    more: TopBarActions,
    selection: SelectionActions,
    view: () -> DocumentView?,
) {
    val newPage = {
        commands.pages.insertPageAfter(panels.current)
        panels.current += 1
    }
    KeysFor(NoteKeys(view, state.session, tools, selection, newPage, more.export?.save, more.images.insert))
}

/** A command of the note's top bar. */
private class NoteAction(
    val icon: ImageVector,
    val label: Int,
    val enabled: Boolean = true,
    val run: () -> Unit,
)

/** What is open around the note, and the page in view. */
@Stable
private class NotePanels {
    var pages by mutableStateOf(false)
    var layers by mutableStateOf(false)
    var current by mutableIntStateOf(0)

    /** The page whose background is being chosen. */
    var background by mutableStateOf<Int?>(null)

    /** The selection's box on the canvas, in pixels; null if nothing is selected. */
    var selection by mutableStateOf<RectF?>(null)
}

/**
 * The page panel, new page, layers and fit width: buttons on wide screens, a menu on narrow
 * ones (the note's name keeps some room).
 */
@Composable
private fun NoteActions(
    panels: NotePanels,
    commands: NoteCommands?,
    view: DocumentView?,
    more: TopBarActions,
) {
    val enabled = view != null
    val paste = more.paste
    val export = more.export
    val take = more.images.takePhoto
    val actions =
        if (commands == null) {
            emptyList()
        } else {
            listOf(
                NoteAction(PagesIcon, R.string.pages) { panels.pages = !panels.pages },
                NoteAction(AddPageIcon, R.string.insert_page) {
                    commands.pages.insertPageAfter(panels.current)
                    panels.current += 1
                },
                NoteAction(LayersIcon, R.string.layers) { panels.layers = true },
                NoteAction(PasteIcon, R.string.paste, paste != null) { paste?.invoke() },
                NoteAction(ImageIcon, R.string.insert_image) { more.images.insert() },
            ) +
                listOfNotNull(
                    take?.let { NoteAction(CameraIcon, R.string.take_photo) { it() } },
                ) +
                listOf(
                    NoteAction(ExportIcon, R.string.export_pdf, export != null) { export?.save?.invoke() },
                    NoteAction(Icons.Filled.Share, R.string.share_pdf, export != null) { export?.share?.invoke() },
                )
        }
    if (LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP) {
        for (action in actions) {
            TooltipIconButton(action.icon, stringResource(action.label), enabled && action.enabled, action.run)
        }
        TextButton(onClick = { view?.fitWidth() }, enabled = enabled) { Text(stringResource(R.string.fit_width)) }
    } else {
        var open by remember { mutableStateOf(false) }
        TooltipIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_actions)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (action in actions) {
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    enabled = enabled && action.enabled,
                    onClick = {
                        open = false
                        action.run()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fit_width)) },
                enabled = enabled,
                onClick = {
                    open = false
                    view?.fitWidth()
                },
            )
        }
    }
}

@Composable
private fun NotePagePanel(
    state: NoteState.Loaded,
    panels: NotePanels,
    commands: NoteCommands,
    goTo: (Int) -> Unit,
) {
    val session = state.session
    // Read for its changes: the pages are the session's.
    session.changes.collectAsState().value
    val scope = rememberCoroutineScope()
    // As sharp as they are shown.
    val width = with(LocalDensity.current) { THUMBNAIL_DP.dp.roundToPx() }
    val thumbnails =
        remember(state, width) { PageThumbnails(scope, Dispatchers.Default, state.files::newRenderer, width) }
    PagePanel(
        session.document.pages,
        panels.current,
        thumbnails,
        // The page acted on becomes the current one, even if the view can't scroll to it.
        PageActions(
            goTo = {
                panels.current = it
                goTo(it)
            },
            insertAfter = {
                commands.pages.insertPageAfter(it)
                panels.current = it + 1
            },
            duplicate = commands.pages::duplicatePage,
            delete = {
                commands.pages.deletePage(it)
                panels.current = it.coerceAtMost(session.document.pages.lastIndex)
            },
            move = { from, to ->
                commands.pages.movePage(from, to)
                panels.current = to
            },
            background = { panels.background = it },
        ),
    )
}

/** The layer sheet and the background dialog, when open. */
@Composable
private fun NoteDialogs(
    state: NoteState.Loaded,
    panels: NotePanels,
    commands: NoteCommands,
) {
    val session = state.session
    session.changes.collectAsState().value
    val layers by session.layers.collectAsState()
    val pages = session.document.pages
    val current = panels.current.coerceIn(0, pages.lastIndex)
    if (panels.layers) {
        LayerSheet(
            page = pages[current],
            number = current + 1,
            view = layers.getOrNull(current) ?: LayerView.of(pages[current]),
            actions =
                LayerActions(
                    select = { commands.layers.selectLayer(current, it) },
                    setHidden = { layer, hidden -> commands.layers.setLayerHidden(current, layer, hidden) },
                    rename = { layer, name -> commands.layers.renameLayer(current, layer, name) },
                    add = { below -> commands.layers.addLayer(current, below) },
                    delete = { commands.layers.removeSelectedLayer(current) },
                    move = { up -> commands.layers.moveSelectedLayer(current, up) },
                    mergeDown = { commands.layers.mergeSelectedLayerDown(current) },
                ),
            onDismiss = { panels.layers = false },
        )
    }
    panels.background?.let { index ->
        BackgroundDialog(
            current = pages.getOrNull(index)?.background,
            newRenderer = state.files::newRenderer,
            onApply = { background, all ->
                commands.pages.setBackground(if (all) pages.indices.toList() else listOf(index), background)
                panels.background = null
            },
            onDismiss = { panels.background = null },
        )
    }
}

/** Undo and redo, shown in [view]. */
@Composable
private fun UndoButtons(
    session: NoteSession,
    view: DocumentView?,
) {
    val history by session.undoState.collectAsState()
    TooltipIconButton(UndoIcon, stringResource(R.string.undo), view != null && history.canUndo) { undo(session, view) }
    TooltipIconButton(RedoIcon, stringResource(R.string.redo), view != null && history.canRedo) { redo(session, view) }
}

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
private fun NoteCanvas(
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
                    onSelection = { panels.selection = selection.bounds }
                    text.onStyle = { style -> tools.onChange { it.copy(text = style) } }
                    position.get()?.let { restore(it) }
                }
            document = view
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
            onView(null)
        },
        modifier = modifier,
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

/** From this width the note's actions are buttons, and the page panel is wide. */
internal const val WIDE_SCREEN_DP = 600
