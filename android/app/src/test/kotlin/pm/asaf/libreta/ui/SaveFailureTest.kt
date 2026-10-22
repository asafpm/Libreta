// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.library.BlankPdfPages
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.LibraryRoots
import java.io.File

/** A note that can't be saved (e.g. its provider is offline) keeps its changes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveFailureTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private lateinit var directory: File
    private lateinit var note: File
    private lateinit var model: AppViewModel

    private val stroke =
        Element.Stroke(StrokeTool.PEN, Color.of(237, 83, 83), 2.26, PointList.of(100.0, 100.0, 200.0, 150.0))

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        BlankPdfPages.install()
        val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        LibraryRoots(preferences).add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        note = File(testData, "physics.xopp").copyTo(File(directory, "Lecture.xopp"))
        val paused = StandardTestDispatcher()
        model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused))
        compose.setContent { MaterialTheme { LibretaApp(model) } }
    }

    private fun eventually(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    /** Opens the note, writes a stroke on it, and lets saving fail: its file can't be written. */
    private fun editWhileSavingFails(): NoteState.Loaded {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Lecture.xopp")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        val loaded = model.note.value as NoteState.Loaded
        val page = loaded.document.pages[0]
        loaded.session.edited(Edit(listOf(PageChange(0, page, page.withElementAdded(stroke)))))
        note.setWritable(false)
        compose.runOnIdle { model.saveNote() }
        eventually { (model.saveStatus.value == pm.asaf.libreta.SaveStatus.NOT_SAVED) }
        return loaded
    }

    private fun savedStrokes(): Int =
        note.source().buffer().use { XoppReader.read(it) }.document.pages[0].layers.sumOf { layer ->
            layer.elements.count { it == stroke }
        }

    @Test
    fun aNoteThatCanNotBeSavedSaysSoAndKeepsItsChangesWhenClosed() {
        val loaded = editWhileSavingFails()
        compose.onNodeWithText("saving failed", substring = true).assertIsDisplayed()
        // Leaving it: it is kept, and the user chooses.
        compose.onNodeWithContentDescription("Back").performClick()
        eventually { model.unsaved.note.value != null }
        compose.onNodeWithText("Lecture.xopp couldn't be saved").assertIsDisplayed()
        assertTrue(loaded.session.dirty)
        // Back online: saved.
        note.setWritable(true)
        compose.onNodeWithText("Retry").performClick()
        eventually { model.unsaved.note.value == null }
        assertEquals(1, savedStrokes())
    }

    @Test
    fun aNoteThatCanNotBeSavedCanBeEditedAgain() {
        val loaded = editWhileSavingFails()
        compose.onNodeWithContentDescription("Back").performClick()
        eventually { model.unsaved.note.value != null }
        compose.onNodeWithText("Keep editing").performClick()
        // The same session, with its changes, still not saved.
        eventually { model.note.value === loaded && (model.saveStatus.value == pm.asaf.libreta.SaveStatus.NOT_SAVED) }
        compose.onNodeWithText("saving failed", substring = true).assertIsDisplayed()
        note.setWritable(true)
        compose.onNodeWithText("Retry").performClick()
        eventually { !loaded.session.dirty }
        assertEquals(1, savedStrokes())
    }

    @Test
    fun theChangesOfANoteThatCanNotBeSavedCanBeDiscarded() {
        editWhileSavingFails()
        compose.onNodeWithContentDescription("Back").performClick()
        eventually { model.unsaved.note.value != null }
        compose.onNodeWithText("Discard changes").performClick()
        eventually { model.unsaved.note.value == null }
        note.setWritable(true)
        assertEquals(0, savedStrokes())
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
