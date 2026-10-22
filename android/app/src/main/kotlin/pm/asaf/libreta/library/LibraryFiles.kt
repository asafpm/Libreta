// SPDX-License-Identifier: GPL-2.0-or-later
// A note keeps with it the files Xournal++ v1.3.8 keeps with a note: its attached files
// (src/core/control/xojfile/SaveHandler.cpp: <note>.<name>) and the PDF it is named after
// (src/core/control/Control.cpp, openFileWithoutSavingTheCurrentDocument), whose link
// Xournal++ follows from the note's folder (src/core/control/xojfile/LoadHandler.cpp),
// © Xournal++ contributors.

package pm.asaf.libreta.library

import okio.ByteString
import okio.IOException
import okio.buffer
import pm.asaf.libreta.core.format.XoppContainer
import pm.asaf.libreta.core.format.XoppFormatException
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreException
import pm.asaf.libreta.core.store.StoreId

/**
 * Renames, moves and deletes library entries, each with what goes with it: a note with its
 * attached files and the PDF it stands for in its folder ([FolderListing]'s rule), a folder
 * with everything in it. Names already taken are refused ([Refused]) before anything
 * changes, as changing one would part a note from its files. Blocking.
 */
class LibraryFiles(
    private val store: DocumentStore,
) {
    enum class Problem { NAME_TAKEN, INTO_ITSELF, BAD_NAME }

    /** Refused before anything changed; [name] is the name in the way. */
    class Refused(
        val problem: Problem,
        val name: String,
    ) : IOException("$problem: $name")

    /** The PDF that goes with [note], if one does. */
    fun pdfOf(note: LibraryEntry.Document): StoreEntry.File? = group(note).pdf

    /**
     * Renames [entry] to [stem] (its name without extensions, see [stem]), and what goes with
     * it alike; a note's link to its PDF follows. Returns it as it is now.
     */
    fun rename(
        entry: LibraryEntry,
        stem: String,
    ): StoreEntry {
        if (!isGoodName(stem)) throw Refused(Problem.BAD_NAME, stem)
        val group = group(entry)
        val old = stemOf(entry)
        val names = group.members.map { stem + it.name.drop(old.length) }
        checkFree(names, group.others.map { it.name })
        val renamed = ArrayList<StoreEntry>()
        undoing({
            renamed.zip(group.members).forEach { (now, was) ->
                if (now.name != was.name) runCatching { store.rename(now.id, was.name) }
            }
        }) {
            group.members.zip(names).forEach { (member, name) ->
                renamed += if (member.name == name) member else store.rename(member.id, name)
            }
            group.pdf?.let { relink(renamed.first(), it.name, names.last()) }
        }
        return renamed.first()
    }

    /** Moves [entry] and what goes with it into [target]. Returns it as it is there. */
    fun move(
        entry: LibraryEntry,
        target: FolderRef,
    ): StoreEntry {
        val group = group(entry)
        if (target.id == entry.parent.id) return group.main
        if (entry is LibraryEntry.Folder && isWithin(target.id, entry.folder.id)) {
            throw Refused(Problem.INTO_ITSELF, entry.name)
        }
        checkFree(group.members.map { it.name }, store.list(target.id).map { it.name })
        val placed = ArrayList<Placed>()
        undoing({ placed.forEach { runCatching { it.undo(target.id, entry.parent.id) } } }) {
            for (member in group.members) {
                val moved = store.move(member.id, entry.parent.id, target.id)
                placed += moved?.let { Placed(it, original = null) } ?: Placed(copy(member, target.id), member)
            }
        }
        placed.forEach { it.original?.let { original -> store.delete(original.id) } }
        return placed.first().entry
    }

    /**
     * Copies the note [entry] with its attached files next to it, as "name (copy)" (or
     * "(copy 2)", …); the PDF it shows stays one, next to both. Returns the copy.
     */
    fun duplicate(entry: LibraryEntry.Document): StoreEntry {
        val group = group(entry)
        val old = stemOf(entry)
        val files = listOf(group.main) + group.attached
        val taken = (group.others + group.members).map { it.name.lowercase() }.toSet()
        val stem =
            generateSequence(1) { it + 1 }
                .map { if (it == 1) "$old (copy)" else "$old (copy $it)" }
                .first { stem -> files.none { (stem + it.name.drop(old.length)).lowercase() in taken } }
        val copies = ArrayList<StoreEntry>()
        undoing({ copies.forEach { runCatching { store.delete(it.id) } } }) {
            for (file in files) copies += copy(file, entry.parent.id, stem + file.name.drop(old.length))
        }
        return copies.first()
    }

    /** Deletes [entry] and what goes with it, but a note's PDF only if [alsoPdf]. */
    fun delete(
        entry: LibraryEntry,
        alsoPdf: Boolean = false,
    ) {
        val group = group(entry)
        group.members
            .drop(1)
            .filter { alsoPdf || it != group.pdf }
            .forEach { store.delete(it.id) }
        store.delete(group.main.id)
    }

    /** Refuses [names] if any is among [taken] (in any case: storage often ignores it). */
    private fun checkFree(
        names: List<String>,
        taken: List<String>,
    ) {
        names.firstOrNull { name -> taken.any { it.equals(name, ignoreCase = true) } }?.let {
            throw Refused(Problem.NAME_TAKEN, it)
        }
    }

    /** Does [action], calling [undo] if it fails. */
    private inline fun undoing(
        undo: () -> Unit,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (e: IOException) {
            undo()
            throw e
        }
    }

    /** Where a member went: moved by the store, or copied from [original]. */
    private inner class Placed(
        val entry: StoreEntry,
        val original: StoreEntry?,
    ) {
        fun undo(
            from: StoreId,
            to: StoreId,
        ) {
            if (original == null) store.move(entry.id, from, to) else store.delete(entry.id)
        }
    }

    /** [entry] (first), what goes with it, and the rest of its folder. */
    private class Group(
        val main: StoreEntry,
        val attached: List<StoreEntry>,
        val pdf: StoreEntry.File?,
        val others: List<StoreEntry>,
    ) {
        val members: List<StoreEntry> = listOf(main) + attached + listOfNotNull(pdf)
    }

    private fun group(entry: LibraryEntry): Group {
        val listed = store.list(entry.parent.id).distinctBy { it.id }
        val main = listed.firstOrNull { it.id == entry.storeId } ?: entry.asStoreEntry()
        val siblings = listed.filter { it.id != main.id }
        if (entry !is LibraryEntry.Document) return Group(main, emptyList(), null, siblings)
        val attached = siblings.filter { FolderListing.isAttachedTo(it.name, main.name) }
        val pdf =
            (siblings - attached.toSet()).filterIsInstance<StoreEntry.File>().firstOrNull { pdf ->
                FolderListing.annotates(main.name, pdf.name) &&
                    listed.count { FolderListing.annotates(it.name, pdf.name) } == 1
            }
        val members = attached + listOfNotNull(pdf)
        return Group(main, attached, pdf, siblings.filter { it !in members })
    }

    /** Whether the folder [folder] is [ancestor] or below it. */
    private fun isWithin(
        folder: StoreId,
        ancestor: StoreId,
    ): Boolean = folder == ancestor || store.pathTo(ancestor, folder) != null

    /** A copy of [entry] in [into], named [name] (its own by default). */
    private fun copy(
        entry: StoreEntry,
        into: StoreId,
        name: String = entry.name,
    ): StoreEntry {
        val copy =
            when (entry) {
                is StoreEntry.File -> store.create(into, name)
                is StoreEntry.Folder -> store.createFolder(into, name)
            }
        try {
            if (copy.name != name) throw Refused(Problem.NAME_TAKEN, name)
            when (entry) {
                is StoreEntry.File -> store.write(copy.id, store.bytes(entry.id), expected = null)
                is StoreEntry.Folder -> store.list(entry.id).forEach { copy(it, copy.id) }
            }
        } catch (e: IOException) {
            runCatching { store.delete(copy.id) }
            throw e
        }
        return copy
    }

    /** Points [note]'s link to the PDF [from] to [to], its new name next to the note. */
    private fun relink(
        note: StoreEntry,
        from: String,
        to: String,
    ) {
        val read =
            try {
                XoppReader.read(store.bytes(note.id))
            } catch (e: XoppFormatException) {
                throw StoreException("Cannot read ${note.name}", e)
            }
        // A zip package carries its PDF.
        if (read.container == XoppContainer.ZIP) return
        var changed = false
        val pages =
            read.document.pages.map { page ->
                val background = page.background
                if (background is Background.Pdf && background.isLinkTo(from)) {
                    changed = true
                    page.copy(background = background.copy(filename = to))
                } else {
                    page
                }
            }
        if (changed) store.write(note.id, XoppWriter.toByteString(read.document.copy(pages = pages)), expected = null)
    }

    companion object {
        /** The part of [entry]'s name that a rename changes: see [stem]; all of a folder's. */
        fun stemOf(entry: LibraryEntry): String = if (entry is LibraryEntry.Folder) entry.name else stem(entry.name)

        /**
         * The part of [name] that a rename changes: without a note's extension, nor the PDF's
         * one a note may be named with (`lecture.pdf.xopp`), nor a PDF's.
         */
        fun stem(name: String): String =
            when {
                FolderListing.isDocumentName(name) -> name.substringBeforeLast('.').let(::withoutPdf)
                FolderListing.isPdfName(name) -> withoutPdf(name)
                else -> name
            }

        /** Whether [stem] can be a name: not blank, not hidden, no folders. */
        fun isGoodName(stem: String): Boolean =
            stem.isNotBlank() && !stem.startsWith(".") && '/' !in stem && '\\' !in stem

        private fun withoutPdf(name: String) = if (FolderListing.isPdfName(name)) name.dropLast(PDF.length) else name

        private const val PDF = ".pdf"

        private fun Background.Pdf.isLinkTo(pdf: String) =
            domain != FileDomain.ATTACH && filename?.substringAfterLast('/')?.substringAfterLast('\\') == pdf

        private fun LibraryEntry.asStoreEntry(): StoreEntry =
            when (this) {
                is LibraryEntry.Folder -> StoreEntry.Folder(folder.id, name, modified)
                is LibraryEntry.File -> StoreEntry.File(id, name, version)
            }
    }
}

/** The contents of [file]. */
private fun DocumentStore.bytes(file: StoreId): ByteString = read(file).buffer().use { it.readByteString() }
