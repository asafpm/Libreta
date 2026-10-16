// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.store

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import okio.ByteString
import okio.Source
import okio.source
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreException
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.core.store.WriteResult
import java.io.IOException

/**
 * Notes in folders granted through Android's Storage Access Framework. Ids are content URIs:
 * folders are documents inside a granted tree (so their children can be listed); files can
 * also be single documents another app shared ("Open with").
 */
class SafDocumentStore(
    private val resolver: ContentResolver,
) : DocumentStore {
    override fun list(folder: StoreId): List<StoreEntry> =
        guard("list") {
            val uri = folder.uri
            val tree = treeOf(uri)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(uri))
            val cursor = resolver.query(children, COLUMNS, null, null) ?: throw StoreException("Cannot list $uri")
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        val id =
                            it.getString(ID)?.let { child ->
                                StoreId(DocumentsContract.buildDocumentUriUsingTree(tree, child).toString())
                            }
                        id?.let { entry -> entryAt(it, entry) }?.let(::add)
                    }
                }
            }
        }

    override fun entry(id: StoreId): StoreEntry? =
        runCatching {
            resolver.query(id.uri, COLUMNS, null, null)?.use { if (it.moveToFirst()) entryAt(it, id) else null }
        }.getOrNull()

    override fun read(file: StoreId): Source =
        guard(
            "read",
        ) { (resolver.openInputStream(file.uri) ?: throw StoreException("Cannot read ${file.uri}")).source() }

    override fun write(
        file: StoreId,
        bytes: ByteString,
        expected: FileVersion?,
    ): WriteResult =
        guard("write") {
            val current = (entry(file) as? StoreEntry.File)?.version
            if (expected != null && current != expected) {
                WriteResult.Conflict(current)
            } else {
                val stream =
                    resolver.openOutputStream(file.uri, "wt") ?: throw StoreException("Cannot write ${file.uri}")
                stream.use { bytes.write(it) }
                WriteResult.Written((entry(file) as? StoreEntry.File)?.version)
            }
        }

    override fun create(
        folder: StoreId,
        name: String,
    ): StoreEntry.File =
        guard("create") {
            val uri =
                DocumentsContract.createDocument(resolver, folder.uri, MIME_TYPE, name)
                    ?: throw StoreException("Cannot create $name in ${folder.uri}")
            val id = StoreId(uri.toString())
            entry(id) as? StoreEntry.File ?: StoreEntry.File(id, name, FileVersion(0, 0))
        }

    override fun createFolder(
        folder: StoreId,
        name: String,
    ): StoreEntry.Folder =
        guard("create folder") {
            val uri =
                DocumentsContract.createDocument(resolver, folder.uri, Document.MIME_TYPE_DIR, name)
                    ?: throw StoreException("Cannot create folder $name in ${folder.uri}")
            val id = StoreId(uri.toString())
            entry(id) as? StoreEntry.Folder ?: StoreEntry.Folder(id, name, 0)
        }

    override fun delete(file: StoreId) {
        guard("delete") { DocumentsContract.deleteDocument(resolver, file.uri) }
    }

    override fun rename(
        id: StoreId,
        name: String,
    ): StoreEntry =
        guard("rename") {
            // The same document when the provider keeps its id.
            val renamed = StoreId((DocumentsContract.renameDocument(resolver, id.uri, name) ?: id.uri).toString())
            entry(renamed) ?: throw StoreException("Cannot find $name after renaming it")
        }

    override fun move(
        id: StoreId,
        from: StoreId,
        to: StoreId,
    ): StoreEntry? =
        id
            .takeIf { it.uri.authority == to.uri.authority }
            // Providers that can't (no FLAG_SUPPORTS_MOVE, other trees, a taken name) fail in their own ways.
            ?.let { runCatching { DocumentsContract.moveDocument(resolver, it.uri, from.uri, to.uri) }.getOrNull() }
            ?.let { moved ->
                // The provider names it in the tree it came from; it is in the target's now.
                val inTarget =
                    DocumentsContract.buildDocumentUriUsingTree(treeOf(to.uri), DocumentsContract.getDocumentId(moved))
                entry(StoreId(inTarget.toString()))
            }

    override fun pathTo(
        root: StoreId,
        file: StoreId,
    ): List<StoreEntry.Folder>? =
        runCatching {
            val rootUri = root.uri
            if (rootUri.authority != file.uri.authority) return null
            val tree = treeOf(rootUri)
            val inTree = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(file.uri))
            // Fails when the document is not in the tree; the path starts at the tree's top.
            val ids = DocumentsContract.findDocumentPath(resolver, inTree)?.path ?: return null
            val start = ids.indexOf(DocumentsContract.getDocumentId(rootUri))
            if (start < 0) return null
            ids.subList(start, ids.size - 1).map { id ->
                val folder = StoreId(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString())
                StoreEntry.Folder(folder, entry(folder)?.name.orEmpty(), 0)
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun entryAt(
        cursor: Cursor,
        id: StoreId,
    ): StoreEntry? {
        val name = cursor.getString(NAME) ?: return null
        val modified = cursor.longOrZero(MODIFIED)
        return if (cursor.getString(TYPE) == Document.MIME_TYPE_DIR) {
            StoreEntry.Folder(id, name, modified)
        } else {
            StoreEntry.File(id, name, FileVersion(modified, if (cursor.isNull(SIZE)) -1 else cursor.getLong(SIZE)))
        }
    }

    /** Runs [action], reporting the provider's failures (often not IOExceptions) as [StoreException]. */
    private inline fun <T> guard(
        what: String,
        action: () -> T,
    ): T =
        try {
            action()
        } catch (e: StoreException) {
            throw e
        } catch (e: IOException) {
            throw StoreException("Cannot $what: ${e.message}", e)
        } catch (e: SecurityException) {
            throw StoreException("Not allowed to $what: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw StoreException("Cannot $what: ${e.message}", e)
        } catch (e: IllegalStateException) {
            // Providers' FileNotFoundExceptions, through ContentResolver.call
            throw StoreException("Cannot $what: ${e.message}", e)
        } catch (e: UnsupportedOperationException) {
            throw StoreException("Cannot $what here", e)
        }

    companion object {
        private const val MIME_TYPE = "application/octet-stream"
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
        private const val TYPE = 2
        private const val MODIFIED = 3
        private const val SIZE = 4

        /** The top folder of a tree picked with ACTION_OPEN_DOCUMENT_TREE. */
        fun rootOf(treeUri: Uri): StoreId =
            StoreId(
                DocumentsContract
                    .buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri),
                    ).toString(),
            )

        private fun treeOf(document: Uri): Uri =
            DocumentsContract.buildTreeDocumentUri(document.authority, DocumentsContract.getTreeDocumentId(document))

        private fun Cursor.longOrZero(column: Int): Long = if (isNull(column)) 0L else getLong(column)
    }
}

/** The content URI of a store id from [SafDocumentStore]. */
val StoreId.uri: Uri get() = Uri.parse(value)
