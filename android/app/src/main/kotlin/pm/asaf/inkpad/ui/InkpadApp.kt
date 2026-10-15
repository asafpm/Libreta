// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pm.asaf.inkpad.AppViewModel
import pm.asaf.inkpad.Screen
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.ToolSettings
import pm.asaf.inkpad.files.DocumentLocation
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.settings.ColorPreferences
import pm.asaf.inkpad.settings.ToolPreferences

/** The app's screens, driven by [model]. */
@Composable
fun InkpadApp(model: AppViewModel) {
    val screens by model.screens.collectAsStateWithLifecycle()
    val newNote by model.newNotes.request.collectAsStateWithLifecycle()
    val newFolder by model.newFolders.request.collectAsStateWithLifecycle()
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let(model.library::addRoot)
        }
    val addFolder = { pickFolder.launch(null) }

    LaunchedEffect(Unit) { model.library.refreshRoots() }
    BackHandler(enabled = screens.size > 1) { model.back() }
    ErrorMessages(model)

    when (val screen = screens.last()) {
        Screen.Home -> Home(model, addFolder)
        is Screen.Folder -> Folder(model, screen)
        Screen.Settings -> Settings(model, addFolder)
        is Screen.Note -> Note(model, screen)
    }

    newNote?.let { request ->
        NewNoteDialog(request, onCreate = model.newNotes::create, onDismiss = model.newNotes::cancel)
    }
    if (newFolder != null) NewFolderDialog(onCreate = model.newFolders::create, onDismiss = model.newFolders::cancel)
}

@Composable
private fun ErrorMessages(model: AppViewModel) {
    val error by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            model.errorShown()
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
)

@Composable
private fun Home(
    model: AppViewModel,
    addFolder: () -> Unit,
) {
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
    )
}

@Composable
private fun Settings(
    model: AppViewModel,
    addFolder: () -> Unit,
) {
    val roots by model.library.roots.collectAsStateWithLifecycle()
    val sortOrder by model.library.sortOrder.collectAsStateWithLifecycle()
    val fingerButton by model.tools.fingerButton.collectAsStateWithLifecycle()
    val tools by model.tools.tools.collectAsStateWithLifecycle()
    val fixedColors by model.colors.fixedColors.collectAsStateWithLifecycle()
    val recentLimit by model.colors.recentLimit.collectAsStateWithLifecycle()
    val visibleSizes by model.tools.visibleSizes.collectAsStateWithLifecycle()
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
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let(model::addFolderForNote)
        }
    NoteScreen(
        name = screen.name,
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
        clipboard = model.clipboard,
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
)
