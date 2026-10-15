// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.store

import okio.ByteString
import okio.IOException
import okio.Source
import kotlin.jvm.JvmInline

/** A file or folder in a [DocumentStore], opaque to everyone else (e.g. a content URI). */
@JvmInline
value class StoreId(
    val value: String,
)

/** A version of a file, to tell whether it changed (modification time and size, or an ETag). */
data class FileVersion(
    /** Milliseconds since the epoch, or 0 if unknown. */
    val modified: Long,
    /** Bytes, or -1 if unknown. */
    val size: Long,
    val etag: String? = null,
)

/** Something in a folder. */
sealed interface StoreEntry {
    val id: StoreId
    val name: String

    data class Folder(
        override val id: StoreId,
        override val name: String,
        /** Milliseconds since the epoch, or 0 if unknown. */
        val modified: Long,
    ) : StoreEntry

    data class File(
        override val id: StoreId,
        override val name: String,
        val version: FileVersion,
    ) : StoreEntry
}

/** What [DocumentStore.write] did. */
sealed interface WriteResult {
    /** Written; the file is now at [version] (null if the store can't tell). */
    data class Written(
        val version: FileVersion?,
    ) : WriteResult

    /** Not written: the file is no longer at the expected version. */
    data class Conflict(
        val current: FileVersion?,
    ) : WriteResult
}

/** A store operation failed (unreachable, gone, not allowed, …). */
class StoreException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Where notes are kept: folders of files, read and written whole. All file access goes through
 * this interface, so that local folders (Android's Storage Access Framework) and remote ones
 * (WebDAV) look the same. Calls are blocking; make them off the main thread.
 */
interface DocumentStore {
    /** The folders and files directly in [folder]. */
    @Throws(StoreException::class)
    fun list(folder: StoreId): List<StoreEntry>

    /** The name and version of [id], or null if it doesn't exist. */
    fun entry(id: StoreId): StoreEntry?

    /** The contents of [file]. */
    @Throws(StoreException::class)
    fun read(file: StoreId): Source

    /**
     * Replaces the contents of [file] with [bytes], unless it changed from [expected] (when
     * given) since; stores without atomic writes check just before writing.
     */
    @Throws(StoreException::class)
    fun write(
        file: StoreId,
        bytes: ByteString,
        expected: FileVersion?,
    ): WriteResult

    /** Creates an empty file named [name] in [folder]; the store may change a taken name. */
    @Throws(StoreException::class)
    fun create(
        folder: StoreId,
        name: String,
    ): StoreEntry.File

    /** Creates a folder named [name] in [folder]; the store may change a taken name. */
    @Throws(StoreException::class)
    fun createFolder(
        folder: StoreId,
        name: String,
    ): StoreEntry.Folder

    @Throws(StoreException::class)
    fun delete(file: StoreId)

    /**
     * The folders from [root] down to the one holding [file], if [file] is below [root];
     * null otherwise (or if the store can't tell).
     */
    fun pathTo(
        root: StoreId,
        file: StoreId,
    ): List<StoreEntry.Folder>?
}
