// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.Manifest
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import org.robolectric.Robolectric
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.store.DirectoryDocumentsProvider
import java.io.File

/**
 * A documents provider over a directory, like the system's external storage provider, offered
 * as one root: document ids are paths relative to the directory, prefixed with `root`.
 */
class FakeDocumentsProvider : DirectoryDocumentsProvider() {
    override fun directory(): File = directory

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID)).apply {
            newRow().add(Root.COLUMN_ROOT_ID, ROOT).add(Root.COLUMN_DOCUMENT_ID, ROOT)
        }

    companion object {
        const val AUTHORITY = "pm.asaf.libreta.test.documents"

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
