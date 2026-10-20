// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.Source
import okio.buffer
import okio.sink
import pm.asaf.libreta.android.render.BitmapLru
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.PdfBackgroundSource
import pm.asaf.libreta.android.render.TileRenderer
import pm.asaf.libreta.core.format.XoppFormatException
import pm.asaf.libreta.core.format.XoppPreview
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.store.DocumentStore
import pm.asaf.libreta.files.DocumentFiles
import pm.asaf.libreta.files.DocumentLocation
import java.io.File
import java.io.IOException

/**
 * Previews of library documents and PDFs: page 1 rendered at the thumbnail width, kept in
 * memory and on disk (JPEG: pages are opaque) per version of the file. Until it is rendered,
 * the small preview Xournal++ embeds in files is shown. [locate] finds where a document is
 * in the library, so that background files outside its folder are found too.
 */
class PreviewCache(
    context: Context,
    private val store: DocumentStore,
    private val directory: File = File(context.cacheDir, "previews"),
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2),
    private val locate: (LibraryEntry.Document) -> DocumentLocation? = { null },
) {
    private val memory = BitmapLru<String>(MEMORY_BYTES)

    private val context: Context = context.applicationContext

    // Rendering is serial, to bound memory; documents are still read in parallel.
    private val rendering = Mutex()

    /** Lets go of the previews in memory (they stay on disk), as memory is short. */
    fun trimMemory() = memory.clear()

    /** The finished preview, if it is in memory. */
    fun cached(document: LibraryEntry.File): Bitmap? = memory[document.previewKey()]

    /**
     * Previews of [document], better ones later: the finished preview if cached, otherwise
     * the embedded preview (if any) and then the rendered one. Empty if the file can't be read.
     */
    fun previews(document: LibraryEntry.File): Flow<Bitmap> =
        flow {
            val key = document.previewKey()
            val file = File(directory, key.encodeUtf8().sha256().hex() + ".jpg")
            val finished = memory[key] ?: decode(file)?.also { memory.put(key, it) }
            if (finished != null) {
                emit(finished)
            } else {
                if (document is LibraryEntry.Document) embedded(document)?.let { emit(it) }
                val rendered =
                    when (document) {
                        is LibraryEntry.Document -> render(document)
                        is LibraryEntry.Pdf -> renderPdf(document)
                    }
                rendered?.let { rendered ->
                    memory.put(key, rendered)
                    save(rendered, file)
                    emit(rendered)
                }
            }
        }
            // Whatever a note holds, the library must not crash over its preview.
            .catch { e -> Log.w(TAG, "No preview for ${document.name}", e) }
            .flowOn(io)

    private fun decode(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path) else null

    private fun embedded(document: LibraryEntry.Document): Bitmap? =
        readOrNull(document) { source ->
            XoppPreview.read(source)?.toByteArray()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }

    private suspend fun render(document: LibraryEntry.Document): Bitmap? {
        val read = readOrNull(document) { XoppReader.read(it) }
        val page = read?.document?.pages?.firstOrNull() ?: return null
        val location = locate(document) ?: DocumentLocation(listOf(document.parent), document.name)
        val files = DocumentFiles.open(context, store, read, location)
        return try {
            rendering.withLock { TileRenderer.renderThumbnail(files.newRenderer(), page) }
        } finally {
            files.close()
        }
    }

    /** A PDF's first page, as a note on it shows it. */
    private suspend fun renderPdf(pdf: LibraryEntry.Pdf): Bitmap? {
        val copy = File.createTempFile("preview", ".pdf", context.cacheDir)
        return try {
            store.read(pdf.id).use { source -> copy.sink().buffer().use { it.writeAll(source) } }
            PdfBackgroundSource(copy).use { source ->
                source.pageSize(1)?.let { (width, height) ->
                    val page = Page(width, height, Background.Pdf(1), listOf(Layer(emptyList())))
                    rendering.withLock { TileRenderer.renderThumbnail(PageRenderer(context, source), page) }
                }
            }
        } catch (e: IOException) {
            null.also { logFailure(pdf, e) }
        } catch (e: SecurityException) {
            // Locked with a password.
            null.also { logFailure(pdf, e) }
        } finally {
            copy.delete()
        }
    }

    private fun <T> readOrNull(
        document: LibraryEntry.File,
        read: (Source) -> T?,
    ): T? =
        try {
            store.read(document.id).use(read)
        } catch (e: IOException) {
            null.also { logFailure(document, e) }
        } catch (e: XoppFormatException) {
            null.also { logFailure(document, e) }
        }

    private fun logFailure(
        document: LibraryEntry.File,
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

/** What a preview of this file is cached under: a new version is a new preview. */
private fun LibraryEntry.File.previewKey() = "${id.value}|$version"
