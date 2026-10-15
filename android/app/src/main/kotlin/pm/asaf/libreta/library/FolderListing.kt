// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreId

/** A folder in a [DocumentStore]. */
data class FolderRef(
    val id: StoreId,
    val name: String,
)

/** A subfolder or a Xournal++ document, as listed in a folder. */
sealed interface LibraryEntry {
    val name: String

    /** Milliseconds since the epoch, or 0 if unknown. */
    val modified: Long

    data class Folder(
        val folder: FolderRef,
        override val modified: Long,
        val parent: FolderRef,
    ) : LibraryEntry {
        override val name: String get() = folder.name
    }

    data class Document(
        val id: StoreId,
        override val name: String,
        val version: FileVersion,
        val parent: FolderRef,
    ) : LibraryEntry {
        override val modified: Long get() = version.modified
    }
}

enum class SortOrder { MODIFIED, NAME }

/** Lists library folders. */
object FolderListing {
    private val EXTENSIONS = listOf(".xopp", ".xoj")

    fun isDocumentName(name: String): Boolean = EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    /**
     * The subfolders (by name) and then the documents (by [order]; newest first for
     * [SortOrder.MODIFIED]) of [folder]. Hidden entries are left out.
     */
    fun list(
        store: DocumentStore,
        folder: FolderRef,
        order: SortOrder = SortOrder.MODIFIED,
    ): List<LibraryEntry> {
        val entries =
            store.list(folder.id).filterNot { it.name.startsWith(".") }.mapNotNull { entry ->
                when (entry) {
                    is StoreEntry.Folder -> {
                        LibraryEntry.Folder(FolderRef(entry.id, entry.name), entry.modified, folder)
                    }

                    is StoreEntry.File -> {
                        if (isDocumentName(
                                entry.name,
                            )
                        ) {
                            LibraryEntry.Document(entry.id, entry.name, entry.version, folder)
                        } else {
                            null
                        }
                    }
                }
            }
        val folders =
            entries.filterIsInstance<LibraryEntry.Folder>().sortedWith(
                compareBy(
                    String.CASE_INSENSITIVE_ORDER,
                ) {
                    it.name
                },
            )
        val documents = entries.filterIsInstance<LibraryEntry.Document>()
        val sorted =
            when (order) {
                SortOrder.MODIFIED -> documents.sortedByDescending { it.modified }
                SortOrder.NAME -> documents.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
        return folders + sorted
    }
}
