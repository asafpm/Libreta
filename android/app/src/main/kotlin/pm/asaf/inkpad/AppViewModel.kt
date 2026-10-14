// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.source
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.core.format.XoppFormatException
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.geometry.ViewportAnchor
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.PaperSize
import pm.asaf.inkpad.core.store.StoreId
import pm.asaf.inkpad.files.Autosave
import pm.asaf.inkpad.files.Backups
import pm.asaf.inkpad.files.DocumentFiles
import pm.asaf.inkpad.files.DocumentLocation
import pm.asaf.inkpad.files.NoteFile
import pm.asaf.inkpad.files.NoteLoader
import pm.asaf.inkpad.files.NoteSaver
import pm.asaf.inkpad.files.NoteSession
import pm.asaf.inkpad.files.SaveResult
import pm.asaf.inkpad.library.FolderListing
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.Library
import pm.asaf.inkpad.library.NewNoteCreator
import pm.asaf.inkpad.library.NewNoteFlow
import pm.asaf.inkpad.library.PreviewCache
import pm.asaf.inkpad.settings.ToolPreferences
import pm.asaf.inkpad.store.SafDocumentStore
import java.io.File
import java.io.IOException

/** A screen of the app; the back stack is a list of these. */
sealed interface Screen {
    data object Home : Screen

    /** A folder below a root: [path] goes from the root's top folder down to the one shown. */
    data class Folder(
        val path: List<FolderRef>,
    ) : Screen {
        val folder: FolderRef get() = path.last()
    }

    data object Settings : Screen

    /** A note; [location] is where its background files are looked for (null: not in the library). */
    data class Note(
        val id: StoreId,
        val name: String,
        val location: DocumentLocation?,
    ) : Screen
}

/** The open note. */
sealed interface NoteState {
    data object Loading : NoteState

    data class Loaded(
        val file: NoteFile,
        /** The document as edited so far. */
        val session: NoteSession,
        /** Tiles and thumbnails, kept across rotation. */
        val caches: RenderCaches,
        val files: DocumentFiles,
        /** Its background files are outside it, but where it is was not granted. */
        val folderNeeded: Boolean,
    ) : NoteState {
        val document: Document get() = session.document
    }

    data class Failed(
        val message: String,
    ) : NoteState
}

/** The app's state: navigation, the library and the open note. */
class AppViewModel
    @JvmOverloads
    constructor(
        application: Application,
        private val io: CoroutineDispatcher = Dispatchers.IO,
        /** Where note pages are rendered. */
        val renderThreads: RenderThreads = RenderThreads(),
        /** When the open note is saved while it is edited. */
        private val autosave: Autosave = Autosave(),
    ) : AndroidViewModel(application) {
        private val context: Context get() = getApplication()
        private val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)

        /** Where notes are: the folders granted through the Storage Access Framework. */
        private val store = SafDocumentStore(application.contentResolver)
        val library = Library(store, application.contentResolver, preferences, viewModelScope, io)
        val previews = PreviewCache(application, store)
        val tools = ToolPreferences(preferences)

        private val stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
        val screens: StateFlow<List<Screen>> = stack.asStateFlow()

        val newNotes =
            NewNoteFlow(
                application,
                store,
                preferences,
                viewModelScope,
                io,
                onCreated = { file, path ->
                    library.refresh(path.last())
                    open(Screen.Note(file.id, file.name, DocumentLocation(path, file.name)))
                },
                onError = { errorState.value = it },
            )

        private val noteState = MutableStateFlow<NoteState?>(null)
        val note: StateFlow<NoteState?> = noteState.asStateFlow()

        /** Where the open note was last shown, restored after rotation. */
        var noteAnchor: ViewportAnchor? = null

        private val saver = NoteSaver(application, store, Backups(File(application.filesDir, "backups")))

        /** The save in progress: a note is read only after it, in case it is the same file. */
        private var saving: Job? = null

        /** Saving the open note as it is edited. */
        private var autosaving: Job? = null

        /** Whether the last save failed, not to say so again with each autosave. */
        private var lastSaveFailed = false

        private val errorState = MutableStateFlow<String?>(null)

        /** A message to show once, e.g. a failed file operation. */
        val error: StateFlow<String?> = errorState.asStateFlow()

        fun errorShown() {
            errorState.value = null
        }

        // Navigation

        fun open(screen: Screen) {
            stack.update { it + screen }
            when (screen) {
                is Screen.Folder -> library.refresh(screen.folder)
                is Screen.Note -> loadNote(screen)
                Screen.Home -> library.refreshRoots()
                Screen.Settings -> Unit
            }
        }

        /** Goes back one screen; false at the home screen. */
        fun back(): Boolean {
            val screens = stack.value
            if (screens.size <= 1) return false
            if (screens.last() is Screen.Note) closeNote()
            stack.value = screens.dropLast(1)
            // Coming back to a folder: it may have a new note in it.
            when (val shown = stack.value.last()) {
                is Screen.Folder -> library.refresh(shown.folder)
                Screen.Home -> library.refreshRoots()
                else -> Unit
            }
            return true
        }

        // New notes

        // The open note

        /** Opens a note from another app ("Open with"), finding it in the library if it is there. */
        fun openExternal(id: StoreId) {
            viewModelScope.launch {
                val screen =
                    withContext(io) {
                        val name = NoteLoader.nameOf(store, id)
                        Screen.Note(id, name, NoteLoader.locate(store, library.roots.value, id, name))
                    }
                if (stack.value.last() is Screen.Note) back()
                open(screen)
            }
        }

        /** Adds the folder of the open note to the library, then opens the note again to find its files. */
        fun addFolderForNote(treeUri: Uri) {
            val note = stack.value.last() as? Screen.Note ?: return
            library.addRoot(treeUri) {
                back()
                openExternal(note.id)
            }
        }

        private fun loadNote(screen: Screen.Note) {
            noteAnchor = null
            noteState.value = NoteState.Loading
            viewModelScope.launch {
                saving?.join()
                val state = withContext(io) { NoteLoader.load(context, store, screen.id, screen.name, screen.location) }
                // Still the same note?
                if (stack.value.lastOrNull() == screen) {
                    noteState.value = state
                    if (state is NoteState.Loaded) startAutosave(state)
                } else {
                    (state as? NoteState.Loaded)?.files?.close()
                }
            }
        }

        /** Saves [note] while it is edited, until it is closed. */
        private fun startAutosave(note: NoteState.Loaded) {
            autosaving?.cancel()
            autosaving =
                viewModelScope.launch {
                    // Changes from now on.
                    autosave.run(note.session.changes.drop(1)) { saveNote().join() }
                }
        }

        /** Saves the open note if it changed, then calls [then] on the main thread. Returns the save's job. */
        fun saveNote(then: () -> Unit = {}): Job {
            val note = noteState.value as? NoteState.Loaded
            val previous = saving
            val job =
                viewModelScope.launch {
                    previous?.join()
                    if (note != null) report(note, withContext(io) { saver.save(note.file, note.session, note.files) })
                    then()
                }
            saving = job
            return job
        }

        /** Tells about a save that went to a copy, or failed (once in a row: autosave would repeat it). */
        private fun report(
            note: NoteState.Loaded,
            result: SaveResult,
        ) {
            when {
                result is SaveResult.SavedAsCopy -> {
                    errorState.value = context.getString(R.string.saved_as_copy, result.name)
                }

                result is SaveResult.Failed && !lastSaveFailed -> {
                    errorState.value = context.getString(R.string.error_save_note, note.file.name)
                }
            }
            lastSaveFailed = result is SaveResult.Failed
            if (result is SaveResult.Saved || result is SaveResult.SavedAsCopy) {
                note.file.location?.let { library.refresh(it.folders.last()) }
            }
        }

        /** Leaves the open note: it is saved, then its files are released. */
        private fun closeNote() {
            val note = noteState.value as? NoteState.Loaded
            autosaving?.cancel()
            autosaving = null
            saveNote {
                note?.caches?.clear()
                note?.files?.close()
            }
            noteState.value = null
            noteAnchor = null
        }

        // Saved when the app stopped (MainActivity.onStop); the scope is gone now.
        override fun onCleared() {
            (noteState.value as? NoteState.Loaded)?.files?.close()
        }

        private companion object {
            const val TAG = "Inkpad"
        }
    }
