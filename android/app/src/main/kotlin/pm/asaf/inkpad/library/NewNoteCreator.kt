// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import okio.sink
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.model.NewNote
import pm.asaf.inkpad.core.model.PaperSize
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime

/** Creates new notes in library folders. */
object NewNoteCreator {
    /** Xournal++ files have no registered type; this keeps providers from changing the extension. */
    private const val MIME_TYPE = "application/octet-stream"

    /** Xournal++'s default name for a note made [now], not clashing with [folder]'s files. */
    fun suggestedName(
        resolver: ContentResolver,
        folder: FolderRef,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val names = runCatching { FolderListing.list(resolver, folder).map { it.name } }
        val existing = names.getOrDefault(emptyList()).toSet()
        return NewNote.suggestedName(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, existing)
    }

    /** [name] made safe as a file name, with the `.xopp` extension. */
    fun fileName(name: String): String {
        val cleaned = name.trim().replace('/', '-').ifEmpty { "Note" }
        return if (FolderListing.isDocumentName(cleaned)) cleaned else cleaned + NewNote.EXTENSION
    }

    /**
     * Creates a note named [name] in [folder] with one page of [paper] and returns its uri.
     * The provider may change the name if it is taken.
     */
    fun create(
        resolver: ContentResolver,
        folder: FolderRef,
        name: String,
        paper: PaperSize,
        landscape: Boolean,
        creator: String,
    ): Uri {
        val uri =
            DocumentsContract.createDocument(resolver, folder.uri, MIME_TYPE, fileName(name))
                ?: throw IOException("Cannot create $name in ${folder.name}")
        try {
            XoppWriter.write(NewNote.document(paper, landscape, creator), resolver.openForWriting(uri).sink())
        } catch (e: IOException) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
        return uri
    }

    private fun ContentResolver.openForWriting(uri: Uri): OutputStream =
        openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")
}
