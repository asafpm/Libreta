// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okio.ByteString.Companion.toByteString
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.files.DocumentFiles
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.store.OpenedPdfsProvider
import pm.asaf.libreta.store.SafDocumentStore

/** The app's own documents provider for notes of PDFs from other apps, on a device. */
@RunWith(AndroidJUnit4::class)
class OpenedPdfsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val store = SafDocumentStore(context.contentResolver)
    private val folder = FolderRef(OpenedPdfsProvider.folder(context), "Opened PDFs")

    @Test
    fun notesOfOpenedPdfsAreKeptInTheAppWithTheirPdf() {
        // A PDF to open, in the provider itself (another app's would come with a grant).
        val bytes = assets.open("libreta-pdf.xopp.bg.pdf").use { it.readBytes() }
        val shared = store.create(folder.id, "shared.pdf.tmp")
        store.write(shared.id, bytes.toByteString(), expected = null)
        val opened = OpenedPdfs(context, store, folder)
        val note = opened.open(shared.id, "device test.pdf", "Libreta device test")
        try {
            assertEquals("device test.xopp", note.name)
            assertEquals(note.id, opened.open(shared.id, "device test.pdf", "x").id)
            // It loads with its PDF, drawn by Android's PdfRenderer.
            val read = store.read(note.id).buffer().use { XoppReader.read(it) }
            DocumentFiles.open(context, store, read, DocumentLocation(listOf(folder), note.name)).use { files ->
                assertEquals(emptyList<String>(), files.missing)
                assertTrue(files.pdfFile?.isFile == true)
                files.newRenderer().renderToBitmap(read.document.pages[0], 0.5f)
            }
        } finally {
            val notes = FolderListing.list(store, folder).filterIsInstance<LibraryEntry.Document>()
            notes.filter { it.id == note.id }.forEach { LibraryFiles(store).delete(it, alsoPdf = true) }
            store.delete(shared.id)
        }
        assertTrue(store.list(folder.id).none { it.name.startsWith("device test") || it.name == "shared.pdf.tmp" })
    }
}
