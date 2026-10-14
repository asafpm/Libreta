// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.inkpad.store.SafDocumentStore

/** A folder shown on the home screen, with everything below it. */
sealed interface LibraryRoot {
    val id: String
    val name: String

    /** A folder picked with the system file picker (on the device or a cloud provider). */
    data class Local(
        val treeUri: Uri,
        override val name: String,
    ) : LibraryRoot {
        override val id: String get() = treeUri.toString()

        val folder: FolderRef get() = FolderRef(SafDocumentStore.rootOf(treeUri), name)
    }
}

/** The configured [LibraryRoot]s, in the user's order, kept in [preferences]. */
class LibraryRoots(
    private val preferences: SharedPreferences,
) {
    private val state = MutableStateFlow(load())
    val roots: StateFlow<List<LibraryRoot>> = state.asStateFlow()

    /** Adds [root] at the end, or leaves the list alone if it is there already. */
    fun add(root: LibraryRoot) {
        if (state.value.none { it.id == root.id }) save(state.value + root)
    }

    fun remove(root: LibraryRoot) = save(state.value.filter { it.id != root.id })

    /** Moves [root] [by] places (negative: up). */
    fun move(
        root: LibraryRoot,
        by: Int,
    ) {
        val list = state.value.toMutableList()
        val from = list.indexOfFirst { it.id == root.id }
        if (from < 0) return
        list.add((from + by).coerceIn(0, list.size - 1), list.removeAt(from))
        save(list)
    }

    private fun save(roots: List<LibraryRoot>) {
        preferences.edit().putString(KEY, roots.joinToString(SEPARATOR) { encode(it) }).apply()
        state.value = roots
    }

    private fun load(): List<LibraryRoot> =
        preferences
            .getString(KEY, null)
            .orEmpty()
            .split(SEPARATOR)
            .mapNotNull { decode(it) }

    private fun encode(root: LibraryRoot): String =
        when (root) {
            is LibraryRoot.Local -> listOf(LOCAL, Uri.encode(root.name), root.treeUri.toString()).joinToString(FIELD)
        }

    /** Null for an empty line, or one from a newer version. */
    private fun decode(line: String): LibraryRoot? {
        val fields = line.split(FIELD)
        val local = fields.size == LOCAL_FIELDS && fields[0] == LOCAL
        return if (local) LibraryRoot.Local(Uri.parse(fields[2]), Uri.decode(fields[1])) else null
    }

    companion object {
        private const val KEY = "roots"
        private const val SEPARATOR = "\n"
        private const val FIELD = "\t"
        private const val LOCAL = "local"
        private const val LOCAL_FIELDS = 3

        /** Keeps access to a folder picked with ACTION_OPEN_DOCUMENT_TREE across restarts. */
        fun keepAccess(
            resolver: ContentResolver,
            treeUri: Uri,
        ) = resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

        /** Gives up the access kept by [keepAccess]. */
        fun releaseAccess(
            resolver: ContentResolver,
            treeUri: Uri,
        ) {
            val granted = resolver.persistedUriPermissions.any { it.uri == treeUri }
            if (granted) {
                resolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }
}
