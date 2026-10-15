// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.store.DocumentStore

/**
 * Creating a folder: the dialog for the folder it goes in ([request]), then the folder.
 * [onCreated] gets the folder it was made in; [onError] a message.
 */
class NewFolderFlow(
    private val context: Context,
    private val store: DocumentStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onCreated: (parent: FolderRef) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val state = MutableStateFlow<FolderRef?>(null)

    /** The folder the dialog makes a folder in, if it is shown. */
    val request: StateFlow<FolderRef?> = state.asStateFlow()

    fun start(parent: FolderRef) {
        state.value = parent
    }

    fun cancel() {
        state.value = null
    }

    /** Creates the folder named [name]. */
    fun create(name: String) {
        val parent = state.value ?: return
        state.value = null
        val cleaned = folderName(name)
        scope.launch {
            withContext(io) { runCatching { store.createFolder(parent.id, cleaned) } }
                .onSuccess { onCreated(parent) }
                .onFailure { e ->
                    Log.w(TAG, "Cannot create folder $cleaned", e)
                    onError(context.getString(R.string.error_create_folder, cleaned))
                }
        }
    }

    private companion object {
        const val TAG = "NewFolderFlow"

        /** [name] made safe as a folder name. */
        fun folderName(name: String): String = name.trim().replace('/', '-')
    }
}
