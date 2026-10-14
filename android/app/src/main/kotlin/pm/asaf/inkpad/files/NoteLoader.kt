// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import okio.source
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.R
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.core.format.XoppFormatException
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.library.FolderListing
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.LibraryRoot
import java.io.FileNotFoundException
import java.io.IOException

/** Opening notes: reading them and finding their background files. Blocking. */
object NoteLoader {
    private const val TAG = "NoteLoader"

    /** Reads the note at [uri] named [name], with its files looked up at [location]. */
    fun load(
        context: Context,
        uri: Uri,
        name: String,
        location: DocumentLocation?,
    ): NoteState =
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            val read = stream.source().use { XoppReader.read(it) }
            val files = DocumentFiles.open(context, read, location)
            NoteState.Loaded(
                edits = NoteEdits(read.document),
                caches = RenderCaches.forDevice(context),
                files = files,
                folderNeeded = location == null && DocumentFiles.needsFolder(read),
            )
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read $uri", e)
            NoteState.Failed(context.getString(R.string.error_read_note, name))
        } catch (e: XoppFormatException) {
            Log.w(TAG, "Cannot read $uri", e)
            NoteState.Failed(context.getString(R.string.error_not_a_note, name))
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to $uri", e)
            NoteState.Failed(context.getString(R.string.error_read_note, name))
        }

    /** The display name of [uri], or its last path segment. */
    fun nameOf(
        resolver: ContentResolver,
        uri: Uri,
    ): String = FolderListing.nameOf(resolver, uri) ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()

    /**
     * Where the document [uri] (e.g. from "Open with") is in the library, if it is inside one
     * of [roots] of the same documents provider.
     */
    fun locate(
        resolver: ContentResolver,
        roots: List<LibraryRoot>,
        uri: Uri,
        name: String,
    ): DocumentLocation? {
        // Not a documents provider's uri (e.g. a download): it can't be in the library.
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return roots
            .filterIsInstance<LibraryRoot.Local>()
            .filter { it.treeUri.authority == uri.authority }
            .firstNotNullOfOrNull { root -> locateIn(resolver, root, documentId, name) }
    }

    private fun locateIn(
        resolver: ContentResolver,
        root: LibraryRoot.Local,
        documentId: String,
        name: String,
    ): DocumentLocation? {
        val inTree = DocumentsContract.buildDocumentUriUsingTree(root.treeUri, documentId)
        // Fails when the document is not below the root.
        val ids = runCatching { DocumentsContract.findDocumentPath(resolver, inTree)?.path }.getOrNull()
        if (ids == null || ids.size < 2) return null
        val folders =
            ids.dropLast(1).mapIndexed { index, id ->
                val folderName =
                    if (index == 0) {
                        root.name
                    } else {
                        nameOf(resolver, DocumentsContract.buildDocumentUriUsingTree(root.treeUri, id))
                    }
                FolderRef(root.treeUri, id, folderName)
            }
        return DocumentLocation(folders, name)
    }
}
