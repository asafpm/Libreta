// SPDX-License-Identifier: GPL-2.0-or-later
// The preview follows Xournal++ v1.3.8 src/core/control/jobs/SaveJob.cpp (updatePreview),
// © Xournal++ contributors.

package pm.asaf.inkpad.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import okio.ByteString
import okio.ByteString.Companion.toByteString
import pm.asaf.inkpad.core.format.XoppContainer
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.store.DocumentStore
import pm.asaf.inkpad.core.store.FileVersion
import pm.asaf.inkpad.core.store.StoreEntry
import pm.asaf.inkpad.core.store.StoreException
import pm.asaf.inkpad.core.store.StoreId
import pm.asaf.inkpad.core.store.WriteResult
import pm.asaf.inkpad.library.NewNoteCreator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import pm.asaf.inkpad.core.model.Document as Note

/** The file of an open note; after a conflict it is the copy the note was saved to. */
class NoteFile(
    @Volatile var id: StoreId,
    @Volatile var name: String,
    /** Where it is in the library, or null (then a conflict copy can't be made next to it). */
    val location: DocumentLocation?,
    val container: XoppContainer,
    /** Its version when read or last saved. */
    @Volatile var version: FileVersion?,
) {
    /** Whether the file as it was has been backed up, once per session. */
    @Volatile var backedUp: Boolean = false

    /** Xournal++ 1.0 zip packages are read only: their attachment paths don't fit the gzip format. */
    val writable: Boolean get() = container != XoppContainer.ZIP
}

/** What saving did. */
sealed interface SaveResult {
    /** Nothing changed since the last save. */
    data object Unchanged : SaveResult

    data object Saved : SaveResult

    /** The file changed elsewhere since it was opened, so the note went into a new file. */
    data class SavedAsCopy(
        val name: String,
    ) : SaveResult

    data object ReadOnly : SaveResult

    data class Failed(
        val error: Exception,
    ) : SaveResult
}

/**
 * Writes edited notes back to their files in one go, keeping everything Inkpad doesn't edit,
 * with a fresh preview like Xournal++. Before a file is first overwritten, it is copied to
 * [backups]. A file changed elsewhere since it was opened is not overwritten: the note is
 * saved next to it as a conflict copy. Blocking.
 */
class NoteSaver(
    private val context: Context,
    private val store: DocumentStore,
    private val backups: Backups,
) {
    fun save(
        file: NoteFile,
        session: NoteSession,
        files: DocumentFiles,
    ): SaveResult {
        val document = session.document
        return when {
            !session.dirty -> SaveResult.Unchanged
            !file.writable -> SaveResult.ReadOnly
            else -> write(file, session, document, files)
        }
    }

    private fun write(
        file: NoteFile,
        session: NoteSession,
        document: Note,
        files: DocumentFiles,
    ): SaveResult =
        try {
            val preview = document.pages.firstOrNull()?.let { preview(it, files) }
            val bytes = NoteEncoder.encode(document.copy(creator = NewNoteCreator.creator(context), preview = preview))
            if (!file.backedUp) backUp(file)
            val result =
                when (val written = store.write(file.id, bytes, expected = file.version)) {
                    is WriteResult.Written -> {
                        file.version = written.version
                        SaveResult.Saved
                    }

                    is WriteResult.Conflict -> {
                        val copy = writeCopy(file, bytes)
                        // Later saves go to the copy.
                        file.id = copy.id
                        file.name = copy.name
                        file.version = (store.entry(copy.id) as? StoreEntry.File)?.version
                        SaveResult.SavedAsCopy(copy.name)
                    }
                }
            session.saved(document)
            result
        } catch (e: IOException) {
            Log.w(TAG, "Cannot save ${file.name}", e)
            SaveResult.Failed(e)
        }

    /** Copies [file] as it is to the backups. Saving goes on without a copy if that fails. */
    private fun backUp(file: NoteFile) {
        try {
            backups.keep(file.id, file.name) { store.read(file.id) }
        } catch (e: IOException) {
            Log.w(TAG, "Cannot back up ${file.name}", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot back up ${file.name}", e)
        }
        file.backedUp = true
    }

    /** Saves [bytes] next to [file] as `<name> (conflict <date>).xopp`. */
    private fun writeCopy(
        file: NoteFile,
        bytes: ByteString,
    ): StoreEntry.File {
        val folder =
            file.location?.folders?.last()
                ?: throw StoreException("${file.name} changed elsewhere, and its folder is unknown")
        val base = file.name.substringBeforeLast('.')
        val copy = store.create(folder.id, "$base (conflict ${LocalDateTime.now().format(STAMP)}).xopp")
        store.write(copy.id, bytes, expected = null)
        return copy
    }

    /** Page 1 with its longer side at [PREVIEW_SIZE] pixels, as PNG, like Xournal++. */
    private fun preview(
        page: Page,
        files: DocumentFiles,
    ): ByteString {
        val zoom = PREVIEW_SIZE / maxOf(page.width, page.height)
        val bitmap =
            Bitmap.createBitmap(
                ceil(page.width * zoom).toInt(),
                ceil(page.height * zoom).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        files.newRenderer().render(page, Canvas(bitmap), zoom.toFloat())
        val png = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, png)
        return png.toByteArray().toByteString()
    }

    private companion object {
        const val TAG = "NoteSaver"
        const val PREVIEW_SIZE = 128.0
        const val PNG_QUALITY = 100 // ignored: PNG is lossless
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")
    }
}
