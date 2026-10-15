// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreId

/** A folder in a [DocumentStore]. */
data class FolderRef(
    val id: StoreId,
    val name: String,
)

/** A subfolder, a Xournal++ document or a PDF, as listed in a folder. */
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

    /** A file shown with a preview of its first page. */
    sealed interface File : LibraryEntry {
        val id: StoreId
        val version: FileVersion
        val parent: FolderRef
        override val modified: Long get() = version.modified
    }

    data class Document(
        override val id: StoreId,
        override val name: String,
        override val version: FileVersion,
        override val parent: FolderRef,
    ) : File

    /** A PDF, to annotate in a note of its own. */
    data class Pdf(
        override val id: StoreId,
        override val name: String,
        override val version: FileVersion,
        override val parent: FolderRef,
    ) : File
}

enum class SortOrder { MODIFIED, NAME }

/** Lists library folders. */
object FolderListing {
    private val EXTENSIONS = listOf(".xopp", ".xoj")

    fun isDocumentName(name: String): Boolean = EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    fun isPdfName(name: String): Boolean = name.endsWith(PDF, ignoreCase = true)

    private const val PDF = ".pdf"

    /**
     * The subfolders (by name) and then the documents and PDFs (by [order]; newest first for
     * [SortOrder.MODIFIED]) of [folder]. Hidden entries are left out, and so are PDFs that
     * have a note (Xournal++'s names for it), which stands for them, and the PDFs notes carry
     * (`note.xopp.bg.pdf`).
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
                        when {
                            isDocumentName(entry.name) -> {
                                LibraryEntry.Document(entry.id, entry.name, entry.version, folder)
                            }

                            isPdfName(entry.name) -> {
                                LibraryEntry.Pdf(entry.id, entry.name, entry.version, folder)
                            }

                            else -> {
                                null
                            }
                        }
                    }
                }
            }
        val names = entries.map { it.name }.toSet()
        val notes = entries.filterIsInstance<LibraryEntry.Document>().map { it.name + "." }
        val folders =
            entries.filterIsInstance<LibraryEntry.Folder>().sortedWith(
                compareBy(
                    String.CASE_INSENSITIVE_ORDER,
                ) {
                    it.name
                },
            )
        val documents =
            entries.filterIsInstance<LibraryEntry.File>().filterNot { entry ->
                entry is LibraryEntry.Pdf &&
                    (NewNote.notesForPdf(entry.name).any { it in names } || notes.any { entry.name.startsWith(it) })
            }
        val sorted =
            when (order) {
                SortOrder.MODIFIED -> documents.sortedByDescending { it.modified }
                SortOrder.NAME -> documents.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
        return folders + sorted
    }
}
