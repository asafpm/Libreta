// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.format.XoppWriter
import pm.asaf.libreta.core.model.FileDomain
import pm.asaf.libreta.core.model.NewNote
import pm.asaf.libreta.library.BlankPdfPages
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.LibraryRoots
import java.io.File

/** Renaming, moving and deleting from the cards' menus. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1200dp-h900dp")
class LibraryManageTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        BlankPdfPages.install()
        val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        LibraryRoots(preferences).add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
    }

    private fun show() {
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused))
        compose.setContent { MaterialTheme { LibretaApp(model) } }
    }

    /** A note on the PDF next to it, as Libreta makes them. */
    private fun noteOnPdf(name: String) {
        val note = NewNote.forPdf(listOf(595.0 to 842.0), FileDomain.ABSOLUTE, "$name.pdf", "test")
        File(directory, "$name.xopp").writeBytes(XoppWriter.toByteString(note).toByteArray())
        File(directory, "$name.pdf").writeText("%PDF")
    }

    private fun names(path: String = "") = File(directory, path).list()?.toSortedSet().orEmpty()

    private fun waitForText(text: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for [condition], running the main thread meanwhile (where messages are shown). */
    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    /** A folder to choose in the dialog. */
    private fun choice(name: String) = hasText(name) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun menu(
        name: String,
        item: String,
    ) {
        waitForText(name)
        compose.onNodeWithContentDescription("More for $name").performClick()
        compose.onNodeWithText(item).performClick()
    }

    @Test
    fun renamesANoteWithItsPdf() {
        noteOnPdf("lecture")
        show()
        menu("lecture.xopp", "Rename")
        compose.onNodeWithText("Rename lecture.xopp").assertIsDisplayed()
        // Just the name: the extension stays.
        compose.onNodeWithText(".xopp").assertIsDisplayed()
        compose.onNode(hasText("Rename") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("week1")
        compose.onNode(hasText("Rename") and hasClickAction()).performClick()
        waitForText("week1.xopp")
        assertEquals(setOf("week1.pdf", "week1.xopp"), names())
    }

    @Test
    fun aTakenNameIsRefusedWithAMessage() {
        File(directory, "a.xopp").writeText("")
        File(directory, "b.xopp").writeText("")
        show()
        menu("a.xopp", "Rename")
        compose.onNode(hasSetTextAction()).performTextReplacement("b")
        compose.onNode(hasText("Rename") and hasClickAction()).performClick()
        waitForText("There is already a b.xopp there")
        assertEquals(setOf("a.xopp", "b.xopp"), names())
    }

    @Test
    fun movesIntoAFolder() {
        File(directory, "Courses/Algebra").mkdirs()
        noteOnPdf("lecture")
        show()
        menu("lecture.xopp", "Move to a folder…")
        compose.onNode(choice("Notes")).performClick()
        // Already there.
        compose.onNodeWithText("Move here").assertIsNotEnabled()
        waitFor { compose.onAllNodes(choice("Courses")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(choice("Courses")).performClick()
        waitFor { compose.onAllNodes(choice("Algebra")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Move here").performClick()
        waitFor { names("Courses") == setOf("Algebra", "lecture.pdf", "lecture.xopp") }
        waitForText("Moved to Courses")
        // Undone: back where it was, with its PDF.
        compose.onNodeWithText("Undo").performClick()
        waitFor { names() == setOf("Courses", "lecture.pdf", "lecture.xopp") }
        assertEquals(setOf("Algebra"), names("Courses"))
    }

    @Test
    fun aFolderIsNotOfferedToMoveIntoItself() {
        File(directory, "Courses/Algebra").mkdirs()
        File(directory, "Physics").mkdirs()
        show()
        menu("Courses", "Move to a folder…")
        compose.onNode(choice("Notes")).performClick()
        waitFor { compose.onAllNodes(choice("Physics")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(choice("Courses")).fetchSemanticsNodes().size)
    }

    @Test
    fun duplicatesANote() {
        noteOnPdf("lecture")
        show()
        menu("lecture.xopp", "Duplicate")
        waitFor { names() == setOf("lecture.pdf", "lecture.xopp", "lecture (copy).xopp") }
        waitForText("lecture (copy).xopp")
    }

    @Test
    fun deletesANoteAndChoosesAboutItsPdf() {
        noteOnPdf("lecture")
        noteOnPdf("seminar")
        show()
        menu("lecture.xopp", "Delete")
        compose.onNodeWithText("Delete lecture.xopp?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        waitFor { names() == setOf("lecture.pdf", "seminar.pdf", "seminar.xopp") }
        menu("seminar.xopp", "Delete")
        compose.onNodeWithText("Also delete seminar.pdf").performClick()
        compose.onNodeWithText("Delete").performClick()
        waitFor { names() == setOf("lecture.pdf") }
    }

    @Test
    fun deletesAFolderWithWhatIsInIt() {
        File(directory, "Courses/Algebra").mkdirs()
        File(directory, "Courses/n.xopp").writeText("")
        show()
        menu("Courses", "Delete")
        compose.onNodeWithText("The folder and everything in it are deleted. This can't be undone.").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        waitFor { names().isEmpty() }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
