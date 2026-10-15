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
        copy("physics.xopp", "Physics.xopp", 3_000_000)
        copy("note-taking.xopp", "algebra.xopp", 2_000_000)
        copy("xournalpp/preview-test2.xoj", "Old.XOJ", 4_000_000)
        copy("physics.xopp", "Courses/Mechanics.xopp", 1_000_000)
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
            copy("physics.xopp", "Physics.xopp", 1_000_000)
            val cacheDirectory = temporary.newFolder("previews")
            val io = StandardTestDispatcher(testScheduler)
            val first = PreviewCache(context, store, cacheDirectory, io).previews(document("Physics.xopp")).toList()
            // Xournal++'s 99 x 128 preview, then page 1 at the thumbnail width.
            assertEquals(listOf(99, 384), first.map { it.width })
            // A new cache (the app restarted) finds the rendered one on disk.
            val again = PreviewCache(context, store, cacheDirectory, io).previews(document("Physics.xopp")).toList()
            assertEquals(listOf(384), again.map { it.width })
            // A changed file gets a new preview.
            File(directory, "Physics.xopp").setLastModified(2_000_000)
            assertEquals(
                2,
                PreviewCache(context, store, cacheDirectory, io).previews(document("Physics.xopp")).toList().size,
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
