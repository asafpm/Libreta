// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.source
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.format.XoppFormatException
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.geometry.ViewportAnchor
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.PaperSize
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.files.Autosave
import pm.asaf.libreta.files.Backups
import pm.asaf.libreta.files.DocumentFiles
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.files.NoteClipboard
import pm.asaf.libreta.files.NoteFile
import pm.asaf.libreta.files.NoteLoader
import pm.asaf.libreta.files.NoteSaver
import pm.asaf.libreta.files.NoteSession
import pm.asaf.libreta.files.PdfExportFlow
import pm.asaf.libreta.files.RecoveredNotes
import pm.asaf.libreta.files.Recovery
import pm.asaf.libreta.files.SaveResult
import pm.asaf.libreta.files.UnsavedNotes
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.Library
import pm.asaf.libreta.library.LibraryFlow
import pm.asaf.libreta.library.NewFolderFlow
import pm.asaf.libreta.library.NewNoteCreator
import pm.asaf.libreta.library.NewNoteFlow
import pm.asaf.libreta.library.OpenedPdfs
import pm.asaf.libreta.library.PdfNotesFlow
import pm.asaf.libreta.library.PreviewCache
import pm.asaf.libreta.settings.AppearancePreferences
import pm.asaf.libreta.settings.ColorPreferences
import pm.asaf.libreta.settings.PagePreferences
import pm.asaf.libreta.settings.ToolPreferences
import pm.asaf.libreta.store.OpenedPdfsProvider
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

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

/** A note ([screen]) closed while it couldn't be saved: [note] is still open, with its changes. */
class UnsavedNote(
    val screen: Screen.Note,
    val note: NoteState.Loaded,
)

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
        private val openedPdfs =
            OpenedPdfs(
                application,
                store,
                FolderRef(OpenedPdfsProvider.folder(application), application.getString(R.string.opened_pdfs)),
            )
        val library =
            Library(
                store,
                application.contentResolver,
                preferences,
                viewModelScope,
                io,
                openedPdfs.folder,
                onAddFailed = { errorState.value = application.getString(R.string.error_add_folder) },
            )
        val previews =
            PreviewCache(application, store, locate = { NoteLoader.locate(store, library.roots.value, it.id, it.name) })
        val tools = ToolPreferences(preferences)
        val colors = ColorPreferences(preferences)
        val pages = PagePreferences(preferences)
        val appearance = AppearancePreferences(preferences)

        /** Elements copied, to paste in any note. */
        val clipboard = NoteClipboard(application)

        private val stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
        val screens: StateFlow<List<Screen>> = stack.asStateFlow()

        val newNotes =
            NewNoteFlow(
                application,
                store,
                pages,
                viewModelScope,
                io,
                onCreated = { file, path ->
                    library.refresh(path.last())
                    open(Screen.Note(file.id, file.name, DocumentLocation(path, file.name)))
                },
                onError = { errorState.value = it },
            )

        val newFolders =
            NewFolderFlow(
                application,
                store,
                viewModelScope,
                io,
                onCreated = { parent -> library.refresh(parent) },
                onError = { errorState.value = it },
            )

        /** Opening PDFs in notes, and the notes of PDFs from other apps. */
        val pdfs =
            PdfNotesFlow(
                application,
                store,
                library,
                openedPdfs,
                viewModelScope,
                io,
                onOpen = { showNote(it) },
                onMessage = { errorState.value = it },
            )

        /** Renaming, moving and deleting folders and notes. */
        val entries =
            LibraryFlow(application, store, library, viewModelScope, io, onMessage = { errorState.value = it })

        /** Exporting the open note as a PDF. */
        val exports = PdfExportFlow(application, viewModelScope, io, onMessage = { errorState.value = it })

        private val noteState = MutableStateFlow<NoteState?>(null)
        val note: StateFlow<NoteState?> = noteState.asStateFlow()

        /** Where the open note was last shown, restored after rotation. */
        var noteAnchor: ViewportAnchor? = null

        private val recovery = Recovery(File(application.filesDir, "recovery"))
        private val saver = NoteSaver(application, store, Backups(File(application.filesDir, "backups")), recovery)

        /** Notes whose last changes weren't saved when the app last stopped. */
        val recovered = RecoveredNotes(application, store, recovery, viewModelScope, io) { errorState.value = it }

        /** The save in progress: a note is read only after it, in case it is the same file. */
        private var saving: Job? = null

        /** Saving the open note as it is edited. */
        private var autosaving: Job? = null

        /** Whether the last save failed, not to say so again with each autosave. */
        private var lastSaveFailed = false

        private val saveFailedState = MutableStateFlow(false)

        /** Whether the open note's last save failed: its changes are not in its file yet. */
        val saveFailed: StateFlow<Boolean> = saveFailedState.asStateFlow()

        /** Saving again after a failure, until one works. */
        private var retrying: Job? = null

        /** A note left while it couldn't be saved: kept, with its changes, until saved, copied or discarded. */
        val unsaved =
            UnsavedNotes(
                application,
                saver,
                viewModelScope,
                io,
                onMessage = { errorState.value = it },
                onSaved = { note -> note.file.location?.let { library.refresh(it.folders.last()) } },
                onReopen = { unsaved ->
                    if (stack.value.last() is Screen.Note) back()
                    stack.update { it + unsaved.screen }
                    lastSaveFailed = false
                    noteState.value = unsaved.note
                    startAutosave(unsaved.note)
                    // Still unsaved: the banner and retries go on.
                    saveNote()
                },
            )

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

        /**
         * Opens a note from another app ("Open with"), finding it in the library if it is there.
         * A PDF opens in its note: next to it in the library, or else kept in the app.
         */
        fun openExternal(id: StoreId) {
            viewModelScope.launch {
                val name = withContext(io) { NoteLoader.nameOf(store, id) }
                if (pdfs.isPdf(id, name)) {
                    pdfs.openShared(id, name)
                } else {
                    val screen =
                        withContext(
                            io,
                        ) { Screen.Note(id, name, NoteLoader.locate(store, library.roots.value, id, name)) }
                    showNote(screen)
                }
            }
        }

        /** Shows [screen] in place of the note shown, if any. */
        private fun showNote(screen: Screen.Note) {
            if (stack.value.last() is Screen.Note) back()
            open(screen)
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
            // Each note's failures are its own.
            lastSaveFailed = false
            saveFailedState.value = false
            noteAnchor = null
            noteState.value = NoteState.Loading
            viewModelScope.launch {
                saving?.join()
                val state = withContext(io) { NoteLoader.load(context, store, screen.id, screen.name, screen.location) }
                // Still shown, as this opening of it? (The same note opened again is loaded again.)
                if (stack.value.lastOrNull() === screen) {
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

        /**
         * Saves the open note if it changed, then calls [then] on the main thread with what saving
         * did (null without a note). Returns the save's job.
         */
        fun saveNote(then: (SaveResult?) -> Unit = {}): Job {
            val note = noteState.value as? NoteState.Loaded
            val previous = saving
            val job =
                viewModelScope.launch {
                    previous?.join()
                    val result = note?.let { withContext(io) { saver.save(it.file, it.session, it.files) } }
                    if (note != null && result != null) report(note, result)
                    then(result)
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
            retrying?.cancel()
            if (noteState.value === note) {
                saveFailedState.value = result is SaveResult.Failed
                // Autosave waits for the next change; the provider may be back before then.
                if (result is SaveResult.Failed) {
                    retrying =
                        viewModelScope.launch {
                            delay(RETRY_DELAY)
                            saveNote()
                        }
                }
            }
            if (result is SaveResult.Saved || result is SaveResult.SavedAsCopy) {
                note.file.location?.let { library.refresh(it.folders.last()) }
            }
        }

        /**
         * Leaves the open note: it is saved, then its files are released; or, if saving fails, it
         * is kept with its changes ([unsaved]).
         */
        private fun closeNote() {
            val note = noteState.value as? NoteState.Loaded
            val screen = stack.value.lastOrNull() as? Screen.Note
            autosaving?.cancel()
            autosaving = null
            retrying?.cancel()
            saveNote { result ->
                if (result is SaveResult.Failed && note != null && screen != null) {
                    unsaved.keep(UnsavedNote(screen, note))
                } else {
                    note?.let(UnsavedNotes::release)
                }
            }
            noteState.value = null
            saveFailedState.value = false
            noteAnchor = null
        }

        // Saved when the app stopped (MainActivity.onStop); the scope is gone now.
        override fun onCleared() {
            clipboard.close()
            (noteState.value as? NoteState.Loaded)?.files?.close()
        }

        private companion object {
            const val TAG = "Libreta"

            /** How soon a failed save is tried again. */
            val RETRY_DELAY = 30.seconds
        }
    }
