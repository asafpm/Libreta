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
import pm.asaf.inkpad.files.DocumentFiles
import pm.asaf.inkpad.files.DocumentLocation
import pm.asaf.inkpad.files.NoteEdits
import pm.asaf.inkpad.files.NoteFile
import pm.asaf.inkpad.files.NoteLoader
import pm.asaf.inkpad.files.NoteSaver
import pm.asaf.inkpad.files.SaveResult
import pm.asaf.inkpad.library.FolderListing
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.Library
import pm.asaf.inkpad.library.NewNoteCreator
import pm.asaf.inkpad.library.NewNoteFlow
import pm.asaf.inkpad.library.PreviewCache
import pm.asaf.inkpad.settings.PenPreferences
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
        val uri: Uri,
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
        val edits: NoteEdits,
        /** Tiles and thumbnails, kept across rotation. */
        val caches: RenderCaches,
        val files: DocumentFiles,
        /** Its background files are outside it, but where it is was not granted. */
        val folderNeeded: Boolean,
    ) : NoteState {
        val document: Document get() = edits.document
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
    ) : AndroidViewModel(application) {
        private val context: Context get() = getApplication()
        private val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)
        val library = Library(application.contentResolver, preferences, viewModelScope, io)
        val previews = PreviewCache(application)
        val pens = PenPreferences(preferences)

        private val stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
        val screens: StateFlow<List<Screen>> = stack.asStateFlow()

        val newNotes =
            NewNoteFlow(
                application,
                preferences,
                viewModelScope,
                io,
                onCreated = { uri, name, path ->
                    library.refresh(path.last())
                    open(Screen.Note(uri, name, DocumentLocation(path, name)))
                },
                onError = { errorState.value = it },
            )

        private val noteState = MutableStateFlow<NoteState?>(null)
        val note: StateFlow<NoteState?> = noteState.asStateFlow()

        /** Where the open note was last shown, restored after rotation. */
        var noteAnchor: ViewportAnchor? = null

        private val saver = NoteSaver(application)

        /** The save in progress: a note is read only after it, in case it is the same file. */
        private var saving: Job? = null

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
        fun openExternal(uri: Uri) {
            viewModelScope.launch {
                val screen =
                    withContext(io) {
                        val name = NoteLoader.nameOf(context.contentResolver, uri)
                        Screen.Note(
                            uri,
                            name,
                            NoteLoader.locate(context.contentResolver, library.roots.value, uri, name),
                        )
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
                openExternal(note.uri)
            }
        }

        private fun loadNote(screen: Screen.Note) {
            noteAnchor = null
            noteState.value = NoteState.Loading
            viewModelScope.launch {
                saving?.join()
                val state = withContext(io) { NoteLoader.load(context, screen.uri, screen.name, screen.location) }
                // Still the same note?
                if (stack.value.lastOrNull() == screen) {
                    noteState.value = state
                } else {
                    (state as? NoteState.Loaded)?.files?.close()
                }
            }
        }

        /** Saves the open note if it changed, then calls [then] on the main thread. */
        fun saveNote(then: () -> Unit = {}) {
            val note = noteState.value as? NoteState.Loaded
            if (note == null) {
                then()
                return
            }
            val previous = saving
            saving =
                viewModelScope.launch {
                    previous?.join()
                    val result = withContext(io) { saver.save(note.file, note.edits, note.files) }
                    when (result) {
                        is SaveResult.SavedAsCopy -> {
                            errorState.value =
                                context.getString(R.string.saved_as_copy, result.name)
                        }

                        is SaveResult.Failed -> {
                            errorState.value =
                                context.getString(R.string.error_save_note, note.file.name)
                        }

                        else -> {
                            Unit
                        }
                    }
                    if (result is SaveResult.Saved || result is SaveResult.SavedAsCopy) {
                        note.file.location?.let { library.refresh(it.folders.last()) }
                    }
                    then()
                }
        }

        /** Leaves the open note: it is saved, then its files are released. */
        private fun closeNote() {
            val note = noteState.value as? NoteState.Loaded
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
