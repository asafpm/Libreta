// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.graphics.RectF
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.lowlatency.LowLatencyCanvasView
import kotlinx.coroutines.Dispatchers
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.R
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.files.NoteClipboard
import pm.asaf.inkpad.files.NoteCommands
import pm.asaf.inkpad.files.NoteSession
import pm.asaf.inkpad.files.PageThumbnails
import pm.asaf.inkpad.viewer.DocumentView
import pm.asaf.inkpad.viewer.LayerView
import pm.asaf.inkpad.viewer.LowLatencyWetInk

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
) {
    var view by remember { mutableStateOf<DocumentView?>(null) }
    val loaded = state as? NoteState.Loaded
    val currentTemplate by rememberUpdatedState(template)
    val commands = loaded?.let { remember(it.session) { NoteCommands(it.session, { view }, { currentTemplate }) } }
    val panels = remember { NotePanels() }
    val canPaste = clipboard?.hasElements?.collectAsState()?.value == true
    // Pasted with the selection tool, so that it can be moved at once.
    val paste = {
        clipboard?.paste()?.let { elements ->
            tools.onTool(Tool.SELECT)
            view?.selection?.paste(panels.current, elements)
        }
        Unit
    }
    Scaffold(
        topBar = {
            NoteTopBar(name, onBack) {
                if (loaded != null && commands != null) UndoButtons(loaded.session, view)
                NoteActions(panels, commands, view, if (canPaste) paste else null)
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
                            FilesNotice(state, onAddFolder)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                val canvas = Modifier.fillMaxSize()
                                NoteCanvas(state, threads, position, tools, panels, { view = it }, canvas)
                                panels.selection?.let { anchor ->
                                    SelectionBar(anchor, selectionActions({ view }, clipboard, paste, canPaste))
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        actions = actions,
    )
}

/** The selection bar's commands on [view]'s selection; cut and copy put it on [clipboard]. */
private fun selectionActions(
    view: () -> DocumentView?,
    clipboard: NoteClipboard?,
    paste: () -> Unit,
    canPaste: Boolean,
) = SelectionActions(
    cut = {
        view()?.selection?.let {
            clipboard?.copy(it.elements)
            it.delete()
        }
    },
    copy = { view()?.selection?.let { clipboard?.copy(it.elements) } },
    paste = paste,
    canPaste = canPaste,
    duplicate = { view()?.selection?.duplicate() },
    delete = { view()?.selection?.delete() },
)

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
    /** Pastes the clipboard's elements; null when there are none. */
    paste: (() -> Unit)?,
) {
    val enabled = view != null
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
            )
        }
    if (LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP) {
        for (action in actions) {
            IconButton(onClick = action.run, enabled = enabled && action.enabled) {
                Icon(action.icon, stringResource(action.label))
            }
        }
        TextButton(onClick = { view?.fitWidth() }, enabled = enabled) { Text(stringResource(R.string.fit_width)) }
    } else {
        var open by remember { mutableStateOf(false) }
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions)) }
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
    val thumbnails = remember(state) { PageThumbnails(scope, Dispatchers.Default, state.files::newRenderer) }
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
    val undo by session.undoState.collectAsState()
    IconButton(
        onClick = { session.undo()?.let { view?.show(it, session) } },
        enabled = view != null && undo.canUndo,
    ) { Icon(UndoIcon, stringResource(R.string.undo)) }
    IconButton(
        onClick = { session.redo()?.let { view?.show(it, session) } },
        enabled = view != null && undo.canRedo,
    ) { Icon(RedoIcon, stringResource(R.string.redo)) }
}

/** Shows [pages] after an undo or redo, with the layers as [session] shows them. */
private fun DocumentView.show(
    pages: List<Page>,
    session: NoteSession,
) {
    showDocument(pages)
    layerViews = session.layers.value
}

/** Why backgrounds are missing, if they are. */
@Composable
private fun FilesNotice(
    state: NoteState.Loaded,
    onAddFolder: () -> Unit,
) {
    val missing = state.files.missing
    if (!state.folderNeeded && missing.isEmpty() && state.file.writable) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val message =
                when {
                    !state.file.writable -> stringResource(R.string.read_only_package)
                    state.folderNeeded -> stringResource(R.string.folder_needed)
                    else -> stringResource(R.string.missing_files, missing.joinToString())
                }
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (state.folderNeeded) {
                TextButton(onClick = onAddFolder) { Text(stringResource(R.string.add_this_folder)) }
            }
        }
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
    val layers by state.session.layers.collectAsState()
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
                    position.get()?.let { restore(it) }
                }
            val ink = LowLatencyCanvasView(context)
            view.wetInk = LowLatencyWetInk(ink)
            document = view
            onView(view)
            FrameLayout(context).apply {
                addView(view)
                addView(ink)
            }
        },
        update = {
            document?.tools = tools.tools
            document?.fingerDraws = tools.fingerDraws
            document?.layerViews = layers
        },
        onRelease = {
            document?.let { released ->
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

/** From this width the note's actions are buttons, and the page panel is wide. */
internal const val WIDE_SCREEN_DP = 600
