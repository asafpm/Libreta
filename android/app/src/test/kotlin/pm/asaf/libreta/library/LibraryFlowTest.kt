// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pm.asaf.libreta.Messages
import pm.asaf.libreta.core.store.FileVersion
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SafDocumentStore(context.contentResolver)
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        File(directory, "Courses").mkdirs()
        File(directory, "a.xopp").writeText("a")
    }

    @Test
    fun anEntryBeingChangedIsBusyAndIsNotChangedTwice() =
        runTest {
            // Nothing runs until the test says so.
            val io = StandardTestDispatcher(testScheduler)
            val library =
                Library(
                    store,
                    context.contentResolver,
                    context.getSharedPreferences("flow-test", Context.MODE_PRIVATE),
                    this,
                    io,
                )
            val flow = LibraryFlow(context, store, library, this, io, Messages())
            val root = FolderRef(FakeDocumentsProvider.folderId(), "Notes")
            val note = LibraryEntry.Document(FakeDocumentsProvider.fileId("a.xopp"), "a.xopp", FileVersion(0, 0), root)
            val courses = FolderRef(FakeDocumentsProvider.folderId("Courses"), "Courses")
            flow.drop(note, courses)
            assertEquals(setOf(note.id), flow.busy.value)
            // Dropped again meanwhile: once is enough.
            flow.drop(note, courses)
            testScheduler.advanceUntilIdle()
            assertEquals(emptySet<Any>(), flow.busy.value)
            assertEquals(listOf("a.xopp"), File(directory, "Courses").list()?.toList())
        }
}
