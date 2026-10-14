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
import pm.asaf.inkpad.files.NoteLoader
import pm.asaf.inkpad.library.FolderListing
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.Library
import pm.asaf.inkpad.library.NewNoteCreator
import pm.asaf.inkpad.library.PreviewCache
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

/** The new-note dialog for the last folder of [path] (from a library root), with a suggested [name]. */
data class NewNoteRequest(
    val path: List<FolderRef>,
    val name: String,
    val paper: PaperSize,
    val landscape: Boolean,
) {
    val folder: FolderRef get() = path.last()
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

        private val stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
        val screens: StateFlow<List<Screen>> = stack.asStateFlow()

        private val newNoteState = MutableStateFlow<NewNoteRequest?>(null)
        val newNote: StateFlow<NewNoteRequest?> = newNoteState.asStateFlow()

        private val noteState = MutableStateFlow<NoteState?>(null)
        val note: StateFlow<NoteState?> = noteState.asStateFlow()

        /** Where the open note was last shown, restored after rotation. */
        var noteAnchor: ViewportAnchor? = null

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

        fun startNewNote(path: List<FolderRef>) {
            viewModelScope.launch {
                val name = withContext(io) { NewNoteCreator.suggestedName(context.contentResolver, path.last()) }
                val paper = PaperSize.entries.getOrNull(preferences.getInt(PAPER_KEY, 0)) ?: PaperSize.A4
                newNoteState.value = NewNoteRequest(path, name, paper, preferences.getBoolean(LANDSCAPE_KEY, false))
            }
        }

        fun cancelNewNote() {
            newNoteState.value = null
        }

        /** Creates the note and opens it. */
        fun createNote(
            name: String,
            paper: PaperSize,
            landscape: Boolean,
        ) {
            val request = newNoteState.value ?: return
            newNoteState.value = null
            preferences
                .edit()
                .putInt(PAPER_KEY, paper.ordinal)
                .putBoolean(LANDSCAPE_KEY, landscape)
                .apply()
            viewModelScope.launch {
                val resolver = context.contentResolver
                val created =
                    withContext(io) {
                        runCatching {
                            val uri =
                                NewNoteCreator.create(
                                    resolver,
                                    request.folder,
                                    name,
                                    paper,
                                    landscape,
                                    NewNoteCreator.creator(context),
                                )
                            uri to (FolderListing.nameOf(resolver, uri) ?: NewNoteCreator.fileName(name))
                        }
                    }
                created
                    .onSuccess { (uri, fileName) ->
                        library.refresh(request.folder)
                        open(Screen.Note(uri, fileName, DocumentLocation(request.path, fileName)))
                    }.onFailure { e ->
                        Log.w(TAG, "Cannot create $name", e)
                        errorState.value = context.getString(R.string.error_create_note, name)
                    }
            }
        }

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
                val state = withContext(io) { NoteLoader.load(context, screen.uri, screen.name, screen.location) }
                // Still the same note?
                if (stack.value.lastOrNull() == screen) {
                    noteState.value = state
                } else {
                    (state as? NoteState.Loaded)?.files?.close()
                }
            }
        }

        private fun closeNote() {
            (noteState.value as? NoteState.Loaded)?.let {
                it.caches.clear()
                it.files.close()
            }
            noteState.value = null
            noteAnchor = null
        }

        override fun onCleared() = closeNote()

        private companion object {
            const val TAG = "Inkpad"
            const val PAPER_KEY = "paper"
            const val LANDSCAPE_KEY = "landscape"
        }
    }
