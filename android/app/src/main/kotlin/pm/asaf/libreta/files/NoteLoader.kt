// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.Context
import android.net.Uri
import android.util.Log
import okio.buffer
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.R
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.core.format.XoppFormatException
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.LibraryRoot
import java.io.IOException

/** Opening notes: reading them and finding their background files. Blocking. */
object NoteLoader {
    private const val TAG = "NoteLoader"

    /** Reads the note [id] named [name] from [store], with its files looked up at [location]. */
    fun load(
        context: Context,
        store: DocumentStore,
        id: StoreId,
        name: String,
        location: DocumentLocation?,
    ): NoteState =
        try {
            // Before reading: a change after this is someone else's.
            val version = (store.entry(id) as? StoreEntry.File)?.version
            val bytes = store.read(id).buffer().use { it.readByteString() }
            val read = XoppReader.read(bytes)
            val files = DocumentFiles.open(context, store, read, location)
            NoteState.Loaded(
                file = NoteFile(id, name, location, read.container, version, bytes.sha256()),
                session = NoteSession(read.document),
                caches = RenderCaches.forDevice(context),
                files = files,
                folderNeeded = location == null && DocumentFiles.needsFolder(read),
            )
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read $id", e)
            NoteState.Failed(context.getString(R.string.error_read_note, name))
        } catch (e: XoppFormatException) {
            Log.w(TAG, "Cannot read $id", e)
            NoteState.Failed(context.getString(R.string.error_not_a_note, name))
        }

    /** The name of [id], or else the last part of its uri. */
    fun nameOf(
        store: DocumentStore,
        id: StoreId,
    ): String =
        store.entry(id)?.name ?: Uri
            .parse(id.value)
            .lastPathSegment
            ?.substringAfterLast('/')
            .orEmpty()

    /** Where the document [id] (e.g. from "Open with") is in the library, if it is below one of [roots]. */
    fun locate(
        store: DocumentStore,
        roots: List<LibraryRoot>,
        id: StoreId,
        name: String,
    ): DocumentLocation? =
        roots.filterIsInstance<LibraryRoot.Local>().firstNotNullOfOrNull { root ->
            store.pathTo(root.folder.id, id)?.let { folders ->
                // The root keeps the name it has in the library.
                val names = listOf(root.name) + folders.drop(1).map { it.name }
                val refs = folders.zip(names) { folder, name -> FolderRef(folder.id, name) }
                DocumentLocation(refs, name)
            }
        }
}
