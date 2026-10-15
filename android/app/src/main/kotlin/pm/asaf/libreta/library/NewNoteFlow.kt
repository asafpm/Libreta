// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.PaperSize
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.settings.PagePreferences

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
 * Creating a note: the dialog's suggestions ([request]), then the file. Its page follows the
 * new page template in [pages]; the paper size and orientation chosen are remembered there.
 * [onCreated] gets the new note's file and folder path; [onError] a message.
 */
class NewNoteFlow(
    private val context: Context,
    private val store: DocumentStore,
    private val pages: PagePreferences,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onCreated: (file: StoreEntry.File, path: List<FolderRef>) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val state = MutableStateFlow<NewNoteRequest?>(null)

    /** The dialog to show, if any. */
    val request: StateFlow<NewNoteRequest?> = state.asStateFlow()

    /** Opens the dialog for the last folder of [path]. */
    fun start(path: List<FolderRef>) {
        scope.launch {
            val name = withContext(io) { NewNoteCreator.suggestedName(store, path.last()) }
            val template = pages.template.value
            state.value = NewNoteRequest(path, name, template.paper, template.landscape)
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
        val template = pages.template.value.copy(paper = paper, landscape = landscape)
        pages.setTemplate(template)
        scope.launch {
            val created =
                withContext(io) {
                    runCatching {
                        NewNoteCreator.create(
                            store,
                            request.folder,
                            name,
                            template,
                            NewNoteCreator.creator(context),
                        )
                    }
                }
            created
                .onSuccess { file -> onCreated(file, request.path) }
                .onFailure { e ->
                    Log.w(TAG, "Cannot create $name", e)
                    onError(context.getString(R.string.error_create_note, name))
                }
        }
    }

    private companion object {
        const val TAG = "NewNoteFlow"
    }
}
