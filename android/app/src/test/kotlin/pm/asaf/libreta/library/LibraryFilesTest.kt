// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.files.FileFinder
import pm.asaf.libreta.store.OpenedPdfsProvider
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class LibraryFilesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SafDocumentStore(context.contentResolver)
    private val files = LibraryFiles(store)
    private lateinit var directory: File
    private val root by lazy { FolderRef(FakeDocumentsProvider.folderId(), "Notes") }

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
    }

    /** A note on the PDF [pdf] next to it, linked by its plain name, as Libreta makes them. */
    private fun noteOn(
        path: String,
        pdf: String,
    ) {
        File(directory, path).writeBytes(
            XoppWriter
                .toByteString(NewNote.forPdf(listOf(PAGE), FileDomain.ABSOLUTE, pdf, "test"))
                .toByteArray(),
        )
        if ('/' !in pdf) File(File(directory, path).parentFile, pdf).writeText("%PDF")
    }

    private fun folder(path: String) = FolderRef(FakeDocumentsProvider.folderId(path), path.substringAfterLast('/'))

    private fun document(
        path: String,
        parent: FolderRef = root,
    ) = LibraryEntry.Document(FakeDocumentsProvider.fileId(path), path.substringAfterLast('/'), VERSION, parent)

    private fun pdf(path: String) =
        LibraryEntry.Pdf(FakeDocumentsProvider.fileId(path), path.substringAfterLast('/'), VERSION, root)

    private fun folderEntry(path: String) = LibraryEntry.Folder(folder(path), 0, root)

    private fun names(path: String = "") = File(directory, path).list()?.toSortedSet().orEmpty()

    /** The PDF the note at [path] links to, as it names it. */
    private fun link(path: String): String? =
        XoppReader
            .read(okio.Buffer().write(File(directory, path).readBytes()))
            .document.pages
            .firstNotNullOf { it.background as? Background.Pdf }
            .filename

    @Test
    fun aNoteGoesWithItsPdfAndAttachedFiles() {
        noteOn("lecture.xopp", "lecture.pdf")
        File(directory, "lecture.xopp.bg.pdf").writeText("%PDF")
        File(directory, "other.pdf").writeText("%PDF")
        assertEquals("lecture.pdf", files.pdfOf(document("lecture.xopp"))?.name)
        File(directory, "Courses").mkdirs()
        files.move(document("lecture.xopp"), folder("Courses"))
        assertEquals(setOf("Courses", "other.pdf"), names())
        assertEquals(setOf("lecture.pdf", "lecture.xopp", "lecture.xopp.bg.pdf"), names("Courses"))
    }

    @Test
    fun aPdfTwoNotesShareStays() {
        noteOn("lecture.xopp", "lecture.pdf")
        noteOn("lecture.pdf.xopp", "lecture.pdf")
        assertNull(files.pdfOf(document("lecture.xopp")))
        File(directory, "Courses").mkdirs()
        files.move(document("lecture.xopp"), folder("Courses"))
        assertEquals(setOf("Courses", "lecture.pdf", "lecture.pdf.xopp"), names())
    }

    @Test
    fun renamingANoteRenamesItsPdfAndLink() {
        noteOn("lecture.xopp", "lecture.pdf")
        File(directory, "lecture.xopp.bg.pdf").writeText("%PDF")
        val renamed = files.rename(document("lecture.xopp"), "week1")
        assertEquals("week1.xopp", renamed.name)
        assertEquals(setOf("week1.pdf", "week1.xopp", "week1.xopp.bg.pdf"), names())
        assertEquals("week1.pdf", link("week1.xopp"))
        // Found where Libreta (and Xournal++) look for it.
        val finder = FileFinder(store, DocumentLocation(listOf(root), "week1.xopp"))
        assertEquals("week1.pdf", finder.path("week1.pdf")?.let { store.entry(it) }?.name)
    }

    @Test
    fun aNoteNamedAfterItsPdfKeepsThatForm() {
        noteOn("lecture.pdf.xopp", "/home/someone/lecture.pdf")
        // A desktop path: the PDF is found by name next to it.
        File(directory, "lecture.pdf").writeText("%PDF")
        assertEquals("lecture", LibraryFiles.stem("lecture.pdf.xopp"))
        files.rename(document("lecture.pdf.xopp"), "week1")
        assertEquals(setOf("week1.pdf", "week1.pdf.xopp"), names())
        assertEquals("week1.pdf", link("week1.pdf.xopp"))
    }

    @Test
    fun aLinkToAnotherPdfIsLeftAlone() {
        // The PDF named like it isn't the one it shows.
        noteOn("lecture.xopp", "other.pdf")
        File(directory, "lecture.pdf").writeText("%PDF")
        val before = File(directory, "lecture.xopp").readBytes()
        files.rename(document("lecture.xopp"), "week1")
        assertEquals(setOf("other.pdf", "week1.pdf", "week1.xopp"), names())
        assertTrue(before.contentEquals(File(directory, "week1.xopp").readBytes()))
    }

    @Test
    fun renamesPdfsAndFolders() {
        File(directory, "paper.PDF").writeText("%PDF")
        assertEquals("paper", LibraryFiles.stem("paper.PDF"))
        assertEquals("draft.PDF", files.rename(pdf("paper.PDF"), "draft").name)
        File(directory, "Courses").mkdirs()
        File(directory, "Courses/a.xopp").writeText("")
        assertEquals("Courses", LibraryFiles.stem("Courses"))
        files.rename(folderEntry("Courses"), "Classes")
        assertEquals(setOf("Classes", "draft.PDF"), names())
        assertEquals(setOf("a.xopp"), names("Classes"))
    }

    @Test
    fun movesFolders() {
        File(directory, "A/B").mkdirs()
        File(directory, "C").mkdirs()
        File(directory, "A/B/n.xopp").writeText("n")
        files.move(LibraryEntry.Folder(folder("A/B"), 0, folder("A")), folder("C"))
        assertEquals(setOf("n.xopp"), names("C/B"))
        assertEquals(emptySet<String>(), names("A"))
    }

    @Test
    fun copiesToAnotherProviderAndThenDeletes() {
        // The app's own folder: another provider, which can't take a move from this one.
        val opened = FolderRef(OpenedPdfsProvider.folder(context), "Opened PDFs")
        val inside = OpenedPdfsProvider.directoryOf(context)
        File(directory, "A/B").mkdirs()
        File(directory, "A/B/n.xopp").writeText("n")
        File(directory, "A/x.pdf").writeText("%PDF")
        val moved = files.move(folderEntry("A"), opened)
        assertEquals("A", moved.name)
        assertEquals(setOf("B", "x.pdf"), File(inside, "A").list()?.toSet())
        assertEquals("n", File(inside, "A/B/n.xopp").readText())
        assertEquals(emptySet<String>(), names())
        // And a note with its PDF, back.
        noteOn("lecture.xopp", "lecture.pdf")
        files.move(document("lecture.xopp"), opened)
        val back = store.list(opened.id).first { it.name == "lecture.xopp" }
        files.move(LibraryEntry.Document(back.id, back.name, VERSION, opened), root)
        assertEquals(setOf("lecture.pdf", "lecture.xopp"), names())
        assertEquals(setOf("A"), inside.list()?.toSet())
    }

    @Test
    fun aFailedCopyLeavesTheOriginals() {
        val opened = FolderRef(OpenedPdfsProvider.folder(context), "Opened PDFs")
        val inside = OpenedPdfsProvider.directoryOf(context)
        File(directory, "A").mkdirs()
        File(directory, "A/n.xopp").writeText("n")
        // Unreadable: the copy fails part way.
        val missing =
            LibraryEntry.Folder(
                FolderRef(StoreId(FakeDocumentsProvider.folderId("gone").value), "gone"),
                0,
                root,
            )
        assertFailsWith<java.io.IOException> { files.move(missing, opened) }
        assertFalse(File(inside, "gone").exists())
        assertEquals(setOf("A"), names())
    }

    @Test
    fun refusesTakenNamesAndLoops() {
        File(directory, "A/B").mkdirs()
        File(directory, "a.xopp").writeText("mine")
        File(directory, "b.xopp").writeText("other")
        File(directory, "A/A.XOPP").writeText("theirs")
        assertProblem(LibraryFiles.Problem.NAME_TAKEN) { files.rename(document("a.xopp"), "b") }
        File(directory, "a.xopp").renameTo(File(directory, "A.xopp"))
        assertProblem(LibraryFiles.Problem.NAME_TAKEN) { files.move(document("A.xopp"), folder("A")) }
        assertProblem(LibraryFiles.Problem.INTO_ITSELF) { files.move(folderEntry("A"), folder("A")) }
        assertProblem(LibraryFiles.Problem.INTO_ITSELF) { files.move(folderEntry("A"), folder("A/B")) }
        for (bad in listOf("", " ", "x/y", ".hidden", "..")) {
            assertProblem(LibraryFiles.Problem.BAD_NAME) { files.rename(document("b.xopp"), bad) }
        }
        assertEquals(setOf("A", "A.xopp", "b.xopp"), names())
        assertEquals("theirs", File(directory, "A/A.XOPP").readText())
    }

    @Test
    fun aNoteWhosePdfNameIsTakenIsNotRenamed() {
        noteOn("lecture.xopp", "lecture.pdf")
        File(directory, "week1.pdf").writeText("%PDF")
        assertProblem(LibraryFiles.Problem.NAME_TAKEN) { files.rename(document("lecture.xopp"), "week1") }
        assertEquals(setOf("lecture.pdf", "lecture.xopp", "week1.pdf"), names())
        assertEquals("lecture.pdf", link("lecture.xopp"))
    }

    @Test
    fun duplicatesANoteWithItsAttachedFilesButNotItsPdf() {
        noteOn("lecture.xopp", "lecture.pdf")
        File(directory, "lecture.xopp.bg.pdf").writeText("%PDF")
        val copy = files.duplicate(document("lecture.xopp"))
        assertEquals("lecture (copy).xopp", copy.name)
        // The copy shows the same PDF, next to both.
        assertTrue(
            File(
                directory,
                "lecture.xopp",
            ).readBytes().contentEquals(File(directory, "lecture (copy).xopp").readBytes()),
        )
        assertEquals("lecture (copy 2).xopp", files.duplicate(document("lecture.xopp")).name)
        assertEquals(
            setOf(
                "lecture.pdf",
                "lecture.xopp",
                "lecture.xopp.bg.pdf",
                "lecture (copy).xopp",
                "lecture (copy).xopp.bg.pdf",
                "lecture (copy 2).xopp",
                "lecture (copy 2).xopp.bg.pdf",
            ),
            names(),
        )
    }

    @Test
    fun deletesWithOrWithoutThePdf() {
        noteOn("lecture.xopp", "lecture.pdf")
        File(directory, "lecture.xopp.bg.pdf").writeText("%PDF")
        files.delete(document("lecture.xopp"), alsoPdf = false)
        assertEquals(setOf("lecture.pdf"), names())
        noteOn("lecture.xopp", "lecture.pdf")
        files.delete(document("lecture.xopp"), alsoPdf = true)
        assertEquals(emptySet<String>(), names())
        File(directory, "A/B").mkdirs()
        File(directory, "A/B/n.xopp").writeText("")
        files.delete(folderEntry("A"))
        assertEquals(emptySet<String>(), names())
    }

    private fun assertProblem(
        problem: LibraryFiles.Problem,
        action: () -> Unit,
    ) {
        assertEquals(problem, assertFailsWith<LibraryFiles.Refused> { action() }.problem)
    }

    private companion object {
        val PAGE = 595.0 to 842.0
        val VERSION = FileVersion(0, 0)
    }
}
