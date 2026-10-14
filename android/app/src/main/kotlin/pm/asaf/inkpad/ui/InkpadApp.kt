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
import pm.asaf.inkpad.android.render.BackgroundSource
import pm.asaf.inkpad.library.LibraryRoot

/** The app's screens, driven by [model]. */
@Composable
fun InkpadApp(model: AppViewModel) {
    val screens by model.screens.collectAsStateWithLifecycle()
    val newNote by model.newNote.collectAsStateWithLifecycle()
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
        NewNoteDialog(request, onCreate = model::createNote, onDismiss = model::cancelNewNote)
    }
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
        val path =
            parent?.path ?: model.library.roots.value
                .filterIsInstance<LibraryRoot.Local>()
                .filter { it.treeUri == entry.folder.treeUri }
                .map { it.folder }
        model.open(Screen.Folder(path + entry.folder))
    },
    openDocument = { entry -> model.open(Screen.Note(entry.uri, entry.name)) },
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
        onNewNote = model::startNewNote,
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
        onNewNote = { model.startNewNote(screen.folder) },
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
    val library = model.library
    SettingsScreen(
        roots = roots,
        sortOrder = sortOrder,
        actions = SettingsActions(addFolder, library::removeRoot, library::moveRoot, library::setSortOrder),
        onBack = { model.back() },
    )
}

@Composable
private fun Note(
    model: AppViewModel,
    screen: Screen.Note,
) {
    val note by model.note.collectAsStateWithLifecycle()
    NoteScreen(
        name = screen.name,
        state = note,
        backgrounds = BackgroundSource.None,
        threads = model.renderThreads,
        position = ViewerPosition({ model.noteAnchor }, { model.noteAnchor = it }),
        onBack = { model.back() },
    )
}
