// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibrarySearchTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SafDocumentStore(context.contentResolver)
    private lateinit var directory: File
    private lateinit var root: LibraryRoot.Local

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        root = LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes")
        for (path in listOf(
            "Algebra.xopp",
            "Courses/Linear algebra.xopp",
            "Courses/Physics.xopp",
            "Courses/Algebra II/notes.xopp",
            "Courses/algebra.pdf",
            "Courses/algebra.xopp",
        )) {
            File(directory, path).apply { parentFile?.mkdirs() }.writeText("")
        }
    }

    private fun kotlinx.coroutines.test.TestScope.search(): LibrarySearch =
        LibrarySearch(store, { listOf(root) }, { SortOrder.NAME }, this, StandardTestDispatcher(testScheduler))

    @Test
    fun findsNotesPdfsAndFoldersByNameAllThroughTheLibrary() =
        runTest {
            val search = search()
            search.search("ALGEBRA")
            testScheduler.advanceUntilIdle()
            val results = search.results.value
            assertTrue(results.done)
            val found =
                results.found
                    .map { found ->
                        found.path.joinToString("/") { it.name } + "/" + found.entry.name
                    }.toSet()
            assertEquals(
                // A PDF with its note is found as its note, as the library shows it.
                setOf(
                    "Notes/Algebra.xopp",
                    "Notes/Courses/Algebra II",
                    "Notes/Courses/Linear algebra.xopp",
                    "Notes/Courses/algebra.xopp",
                ),
                found,
            )
        }

    @Test
    fun aNewSearchReplacesTheOneGoingOn() =
        runTest {
            val search = search()
            search.search("alg")
            search.search("phys")
            testScheduler.advanceUntilIdle()
            assertEquals("phys", search.results.value.query)
            assertEquals(
                listOf("Physics.xopp"),
                search.results.value.found
                    .map { it.entry.name },
            )
            search.search("")
            testScheduler.advanceUntilIdle()
            assertEquals(emptyList<Found>(), search.results.value.found)
        }
}
