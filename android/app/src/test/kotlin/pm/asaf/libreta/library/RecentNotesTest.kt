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
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.store.SafDocumentStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecentNotesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("recent-test", Context.MODE_PRIVATE)
    private val store = SafDocumentStore(context.contentResolver)
    private lateinit var directory: File

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
    }

    private fun note(name: String): RecentNote {
        File(directory, "Courses").mkdirs()
        File(directory, "Courses/$name").writeText("")
        val folders =
            listOf(
                FolderRef(FakeDocumentsProvider.folderId(), "Notes"),
                FolderRef(FakeDocumentsProvider.folderId("Courses"), "Courses"),
            )
        return RecentNote(FakeDocumentsProvider.fileId("Courses/$name"), name, DocumentLocation(folders, name))
    }

    @Test
    fun theLastNotesOpenedAreKeptNewestFirst() =
        runTest {
            val recent = RecentNotes(preferences, store, this, StandardTestDispatcher(testScheduler), limit = 3)
            val a = note("a.xopp")
            val c = note("c.xopp")
            val d = note("d.xopp")
            val b = note("b.xopp")
            for (note in listOf(a, b, c, a, d)) recent.opened(note)
            assertEquals(listOf(d, a, c), recent.notes.value)
            // As the app finds them when it starts again, where they are included.
            val again = RecentNotes(preferences, store, this, StandardTestDispatcher(testScheduler), limit = 3)
            assertEquals(listOf(d, a, c), again.notes.value)
            assertEquals(
                d.location,
                again.notes.value
                    .first()
                    .location,
            )
        }

    @Test
    fun notesThatAreGoneAreLeftOut() =
        runTest {
            val recent = RecentNotes(preferences, store, this, StandardTestDispatcher(testScheduler))
            val (a, b) = listOf("a.xopp", "b.xopp").map(::note)
            recent.opened(a)
            recent.opened(b)
            File(directory, "Courses/a.xopp").delete()
            recent.refresh()
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("b.xopp"), recent.entries.value.map { it.name })
            assertEquals(listOf(b), recent.notes.value)
        }

    @Test
    fun theNoteLeftOpenIsKnownUntilClosed() =
        runTest {
            val recent = RecentNotes(preferences, store, this, StandardTestDispatcher(testScheduler))
            val a = note("a.xopp")
            recent.opened(a)
            assertEquals(a, RecentNotes(preferences, store, this, StandardTestDispatcher(testScheduler)).takeOpen())
            // Taken: a note that crashed the app as it opened isn't opened again and again.
            assertEquals(null, RecentNotes(preferences, store, this, StandardTestDispatcher(testScheduler)).takeOpen())
            recent.opened(a)
            recent.closed()
            assertEquals(null, recent.takeOpen())
        }
}
