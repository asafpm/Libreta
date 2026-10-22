// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.Message
import pm.asaf.libreta.Messages
import pm.asaf.libreta.R
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry

/** A dialog about a library entry. */
sealed interface EntryRequest {
    val entry: LibraryEntry

    data class Rename(
        override val entry: LibraryEntry,
    ) : EntryRequest

    data class Move(
        override val entry: LibraryEntry,
    ) : EntryRequest

    /** Deleting [entry]; [pdf] names the PDF that may go with it (a choice). */
    data class Delete(
        override val entry: LibraryEntry,
        val pdf: String?,
    ) : EntryRequest
}

/**
 * Renaming, moving and deleting library entries ([LibraryFiles]): the dialog for one
 * ([request]), then the change, on [io]. The folders changed are listed again in [library];
 * [messages] gets what to tell: a move can be undone, and a change that failed tried again.
 */
class LibraryFlow(
    private val context: Context,
    store: DocumentStore,
    private val library: Library,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val messages: Messages,
) {
    private val files = LibraryFiles(store)
    private val state = MutableStateFlow<EntryRequest?>(null)

    /** The dialog shown, if any. */
    val request: StateFlow<EntryRequest?> = state.asStateFlow()

    fun rename(entry: LibraryEntry) {
        state.value = EntryRequest.Rename(entry)
    }

    fun move(entry: LibraryEntry) {
        state.value = EntryRequest.Move(entry)
    }

    /** Asks about deleting [entry], once the PDF that may go with it is known. */
    fun delete(entry: LibraryEntry) {
        scope.launch {
            val pdf =
                (entry as? LibraryEntry.Document)?.let { note ->
                    withContext(io) { runCatching { files.pdfOf(note) }.getOrNull() }
                }
            state.value = EntryRequest.Delete(entry, pdf?.name)
        }
    }

    fun cancel() {
        state.value = null
    }

    /** Renames the entry asked about to [stem] (its name without extensions). */
    fun renameTo(stem: String) {
        val entry = (state.value as? EntryRequest.Rename)?.entry ?: return
        state.value = null
        run(entry, R.string.error_rename, listOf(entry.parent), { files.rename(entry, stem.trim()) })
    }

    /** Moves the entry asked about into [target]. */
    fun moveTo(target: FolderRef) {
        val entry = (state.value as? EntryRequest.Move)?.entry ?: return
        state.value = null
        drop(entry, target)
    }

    /** Moves [entry] into [target], as dropped there. */
    fun drop(
        entry: LibraryEntry,
        target: FolderRef,
    ) {
        if (target.id == entry.parent.id) return
        run(entry, R.string.error_move, listOf(entry.parent, target), { files.move(entry, target) }) { moved ->
            // Undone by moving it back.
            val undo = Message.Action(context.getString(R.string.undo)) { drop(moved.at(target), entry.parent) }
            messages.say(context.getString(R.string.moved_to, target.name), undo)
        }
    }

    /** Copies the note [entry] (with its attached files) next to it. */
    fun duplicate(entry: LibraryEntry.Document) =
        run(entry, R.string.error_duplicate, listOf(entry.parent), { files.duplicate(entry) })

    /** Deletes the entry asked about, and the PDF that goes with it if [alsoPdf]. */
    fun confirmDelete(alsoPdf: Boolean) {
        val entry = (state.value as? EntryRequest.Delete)?.entry ?: return
        state.value = null
        run(entry, R.string.error_delete, listOf(entry.parent), { files.delete(entry, alsoPdf) })
    }

    /**
     * Does [change] to [entry] on [io], then lists [changed] again and calls [done] with what it
     * gave; or says what failed ([failed]), offering to try again unless it was refused.
     */
    private fun <T> run(
        entry: LibraryEntry,
        failed: Int,
        changed: List<FolderRef>,
        change: () -> T,
        done: (T) -> Unit = {},
    ) {
        scope.launch {
            withContext(io) { runCatching(change) }
                .onSuccess(done)
                .onFailure { e ->
                    Log.w(TAG, "Cannot change ${entry.name}", e)
                    val retry =
                        Message.Action(context.getString(R.string.retry)) { run(entry, failed, changed, change, done) }
                    messages.say(message(e, entry, failed), retry.takeIf { e !is LibraryFiles.Refused })
                }
            changed.forEach(library::refresh)
        }
    }

    /** What to tell about [e], which [entry]'s change failed with; [failed] says it in general. */
    fun message(
        e: Throwable,
        entry: LibraryEntry,
        failed: Int,
    ): String =
        when ((e as? LibraryFiles.Refused)?.problem) {
            LibraryFiles.Problem.NAME_TAKEN -> context.getString(R.string.error_name_taken, e.message(entry))
            LibraryFiles.Problem.INTO_ITSELF -> context.getString(R.string.error_into_itself, entry.name)
            LibraryFiles.Problem.BAD_NAME -> context.getString(R.string.error_bad_name, e.message(entry))
            null -> context.getString(failed, entry.name)
        }

    private companion object {
        const val TAG = "LibraryFlow"

        fun Throwable.message(entry: LibraryEntry) = (this as? LibraryFiles.Refused)?.name ?: entry.name

        /** This entry as listed in [folder]. */
        fun StoreEntry.at(folder: FolderRef): LibraryEntry =
            when (this) {
                is StoreEntry.Folder -> {
                    LibraryEntry.Folder(FolderRef(id, name), modified, folder)
                }

                is StoreEntry.File -> {
                    if (FolderListing.isPdfName(name)) {
                        LibraryEntry.Pdf(id, name, version, folder)
                    } else {
                        LibraryEntry.Document(id, name, version, folder)
                    }
                }
            }
    }
}
