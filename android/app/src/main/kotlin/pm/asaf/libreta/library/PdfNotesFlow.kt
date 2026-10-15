// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.R
import pm.asaf.libreta.Screen
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.files.NoteLoader

/**
 * Opening PDFs in their notes: next to them in the [library], or for PDFs from other apps that
 * aren't in it, kept in the app ([opened]), from where they can be moved or deleted. Files are
 * handled on [io]; [onOpen] shows a note, [onMessage] tells the user something.
 */
class PdfNotesFlow(
    private val context: Context,
    private val store: DocumentStore,
    private val library: Library,
    private val opened: OpenedPdfs,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onOpen: (Screen.Note) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    /** Opens the note of [pdf] (in the folder at the end of [path]), made the first time. */
    fun open(
        pdf: LibraryEntry.Pdf,
        path: List<FolderRef>,
    ) = show(pdf.name) { noteNextTo(pdf, path) }

    /** Opens the note of the PDF [id] (from another app), named [name]. */
    fun openShared(
        id: StoreId,
        name: String,
    ) = show(name) {
        val location = NoteLoader.locate(store, library.roots.value, id, name)
        if (location != null) {
            noteNextTo(LibraryEntry.Pdf(id, name, FileVersion(0, 0), location.folders.last()), location.folders)
        } else {
            val file = opened.open(id, name, NewNoteCreator.creator(context))
            library.refresh(opened.folder)
            Screen.Note(file.id, file.name, DocumentLocation(listOf(opened.folder), file.name))
        }
    }

    /** Whether the file [id] named [name] is a PDF. */
    fun isPdf(
        id: StoreId,
        name: String,
    ): Boolean = FolderListing.isPdfName(name) || context.contentResolver.getType(Uri.parse(id.value)) == PDF_TYPE

    /** Moves [note], kept in the app, with its PDF to the library folder [target]. */
    fun move(
        note: LibraryEntry.Document,
        target: FolderRef,
    ) {
        scope.launch {
            withContext(io) { runCatching { opened.move(note, target) } }
                .onSuccess { onMessage(context.getString(R.string.moved_to, target.name)) }
                .onFailure { e ->
                    Log.w(TAG, "Cannot move ${note.name}", e)
                    onMessage(context.getString(R.string.error_move_note, note.name))
                }
            library.refresh(opened.folder)
            library.refresh(target)
        }
    }

    /** Deletes [note], kept in the app, and its PDF. */
    fun delete(note: LibraryEntry.Document) {
        scope.launch {
            withContext(
                io,
            ) { runCatching { opened.delete(note) } }.onFailure { Log.w(TAG, "Cannot delete ${note.name}", it) }
            library.refresh(opened.folder)
        }
    }

    private fun noteNextTo(
        pdf: LibraryEntry.Pdf,
        path: List<FolderRef>,
    ): Screen.Note {
        val file = PdfNotes.open(context, store, pdf, NewNoteCreator.creator(context))
        library.refresh(path.last())
        return Screen.Note(file.id, file.name, DocumentLocation(path, file.name))
    }

    /** Shows the note [find] gives, found on [io]; or says the PDF [name] can't be opened. */
    private fun show(
        name: String,
        find: () -> Screen.Note,
    ) {
        scope.launch {
            withContext(io) { runCatching(find) }
                .onSuccess(onOpen)
                .onFailure { e ->
                    Log.w(TAG, "Cannot open $name", e)
                    onMessage(context.getString(R.string.error_open_pdf, name))
                }
        }
    }

    private companion object {
        const val TAG = "PdfNotesFlow"
        const val PDF_TYPE = "application/pdf"
    }
}
