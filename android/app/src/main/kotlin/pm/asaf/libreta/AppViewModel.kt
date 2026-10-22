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
import pm.asaf.libreta.core.store.FileVersion
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
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.LibraryFiles
import pm.asaf.libreta.library.LibraryFlow
import pm.asaf.libreta.library.LibrarySearch
import pm.asaf.libreta.library.NewFolderFlow
import pm.asaf.libreta.library.NewNoteCreator
import pm.asaf.libreta.library.NewNoteFlow
import pm.asaf.libreta.library.OpenedPdfs
import pm.asaf.libreta.library.PdfNotesFlow
import pm.asaf.libreta.library.PreviewCache
import pm.asaf.libreta.library.RecentNote
import pm.asaf.libreta.library.RecentNotes
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

/** Whether the open note's changes are in its file. */
enum class SaveStatus {
    SAVED,

    /** Changed since its last save; saved soon. */
    SAVING,

    /** Its last save failed. */
    NOT_SAVED,
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

        /** What to tell the user, e.g. a failed file operation, or a change that can be undone. */
        val messages = Messages()

        /** Where notes are: the folders granted through the Storage Access Framework. */
        private val store = SafDocumentStore(application.contentResolver)

        /** The notes opened last, and the one left open. */
        val recent = RecentNotes(preferences, store, viewModelScope, io)

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
                onAddFailed = { messages.say(application.getString(R.string.error_add_folder)) },
            )

        /** Looking for notes by name all through the library. */
        val search = LibrarySearch(store, { library.roots.value }, { library.sortOrder.value }, viewModelScope, io)

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
                onError = { messages.say(it) },
            )

        val newFolders =
            NewFolderFlow(
                application,
                store,
                viewModelScope,
                io,
                onCreated = { parent -> library.refresh(parent) },
                onError = { messages.say(it) },
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
                onMessage = { messages.say(it) },
            )

        /** Renaming, moving and deleting folders and notes. */
        val entries =
            LibraryFlow(application, store, library, viewModelScope, io, messages)

        /** Exporting the open note as a PDF. */
        val exports = PdfExportFlow(application, viewModelScope, io, messages)

        private val noteState = MutableStateFlow<NoteState?>(null)
        val note: StateFlow<NoteState?> = noteState.asStateFlow()

        /** Where the open note was last shown, restored after rotation. */
        var noteAnchor: ViewportAnchor? = null

        private val recovery = Recovery(File(application.filesDir, "recovery"))
        private val saver = NoteSaver(application, store, Backups(File(application.filesDir, "backups")), recovery)

        /** Notes whose last changes weren't saved when the app last stopped. */
        val recovered = RecoveredNotes(application, store, recovery, viewModelScope, io) { messages.say(it) }

        /** The save in progress: a note is read only after it, in case it is the same file. */
        private var saving: Job? = null

        /** Saving the open note as it is edited. */
        private var autosaving: Job? = null

        /** Whether the last save failed, not to say so again with each autosave. */
        private var lastSaveFailed = false

        private val saveStatusState = MutableStateFlow(SaveStatus.SAVED)

        /** Whether the open note's changes are in its file. */
        val saveStatus: StateFlow<SaveStatus> = saveStatusState.asStateFlow()

        /** Saving again after a failure, until one works. */
        private var retrying: Job? = null

        /** A note left while it couldn't be saved: kept, with its changes, until saved, copied or discarded. */
        val unsaved =
            UnsavedNotes(
                application,
                saver,
                viewModelScope,
                io,
                onMessage = { messages.say(it) },
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

        // Navigation

        fun open(screen: Screen) {
            stack.update { it + screen }
            when (screen) {
                is Screen.Folder -> {
                    library.refresh(screen.folder)
                }

                is Screen.Note -> {
                    loadNote(screen)
                }

                Screen.Home -> {
                    library.refreshRoots()
                    recent.refresh()
                }

                Screen.Settings -> {
                    Unit
                }
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
                is Screen.Folder -> {
                    library.refresh(shown.folder)
                }

                Screen.Home -> {
                    library.refreshRoots()
                    recent.refresh()
                }

                else -> {
                    Unit
                }
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

        /**
         * Renames the open note to [stem] (its name without extensions), with what goes with it:
         * it is saved and closed, renamed, then opened again where it was shown ([anchor]). Not
         * if it can't be saved (then the note left unsaved is offered).
         */
        fun renameNote(
            stem: String,
            anchor: ViewportAnchor?,
        ) {
            val screen = stack.value.last() as? Screen.Note
            val note = noteState.value as? NoteState.Loaded
            val location = screen?.location
            if (screen == null || note == null || location == null) return
            val entry =
                LibraryEntry.Document(
                    note.file.id,
                    note.file.name,
                    note.file.version ?: FileVersion(0, -1),
                    location.folders.last(),
                )
            back()
            viewModelScope.launch {
                saving?.join()
                if (unsaved.note.value?.note === note) return@launch
                val renamed = withContext(io) { runCatching { LibraryFiles(store).rename(entry, stem.trim()) } }
                renamed.exceptionOrNull()?.let { messages.say(entries.message(it, entry, R.string.error_rename)) }
                val file = renamed.getOrNull()
                open(file?.let { Screen.Note(it.id, it.name, location.copy(name = it.name)) } ?: screen)
                noteAnchor = anchor
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
            // Each note's failures are its own.
            lastSaveFailed = false
            saveStatusState.value = SaveStatus.SAVED
            noteAnchor = null
            noteState.value = NoteState.Loading
            viewModelScope.launch {
                saving?.join()
                val state = withContext(io) { NoteLoader.load(context, store, screen.id, screen.name, screen.location) }
                // Still shown, as this opening of it? (The same note opened again is loaded again.)
                if (stack.value.lastOrNull() === screen) {
                    noteState.value = state
                    if (state is NoteState.Loaded) {
                        startAutosave(state)
                        recent.opened(RecentNote(screen.id, screen.name, screen.location))
                    }
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
                    // Changes from now on: saved soon (unless saving fails).
                    launch {
                        note.session.changes.drop(1).collect {
                            if (saveStatusState.value == SaveStatus.SAVED) saveStatusState.value = SaveStatus.SAVING
                        }
                    }
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
                    messages.say(context.getString(R.string.saved_as_copy, result.name))
                }

                result is SaveResult.Failed && !lastSaveFailed -> {
                    messages.say(context.getString(R.string.error_save_note, note.file.name))
                }
            }
            lastSaveFailed = result is SaveResult.Failed
            retrying?.cancel()
            if (noteState.value === note) {
                saveStatusState.value =
                    when {
                        result is SaveResult.Failed -> SaveStatus.NOT_SAVED
                        note.session.dirty -> SaveStatus.SAVING
                        else -> SaveStatus.SAVED
                    }
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
            recent.closed()
            saveNote { result ->
                if (result is SaveResult.Failed && note != null && screen != null) {
                    unsaved.keep(UnsavedNote(screen, note))
                } else {
                    note?.let(UnsavedNotes::release)
                }
            }
            noteState.value = null
            saveStatusState.value = SaveStatus.SAVED
            noteAnchor = null
        }

        init {
            // Android stopped the app while a note was open: it opens again.
            recent.takeOpen()?.let { open(Screen.Note(it.id, it.name, it.location)) }
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
