// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.library.FolderRef

/**
 * Finds files referenced by a document at [location] in its [store], the way Xournal++
 * resolves them on disk (docs/xopp-format.md §4). Folder listings are cached, so use one
 * finder per document opening.
 */
internal class FileFinder(
    private val store: DocumentStore,
    private val location: DocumentLocation?,
) {
    private val listings = HashMap<FolderRef, Map<String, StoreEntry>>()

    /** `<document name>.<filename>` next to the document: gzip `attach` files. */
    fun sidecar(filename: String): StoreId? {
        val location = location ?: return null
        return fileIn(location.folders.last(), "${location.name}.$filename")
    }

    /**
     * An `absolute` path. Relative paths are followed from the document's folder, also up
     * (`..`) as far as the library root; anything else (desktop paths, paths leaving the root)
     * falls back to a file of the same name next to the document.
     */
    fun path(path: String): StoreId? {
        val location = location ?: return null
        val parts = path.split('/', '\\').filter { it.isNotEmpty() && it != "." }
        val relative = !path.startsWith("/") && !path.startsWith("\\") && !WINDOWS_DRIVE.matches(path)
        val followed = if (relative && parts.isNotEmpty()) follow(location.folders, parts) else null
        return followed ?: parts.lastOrNull()?.let { fileIn(location.folders.last(), it) }
    }

    /** The file at the end of [parts], following the folders before it from [start]. */
    private fun follow(
        start: List<FolderRef>,
        parts: List<String>,
    ): StoreId? {
        val folders =
            parts.dropLast(1).fold<String, List<FolderRef>?>(start) { folders, part ->
                folders?.let {
                    step(it, part)
                }
            }
        return folders?.let { fileIn(it.last(), parts.last()) }
    }

    /** One folder up (`..`) or down from the last of [folders]; null if there is no such folder. */
    private fun step(
        folders: List<FolderRef>,
        part: String,
    ): List<FolderRef>? =
        if (part == "..") {
            folders.dropLast(1).ifEmpty { null }
        } else {
            (children(folders.last())[part] as? StoreEntry.Folder)?.let { folders + FolderRef(it.id, part) }
        }

    private fun fileIn(
        folder: FolderRef,
        name: String,
    ): StoreId? = (children(folder)[name] as? StoreEntry.File)?.id

    /** The entries of [folder] by name; none if it can't be listed. */
    private fun children(folder: FolderRef): Map<String, StoreEntry> =
        listings.getOrPut(folder) {
            runCatching { store.list(folder.id).associateBy { it.name } }.getOrDefault(emptyMap())
        }

    private companion object {
        val WINDOWS_DRIVE = Regex("^[A-Za-z]:[\\\\/].*")
    }
}
