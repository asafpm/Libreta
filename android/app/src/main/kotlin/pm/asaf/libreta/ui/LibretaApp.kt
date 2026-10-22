// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.joinAll
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.SaveStatus
import pm.asaf.libreta.Screen
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.ToolSettings
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.library.EntryRequest
import pm.asaf.libreta.library.Found
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.settings.ColorPreferences
import pm.asaf.libreta.settings.ToolPreferences

/** The app's screens, driven by [model]. */
@Composable
fun LibretaApp(model: AppViewModel) {
    val screens by model.screens.collectAsStateWithLifecycle()
    val newNote by model.newNotes.request.collectAsStateWithLifecycle()
    val newFolder by model.newFolders.request.collectAsStateWithLifecycle()
    val entryRequest by model.entries.request.collectAsStateWithLifecycle()
    val unsaved by model.unsaved.note.collectAsStateWithLifecycle()
    val saveCopy =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NOTE_TYPE)) { uri: Uri? ->
            uri?.let(model.unsaved::saveCopy)
        }
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let(model.library::addRoot)
        }
    val addFolder = { pickFolder.launch(null) }

    LaunchedEffect(Unit) {
        model.library.refreshRoots()
        model.recent.refresh()
    }
    BackHandler(enabled = screens.size > 1) { model.back() }

    CompositionLocalProvider(LocalMessages provides model.messages) {
        Box(Modifier.fillMaxSize()) {
            when (val screen = screens.last()) {
                Screen.Home -> Home(model, addFolder)
                is Screen.Folder -> Folder(model, screen)
                Screen.Settings -> Settings(model, addFolder)
                is Screen.Note -> Note(model, screen)
            }
            MessageHost(model.messages)
        }
    }

    newNote?.let { request ->
        NewNoteDialog(request, onCreate = model.newNotes::create, onDismiss = model.newNotes::cancel)
    }
    if (newFolder != null) NewFolderDialog(onCreate = model.newFolders::create, onDismiss = model.newFolders::cancel)
    entryRequest?.let { EntryDialogs(model, it) }
    RecoveredNotes(model)
    unsaved?.let { note ->
        val name = note.note.file.name
        UnsavedNoteDialog(
            name,
            model.unsaved.let {
                UnsavedNoteActions(
                    it::retry,
                    { saveCopy.launch(name) },
                    it::keepEditing,
                    it::discard,
                )
            },
        )
    }
}

/** The first note whose last changes weren't saved when the app last stopped, if any. */
@Composable
private fun RecoveredNotes(model: AppViewModel) {
    val copies by model.recovered.copies.collectAsStateWithLifecycle()
    val copy = copies.firstOrNull()
    val saveCopy =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NOTE_TYPE)) { uri: Uri? ->
            if (uri != null && copy != null) model.recovered.saveCopy(copy, uri)
        }
    copy?.let {
        RecoveredNoteDialog(
            it.name,
            onPutBack = { model.recovered.putBack(it) },
            onSaveCopy = { saveCopy.launch(it.name) },
            onDiscard = { model.recovered.discard(it) },
        )
    }
}

/** What a saved copy of a note is offered as: Android has no type for .xopp. */
private const val NOTE_TYPE = "application/octet-stream"

/** Renaming, moving or deleting what [request] is about. */
@Composable
private fun EntryDialogs(
    model: AppViewModel,
    request: EntryRequest,
) {
    val roots by model.library.roots.collectAsStateWithLifecycle()
    val folders by model.library.folders.collectAsStateWithLifecycle()
    val entries = model.entries
    EntryDialog(
        request,
        roots.filterIsInstance<LibraryRoot.Local>(),
        folders,
        model.library.opened,
        model.library::refresh,
        EntryDialogActions(entries::renameTo, entries::moveTo, entries::confirmDelete, entries::cancel),
    )
}

/** Opens [found], from where it is: a folder, a note, or a PDF's note. */
private fun open(
    model: AppViewModel,
    found: Found,
) {
    when (val entry = found.entry) {
        is LibraryEntry.Folder -> {
            model.open(Screen.Folder(found.path + entry.folder))
        }

        is LibraryEntry.Document -> {
            model.open(
                Screen.Note(entry.id, entry.name, DocumentLocation(found.path, entry.name)),
            )
        }

        is LibraryEntry.Pdf -> {
            model.pdfs.open(entry, found.path)
        }
    }
}

/** Opening folders and notes from a grid; a folder's path continues from [parent]'s. */
private fun libraryActions(
    model: AppViewModel,
    parent: Screen.Folder?,
) = LibraryActions(
    openFolder = { entry ->
        val path = parent?.path ?: listOf(entry.parent)
        model.open(Screen.Folder(path + entry.folder))
    },
    openDocument = { entry ->
        val folders = parent?.path ?: listOf(entry.parent)
        model.open(Screen.Note(entry.id, entry.name, DocumentLocation(folders, entry.name)))
    },
    openPdf = { entry -> model.pdfs.open(entry, parent?.path ?: listOf(entry.parent)) },
    manage = model.entries.let { ManageActions(it::rename, it::move, it::delete, it::drop) },
)

@Composable
private fun Home(
    model: AppViewModel,
    addFolder: () -> Unit,
) {
    val recent by model.recent.entries.collectAsStateWithLifecycle()
    val sortOrder by model.library.sortOrder.collectAsStateWithLifecycle()
    val found by model.search.results.collectAsStateWithLifecycle()
    val roots by model.library.roots.collectAsStateWithLifecycle()
    val folders by model.library.folders.collectAsStateWithLifecycle()
    HomeScreen(
        roots = roots,
        folders = folders,
        previews = model.previews,
        actions = libraryActions(model, parent = null),
        onNewFolder = model.newFolders::start,
        onNewNote = { folder -> model.newNotes.start(listOf(folder)) },
        onAddFolder = addFolder,
        onSettings = { model.open(Screen.Settings) },
        opened = model.library.opened,
        recent = recent,
        sorting = Sorting(sortOrder, model.library::setSortOrder),
        onRefresh = {
            model.library.refreshRoots().joinAll()
            model.recent.refresh()
        },
        search = SearchControls(found, model.search::search) { open(model, it) },
        onOpenRecent = { entry ->
            model.recent.notes.value.firstOrNull { it.id == entry.id }?.let {
                model.open(Screen.Note(it.id, entry.name, it.location))
            }
        },
    )
}

@Composable
private fun Folder(
    model: AppViewModel,
    screen: Screen.Folder,
) {
    val folders by model.library.folders.collectAsStateWithLifecycle()
    FolderScreen(
        path = screen.path,
        state = folders[screen.folder],
        previews = model.previews,
        actions = libraryActions(model, parent = screen),
        onNewFolder = { model.newFolders.start(screen.folder) },
        onNewNote = { model.newNotes.start(screen.path) },
        onBreadcrumb = { index -> repeat(screen.path.lastIndex - index) { model.back() } },
        onBack = { model.back() },
        onRefresh = { model.library.refresh(screen.folder).join() },
    )
}

@Composable
private fun Settings(
    model: AppViewModel,
    addFolder: () -> Unit,
) {
    val roots by model.library.roots.collectAsStateWithLifecycle()
    val sortOrder by model.library.sortOrder.collectAsStateWithLifecycle()
    val found by model.search.results.collectAsStateWithLifecycle()
    val fingerButton by model.tools.fingerButton.collectAsStateWithLifecycle()
    val tools by model.tools.tools.collectAsStateWithLifecycle()
    val fixedColors by model.colors.fixedColors.collectAsStateWithLifecycle()
    val recentLimit by model.colors.recentLimit.collectAsStateWithLifecycle()
    val visibleSizes by model.tools.visibleSizes.collectAsStateWithLifecycle()
    val theme by model.appearance.theme.collectAsStateWithLifecycle()
    val darkPages by model.appearance.darkPages.collectAsStateWithLifecycle()
    val newPages by model.pages.template.collectAsStateWithLifecycle()
    val library = model.library
    SettingsScreen(
        roots = roots,
        sortOrder = sortOrder,
        fingerButton = fingerButton,
        stylusButton = tools.button,
        toolbar = ToolbarSettings(fixedColors, recentLimit, visibleSizes),
        newPages = newPages,
        actions =
            SettingsActions(
                addFolder,
                library::removeRoot,
                library::moveRoot,
                library::setSortOrder,
                model.tools::setFingerButton,
                model.tools::setButton,
                model.colors::setFixedCount,
                model.colors::setFixedColor,
                model.colors::setRecentLimit,
                model.tools::setSizeVisible,
                model.pages::setTemplate,
            ),
        onBack = { model.back() },
        appearance = AppearanceSettings(theme, darkPages, model.appearance::setTheme, model.appearance::setDarkPages),
    )
}

@Composable
private fun Note(
    model: AppViewModel,
    screen: Screen.Note,
) {
    val note by model.note.collectAsStateWithLifecycle()
    val tools by model.tools.tools.collectAsStateWithLifecycle()
    val fixedColors by model.colors.fixedColors.collectAsStateWithLifecycle()
    val recentColors by model.colors.recentColors.collectAsStateWithLifecycle()
    val visibleSizes by model.tools.visibleSizes.collectAsStateWithLifecycle()
    val fingerDraws by model.tools.fingerDraws.collectAsStateWithLifecycle()
    val fingerButton by model.tools.fingerButton.collectAsStateWithLifecycle()
    val newPages by model.pages.template.collectAsStateWithLifecycle()
    val saveStatus by model.saveStatus.collectAsStateWithLifecycle()
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let(model::addFolderForNote)
        }
    NoteScreen(
        // After a conflict, the copy the note is saved to.
        name = (note as? NoteState.Loaded)?.file?.name ?: screen.name,
        state = note,
        threads = model.renderThreads,
        position = ViewerPosition({ model.noteAnchor }, { model.noteAnchor = it }),
        tools =
            toolControls(
                model.tools,
                model.colors,
                tools,
                fixedColors,
                recentColors,
                visibleSizes,
                fingerDraws,
                fingerButton,
            ),
        // The picker starts next to the note, where the provider supports it.
        onAddFolder = { pickFolder.launch(Uri.parse(screen.id.value)) },
        onBack = { model.back() },
        template = newPages,
        saveStatus = saveStatus,
        onRetrySave = if (saveStatus == SaveStatus.NOT_SAVED) ({ model.saveNote() }) else null,
        clipboard = model.clipboard,
        exports = model.exports,
    )
}

private fun toolControls(
    preferences: ToolPreferences,
    colors: ColorPreferences,
    tools: ToolSettings,
    fixedColors: List<Color>,
    recentColors: List<Color>,
    visibleSizes: Set<PenSize>,
    fingerDraws: Boolean,
    fingerButton: Boolean,
) = ToolControls(
    tools = tools,
    fixedColors = fixedColors,
    fingerDraws = fingerDraws,
    fingerButton = fingerButton,
    recentColors = recentColors,
    visibleSizes = visibleSizes,
    onTool = preferences::selectTool,
    onColor = preferences::setColor,
    onCustomColor = { color ->
        preferences.setColor(color)
        colors.addRecentColor(color)
    },
    onSize = preferences::setSize,
    onEraserType = preferences::setEraserType,
    onSelectMode = preferences::setSelectMode,
    onFingerDraws = preferences::setFingerDraws,
    onChange = preferences::change,
)
