// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreException

/** A library entry found by name, and the folders from its root down to the one it is in. */
data class Found(
    val entry: LibraryEntry,
    val path: List<FolderRef>,
)

/** What a search for [query] found so far; [done] once every folder was looked in. */
data class SearchResults(
    val query: String,
    val found: List<Found>,
    val done: Boolean,
)

/**
 * Looking for notes, PDFs and folders by name (any case) all through the library ([roots]),
 * listed as the library lists them ([order]) on [io]; results come as folders are looked in.
 */
class LibrarySearch(
    private val store: DocumentStore,
    private val roots: () -> List<LibraryRoot>,
    private val order: () -> SortOrder,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val state = MutableStateFlow(SearchResults("", emptyList(), done = true))
    val results: StateFlow<SearchResults> = state.asStateFlow()
    private var job: Job? = null

    /** Looks for [text], in place of what was looked for. */
    fun search(text: String) {
        job?.cancel()
        val query = text.trim()
        if (query.isEmpty()) {
            state.value = SearchResults("", emptyList(), done = true)
            return
        }
        job =
            scope.launch {
                // Typing: looked for once it pauses.
                delay(PAUSE_MS)
                val found = ArrayList<Found>()
                state.value = SearchResults(query, emptyList(), done = false)
                matches(query).collect {
                    found += it
                    state.value = SearchResults(query, found.toList(), done = false)
                }
                state.value = SearchResults(query, found.toList(), done = true)
            }
    }

    private fun matches(query: String): Flow<List<Found>> =
        flow {
            var count = 0
            for (root in roots().filterIsInstance<LibraryRoot.Local>()) {
                count += lookIn(root.folder, listOf(root.folder), query, MAX_RESULTS - count)
                if (count >= MAX_RESULTS) break
            }
        }.flowOn(io)

    /** Emits what [folder] (at [path]) and its subfolders hold named like [query], up to [left]; returns how many. */
    private suspend fun FlowCollector<List<Found>>.lookIn(
        folder: FolderRef,
        path: List<FolderRef>,
        query: String,
        left: Int,
    ): Int {
        val entries =
            try {
                FolderListing.list(store, folder, order())
            } catch (e: StoreException) {
                // A folder that can't be listed: the others still can.
                Log.w(TAG, "Cannot look in ${folder.name}", e)
                emptyList()
            }
        val matches = entries.filter { it.name.contains(query, ignoreCase = true) }.take(left).map { Found(it, path) }
        if (matches.isNotEmpty()) emit(matches)
        var count = matches.size
        if (path.size < MAX_DEPTH) {
            for (sub in entries.filterIsInstance<LibraryEntry.Folder>()) {
                if (count >= left) break
                count += lookIn(sub.folder, path + sub.folder, query, left - count)
            }
        }
        return count
    }

    private companion object {
        const val TAG = "LibrarySearch"
        const val PAUSE_MS = 300L
        const val MAX_RESULTS = 200
        const val MAX_DEPTH = 12
    }
}
