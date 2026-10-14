// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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
    private val resolver: ContentResolver,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val configured = LibraryRoots(preferences)
    val roots: StateFlow<List<LibraryRoot>> = configured.roots

    private val folderStates = MutableStateFlow<Map<FolderRef, FolderState>>(emptyMap())
    val folders: StateFlow<Map<FolderRef, FolderState>> = folderStates.asStateFlow()

    private val order =
        MutableStateFlow(SortOrder.entries.getOrNull(preferences.getInt(SORT_KEY, 0)) ?: SortOrder.MODIFIED)
    val sortOrder: StateFlow<SortOrder> = order.asStateFlow()

    /** Lists the top folder of every root again. */
    fun refreshRoots() = roots.value.filterIsInstance<LibraryRoot.Local>().forEach { refresh(it.folder) }

    /** Lists [folder] again; its previous contents stay shown meanwhile. */
    fun refresh(folder: FolderRef) {
        folderStates.update { states -> if (folder in states) states else states + (folder to FolderState.Loading) }
        scope.launch {
            val state =
                withContext(io) {
                    try {
                        FolderState.Loaded(FolderListing.list(resolver, folder, order.value))
                    } catch (e: IOException) {
                        FolderState.Failed(e.message ?: e.toString())
                    } catch (e: SecurityException) {
                        FolderState.Failed(e.message ?: e.toString()) // access was withdrawn
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

    /** Adds a folder picked with ACTION_OPEN_DOCUMENT_TREE, keeping access to it. */
    fun addRoot(treeUri: Uri) {
        scope.launch {
            val root =
                withContext(io) {
                    LibraryRoots.keepAccess(resolver, treeUri)
                    val name = FolderListing.nameOf(resolver, FolderRef.rootOf(treeUri, "").uri)
                    LibraryRoot.Local(treeUri, name ?: treeUri.lastPathSegment.orEmpty())
                }
            configured.add(root)
            refresh(root.folder)
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
        const val SORT_KEY = "sort"
    }
}
