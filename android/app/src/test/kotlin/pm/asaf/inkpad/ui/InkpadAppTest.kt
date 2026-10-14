// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pm.asaf.inkpad.AppViewModel
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.Screen
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.library.FakeDocumentsProvider
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.library.LibraryRoots
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkpadAppTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("inkpad.testdata")))
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        application
            .getSharedPreferences("library", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun show(): AppViewModel {
        // Note pages are not rendered: that is tested elsewhere, and slow here.
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused))
        compose.setContent { MaterialTheme { InkpadApp(model) } }
        return model
    }

    /** Waits for [condition] on the app's state, running the main thread meanwhile. */
    private fun eventually(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun withoutFoldersAsksForOne() {
        show()
        compose.onNodeWithText("Add a folder").assertIsDisplayed()
    }

    @Test
    fun browsesFoldersAndCreatesANote() {
        File(testData, "physics.xopp").copyTo(File(directory, "Physics.xopp"))
        File(testData, "note-taking.xopp").copyTo(File(directory, "Courses/Algebra.xopp"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()

        waitForText("Physics.xopp")
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Courses").performClick()
        waitForText("Algebra.xopp")
        assertEquals(listOf("Notes", "Courses"), (model.screens.value.last() as Screen.Folder).path.map { it.name })

        // A new note in Courses, with the suggested name changed.
        compose.onNodeWithText("New note").performClick()
        waitForText("Create")
        compose.onNode(hasText("-Note-", substring = true)).performTextReplacement("Lecture 1")
        compose.onNodeWithText("Letter").performClick()
        compose.onNodeWithText("Create").performClick()
        eventually { model.note.value is NoteState.Loaded }
        compose.onNodeWithText("Lecture 1.xopp").assertIsDisplayed()
        val page = (model.note.value as NoteState.Loaded).document.pages.single()
        assertEquals(612.0, page.width, 0.0)
        assertTrue(File(directory, "Courses/Lecture 1.xopp").isFile)

        // Back in the folder, the note is listed.
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Lecture 1.xopp")
        assertTrue(model.screens.value.last() is Screen.Folder)
    }

    @Test
    fun opensANote() {
        File(testData, "physics.xopp").copyTo(File(directory, "Physics.xopp"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        waitForText("Physics.xopp")
        compose.onNodeWithText("Physics.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        assertEquals(8, (model.note.value as NoteState.Loaded).document.pages.size)
        compose.onNodeWithText("Fit width").assertIsDisplayed()
    }

    @Test
    fun aDamagedNoteShowsAnError() {
        File(directory, "Broken.xopp").writeText("not a note")
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        show()
        waitForText("Broken.xopp")
        compose.onNodeWithText("Broken.xopp").performClick()
        waitForText("Broken.xopp isn't a Xournal++ note, or it is damaged")
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
