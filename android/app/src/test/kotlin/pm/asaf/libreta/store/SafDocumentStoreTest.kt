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
    fun failuresAreStoreExceptions() {
        assertFailsWith<StoreException> { store.read(FakeDocumentsProvider.fileId("missing.xopp")) }
        assertFailsWith<StoreException> { store.list(FakeDocumentsProvider.folderId("missing")) }
    }
}
