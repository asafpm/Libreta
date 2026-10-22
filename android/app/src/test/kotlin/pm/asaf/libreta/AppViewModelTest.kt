// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.files.DocumentLocation
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.FolderRef
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppViewModelTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private val io = TestCoroutineScheduler()
    private lateinit var model: AppViewModel

    @Before
    fun setUp() {
        val directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        File(testData, "physics.xopp").copyTo(File(directory, "Lecture.xopp"))
        application
            .getSharedPreferences("library", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val paused = StandardTestDispatcher()
        // File work runs only when the test says so.
        model = AppViewModel(application, StandardTestDispatcher(io), RenderThreads(workers = 1, paused, paused))
    }

    private fun lecture(): Screen.Note {
        val root = FolderRef(FakeDocumentsProvider.folderId(), "Notes")
        return Screen.Note(
            FakeDocumentsProvider.fileId("Lecture.xopp"),
            "Lecture.xopp",
            DocumentLocation(listOf(root), "Lecture.xopp"),
        )
    }

    @Test
    fun anEarlierLoadOfTheSameNoteIsNotShown() {
        model.open(lecture())
        // Read on another thread, as it is, so not shown yet…
        kotlin.concurrent.thread { io.advanceUntilIdle() }.join()
        // …when the note is left and opened again.
        model.back()
        model.open(lecture())
        ShadowLooper.idleMainLooper()
        // The first reading is for a note no longer shown: the second is awaited.
        assertEquals(NoteState.Loading, model.note.value)
        kotlin.concurrent.thread { io.advanceUntilIdle() }.join()
        ShadowLooper.idleMainLooper()
        assert(model.note.value is NoteState.Loaded)
    }

    @Test
    fun aNoteLeftOpenOpensAgainWhenTheAppStarts() {
        val note = lecture()
        // The app was stopped by Android while the note was open.
        pm.asaf.libreta.library
            .RecentNotes(
                application.getSharedPreferences("library", Context.MODE_PRIVATE),
                pm.asaf.libreta.store
                    .SafDocumentStore(application.contentResolver),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                kotlinx.coroutines.Dispatchers.Unconfined,
            ).opened(
                pm.asaf.libreta.library
                    .RecentNote(note.id, note.name, note.location),
            )
        val paused = StandardTestDispatcher()
        val again = AppViewModel(application, StandardTestDispatcher(io), RenderThreads(workers = 1, paused, paused))
        assertEquals(note, again.screens.value.last())
    }
}
