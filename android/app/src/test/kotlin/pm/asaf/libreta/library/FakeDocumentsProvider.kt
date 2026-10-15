// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.Manifest
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import org.robolectric.Robolectric
import pm.asaf.libreta.core.store.StoreId
import java.io.File
import java.io.FileNotFoundException

/**
 * A documents provider over a directory, like the system's external storage provider:
 * document ids are paths relative to the directory, prefixed with `root`.
 */
class FakeDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID)).apply {
            newRow().add(Root.COLUMN_ROOT_ID, ROOT).add(Root.COLUMN_DOCUMENT_ID, ROOT)
        }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        // Like real providers: missing documents fail.
        if (!fileOf(documentId).exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: DEFAULT_COLUMNS).apply { addRow(this, documentId) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val folder = fileOf(parentDocumentId)
        if (!folder.isDirectory) throw FileNotFoundException(parentDocumentId)
        return MatrixCursor(projection ?: DEFAULT_COLUMNS).apply {
            folder.listFiles()?.forEach { addRow(this, "$parentDocumentId/${it.name}") }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileOf(documentId)
        if (!file.exists() && !mode.contains('w')) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = fileOf(parentDocumentId)
        // Like the system provider: "name (1).ext" when the name is taken.
        val base = displayName.substringBeforeLast('.')
        val extension = displayName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val file =
            generateSequence(0) { it + 1 }
                .map { File(parent, if (it == 0) displayName else "$base ($it)$extension") }
                .first { !it.exists() }
        if (mimeType == Document.MIME_TYPE_DIR) file.mkdirs() else file.createNewFile()
        return "$parentDocumentId/${file.name}"
    }

    override fun deleteDocument(documentId: String) {
        fileOf(documentId).deleteRecursively()
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path {
        val parent = parentDocumentId ?: ROOT
        if (childDocumentId != parent &&
            !isChildDocument(parent, childDocumentId)
        ) {
            throw FileNotFoundException(childDocumentId)
        }
        val below = childDocumentId.removePrefix(parent).split('/').filter { it.isNotEmpty() }
        val ids = below.runningFold(parent) { id, name -> "$id/$name" }
        return DocumentsContract.Path(if (parentDocumentId == null) ROOT else null, ids)
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean = documentId.startsWith("$parentDocumentId/")

    private fun addRow(
        cursor: MatrixCursor,
        documentId: String,
    ) {
        val file = fileOf(documentId)
        cursor
            .newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(
                Document.COLUMN_MIME_TYPE,
                if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream",
            ).add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_SIZE, file.length())
            .add(
                Document.COLUMN_FLAGS,
                Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE,
            )
    }

    private fun fileOf(documentId: String): File {
        require(documentId == ROOT || documentId.startsWith("$ROOT/")) { documentId }
        return File(directory, documentId.removePrefix(ROOT).removePrefix("/"))
    }

    companion object {
        const val AUTHORITY = "pm.asaf.libreta.test.documents"
        private const val ROOT = "root"
        private val DEFAULT_COLUMNS =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_SIZE,
                Document.COLUMN_FLAGS,
            )

        private lateinit var directory: File

        /** Serves [root] until the next call. */
        fun install(root: File) {
            directory = root
            val info =
                ProviderInfo().apply {
                    authority = AUTHORITY
                    exported = true
                    grantUriPermissions = true
                    readPermission = Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = Manifest.permission.MANAGE_DOCUMENTS
                }
            Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(info)
        }

        /** The folder at [path] below the root ("" for the root itself), in the root's tree. */
        fun folderId(path: String = ""): StoreId = documentId(path)

        /** The file at [path] below the root, in the root's tree. */
        fun fileId(path: String): StoreId = documentId(path)

        private fun documentId(path: String): StoreId =
            StoreId(
                DocumentsContract
                    .buildDocumentUriUsingTree(
                        treeUri(),
                        if (path.isEmpty()) ROOT else "$ROOT/$path",
                    ).toString(),
            )

        /** The tree of [path] below the root ("" for the root itself). */
        fun treeUri(path: String = ""): Uri =
            DocumentsContract.buildTreeDocumentUri(AUTHORITY, if (path.isEmpty()) ROOT else "$ROOT/$path")
    }
}
