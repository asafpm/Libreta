// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.files.DocumentLocation

/** A note opened recently, and where it is (null: not in the library), to open it as before. */
data class RecentNote(
    val id: StoreId,
    val name: String,
    val location: DocumentLocation?,
)

/**
 * The notes opened last, newest first (at most [limit]), and the one open when the app was last
 * left, kept in [preferences]. [entries] are the ones still in [store], as cards, found on [io].
 */
class RecentNotes(
    private val preferences: SharedPreferences,
    private val store: DocumentStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val limit: Int = LIMIT,
) {
    private val state = MutableStateFlow(preferences.getString(KEY_RECENT, null)?.let(::decodeAll).orEmpty())

    /** The notes opened last, newest first. */
    val notes: StateFlow<List<RecentNote>> = state.asStateFlow()

    private val entryState = MutableStateFlow<List<LibraryEntry.Document>>(emptyList())

    /** [notes] still there, as the library lists them ([refresh]). */
    val entries: StateFlow<List<LibraryEntry.Document>> = entryState.asStateFlow()

    /** [note] was opened (and is open, until [closed]). */
    fun opened(note: RecentNote) {
        save((listOf(note) + state.value.filter { it.id != note.id }).take(limit))
        preferences.edit().putString(KEY_OPEN, encode(note).toString()).apply()
    }

    /** The open note was closed. */
    fun closed() = preferences.edit().remove(KEY_OPEN).apply()

    /**
     * The note open when the app was last left without closing it (Android stopped the app), to
     * open again. Taken: it is open again once it has loaded ([opened]); if it can't be (it
     * crashed the app), the app doesn't try again at each start.
     */
    fun takeOpen(): RecentNote? {
        val open = preferences.getString(KEY_OPEN, null)?.let(::decodeOne)
        closed()
        return open
    }

    /** Looks for [notes] in the store again: those gone are forgotten. */
    fun refresh() {
        val notes = state.value
        scope.launch {
            val found = withContext(io) { notes.map { note -> note to store.entry(note.id) as? StoreEntry.File } }
            val gone = found.filter { it.second == null }.map { it.first.id }.toSet()
            if (gone.isNotEmpty()) save(state.value.filter { it.id !in gone })
            entryState.value =
                found.mapNotNull { (note, entry) ->
                    entry?.let {
                        val parent = note.location?.folders?.last() ?: FolderRef(note.id, "")
                        LibraryEntry.Document(note.id, entry.name, entry.version, parent)
                    }
                }
        }
    }

    private fun save(notes: List<RecentNote>) {
        state.value = notes
        preferences.edit().putString(KEY_RECENT, JSONArray(notes.map(::encode)).toString()).apply()
    }

    private companion object {
        const val LIMIT = 6
        const val KEY_RECENT = "recent.notes"
        const val KEY_OPEN = "recent.open"

        fun encode(note: RecentNote): JSONObject =
            JSONObject()
                .put("id", note.id.value)
                .put("name", note.name)
                .put(
                    "folders",
                    note.location?.let { location ->
                        JSONArray(location.folders.map { JSONObject().put("id", it.id.value).put("name", it.name) })
                    },
                )

        // Unreadable (from another version): none.
        fun decodeAll(text: String): List<RecentNote> =
            runCatching {
                JSONArray(text).let { array -> (0 until array.length()).map { decode(array.getJSONObject(it)) } }
            }.getOrDefault(emptyList())

        fun decodeOne(text: String): RecentNote? = runCatching { decode(JSONObject(text)) }.getOrNull()

        fun decode(json: JSONObject): RecentNote {
            val name = json.getString("name")
            val folders =
                json.optJSONArray("folders")?.let { array ->
                    (0 until array.length()).map {
                        array.getJSONObject(it).let { f ->
                            FolderRef(StoreId(f.getString("id")), f.getString("name"))
                        }
                    }
                }
            return RecentNote(
                StoreId(json.getString("id")),
                name,
                folders
                    ?.takeIf {
                        it.isNotEmpty()
                    }?.let { DocumentLocation(it, name) },
            )
        }
    }
}
