// SPDX-License-Identifier: GPL-2.0-or-later
// The preview follows Xournal++ v1.3.8 src/core/control/jobs/SaveJob.cpp (updatePreview),
// © Xournal++ contributors.

package pm.asaf.inkpad.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import okio.ByteString
import okio.ByteString.Companion.toByteString
import pm.asaf.inkpad.core.format.XoppContainer
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.library.NewNoteCreator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import pm.asaf.inkpad.core.model.Document as Note

/** A version of a file, to tell whether someone else changed it. */
data class FileStamp(
    val modified: Long,
    val size: Long,
) {
    companion object {
        /** The stamp of the document at [uri], or null if the provider doesn't say. */
        fun of(
            resolver: ContentResolver,
            uri: Uri,
        ): FileStamp? =
            runCatching {
                val columns = arrayOf(Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE)
                resolver.query(uri, columns, null, null)?.use { cursor ->
                    val known = cursor.moveToFirst() && !cursor.isNull(0)
                    if (known) FileStamp(cursor.getLong(0), cursor.getLong(1)) else null
                }
            }.getOrNull()
    }
}

/** The file of an open note; after a conflict it is the copy the note was saved to. */
class NoteFile(
    @Volatile var uri: Uri,
    @Volatile var name: String,
    /** Where it is in the library, or null (then a conflict copy can't be made next to it). */
    val location: DocumentLocation?,
    val container: XoppContainer,
    /** Its version when read or last saved. */
    @Volatile var stamp: FileStamp?,
) {
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
 * with a fresh preview like Xournal++. A file changed elsewhere since it was opened is not
 * overwritten: the note is saved next to it as a conflict copy. Blocking.
 */
class NoteSaver(
    private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun save(
        file: NoteFile,
        edits: NoteEdits,
        files: DocumentFiles,
    ): SaveResult {
        val document = edits.document
        return when {
            !edits.dirty -> SaveResult.Unchanged
            !file.writable -> SaveResult.ReadOnly
            else -> write(file, edits, document, files)
        }
    }

    private fun write(
        file: NoteFile,
        edits: NoteEdits,
        document: Note,
        files: DocumentFiles,
    ): SaveResult =
        try {
            val preview = document.pages.firstOrNull()?.let { preview(it, files) }
            val bytes =
                XoppWriter.toByteString(
                    document.copy(creator = NewNoteCreator.creator(context), preview = preview),
                )
            val changedElsewhere = file.stamp != null && FileStamp.of(resolver, file.uri) != file.stamp
            val result =
                if (changedElsewhere) {
                    val (copy, name) = writeCopy(file, bytes)
                    // Later saves go to the copy.
                    file.uri = copy
                    file.name = name
                    file.stamp = FileStamp.of(resolver, copy)
                    SaveResult.SavedAsCopy(name)
                } else {
                    writeTo(file.uri, bytes)
                    file.stamp = FileStamp.of(resolver, file.uri)
                    SaveResult.Saved
                }
            edits.saved(document)
            result
        } catch (e: IOException) {
            Log.w(TAG, "Cannot save ${file.name}", e)
            SaveResult.Failed(e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to save ${file.name}", e)
            SaveResult.Failed(e)
        }

    private fun writeTo(
        uri: Uri,
        bytes: ByteString,
    ) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")
        stream.use { bytes.write(it) }
    }

    /** Saves [bytes] next to [file] as `<name> (conflict <date>).xopp`; returns its uri and name. */
    private fun writeCopy(
        file: NoteFile,
        bytes: ByteString,
    ): Pair<Uri, String> {
        val folder =
            file.location?.folders?.last()
                ?: throw IOException("${file.name} changed elsewhere, and its folder is unknown")
        val base = file.name.substringBeforeLast('.')
        val name = "$base (conflict ${LocalDateTime.now().format(STAMP)}).xopp"
        val uri =
            DocumentsContract.createDocument(resolver, folder.uri, MIME_TYPE, name)
                ?: throw IOException("Cannot create $name")
        writeTo(uri, bytes)
        return uri to name
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
        const val MIME_TYPE = "application/octet-stream"
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")
    }
}
