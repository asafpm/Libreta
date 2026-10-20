// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.R
import pm.asaf.libreta.UnsavedNote
import pm.asaf.libreta.core.store.StoreId

/**
 * A note left while it couldn't be saved (its provider offline, its file gone, …): kept open,
 * with its changes, until it is saved, copied elsewhere, reopened ([onReopen]) or discarded.
 * Saving is done on [io]; [onMessage] tells how it went, [onSaved] gets a note saved.
 */
class UnsavedNotes(
    private val context: Context,
    private val saver: NoteSaver,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onMessage: (String) -> Unit,
    private val onSaved: (NoteState.Loaded) -> Unit,
    private val onReopen: (UnsavedNote) -> Unit,
) {
    private val state = MutableStateFlow<UnsavedNote?>(null)

    /** The note left unsaved, if any. */
    val note: StateFlow<UnsavedNote?> = state.asStateFlow()

    /** Keeps [unsaved], whose save failed as it was closed. */
    fun keep(unsaved: UnsavedNote) {
        state.value = unsaved
    }

    /** Tries saving it again; it is released once saved. */
    fun retry() {
        val unsaved = state.value ?: return
        val note = unsaved.note
        scope.launch {
            val result = withContext(io) { saver.save(note.file, note.session, note.files) }
            if (result is SaveResult.Failed) {
                onMessage(context.getString(R.string.error_save_note, note.file.name))
            } else {
                done(unsaved)
                onSaved(note)
            }
        }
    }

    /** Saves it to [uri] (a file the user picked), then lets it go. */
    fun saveCopy(uri: Uri) {
        val unsaved = state.value ?: return
        val note = unsaved.note
        scope.launch {
            val result = withContext(io) { saver.saveCopy(note.session, note.files, StoreId(uri.toString())) }
            if (result is SaveResult.Failed) {
                onMessage(context.getString(R.string.error_save_copy))
            } else {
                onMessage(context.getString(R.string.saved_copy))
                saver.forget(note.file)
                done(unsaved)
            }
        }
    }

    /** Opens it again, with its changes, to go on editing it. */
    fun keepEditing() {
        val unsaved = state.value ?: return
        state.value = null
        onReopen(unsaved)
    }

    /** Lets it go, with the changes that couldn't be saved. */
    fun discard() {
        state.value?.let { unsaved ->
            done(unsaved)
            scope.launch { withContext(io) { saver.forget(unsaved.note.file) } }
        }
    }

    private fun done(unsaved: UnsavedNote) {
        if (state.value === unsaved) state.value = null
        release(unsaved.note)
    }

    companion object {
        /** Releases what an open [note] holds: its caches and files. */
        fun release(note: NoteState.Loaded) {
            note.caches.clear()
            note.files.close()
        }
    }
}
