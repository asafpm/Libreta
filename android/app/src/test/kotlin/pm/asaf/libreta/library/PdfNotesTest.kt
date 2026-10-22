// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class PdfNotesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SafDocumentStore(context.contentResolver)
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private lateinit var directory: File
    private lateinit var root: FolderRef

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        root = FolderRef(FakeDocumentsProvider.folderId(), "Notes")
    }

    private fun pdf(name: String): LibraryEntry.Pdf {
        File(testData, "libreta-pdf.xopp.bg.pdf").copyTo(File(directory, name))
        return FolderListing.list(store, root).filterIsInstance<LibraryEntry.Pdf>().single { it.name == name }
    }

    private fun open(pdf: LibraryEntry.Pdf) = PdfNotes.open(context, store, pdf, "Libreta test")

    @Test
    fun aNoteIsMadeNextToThePdfWithAPagePerPdfPage() {
        val note = open(pdf("paper.pdf"))
        assertEquals("paper.xopp", note.name)
        val document =
            File(directory, "paper.xopp")
                .source()
                .buffer()
                .use { XoppReader.read(it) }
                .document
        assertEquals(2, document.pages.size)
        val page = document.pages.first()
        assertEquals(612.0, page.width, 0.5)
        assertEquals(792.0, page.height, 0.5)
        // The PDF named relative to the note, as Xournal++ writes it.
        assertEquals(Background.Pdf(1, FileDomain.ABSOLUTE, "paper.pdf"), page.background)
        assertEquals("Libreta test", document.creator)
    }

    @Test
    fun theNoteIsReused() {
        val pdf = pdf("paper.pdf")
        val first = open(pdf)
        File(directory, "paper.xopp").setLastModified(1_000)
        assertEquals(first.id, open(pdf).id)
        assertEquals(listOf("paper.pdf", "paper.xopp"), directory.list()?.sorted())
    }

    @Test
    fun aNoteUnderAnotherOfXournalsNamesIsOpened() {
        // Made by Xournal++ elsewhere, with the name it may give.
        File(testData, "libreta-pdf.xopp").copyTo(File(directory, "paper.pdf.xopp"))
        val pdf = LibraryEntry.Pdf(FakeDocumentsProvider.fileId("paper.pdf"), "paper.pdf", pdf("x.pdf").version, root)
        File(directory, "x.pdf").renameTo(File(directory, "paper.pdf"))
        assertEquals("paper.pdf.xopp", open(pdf).name)
        assertFalse(File(directory, "paper.xopp").exists())
    }

    @Test
    fun aFileThatIsNotAPdfMakesNoNote() {
        File(directory, "fake.pdf").writeText("not a PDF")
        val fake = FolderListing.list(store, root).single() as LibraryEntry.Pdf
        assertFailsWith<IOException> { open(fake) }
        assertEquals(listOf("fake.pdf"), directory.list()?.toList())
    }
}
