// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.R
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreId
import java.io.IOException

/**
 * Notes whose last changes were not saved when the app last stopped (killed while saving, or
 * while saving failed), as [Recovery] kept them: offered once, at start, to put back into the
 * note, save as a copy elsewhere, or discard. Work is done on [io]; [onMessage] tells how it went.
 */
class RecoveredNotes(
    private val context: Context,
    private val store: DocumentStore,
    private val recovery: Recovery,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onMessage: (String) -> Unit,
) {
    private val state = MutableStateFlow<List<Recovery.Copy>>(emptyList())

    /** What there is to recover, first to be asked about first. */
    val copies: StateFlow<List<Recovery.Copy>> = state.asStateFlow()

    init {
        scope.launch { state.value = withContext(io) { recovery.copies() } }
    }

    /** Writes [copy] over its note's file. */
    fun putBack(copy: Recovery.Copy) = write(copy, copy.id, R.string.recovered, R.string.error_recover)

    /** Writes [copy] to [uri], a file the user picked. */
    fun saveCopy(
        copy: Recovery.Copy,
        uri: Uri,
    ) = write(copy, StoreId(uri.toString()), R.string.saved_copy, R.string.error_save_copy)

    /** Forgets [copy], with the changes in it. */
    fun discard(copy: Recovery.Copy) {
        state.update { it - copy }
        scope.launch { withContext(io) { recovery.forget(copy.id) } }
    }

    private fun write(
        copy: Recovery.Copy,
        to: StoreId,
        done: Int,
        failed: Int,
    ) {
        scope.launch {
            val written =
                withContext(io) {
                    try {
                        store.write(to, copy.bytes(), expected = null)
                        recovery.forget(copy.id)
                        true
                    } catch (e: IOException) {
                        Log.w(TAG, "Cannot recover ${copy.name}", e)
                        false
                    }
                }
            if (written) state.update { it - copy }
            onMessage(context.getString(if (written) done else failed, copy.name))
        }
    }

    private companion object {
        const val TAG = "RecoveredNotes"
    }
}
