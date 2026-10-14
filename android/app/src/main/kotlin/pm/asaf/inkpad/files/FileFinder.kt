// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import pm.asaf.inkpad.library.FolderRef

/**
 * Finds files referenced by a document at [location] through the Storage Access Framework,
 * the way Xournal++ resolves them on disk (docs/xopp-format.md §4). Folder listings are
 * cached, so use one finder per document opening.
 */
internal class FileFinder(
    private val resolver: ContentResolver,
    private val location: DocumentLocation?,
) {
    private data class Child(
        val documentId: String,
        val directory: Boolean,
    )

    private val listings = HashMap<FolderRef, Map<String, Child>>()

    /** `<document name>.<filename>` next to the document: gzip `attach` files. */
    fun sidecar(filename: String): Uri? {
        val location = location ?: return null
        return fileIn(location.folders.last(), "${location.name}.$filename")
    }

    /**
     * An `absolute` path. Relative paths are followed from the document's folder, also up
     * (`..`) as far as the library root; anything else (desktop paths, paths leaving the root)
     * falls back to a file of the same name next to the document.
     */
    fun path(path: String): Uri? {
        val location = location ?: return null
        val parts = path.split('/', '\\').filter { it.isNotEmpty() && it != "." }
        val relative = !path.startsWith("/") && !path.startsWith("\\") && !WINDOWS_DRIVE.matches(path)
        val followed = if (relative && parts.isNotEmpty()) follow(location.folders, parts) else null
        return followed ?: parts.lastOrNull()?.let { fileIn(location.folders.last(), it) }
    }

    /** The file at the end of [parts], following the folders before it from [start]. */
    private fun follow(
        start: List<FolderRef>,
        parts: List<String>,
    ): Uri? {
        val folders =
            parts.dropLast(1).fold<String, List<FolderRef>?>(start) { folders, part ->
                folders?.let {
                    step(it, part)
                }
            }
        return folders?.let { fileIn(it.last(), parts.last()) }
    }

    /** One folder up (`..`) or down from the last of [folders]; null if there is no such folder. */
    private fun step(
        folders: List<FolderRef>,
        part: String,
    ): List<FolderRef>? =
        if (part == "..") {
            folders.dropLast(1).ifEmpty { null }
        } else {
            children(folders.last())[part]
                ?.takeIf { it.directory }
                ?.let { folders + FolderRef(folders.last().treeUri, it.documentId, part) }
        }

    private fun fileIn(
        folder: FolderRef,
        name: String,
    ): Uri? =
        children(folder)[name]
            ?.takeIf { !it.directory }
            ?.let { DocumentsContract.buildDocumentUriUsingTree(folder.treeUri, it.documentId) }

    private fun children(folder: FolderRef): Map<String, Child> =
        listings.getOrPut(folder) {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.treeUri, folder.documentId)
            val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
            val children = HashMap<String, Child>()
            runCatching {
                resolver.query(uri, columns, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val name = cursor.getString(1)
                        if (id != null &&
                            name != null
                        ) {
                            children[name] = Child(id, cursor.getString(2) == Document.MIME_TYPE_DIR)
                        }
                    }
                }
            }
            children
        }

    private companion object {
        val WINDOWS_DRIVE = Regex("^[A-Za-z]:[\\\\/].*")
    }
}
