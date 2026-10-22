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
    /** Whether its changes are saved, shown under its name. */
    saveStatus: SaveStatus = SaveStatus.SAVED,
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
            NoteTopBar(name, saveStatus.takeIf { loaded != null }, onBack) {
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
                            NotePagePanel(state, panels, commands, { view }) { view?.scrollToPage(it) }
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
                    if (commands != null) NoteDialogs(state, panels, commands) { view }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteTopBar(
    name: String,
    status: SaveStatus?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                status?.let { SaveStatusLine(it) }
            }
        },
        navigationIcon = {
            BackButton(onBack)
        },
        actions = actions,
    )
}

/** Whether the note's changes are in its file: under its name. */
@Composable
private fun SaveStatusLine(status: SaveStatus) {
    val (text, color) =
        when (status) {
            SaveStatus.SAVED -> R.string.saved to MaterialTheme.colorScheme.onSurfaceVariant
            SaveStatus.SAVING -> R.string.saving to MaterialTheme.colorScheme.onSurfaceVariant
            SaveStatus.NOT_SAVED -> R.string.not_saved_short to MaterialTheme.colorScheme.error
        }
    Text(stringResource(text), style = MaterialTheme.typography.labelSmall, color = color)
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
internal class NotePanels {
    var pages by mutableStateOf(false)
    var layers by mutableStateOf(false)
    var current by mutableIntStateOf(0)

    /** The zoom in view, in pixels a point. */
    var zoom by mutableDoubleStateOf(0.0)

    /** The page whose background is being chosen. */
    var background by mutableStateOf<Int?>(null)

    /** The selection's box on the canvas, in pixels; null if nothing is selected. */
    var selection by mutableStateOf<RectF?>(null)
}

/**
 * The note's commands (pages, layers, pictures, export, …): buttons as far as the window's width
 * allows, the note's name keeping some room, and the rest under More.
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
            // Most used first: the first ones stay in the top bar on narrow windows.
            listOf(
                NoteAction(PagesIcon, R.string.pages) { panels.pages = !panels.pages },
                NoteAction(LayersIcon, R.string.layers) { panels.layers = true },
                NoteAction(AddPageIcon, R.string.insert_page) {
                    commands.pages.insertPageAfter(panels.current)
                    panels.current += 1
                },
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
    // Back, undo, redo and help are always there, and the note's name keeps some room.
    val room = (LocalConfiguration.current.screenWidthDp - FIXED_DP - NAME_DP) / ACTION_DP
    val inline = if (room >= actions.size) actions.size else (room - 1).coerceAtLeast(0)
    for (action in actions.take(inline)) {
        TooltipIconButton(action.icon, stringResource(action.label), enabled && action.enabled, action.run)
    }
    val rest = actions.drop(inline)
    if (rest.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        TooltipIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_actions)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (action in rest) {
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
        }
    }
}

/** The top bar's buttons there always are (back, undo, redo, help, with the bar's padding), in dp. */
private const val FIXED_DP = 4 * 48 + 16

/** What the note's name keeps of the top bar, at least, in dp. */
private const val NAME_DP = 180

private const val ACTION_DP = 48

@Composable
private fun NotePagePanel(
    state: NoteState.Loaded,
    panels: NotePanels,
    commands: NoteCommands,
    view: () -> DocumentView?,
    goTo: (Int) -> Unit,
) {
    val session = state.session
    val resources = LocalResources.current
    val offerUndo = rememberUndoOffer(session, view)
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
                offerUndo(resources.getString(R.string.page_deleted, it + 1))
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
    view: () -> DocumentView?,
) {
    val session = state.session
    val offerUndo = rememberUndoOffer(session, view)
    val deleted = stringResource(R.string.layer_deleted)
    val merged = stringResource(R.string.layer_merged)
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
                    delete = {
                        commands.layers.removeSelectedLayer(current)
                        offerUndo(deleted)
                    },
                    move = { up -> commands.layers.moveSelectedLayer(current, up) },
                    mergeDown = {
                        commands.layers.mergeSelectedLayerDown(current)
                        offerUndo(merged)
                    },
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

/** From this width the note's actions are buttons, and the page panel is wide. */
internal const val WIDE_SCREEN_DP = 600
