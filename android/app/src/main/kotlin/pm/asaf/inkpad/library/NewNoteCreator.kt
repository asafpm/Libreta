// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.Context
import pm.asaf.inkpad.R
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.NewNote
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.store.DocumentStore
import pm.asaf.inkpad.core.store.StoreEntry
import pm.asaf.inkpad.core.store.StoreException
import java.time.LocalDateTime

/** Creates new notes in library folders. */
object NewNoteCreator {
    /** Xournal++'s default name for a note made [now], not clashing with [folder]'s files. */
    fun suggestedName(
        store: DocumentStore,
        folder: FolderRef,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val names = runCatching { FolderListing.list(store, folder).map { it.name } }
        val existing = names.getOrDefault(emptyList()).toSet()
        return NewNote.suggestedName(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, existing)
    }

    /** The `creator` written into new notes: the app name and version. */
    fun creator(context: Context): String {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        return listOfNotNull(context.getString(R.string.app_name), version).joinToString(" ")
    }

    /** [name] made safe as a file name, with the `.xopp` extension. */
    fun fileName(name: String): String {
        val cleaned = name.trim().replace('/', '-').ifEmpty { "Note" }
        return if (FolderListing.isDocumentName(cleaned)) cleaned else cleaned + NewNote.EXTENSION
    }

    /**
     * Creates a note named [name] in [folder] with one page as [template] makes a note's first
     * page. The store may change the name if it is taken.
     */
    fun create(
        store: DocumentStore,
        folder: FolderRef,
        name: String,
        template: NewPageTemplate,
        creator: String,
    ): StoreEntry.File {
        val file = store.create(folder.id, fileName(name))
        val background = Background.Solid(template.color, template.style)
        val document = NewNote.document(template.paper, template.landscape, creator, background)
        try {
            store.write(file.id, XoppWriter.toByteString(document), expected = null)
        } catch (e: StoreException) {
            runCatching { store.delete(file.id) }
            throw e
        }
        return file
    }
}
