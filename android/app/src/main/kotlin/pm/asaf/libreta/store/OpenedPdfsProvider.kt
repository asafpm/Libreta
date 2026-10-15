// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.store

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import pm.asaf.libreta.core.store.StoreId
import java.io.File

/**
 * The notes of PDFs opened from other apps, kept in the app's private storage (with their
 * copy of the PDF) rather than in the user's folders. It has no roots, so it is not offered
 * in the system file picker: only Libreta uses it.
 */
class OpenedPdfsProvider : DirectoryDocumentsProvider() {
    override fun directory(): File = directoryOf(checkNotNull(context))

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID))

    companion object {
        fun directoryOf(context: Context): File = File(context.filesDir, "opened-pdfs").apply { mkdirs() }

        fun authority(context: Context): String = "${context.packageName}.opened"

        /** The folder holding the notes. */
        fun folder(context: Context): StoreId {
            val tree = DocumentsContract.buildTreeDocumentUri(authority(context), ROOT)
            return StoreId(DocumentsContract.buildDocumentUriUsingTree(tree, ROOT).toString())
        }
    }
}
