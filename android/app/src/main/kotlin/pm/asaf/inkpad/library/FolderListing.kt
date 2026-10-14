// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/** A folder inside a folder tree the app has access to. */
data class FolderRef(
    val treeUri: Uri,
    val documentId: String,
    val name: String,
) {
    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    companion object {
        /** The top folder of [treeUri]. */
        fun rootOf(
            treeUri: Uri,
            name: String,
        ) = FolderRef(treeUri, DocumentsContract.getTreeDocumentId(treeUri), name)
    }
}

/** A subfolder or a Xournal++ document, as listed in a folder. */
sealed interface LibraryEntry {
    val name: String

    /** Milliseconds since the epoch, or 0 if unknown. */
    val modified: Long

    data class Folder(
        val folder: FolderRef,
        override val modified: Long,
    ) : LibraryEntry {
        override val name: String get() = folder.name
    }

    data class Document(
        val uri: Uri,
        override val name: String,
        override val modified: Long,
        val size: Long,
        val parent: FolderRef,
    ) : LibraryEntry
}

enum class SortOrder { MODIFIED, NAME }

/** Lists folders through the Storage Access Framework. */
object FolderListing {
    private val EXTENSIONS = listOf(".xopp", ".xoj")

    private val COLUMNS =
        arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
    private const val ID = 0
    private const val NAME = 1
    private const val MIME_TYPE = 2
    private const val MODIFIED = 3
    private const val SIZE = 4

    fun isDocumentName(name: String): Boolean = EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    /**
     * The subfolders (by name) and then the documents (by [order]; newest first for
     * [SortOrder.MODIFIED]) of [folder]. Hidden entries are left out.
     */
    fun list(
        resolver: ContentResolver,
        folder: FolderRef,
        order: SortOrder = SortOrder.MODIFIED,
    ): List<LibraryEntry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.treeUri, folder.documentId)
        val entries = ArrayList<LibraryEntry>()
        val cursor = resolver.query(children, COLUMNS, null, null) ?: throw IOException("Cannot list ${folder.name}")
        cursor.use { while (it.moveToNext()) entryAt(it, folder)?.let(entries::add) }
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

    /** The display name of the document at [uri], if it can be queried. */
    fun nameOf(
        resolver: ContentResolver,
        uri: Uri,
    ): String? =
        runCatching {
            resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun entryAt(
        cursor: Cursor,
        parent: FolderRef,
    ): LibraryEntry? {
        val id = cursor.getString(ID)
        val name = cursor.getString(NAME)
        val modified = cursor.longOrZero(MODIFIED)
        return when {
            id == null || name == null || name.startsWith(".") -> {
                null
            }

            cursor.getString(MIME_TYPE) == Document.MIME_TYPE_DIR -> {
                LibraryEntry.Folder(FolderRef(parent.treeUri, id, name), modified)
            }

            isDocumentName(name) -> {
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent.treeUri, id)
                LibraryEntry.Document(uri, name, modified, cursor.longOrZero(SIZE), parent)
            }

            else -> {
                null
            }
        }
    }

    private fun Cursor.longOrZero(column: Int): Long = if (isNull(column)) 0L else getLong(column)
}
