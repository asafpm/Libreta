// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.asaf.libreta.Message
import pm.asaf.libreta.Messages
import pm.asaf.libreta.R
import pm.asaf.libreta.android.render.PdfExport
import pm.asaf.libreta.android.render.PdfInfo
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.library.NewNoteCreator
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream

/**
 * Exporting the open note as a PDF ([PdfExport]): saved where the user chose, or shared.
 * Written on [io]; [progress] shows how far it is, and [cancel] stops it. [messages] tell
 * the user how it went (offering to open a PDF saved).
 */
class PdfExportFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val messages: Messages,
) {
    private val state = MutableStateFlow<Float?>(null)

    /** How much of the export is done, from 0 to 1; null when nothing is exported. */
    val progress: StateFlow<Float?> = state.asStateFlow()

    @Volatile
    private var cancelled = false
    private var job: Job? = null

    /**
     * Exports [pages] (as shown) of [files]' document, the note [name], to [target] (made by
     * the system's save picker).
     */
    fun save(
        files: DocumentFiles,
        pages: List<Page>,
        name: String,
        target: Uri,
    ) = run(files, pages, name, {
        context.contentResolver.openOutputStream(target, "wt") ?: throw FileNotFoundException(target.toString())
    }, onCancel = { runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) } }) {
        val open =
            Message.Action(context.getString(R.string.open)) {
                val view =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(target, PDF_TYPE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                // No app to show PDFs: nothing to do.
                runCatching { context.startActivity(view) }
            }
        messages.say(context.getString(R.string.exported), open)
    }

    /**
     * Exports [pages] (as shown) of [files]' document, the note [name], to a file to share,
     * then hands [share] the intent sending it.
     */
    fun share(
        files: DocumentFiles,
        pages: List<Page>,
        name: String,
        exportName: String,
        share: (Intent) -> Unit,
    ) {
        val directory = File(context.cacheDir, EXPORTS).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, exportName)
        run(files, pages, name, { file.outputStream() }, onCancel = { file.delete() }) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(PDF_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            share(Intent.createChooser(send, exportName))
        }
    }

    fun cancel() {
        cancelled = true
    }

    @Suppress("LongParameterList") // what is exported, where to, and what follows
    private fun run(
        files: DocumentFiles,
        pages: List<Page>,
        name: String,
        open: () -> OutputStream,
        onCancel: () -> Unit,
        onDone: () -> Unit,
    ) {
        if (job?.isActive == true) return
        cancelled = false
        state.value = 0f
        job =
            scope.launch {
                val title = name.substringBeforeLast('.')
                val written =
                    withContext(io) {
                        runCatching {
                            open().use { output ->
                                PdfExport.write(
                                    context,
                                    pages,
                                    files.newRenderer(),
                                    files.pdfFile,
                                    output,
                                    PdfInfo(title, NewNoteCreator.creator(context)),
                                    progress = { state.value = it },
                                    cancelled = { cancelled },
                                )
                            }
                        }.onFailure { onCancel() }
                    }
                state.value = null
                written
                    .onSuccess { onDone() }
                    .onFailure { e ->
                        if (e !is InterruptedIOException) {
                            Log.w(TAG, "Cannot export $name", e)
                            messages.say(context.getString(R.string.error_export))
                        }
                    }
            }
    }

    private companion object {
        const val TAG = "PdfExportFlow"
        const val EXPORTS = "exports"
        const val PDF_TYPE = "application/pdf"
    }
}
