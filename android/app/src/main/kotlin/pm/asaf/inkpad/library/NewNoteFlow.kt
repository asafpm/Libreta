// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.model.PaperSize

/** The new-note dialog for the last folder of [path] (from a library root), with a suggested [name]. */
data class NewNoteRequest(
    val path: List<FolderRef>,
    val name: String,
    val paper: PaperSize,
    val landscape: Boolean,
) {
    val folder: FolderRef get() = path.last()
}

/**
 * Creating a note: the dialog's suggestions ([request]), then the file. Paper size and
 * orientation are remembered in [preferences]. [onCreated] gets the new note's uri, name and
 * folder path; [onError] a message.
 */
class NewNoteFlow(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onCreated: (uri: Uri, name: String, path: List<FolderRef>) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val state = MutableStateFlow<NewNoteRequest?>(null)

    /** The dialog to show, if any. */
    val request: StateFlow<NewNoteRequest?> = state.asStateFlow()

    /** Opens the dialog for the last folder of [path]. */
    fun start(path: List<FolderRef>) {
        scope.launch {
            val name = withContext(io) { NewNoteCreator.suggestedName(context.contentResolver, path.last()) }
            val paper = PaperSize.entries.getOrNull(preferences.getInt(PAPER_KEY, 0)) ?: PaperSize.A4
            state.value = NewNoteRequest(path, name, paper, preferences.getBoolean(LANDSCAPE_KEY, false))
        }
    }

    fun cancel() {
        state.value = null
    }

    /** Creates the note the dialog describes. */
    fun create(
        name: String,
        paper: PaperSize,
        landscape: Boolean,
    ) {
        val request = state.value ?: return
        state.value = null
        preferences
            .edit()
            .putInt(PAPER_KEY, paper.ordinal)
            .putBoolean(LANDSCAPE_KEY, landscape)
            .apply()
        scope.launch {
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
                .onSuccess { (uri, fileName) -> onCreated(uri, fileName, request.path) }
                .onFailure { e ->
                    Log.w(TAG, "Cannot create $name", e)
                    onError(context.getString(R.string.error_create_note, name))
                }
        }
    }

    private companion object {
        const val TAG = "NewNoteFlow"
        const val PAPER_KEY = "paper"
        const val LANDSCAPE_KEY = "landscape"
    }
}
