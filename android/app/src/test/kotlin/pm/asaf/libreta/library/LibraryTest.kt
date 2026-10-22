// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.NewPageTemplate
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PaperSize
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryTest {
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

    private fun copy(
        sample: String,
        name: String,
        modified: Long,
    ) = File(directory, name).also {
        it.parentFile?.mkdirs()
        File(testData, sample).copyTo(it)
        it.setLastModified(modified)
    }

    @Test
    fun listsFoldersThenDocuments() {
        copy("libreta-sample.xopp", "Physics.xopp", 3_000_000)
        copy("libreta-notes.xopp", "algebra.xopp", 2_000_000)
        copy("xournalpp/preview-test2.xoj", "Old.XOJ", 4_000_000)
        copy("libreta-sample.xopp", "Courses/Mechanics.xopp", 1_000_000)
        File(directory, "Archive").mkdirs()
        File(directory, "readme.txt").writeText("not a note")
        File(directory, ".hidden.xopp").writeText("")

        val byDate = FolderListing.list(store, root)
        assertEquals(listOf("Archive", "Courses", "Old.XOJ", "Physics.xopp", "algebra.xopp"), byDate.map { it.name })
        assertTrue(byDate[0] is LibraryEntry.Folder && byDate[2] is LibraryEntry.Document)
        val byName = FolderListing.list(store, root, SortOrder.NAME)
        assertEquals(listOf("Archive", "Courses", "algebra.xopp", "Old.XOJ", "Physics.xopp"), byName.map { it.name })

        val courses = (byDate[1] as LibraryEntry.Folder).folder
        val inside = FolderListing.list(store, courses).single() as LibraryEntry.Document
        assertEquals("Mechanics.xopp", inside.name)
        assertEquals(courses, inside.parent)
        assertEquals(File(directory, "Courses/Mechanics.xopp").length(), inside.version.size)
    }

    @Test
    fun pdfsAreListedUnlessTheyHaveANote() {
        copy("libreta-pdf.xopp.bg.pdf", "paper.pdf", 3_000_000)
        copy("libreta-pdf.xopp.bg.pdf", "slides.PDF", 2_000_000)
        copy("libreta-pdf.xopp.bg.pdf", "annotated.pdf", 1_000_000)
        copy("libreta-pdf.xopp", "annotated.pdf.xopp", 1_000_000)
        // A note carrying its PDF, as Xournal++ attaches one.
        copy("libreta-pdf.xopp", "carrying.xopp", 500_000)
        copy("libreta-pdf.xopp.bg.pdf", "carrying.xopp.bg.pdf", 500_000)
        val listed = FolderListing.list(store, root)
        // "annotated.pdf" has a note, which stands for it; "carrying.xopp.bg.pdf" is part of its note.
        assertEquals(listOf("paper.pdf", "slides.PDF", "annotated.pdf.xopp", "carrying.xopp"), listed.map { it.name })
        assertTrue(listed[0] is LibraryEntry.Pdf && listed[1] is LibraryEntry.Pdf && listed[2] is LibraryEntry.Document)
    }

    @Test
    fun pdfsHavePreviews() =
        runTest {
            BlankPdfPages.install()
            copy("libreta-pdf.xopp.bg.pdf", "paper.pdf", 3_000_000)
            val pdf = FolderListing.list(store, root).single() as LibraryEntry.Pdf
            val io = StandardTestDispatcher(testScheduler)
            val preview =
                PreviewCache(context, store, temporary.newFolder("previews"), io).previews(pdf).toList().single()
            // Its first page, portrait like the PDF.
            assertTrue("${preview.width} x ${preview.height}", preview.width < preview.height)
        }

    @Test
    fun previewsInMemoryAreLetGoWhenMemoryIsShort() =
        runTest {
            BlankPdfPages.install()
            copy("libreta-pdf.xopp.bg.pdf", "paper.pdf", 3_000_000)
            val pdf = FolderListing.list(store, root).single() as LibraryEntry.Pdf
            val previews =
                PreviewCache(context, store, temporary.newFolder("previews"), StandardTestDispatcher(testScheduler))
            previews.previews(pdf).toList()
            assertTrue(previews.cached(pdf) != null)
            previews.trimMemory()
            assertEquals(null, previews.cached(pdf))
            // Still on disk: shown again without rendering.
            assertEquals(1, previews.previews(pdf).toList().size)
        }

    @Test
    fun aNoteWhosePreviewFailsHasNone() =
        runTest {
            // Its PDF can't be drawn, in a way nobody expected.
            pm.asaf.libreta.android.render.PdfPageRenderer.open = { throw IllegalStateException("broken") }
            try {
                copy("libreta-pdf.xopp.bg.pdf", "libreta-pdf.xopp.bg.pdf", 3_000_000)
                copy("libreta-pdf.xopp", "libreta-pdf.xopp", 3_000_000)
                val note = FolderListing.list(store, root).single() as LibraryEntry.Document
                val io = StandardTestDispatcher(testScheduler)
                val previews = PreviewCache(context, store, temporary.newFolder("previews"), io).previews(note)
                // No crash, just the preview the note carries (or none).
                assertTrue(previews.toList().size <= 1)
            } finally {
                BlankPdfPages.install()
            }
        }

    @Test
    fun aFolderWhoseAccessCanNotBeKeptIsNotAdded() =
        runTest {
            val messages = mutableListOf<String>()
            val library =
                Library(
                    store,
                    context.contentResolver,
                    context.getSharedPreferences("add-test", Context.MODE_PRIVATE),
                    this,
                    StandardTestDispatcher(testScheduler),
                    onAddFailed = { messages += it.toString() },
                    keepAccess = { throw SecurityException("No persistable permission grants found") },
                )
            library.addRoot(FakeDocumentsProvider.treeUri())
            testScheduler.advanceUntilIdle()
            assertEquals(emptyList<LibraryRoot>(), library.roots.value)
            assertEquals(1, messages.size)
        }

    @Test
    fun rootsKeepTheirOrder() {
        val preferences = context.getSharedPreferences("roots-test", Context.MODE_PRIVATE)
        val roots = LibraryRoots(preferences)
        val a = LibraryRoot.Local(FakeDocumentsProvider.treeUri("a"), "Notes\tand\nmore")
        val b = LibraryRoot.Local(FakeDocumentsProvider.treeUri("b"), "B")
        val c = LibraryRoot.Local(FakeDocumentsProvider.treeUri("c"), "C")
        roots.add(a)
        roots.add(b)
        roots.add(c)
        roots.add(a)
        roots.move(c, -1)
        assertEquals(listOf(a, c, b), roots.roots.value)
        roots.remove(a)
        assertEquals(listOf(c, b), LibraryRoots(preferences).roots.value)
    }

    @Test
    fun suggestsXournalppNames() {
        val now = LocalDateTime.of(2026, 10, 14, 14, 30)
        assertEquals("2026-10-14-Note-14-30.xopp", NewNoteCreator.suggestedName(store, root, now))
        File(directory, "2026-10-14-Note-14-30.xopp").writeText("")
        assertEquals("2026-10-14-Note-14-30 (2).xopp", NewNoteCreator.suggestedName(store, root, now))
        assertEquals("Lecture 3.xopp", NewNoteCreator.fileName(" Lecture 3 "))
        assertEquals("a-b.xoj", NewNoteCreator.fileName("a/b.xoj"))
    }

    @Test
    fun createsNotes() {
        val created =
            NewNoteCreator.create(
                store,
                root,
                "Lecture",
                NewPageTemplate(
                    paper = PaperSize.LETTER,
                    landscape = true,
                    style = PageStyle.GRAPH,
                    color = Color.of(255, 255, 128),
                ),
                creator = "Libreta test",
            )
        val file = File(directory, "Lecture.xopp")
        val document = file.source().use { XoppReader.read(it).document }
        val page = document.pages.single()
        assertEquals(792.0 to 612.0, page.width to page.height)
        assertEquals(Background.Solid(Color.of(255, 255, 128), PageStyle.GRAPH), page.background)
        assertEquals("Libreta test", document.creator)
        assertEquals("Lecture.xopp", created.name)
        assertEquals("Lecture.xopp", store.entry(created.id)?.name)
        // A taken name is changed by the provider.
        val second =
            NewNoteCreator.create(
                store,
                root,
                "Lecture.xopp",
                NewPageTemplate(),
                creator = "Libreta test",
            )
        assertEquals("Lecture (1).xopp", second.name)
    }

    private fun document(name: String) =
        FolderListing.list(store, root).filterIsInstance<LibraryEntry.Document>().single {
            it.name ==
                name
        }

    @Test
    fun previewsStartWithTheEmbeddedOneAndAreCached() =
        runTest {
            copy("xournalpp/packaged_xopp/suite.xopp", "Suite.xopp", 1_000_000)
            val cacheDirectory = temporary.newFolder("previews")
            val io = StandardTestDispatcher(testScheduler)
            val first = PreviewCache(context, store, cacheDirectory, io).previews(document("Suite.xopp")).toList()
            // Xournal++'s 91 x 128 preview, then page 1 at the thumbnail width.
            assertEquals(listOf(91, 384), first.map { it.width })
            // A new cache (the app restarted) finds the rendered one on disk.
            val again = PreviewCache(context, store, cacheDirectory, io).previews(document("Suite.xopp")).toList()
            assertEquals(listOf(384), again.map { it.width })
            // A changed file gets a new preview.
            File(directory, "Suite.xopp").setLastModified(2_000_000)
            assertEquals(
                2,
                PreviewCache(context, store, cacheDirectory, io).previews(document("Suite.xopp")).toList().size,
            )
        }

    @Test
    fun previewsWithoutAnEmbeddedOneOrUnreadable() =
        runTest {
            NewNoteCreator.create(store, root, "New", NewPageTemplate(), creator = "Libreta test")
            File(directory, "Broken.xopp").writeText("not a document")
            val cache =
                PreviewCache(context, store, temporary.newFolder("previews"), StandardTestDispatcher(testScheduler))
            val rendered = cache.previews(document("New.xopp")).toList().single()
            assertEquals(384 to 543, rendered.width to rendered.height)
            assertEquals(rendered, cache.cached(document("New.xopp")))
            assertEquals(emptyList<Any>(), cache.previews(document("Broken.xopp")).toList())
        }
}
