// SPDX-License-Identifier: GPL-2.0-or-later
// A note carrying its PDF follows Xournal++ v1.3.8's "attach" (src/core/control/xojfile/
// SaveHandler.cpp: the PDF saved next to the note as <note>.bg.pdf, named "bg.pdf"),
// © Xournal++ contributors.

package pm.asaf.libreta.library

import android.content.Context
import okio.ByteString
import okio.buffer
import pm.asaf.libreta.android.render.PdfPages
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreException
import pm.asaf.libreta.core.store.StoreId
import java.io.IOException

/**
 * Notes of PDFs opened from other apps that are not in the library, kept in [folder] (the
 * app's own storage). The PDF may not stay where it was, so each note carries a copy of it,
 * as Xournal++ attaches one: `name.xopp` and `name.xopp.bg.pdf`.
 */
class OpenedPdfs(
    private val context: Context,
    private val store: DocumentStore,
    val folder: FolderRef,
) {
    /**
     * The note of the PDF [pdf] named [name]: an earlier one with the same PDF, or a new one
     * named after it. Fails with an [IOException] if it isn't a PDF that can be read.
     */
    @Throws(IOException::class)
    fun open(
        pdf: StoreId,
        name: String,
        creator: String,
    ): StoreEntry.File {
        val bytes = store.read(pdf).buffer().use { it.readByteString() }
        val base = if (name.endsWith(PDF, ignoreCase = true)) name.dropLast(PDF.length) else name
        val files = store.list(folder.id).filterIsInstance<StoreEntry.File>().associateBy { it.name }
        files.values.firstOrNull { it.isNoteOf(base) && carries(files[sidecarOf(it.name)], bytes) }?.let { return it }
        val sizes = PdfPages.sizes(context, bytes.toByteArray().inputStream())
        if (sizes.isEmpty()) throw IOException("$name has no pages")
        val note = NewNote.forPdf(sizes, FileDomain.ATTACH, ATTACHED, creator)
        return written(folder, base.ifBlank { "PDF" } + NewNote.EXTENSION, XoppWriter.toByteString(note), bytes)
    }

    /**
     * Moves [note] and its PDF to [target] (a library folder), where a taken name is changed.
     * Returns the note there.
     */
    @Throws(IOException::class)
    fun move(
        note: LibraryEntry.Document,
        target: FolderRef,
    ): StoreEntry.File {
        val sidecar = store.list(folder.id).firstOrNull { it.name == sidecarOf(note.name) }
        val pdf = sidecar?.let { entry -> store.read(entry.id).buffer().use { it.readByteString() } }
        val bytes = store.read(note.id).buffer().use { it.readByteString() }
        val moved = written(target, note.name, bytes, pdf)
        delete(note)
        return moved
    }

    /** Deletes [note] and its PDF. */
    @Throws(IOException::class)
    fun delete(note: LibraryEntry.Document) {
        store.list(folder.id).firstOrNull { it.name == sidecarOf(note.name) }?.let { store.delete(it.id) }
        store.delete(note.id)
    }

    /** Writes a note [named] (or as the store renames it) in [into], with its PDF if there is one. */
    private fun written(
        into: FolderRef,
        named: String,
        note: ByteString,
        pdf: ByteString?,
    ): StoreEntry.File {
        val file = store.create(into.id, named)
        var sidecar: StoreEntry.File? = null
        try {
            store.write(file.id, note, expected = null)
            if (pdf != null) {
                sidecar = store.create(into.id, sidecarOf(file.name))
                store.write(sidecar.id, pdf, expected = null)
            }
        } catch (e: StoreException) {
            sidecar?.let { runCatching { store.delete(it.id) } }
            runCatching { store.delete(file.id) }
            throw e
        }
        return file
    }

    /** Whether this is a note named after [base], possibly with " (n)" added. */
    private fun StoreEntry.File.isNoteOf(base: String): Boolean =
        name.endsWith(NewNote.EXTENSION) &&
            name
                .removeSuffix(
                    NewNote.EXTENSION,
                ).let { it == base || NUMBERED.matchEntire(it.removePrefix(base)) != null }

    /** Whether [sidecar] holds exactly [pdf]. */
    private fun carries(
        sidecar: StoreEntry.File?,
        pdf: ByteString,
    ): Boolean =
        sidecar != null &&
            sidecar.version.size == pdf.size.toLong() &&
            store
                .read(sidecar.id)
                .buffer()
                .use { it.readByteString() }
                .sha256() == pdf.sha256()

    companion object {
        private const val PDF = ".pdf"

        /** The name a note calls its PDF, and Xournal++'s file for it next to the note. */
        private const val ATTACHED = "bg.pdf"
        private val NUMBERED = Regex(""" \(\d+\)""")

        fun sidecarOf(note: String): String = "$note.$ATTACHED"
    }
}
