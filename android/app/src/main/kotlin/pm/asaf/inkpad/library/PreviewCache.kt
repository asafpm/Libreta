// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.library

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.source
import pm.asaf.inkpad.android.render.BitmapLru
import pm.asaf.inkpad.android.render.TileRenderer
import pm.asaf.inkpad.core.format.XoppFormatException
import pm.asaf.inkpad.core.format.XoppPreview
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.files.DocumentFiles
import pm.asaf.inkpad.files.DocumentLocation
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Previews of library documents: page 1 rendered at the thumbnail width, kept in memory and
 * on disk (JPEG: pages are opaque) per version of the file (uri, modification time, size). Until it is rendered, the
 * small preview Xournal++ embeds in files is shown.
 */
class PreviewCache(
    context: Context,
    private val directory: File = File(context.cacheDir, "previews"),
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2),
) {
    private val resolver: ContentResolver = context.contentResolver
    private val memory = BitmapLru<String>(MEMORY_BYTES)

    private val context: Context = context.applicationContext

    // Rendering is serial, to bound memory; documents are still read in parallel.
    private val rendering = Mutex()

    /** The finished preview, if it is in memory. */
    fun cached(document: LibraryEntry.Document): Bitmap? = memory[keyOf(document)]

    /**
     * Previews of [document], better ones later: the finished preview if cached, otherwise
     * the embedded preview (if any) and then the rendered one. Empty if the file can't be read.
     */
    fun previews(document: LibraryEntry.Document): Flow<Bitmap> =
        flow {
            val key = keyOf(document)
            val file = File(directory, key.encodeUtf8().sha256().hex() + ".jpg")
            val finished = memory[key] ?: decode(file)?.also { memory.put(key, it) }
            if (finished != null) {
                emit(finished)
            } else {
                embedded(document)?.let { emit(it) }
                render(document)?.let { rendered ->
                    memory.put(key, rendered)
                    save(rendered, file)
                    emit(rendered)
                }
            }
        }.flowOn(io)

    private fun keyOf(document: LibraryEntry.Document) = "${document.uri}|${document.modified}|${document.size}"

    private fun decode(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    private fun embedded(document: LibraryEntry.Document): Bitmap? =
        readOrNull(document) { stream ->
            XoppPreview.read(stream.source())?.toByteArray()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }

    private suspend fun render(document: LibraryEntry.Document): Bitmap? {
        val read = readOrNull(document) { XoppReader.read(it.source()) }
        val page = read?.document?.pages?.firstOrNull() ?: return null
        // Background files next to the note are found; paths leaving its folder are not.
        val files = DocumentFiles.open(context, read, DocumentLocation(listOf(document.parent), document.name))
        return try {
            rendering.withLock { TileRenderer.renderThumbnail(files.newRenderer(), page) }
        } finally {
            files.close()
        }
    }

    private fun <T> readOrNull(
        document: LibraryEntry.Document,
        read: (InputStream) -> T?,
    ): T? =
        try {
            resolver.openInputStream(document.uri)?.use(read)
        } catch (e: IOException) {
            null.also { logFailure(document, e) }
        } catch (e: XoppFormatException) {
            null.also { logFailure(document, e) }
        } catch (e: SecurityException) {
            null.also { logFailure(document, e) } // access withdrawn
        }

    private fun logFailure(
        document: LibraryEntry.Document,
        e: Exception,
    ) = Log.w(TAG, "No preview for ${document.name}", e)

    private fun save(
        bitmap: Bitmap,
        file: File,
    ) {
        try {
            directory.mkdirs()
            val partial = File(directory, file.name + ".part")
            partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            partial.renameTo(file)
            trimDisk()
        } catch (e: IOException) {
            Log.w(TAG, "Cannot cache preview", e)
        }
    }

    /** Deletes the oldest files once the directory is over [DISK_BYTES]. */
    private fun trimDisk() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= DISK_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private companion object {
        const val TAG = "PreviewCache"
        const val MEMORY_BYTES = 48L * 1024 * 1024
        const val DISK_BYTES = 64L * 1024 * 1024
        const val QUALITY = 85
    }
}
