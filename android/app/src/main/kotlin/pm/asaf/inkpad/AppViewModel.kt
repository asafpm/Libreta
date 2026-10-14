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

    data class Note(
        val uri: Uri,
        val name: String,
    ) : Screen
}

/** The open note. */
sealed interface NoteState {
    data object Loading : NoteState

    data class Loaded(
        val document: Document,
        /** Tiles and thumbnails, kept across rotation. */
        val caches: RenderCaches,
    ) : NoteState

    data class Failed(
        val message: String,
    ) : NoteState
}

/** The new-note dialog for [folder], with a suggested [name]. */
data class NewNoteRequest(
    val folder: FolderRef,
    val name: String,
    val paper: PaperSize,
    val landscape: Boolean,
)

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

        fun startNewNote(folder: FolderRef) {
            viewModelScope.launch {
                val name = withContext(io) { NewNoteCreator.suggestedName(context.contentResolver, folder) }
                val paper = PaperSize.entries.getOrNull(preferences.getInt(PAPER_KEY, 0)) ?: PaperSize.A4
                newNoteState.value = NewNoteRequest(folder, name, paper, preferences.getBoolean(LANDSCAPE_KEY, false))
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
                            val uri = NewNoteCreator.create(resolver, request.folder, name, paper, landscape, creator())
                            uri to (FolderListing.nameOf(resolver, uri) ?: NewNoteCreator.fileName(name))
                        }
                    }
                created
                    .onSuccess { (uri, fileName) ->
                        library.refresh(request.folder)
                        open(Screen.Note(uri, fileName))
                    }.onFailure { e ->
                        Log.w(TAG, "Cannot create $name", e)
                        errorState.value = context.getString(R.string.error_create_note, name)
                    }
            }
        }

        private fun creator(): String {
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            return listOfNotNull(context.getString(R.string.app_name), version).joinToString(" ")
        }

        // The open note

        private fun loadNote(screen: Screen.Note) {
            noteAnchor = null
            noteState.value = NoteState.Loading
            viewModelScope.launch {
                val state =
                    withContext(io) {
                        try {
                            val stream =
                                context.contentResolver.openInputStream(screen.uri) ?: throw IOException("No stream")
                            val document = stream.source().use { XoppReader.read(it).document }
                            NoteState.Loaded(document, RenderCaches.forDevice(context))
                        } catch (e: IOException) {
                            Log.w(TAG, "Cannot read ${screen.uri}", e)
                            NoteState.Failed(context.getString(R.string.error_read_note, screen.name))
                        } catch (e: XoppFormatException) {
                            Log.w(TAG, "Cannot read ${screen.uri}", e)
                            NoteState.Failed(context.getString(R.string.error_not_a_note, screen.name))
                        } catch (e: SecurityException) {
                            Log.w(TAG, "No access to ${screen.uri}", e)
                            NoteState.Failed(context.getString(R.string.error_read_note, screen.name))
                        }
                    }
                // Still the same note?
                if (stack.value.lastOrNull() == screen) noteState.value = state
            }
        }

        private fun closeNote() {
            (noteState.value as? NoteState.Loaded)?.caches?.clear()
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
