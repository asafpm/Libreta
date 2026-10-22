// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreException
import pm.asaf.libreta.store.SafDocumentStore

/** The contents of a folder as last loaded. */
sealed interface FolderState {
    data object Loading : FolderState

    data class Loaded(
        val entries: List<LibraryEntry>,
    ) : FolderState

    data class Failed(
        val message: String,
    ) : FolderState
}

/**
 * The library as shown: the roots, the folders listed so far and the sort order. Folders are
 * listed on [io], in [scope].
 */
class Library(
    private val store: DocumentStore,
    /** For folder permissions, which belong to the Storage Access Framework. */
    private val resolver: ContentResolver,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Where notes of PDFs opened from other apps are kept (shown when there are any). */
    val opened: FolderRef? = null,
    /** Called with a folder that couldn't be added (Android didn't let the app keep access). */
    private val onAddFailed: (Uri) -> Unit = {},
    /** Keeps access to a picked folder ([LibraryRoots.keepAccess]). */
    private val keepAccess: (Uri) -> Unit = { LibraryRoots.keepAccess(resolver, it) },
) {
    private val configured = LibraryRoots(preferences)
    val roots: StateFlow<List<LibraryRoot>> = configured.roots

    private val folderStates = MutableStateFlow<Map<FolderRef, FolderState>>(emptyMap())
    val folders: StateFlow<Map<FolderRef, FolderState>> = folderStates.asStateFlow()

    private val order =
        MutableStateFlow(SortOrder.entries.getOrNull(preferences.getInt(SORT_KEY, 0)) ?: SortOrder.MODIFIED)
    val sortOrder: StateFlow<SortOrder> = order.asStateFlow()

    /** Lists the top folder of every root again, and the opened PDFs. Returns the listings' jobs. */
    fun refreshRoots(): List<Job> =
        roots.value.filterIsInstance<LibraryRoot.Local>().map { refresh(it.folder) } +
            listOfNotNull(opened?.let(::refresh))

    /** Lists [folder] again; its previous contents stay shown meanwhile. Returns the listing's job. */
    fun refresh(folder: FolderRef): Job {
        folderStates.update { states -> if (folder in states) states else states + (folder to FolderState.Loading) }
        return scope.launch {
            val state =
                withContext(io) {
                    try {
                        FolderState.Loaded(FolderListing.list(store, folder, order.value))
                    } catch (e: StoreException) {
                        FolderState.Failed(e.message ?: e.toString())
                    }
                }
            folderStates.update { it + (folder to state) }
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        order.value = sortOrder
        preferences.edit().putInt(SORT_KEY, sortOrder.ordinal).apply()
        folderStates.value.keys.forEach { refresh(it) }
    }

    /** Adds a folder picked with ACTION_OPEN_DOCUMENT_TREE, keeping access to it, then calls [then]. */
    fun addRoot(
        treeUri: Uri,
        then: () -> Unit = {},
    ) {
        scope.launch {
            val root =
                withContext(io) {
                    runCatching { keepAccess(treeUri) }
                        .map {
                            val name = store.entry(SafDocumentStore.rootOf(treeUri))?.name
                            LibraryRoot.Local(treeUri, name ?: treeUri.lastPathSegment.orEmpty())
                        }
                }
            root
                .onSuccess {
                    configured.add(it)
                    refresh(it.folder)
                    then()
                }.onFailure {
                    Log.w(TAG, "Cannot add $treeUri", it)
                    onAddFailed(treeUri)
                }
        }
    }

    fun removeRoot(root: LibraryRoot) {
        configured.remove(root)
        if (root is LibraryRoot.Local) scope.launch(io) { LibraryRoots.releaseAccess(resolver, root.treeUri) }
    }

    fun moveRoot(
        root: LibraryRoot,
        by: Int,
    ) = configured.move(root, by)

    private companion object {
        const val TAG = "Library"
        const val SORT_KEY = "sort"
    }
}
