// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.util.LruCache
import okio.ByteString
import pm.asaf.inkpad.android.render.AndroidPdfRasterizer
import pm.asaf.inkpad.android.render.BackgroundSource
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.PdfBackgroundSource
import pm.asaf.inkpad.core.format.XoppContainer
import pm.asaf.inkpad.core.format.XoppReadResult
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.Document
import pm.asaf.inkpad.core.model.FileDomain
import pm.asaf.inkpad.library.FolderRef
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Where a document is: the folders from a library root down to the one holding it, and its
 * file name. Background files are looked for there.
 */
data class DocumentLocation(
    val folders: List<FolderRef>,
    val name: String,
) {
    init {
        require(folders.isNotEmpty())
    }
}

/**
 * The background files of an open document (docs/xopp-format.md §4): images and the PDF,
 * found inside a zip package, next to the document or along a relative path. All files are
 * located when the document is opened ([open]); drawing only reads what was found.
 * Thread-safe, so that render workers can share it.
 */
class DocumentFiles private constructor(
    private val context: Context,
    private val document: Document,
    private val images: Map<String, FileSource>,
    private val pdf: PdfBackgroundSource?,
    private val pdfCopy: File?,
    /** Background files that were referenced but not found. */
    val missing: List<String>,
) : BackgroundSource,
    Closeable {
    private val decoded = LruCache<String, Bitmap>(IMAGE_CACHE_SIZE)
    private val rasterizer = AndroidPdfRasterizer(File(context.cacheDir, "pdf-images"))

    /** A renderer for this document's pages; one per thread. */
    fun newRenderer(): PageRenderer = PageRenderer(context.applicationContext, this, rasterizer)

    override fun image(background: Background.Pixmap): Bitmap? {
        val own = if (background.domain == FileDomain.CLONE) clonedFrom(background) else background
        return own?.let(::keyOf)?.let(::cachedOrDecoded)
    }

    private fun cachedOrDecoded(key: String): Bitmap? =
        synchronized(decoded) { decoded.get(key) }
            ?: images[key]?.let(::decode)?.also { synchronized(decoded) { decoded.put(key, it) } }

    override fun drawPdfPage(
        canvas: Canvas,
        pageNumber: Long,
        zoom: Float,
    ): Boolean = pdf?.drawPdfPage(canvas, pageNumber, zoom) ?: false

    override fun close() {
        pdf?.close()
        pdfCopy?.delete()
        synchronized(decoded) { decoded.evictAll() }
    }

    /** The page background a clone background copies (its filename is the 0-based page index). */
    private fun clonedFrom(background: Background.Pixmap): Background.Pixmap? =
        background.filename
            ?.toIntOrNull()
            ?.let { document.pages.getOrNull(it)?.background as? Background.Pixmap }
            ?.takeIf { it.domain != FileDomain.CLONE }

    private fun decode(source: FileSource): Bitmap? =
        when (source) {
            is FileSource.Bytes -> {
                val bytes = source.bytes.toByteArray()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            is FileSource.Content -> {
                try {
                    context.contentResolver.openInputStream(source.uri)?.use { BitmapFactory.decodeStream(it) }
                } catch (e: IOException) {
                    null.also { Log.w(TAG, "Cannot read ${source.uri}", e) }
                } catch (e: SecurityException) {
                    null.also { Log.w(TAG, "No access to ${source.uri}", e) }
                }
            }
        }

    /** Where a referenced file's bytes come from. */
    private sealed interface FileSource {
        data class Bytes(
            val bytes: ByteString,
        ) : FileSource

        data class Content(
            val uri: Uri,
        ) : FileSource
    }

    companion object {
        private const val TAG = "DocumentFiles"
        private const val IMAGE_CACHE_SIZE = 4

        private fun keyOf(background: Background.Pixmap): String? =
            background.filename?.let { "${background.domain}:$it" }

        /** Whether [read] refers to files outside itself (never for zip packages). */
        fun needsFolder(read: XoppReadResult): Boolean =
            read.container != XoppContainer.ZIP && references(read.document).isNotEmpty()

        /** A background file a document refers to. */
        private data class Reference(
            val domain: FileDomain?,
            val filename: String,
            val pdf: Boolean,
        )

        /** Each image and PDF background file, once. */
        private fun references(document: Document): Set<Reference> =
            document.pages
                .mapNotNull { page ->
                    when (val background = page.background) {
                        is Background.Pixmap -> {
                            background.filename
                                ?.takeIf { background.domain != FileDomain.CLONE }
                                ?.let { Reference(background.domain, it, pdf = false) }
                        }

                        is Background.Pdf -> {
                            background.filename?.let { Reference(background.domain, it, pdf = true) }
                        }

                        else -> {
                            null
                        }
                    }
                }.toSet()

        /**
         * Locates the background files of [read], the document at [location] (null if it is
         * not in the library, so only files inside a zip package can be found). Blocking.
         */
        fun open(
            context: Context,
            read: XoppReadResult,
            location: DocumentLocation?,
        ): DocumentFiles {
            val finder = FileFinder(context.contentResolver, location)
            val images = HashMap<String, FileSource>()
            val missing = ArrayList<String>()
            var pdf: Pair<PdfBackgroundSource, File>? = null
            for (reference in references(read.document)) {
                val source = locate(read, finder, reference)
                when {
                    source == null -> missing += reference.filename
                    reference.pdf -> pdf = pdf ?: openPdf(context, source)
                    else -> images["${reference.domain}:${reference.filename}"] = source
                }
            }
            return DocumentFiles(context, read.document, images, pdf?.first, pdf?.second, missing)
        }

        private fun locate(
            read: XoppReadResult,
            finder: FileFinder,
            reference: Reference,
        ): FileSource? =
            if (read.container == XoppContainer.ZIP) {
                read.attachments[reference.filename]?.let { FileSource.Bytes(it) }
            } else {
                val uri =
                    when (reference.domain) {
                        FileDomain.ATTACH -> finder.sidecar(reference.filename)
                        else -> finder.path(reference.filename)
                    }
                uri?.let { FileSource.Content(it) }
            }

        private fun openPdf(
            context: Context,
            source: FileSource,
        ): Pair<PdfBackgroundSource, File>? {
            val directory = File(context.cacheDir, "pdf-backgrounds").apply { mkdirs() }
            val copy = File.createTempFile("background", ".pdf", directory)
            return try {
                when (source) {
                    is FileSource.Bytes -> {
                        copy.writeBytes(source.bytes.toByteArray())
                    }

                    is FileSource.Content -> {
                        val input =
                            context.contentResolver.openInputStream(source.uri) ?: throw IOException("No stream")
                        input.use { stream -> copy.outputStream().use { stream.copyTo(it) } }
                    }
                }
                PdfBackgroundSource(copy) to copy
            } catch (e: IOException) {
                Log.w(TAG, "Cannot open the background PDF", e)
                copy.delete()
                null
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot open the background PDF", e)
                copy.delete()
                null
            }
        }
    }
}
