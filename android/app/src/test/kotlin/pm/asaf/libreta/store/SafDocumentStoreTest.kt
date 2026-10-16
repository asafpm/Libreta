// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.core.store.StoreEntry
import pm.asaf.libreta.core.store.StoreException
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.core.store.WriteResult
import pm.asaf.libreta.library.FakeDocumentsProvider
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class SafDocumentStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val store = SafDocumentStore(ApplicationProvider.getApplicationContext<Context>().contentResolver)
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
    }

    @Test
    fun listsFoldersAndFiles() {
        File(directory, "Courses").mkdirs()
        File(directory, "a.xopp").writeText("hello")
        val entries = store.list(FakeDocumentsProvider.folderId()).sortedBy { it.name }
        assertEquals(listOf("Courses", "a.xopp"), entries.map { it.name })
        assertIs<StoreEntry.Folder>(entries[0])
        assertEquals(5L, assertIs<StoreEntry.File>(entries[1]).version.size)
        assertEquals(entries[1], store.entry(entries[1].id))
        assertNull(store.entry(FakeDocumentsProvider.fileId("missing.xopp")))
    }

    @Test
    fun writesOnlyAnExpectedVersion() {
        val file = store.create(FakeDocumentsProvider.folderId(), "n.xopp")
        val first = assertIs<WriteResult.Written>(store.write(file.id, "one".encodeUtf8(), expected = null))
        assertEquals("one", store.read(file.id).buffer().use { it.readUtf8() })
        // Someone else changed it since.
        File(directory, "n.xopp").apply {
            writeText("theirs")
            setLastModified(lastModified() + 60_000)
        }
        assertIs<WriteResult.Conflict>(store.write(file.id, "two".encodeUtf8(), expected = first.version))
        assertEquals("theirs", File(directory, "n.xopp").readText())
        // A taken name is changed.
        assertEquals("n (1).xopp", store.create(FakeDocumentsProvider.folderId(), "n.xopp").name)
    }

    @Test
    fun createsFolders() {
        val folder = store.createFolder(FakeDocumentsProvider.folderId(), "Courses")
        assertEquals("Courses", folder.name)
        assertTrue(File(directory, "Courses").isDirectory)
        // Listed, and notes can go in it.
        assertEquals(listOf(folder.id), store.list(FakeDocumentsProvider.folderId()).map { it.id })
        store.create(folder.id, "n.xopp")
        assertTrue(File(directory, "Courses/n.xopp").isFile)
        // A taken name is changed.
        assertEquals("Courses (1)", store.createFolder(FakeDocumentsProvider.folderId(), "Courses").name)
    }

    @Test
    fun findsThePathToAFile() {
        File(directory, "A/B").mkdirs()
        File(directory, "A/B/c.xopp").writeText("")
        val path = store.pathTo(FakeDocumentsProvider.folderId(), FakeDocumentsProvider.fileId("A/B/c.xopp"))
        assertEquals(listOf("Notes", "A", "B"), path?.map { it.name })
        assertTrue(
            store.pathTo(FakeDocumentsProvider.folderId("A/B"), FakeDocumentsProvider.fileId("A/x.xopp")) == null,
        )
    }

    @Test
    fun renamesFilesAndFolders() {
        File(directory, "Courses").mkdirs()
        File(directory, "Courses/a.xopp").writeText("hello")
        val folder = store.rename(FakeDocumentsProvider.folderId("Courses"), "Classes")
        assertEquals("Classes", assertIs<StoreEntry.Folder>(folder).name)
        assertEquals(listOf("a.xopp"), store.list(folder.id).map { it.name })
        val file = store.rename(store.list(folder.id).single().id, "b.xopp")
        assertEquals("b.xopp", file.name)
        assertEquals("hello", store.read(file.id).buffer().use { it.readUtf8() })
        assertEquals(listOf("b.xopp"), File(directory, "Classes").list()?.toList())
        // A taken name isn't changed: it fails.
        File(directory, "Classes/c.xopp").writeText("")
        assertFailsWith<StoreException> { store.rename(file.id, "c.xopp") }
        assertEquals("hello", File(directory, "Classes/b.xopp").readText())
    }

    @Test
    fun movesWithinTheProvider() {
        File(directory, "A/B").mkdirs()
        File(directory, "a.xopp").writeText("hello")
        File(directory, "A/B/c.xopp").writeText("")
        val root = FakeDocumentsProvider.folderId()
        val file = store.move(FakeDocumentsProvider.fileId("a.xopp"), root, FakeDocumentsProvider.folderId("A"))
        assertEquals("hello", file?.let { store.read(it.id).buffer().use { source -> source.readUtf8() } })
        assertTrue(File(directory, "A/a.xopp").isFile)
        // A folder, with what's in it.
        val folder = store.move(FakeDocumentsProvider.folderId("A/B"), FakeDocumentsProvider.folderId("A"), root)
        assertEquals(listOf("c.xopp"), folder?.let { store.list(it.id).map { entry -> entry.name } })
        assertEquals(listOf("a.xopp"), File(directory, "A").list()?.toList())
    }

    @Test
    fun cannotMoveToAnotherProvider() {
        File(directory, "a.xopp").writeText("hello")
        val elsewhere = StoreId("content://pm.asaf.libreta.other/tree/root/document/root")
        assertNull(store.move(FakeDocumentsProvider.fileId("a.xopp"), FakeDocumentsProvider.folderId(), elsewhere))
        assertTrue(File(directory, "a.xopp").isFile)
    }

    @Test
    fun aMoveOntoATakenNameIsNotDone() {
        File(directory, "A").mkdirs()
        File(directory, "a.xopp").writeText("mine")
        File(directory, "A/a.xopp").writeText("theirs")
        val moved =
            store.move(
                FakeDocumentsProvider.fileId("a.xopp"),
                FakeDocumentsProvider.folderId(),
                FakeDocumentsProvider.folderId("A"),
            )
        assertNull(moved)
        assertEquals("mine", File(directory, "a.xopp").readText())
        assertEquals("theirs", File(directory, "A/a.xopp").readText())
    }

    @Test
    fun failuresAreStoreExceptions() {
        assertFailsWith<StoreException> { store.read(FakeDocumentsProvider.fileId("missing.xopp")) }
        assertFailsWith<StoreException> { store.list(FakeDocumentsProvider.folderId("missing")) }
    }
}
