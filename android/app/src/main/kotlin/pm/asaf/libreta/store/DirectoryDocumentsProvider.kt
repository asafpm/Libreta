// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.store

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A documents provider over a directory, like the system's external storage provider:
 * document ids are paths relative to the directory, prefixed with `root`. Nothing outside the
 * directory can be reached.
 */
abstract class DirectoryDocumentsProvider : DocumentsProvider() {
    /** The directory served. */
    protected abstract fun directory(): File

    override fun onCreate(): Boolean = true

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
        checkName(displayName)
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

    // Like the system provider: a taken name fails.
    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        checkName(displayName)
        val file = fileOf(documentId)
        return movedTo(file, File(file.parentFile, displayName), "${documentId.substringBeforeLast('/')}/$displayName")
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val file = fileOf(sourceDocumentId)
        if (file.parentFile != fileOf(sourceParentDocumentId)) throw FileNotFoundException(sourceDocumentId)
        val target = fileOf(targetParentDocumentId)
        if (!target.isDirectory || target.path.startsWith(file.path + File.separator) || target == file) {
            throw FileNotFoundException(targetParentDocumentId)
        }
        return movedTo(file, File(target, file.name), "$targetParentDocumentId/${file.name}")
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path {
        val parent = parentDocumentId ?: ROOT
        if (childDocumentId != parent && !isChildDocument(parent, childDocumentId)) {
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
                Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_MOVE,
            )
    }

    /** Moves [file] to [to] (not over anything), which is the document [id]. */
    private fun movedTo(
        file: File,
        to: File,
        id: String,
    ): String {
        if (!file.exists() || to.exists() || !file.renameTo(to)) throw FileNotFoundException(to.name)
        return id
    }

    private fun checkName(name: String) {
        if (name.isEmpty() || name.contains('/') || name in setOf(".", "..")) throw FileNotFoundException(name)
    }

    private fun fileOf(documentId: String): File {
        if (documentId != ROOT && !documentId.startsWith("$ROOT/")) throw FileNotFoundException(documentId)
        val directory = directory().canonicalFile
        val file = File(directory, documentId.removePrefix(ROOT).removePrefix("/")).canonicalFile
        // No way out with "..".
        if (file != directory && !file.path.startsWith(directory.path + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        return file
    }

    companion object {
        /** The document id of the directory itself. */
        const val ROOT = "root"
        private val DEFAULT_COLUMNS =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_SIZE,
                Document.COLUMN_FLAGS,
            )
    }
}
