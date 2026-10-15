// SPDX-License-Identifier: GPL-2.0-or-later
// Like Xournal++ v1.3.8 src/core/control/Control.cpp (openFileWithoutSavingTheCurrentDocument:
// a PDF's existing note is opened instead, with autoloadPdfXoj) and src/core/model/Document.cpp
// (readPdf: a page per PDF page), © Xournal++ contributors.

package pm.asaf.libreta.library

import android.content.Context
import okio.buffer
import pm.asaf.libreta.android.render.PdfPages
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreException
import java.io.IOException

/** Notes on PDFs in library folders, kept next to the PDF. */
object PdfNotes {
    /**
     * The note of [pdf]: the first of Xournal++'s names for it in its folder, or else a new
     * `name.xopp` there with a page per PDF page, naming the PDF relative to the note. Fails
     * with an [IOException] if the PDF can't be read (not a PDF, or locked with a password).
     */
    @Throws(IOException::class)
    fun open(
        context: Context,
        store: DocumentStore,
        pdf: LibraryEntry.Pdf,
        creator: String,
    ): StoreEntry.File {
        val names = NewNote.notesForPdf(pdf.name)
        val files = store.list(pdf.parent.id).filterIsInstance<StoreEntry.File>().associateBy { it.name }
        names.firstNotNullOfOrNull { files[it] }?.let { return it }
        val sizes = store.read(pdf.id).buffer().use { PdfPages.sizes(context, it.inputStream()) }
        if (sizes.isEmpty()) throw IOException("${pdf.name} has no pages")
        val note = NewNote.forPdf(sizes, FileDomain.ABSOLUTE, pdf.name, creator)
        val file = store.create(pdf.parent.id, names.first())
        try {
            store.write(file.id, XoppWriter.toByteString(note), expected = null)
        } catch (e: StoreException) {
            runCatching { store.delete(file.id) }
            throw e
        }
        return file
    }
}
