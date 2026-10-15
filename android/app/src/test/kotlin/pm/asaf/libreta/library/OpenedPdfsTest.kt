// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.files.DocumentFiles
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.store.OpenedPdfsProvider
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenedPdfsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SafDocumentStore(context.contentResolver)
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private val opened = OpenedPdfs(context, store, FolderRef(OpenedPdfsProvider.folder(context), "Opened PDFs"))
    private val inside = OpenedPdfsProvider.directoryOf(context)

    /** Where other apps' PDFs are (e.g. Downloads), and the library folder. */
    private lateinit var outside: File

    @Before
    fun setUp() {
        outside = temporary.newFolder("outside")
        FakeDocumentsProvider.install(outside)
        BlankPdfPages.install()
    }

    @After
    fun tearDown() = BlankPdfPages.uninstall()

    /** A PDF another app shares, named [name]; [sample] gives its bytes. */
    private fun shared(
        name: String,
        sample: String = "pdf_annotation.xopp.bg.pdf",
    ) = File(testData, sample).copyTo(File(outside, name), overwrite = true).let { FakeDocumentsProvider.fileId(name) }

    private fun open(name: String) = opened.open(shared(name), name, "Libreta test")

    private fun notes() = FolderListing.list(store, opened.folder).filterIsInstance<LibraryEntry.Document>()

    @Test
    fun aNoteCarriesACopyOfThePdf() {
        val note = open("paper.pdf")
        assertEquals("paper.xopp", note.name)
        assertEquals(setOf("paper.xopp", "paper.xopp.bg.pdf"), inside.list()?.toSet())
        assertTrue(File(outside, "paper.pdf").readBytes().contentEquals(File(inside, "paper.xopp.bg.pdf").readBytes()))
        val read = store.read(note.id).buffer().use { XoppReader.read(it) }
        // As Xournal++ attaches a PDF.
        assertEquals(
            Background.Pdf(1, FileDomain.ATTACH, "bg.pdf"),
            read.document.pages
                .first()
                .background,
        )
        // It loads with its PDF found.
        DocumentFiles.open(context, store, read, DocumentLocation(listOf(opened.folder), note.name)).use { files ->
            assertEquals(emptyList<String>(), files.missing)
        }
        // Listed as one note.
        assertEquals(listOf("paper.xopp"), notes().map { it.name })
    }

    @Test
    fun theSamePdfReopensItsNoteAndAnotherOneOfTheSameNameGetsItsOwn() {
        val first = open("paper.pdf")
        assertEquals(first.id, open("paper.pdf").id)
        // Another PDF, also called paper.pdf.
        val other =
            opened.open(
                shared("paper.pdf", "xournalpp/packaged_xopp/pdfBackground/old.xopp.bg.pdf"),
                "paper.pdf",
                "x",
            )
        assertNotEquals(first.id, other.id)
        assertEquals("paper (1).xopp", other.name)
        assertTrue(File(inside, "paper (1).xopp.bg.pdf").isFile)
        assertEquals(other.id, opened.open(FakeDocumentsProvider.fileId("paper.pdf"), "paper.pdf", "x").id)
    }

    @Test
    fun aNoteMovesWithItsPdfToALibraryFolder() {
        open("paper.pdf")
        File(outside, "Courses").mkdirs()
        File(outside, "Courses/paper.xopp").writeText("taken")
        val courses = FolderRef(FakeDocumentsProvider.folderId("Courses"), "Courses")
        val moved = opened.move(notes().single(), courses)
        assertEquals("paper (1).xopp", moved.name)
        assertEquals(
            setOf("paper.xopp", "paper (1).xopp", "paper (1).xopp.bg.pdf"),
            File(outside, "Courses").list()?.toSet(),
        )
        assertEquals(emptyList<String>(), inside.list()?.toList())
        // Its PDF found there.
        val read = store.read(moved.id).buffer().use { XoppReader.read(it) }
        DocumentFiles.open(context, store, read, DocumentLocation(listOf(courses), moved.name)).use { files ->
            assertEquals(emptyList<String>(), files.missing)
        }
    }

    @Test
    fun deletingRemovesTheNoteAndItsPdf() {
        open("paper.pdf")
        opened.delete(notes().single())
        assertEquals(emptyList<String>(), inside.list()?.toList())
        assertFalse(store.list(opened.folder.id).any { it is StoreEntry.File })
    }

    @Test
    fun nothingLeavesItsDirectory() {
        val tree = DocumentsContract.buildTreeDocumentUri(OpenedPdfsProvider.authority(context), "root")
        val escape = DocumentsContract.buildDocumentUriUsingTree(tree, "root/../../shared_prefs")
        assertNull(store.entry(StoreId(escape.toString())))
    }
}
