// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.buffer
import okio.sink
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.inkpad.core.format.XoppReadResult
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.format.XoppWriter
import pm.asaf.inkpad.core.model.Background
import pm.asaf.inkpad.core.model.FileDomain
import pm.asaf.inkpad.core.model.NewNote
import pm.asaf.inkpad.core.model.PaperSize
import pm.asaf.inkpad.core.store.StoreId
import pm.asaf.inkpad.library.FakeDocumentsProvider
import pm.asaf.inkpad.library.FolderRef
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.store.SafDocumentStore
import java.io.File
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentFilesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testData = File(checkNotNull(System.getProperty("inkpad.testdata")))
    private lateinit var directory: File
    private lateinit var root: FolderRef
    private val store = SafDocumentStore(context.contentResolver)

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        root = FolderRef(FakeDocumentsProvider.folderId(), "Notes")
    }

    /** Copies testdata [samples] into [folder] (relative to the root). */
    private fun copy(
        folder: String,
        vararg samples: String,
    ) = samples.forEach { File(testData, it).copyTo(File(directory, "$folder/${File(it).name}")) }

    private fun read(path: String): XoppReadResult = File(directory, path).source().buffer().use { XoppReader.read(it) }

    /** The folders from the root to [folder], e.g. "Courses/Physics". */
    private fun folders(folder: String): List<FolderRef> {
        val names = folder.split('/').filter { it.isNotEmpty() }
        return listOf(root) +
            names.indices.map {
                FolderRef(
                    FakeDocumentsProvider.folderId(names.take(it + 1).joinToString("/")),
                    names[it],
                )
            }
    }

    private fun documentIdOf(id: StoreId?): String? = id?.let { DocumentsContract.getDocumentId(Uri.parse(it.value)) }

    private fun open(path: String): DocumentFiles {
        val folder = path.substringBeforeLast('/', "")
        return DocumentFiles.open(
            context,
            store,
            read(path),
            DocumentLocation(folders(folder), path.substringAfterLast('/')),
        )
    }

    private fun pixmap(read: XoppReadResult) = read.document.pages[0].background as Background.Pixmap

    @Test
    fun findsSidecarImagesOfGzipNotes() {
        copy(
            "Old",
            "xournalpp/packaged_xopp/imgBackground/old.xopp",
            "xournalpp/packaged_xopp/imgBackground/old.xopp.bg_1.png",
        )
        val files = open("Old/old.xopp")
        assertEquals(emptyList<String>(), files.missing)
        assertNotNull(files.image(pixmap(read("Old/old.xopp"))))
        assertTrue(DocumentFiles.needsFolder(read("Old/old.xopp")))
    }

    @Test
    fun findsAttachmentsInZipPackagesWithoutAFolder() {
        copy("New", "xournalpp/packaged_xopp/imgBackground/new.xopp")
        val read = read("New/new.xopp")
        val files = DocumentFiles.open(context, store, read, location = null)
        assertEquals(emptyList<String>(), files.missing)
        assertNotNull(files.image(pixmap(read)))
        assertFalse(DocumentFiles.needsFolder(read))
    }

    @Test
    fun reportsMissingFiles() {
        copy("Old", "xournalpp/packaged_xopp/imgBackground/old.xopp")
        assertEquals(listOf("bg_1.png"), open("Old/old.xopp").missing)
        // Outside the library nothing next to the note can be found.
        copy(
            "Elsewhere",
            "xournalpp/packaged_xopp/imgBackground/old.xopp",
            "xournalpp/packaged_xopp/imgBackground/old.xopp.bg_1.png",
        )
        assertEquals(
            listOf("bg_1.png"),
            DocumentFiles.open(context, store, read("Elsewhere/old.xopp"), location = null).missing,
        )
    }

    /** A note at [path] with one image background per entry of [backgrounds]. */
    private fun note(
        path: String,
        vararg backgrounds: Background.Pixmap,
    ) {
        val blank = NewNote.document(PaperSize.A5, landscape = false, creator = "test")
        val pages = backgrounds.map { blank.pages[0].copy(background = it) }
        File(
            directory,
            path,
        ).apply { parentFile?.mkdirs() }.sink().use { XoppWriter.write(blank.copy(pages = pages), it) }
    }

    private fun image(path: String) = File(testData, "xournalpp/load/pages.xoj.bg_1.png").copyTo(File(directory, path))

    @Test
    fun followsRelativePathsAndClones() {
        image("Images/bg.png")
        image("Courses/Physics/local.png")
        note(
            "Courses/Physics/note.xopp",
            Background.Pixmap(FileDomain.ABSOLUTE, "../../Images/bg.png"),
            Background.Pixmap(FileDomain.ABSOLUTE, "/home/someone/Pictures/local.png"),
            Background.Pixmap(FileDomain.ABSOLUTE, "C:\\Users\\someone\\local.png"),
            Background.Pixmap(FileDomain.CLONE, "0"),
            Background.Pixmap(FileDomain.ABSOLUTE, "../../../outside.png"),
        )
        val read = read("Courses/Physics/note.xopp")
        val files = open("Courses/Physics/note.xopp")
        val backgrounds = read.document.pages.map { it.background as Background.Pixmap }
        val first = assertNotNull(files.image(backgrounds[0]))
        // Desktop paths fall back to the file of that name next to the note.
        assertNotNull(files.image(backgrounds[1]))
        assertNotNull(files.image(backgrounds[2]))
        assertSame(first, files.image(backgrounds[3]))
        // Above the library root: not found.
        assertEquals(listOf("../../../outside.png"), files.missing)
        assertNull(files.image(backgrounds[4]))
    }

    @Test
    fun locatesPdfBackgrounds() {
        copy(
            "Old",
            "xournalpp/packaged_xopp/pdfBackground/old.xopp",
            "xournalpp/packaged_xopp/pdfBackground/old.xopp.bg.pdf",
        )
        copy("CJK", "xournalpp/cjk/测试.xopp", "xournalpp/cjk/测试.pdf")
        val sidecar =
            FileFinder(store, DocumentLocation(folders("Old"), "old.xopp")).sidecar("bg.pdf")
        assertEquals("root/Old/old.xopp.bg.pdf", documentIdOf(sidecar))
        val relative =
            FileFinder(store, DocumentLocation(folders("CJK"), "测试.xopp")).path("./././测试.pdf")
        assertEquals("root/CJK/测试.pdf", documentIdOf(relative))
    }

    @Test
    fun findsOpenedFilesInTheLibrary() {
        copy("Courses/Physics", "physics.xopp")
        val roots = listOf(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        // As a file manager would send it: a plain document uri.
        val id =
            StoreId(
                DocumentsContract
                    .buildDocumentUri(
                        FakeDocumentsProvider.AUTHORITY,
                        "root/Courses/Physics/physics.xopp",
                    ).toString(),
            )
        val location = assertNotNull(NoteLoader.locate(store, roots, id, "physics.xopp"))
        assertEquals(listOf("Notes", "Courses", "Physics"), location.folders.map { it.name })
        assertEquals("root/Courses/Physics", documentIdOf(location.folders.last().id))
        // Not below any root.
        val elsewhere = listOf(LibraryRoot.Local(FakeDocumentsProvider.treeUri("Other"), "Other"))
        assertNull(NoteLoader.locate(store, elsewhere, id, "physics.xopp"))
    }
}
