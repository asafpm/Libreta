// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.app.Application
import android.content.Context
import android.provider.DocumentsContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.Screen
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.store.StoreId
import pm.asaf.libreta.library.BlankPdfPages
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.LibraryRoots
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibretaAppTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        BlankPdfPages.install()
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
        compose.setContent { MaterialTheme { LibretaApp(model) } }
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

    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun withoutFoldersAsksForOne() {
        show()
        compose.onNodeWithText("Add a folder").assertIsDisplayed()
    }

    @Test
    fun browsesFoldersAndCreatesANote() {
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "Physics.xopp"))
        File(testData, "libreta-notes.xopp").copyTo(File(directory, "Courses/Algebra.xopp"))
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
    fun aPdfOpensInANoteMadeNextToIt() {
        File(testData, "libreta-pdf.xopp.bg.pdf").copyTo(File(directory, "paper.pdf"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        waitForText("paper.pdf")
        compose.onNodeWithText("paper.pdf").performClick()
        eventually { model.note.value is NoteState.Loaded }
        compose.onNodeWithText("paper.xopp").assertIsDisplayed()
        val pages = (model.note.value as NoteState.Loaded).document.pages
        assertEquals(2, pages.size)
        assertEquals(612.0, pages.first().width, 0.5)
        assertTrue(File(directory, "paper.xopp").isFile)

        // Back in the folder, the note stands for the PDF.
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("paper.xopp")
        compose.onNodeWithText("paper.pdf").assertDoesNotExist()
    }

    /** A PDF in Downloads, outside the library ("Notes"), shared by another app. */
    private fun sharedPdf(): StoreId {
        FakeDocumentsProvider.install(temporary.root)
        File(testData, "libreta-pdf.xopp.bg.pdf").copyTo(File(temporary.root, "Downloads/paper.pdf"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri("Notes"), "Notes"))
        return FakeDocumentsProvider.fileId("Downloads/paper.pdf")
    }

    private fun openShared(
        model: AppViewModel,
        pdf: StoreId,
    ) {
        compose.runOnIdle { model.openExternal(pdf) }
        eventually { model.screens.value.last() is Screen.Note && model.note.value is NoteState.Loaded }
        compose.onNodeWithContentDescription("Back").performClick()
        scrollTo("Opened PDFs")
    }

    /** Scrolls the library to [text], once it is there (it may be below the screen). */
    private fun scrollTo(text: String) =
        compose.waitUntil(TIMEOUT) {
            runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text)) }.isSuccess
        }

    @Test
    fun pdfsFromOtherAppsKeepTheirNoteInTheAppUntilMoved() {
        val pdf = sharedPdf()
        val model = show()
        compose.runOnIdle { model.openExternal(pdf) }
        eventually { model.note.value is NoteState.Loaded }
        assertEquals(
            listOf(612.0, 612.0),
            (model.note.value as NoteState.Loaded).document.pages.map { it.width },
        )
        // Nothing is added to the user's folders.
        assertEquals(emptyList<String>(), directory.list()?.toList())
        assertEquals(listOf("paper.pdf"), File(temporary.root, "Downloads").list()?.toList())

        // On the home screen, apart; then moved into the library with its PDF.
        compose.onNodeWithContentDescription("Back").performClick()
        // Below the notes opened last, where it is too.
        scrollTo("Opened PDFs")
        compose.onNodeWithContentDescription("More for paper.xopp").performClick()
        compose.onNodeWithText("Move to a folder…").performClick()
        compose.onNode(hasText("Notes") and hasClickAction()).performClick()
        compose.onNodeWithText("Move here").performClick()
        eventually { File(directory, "paper.xopp").isFile && File(directory, "paper.xopp.bg.pdf").isFile }
        waitForGone("Opened PDFs")
        // Opening the shared PDF again makes a new note in the app.
        openShared(model, pdf)
    }

    @Test
    fun aNoteOfAPdfFromAnotherAppCanBeDeleted() {
        val pdf = sharedPdf()
        val model = show()
        openShared(model, pdf)
        compose.onNodeWithContentDescription("More for paper.xopp").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete paper.xopp?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        waitForGone("Opened PDFs")
        // The shared PDF is left alone.
        assertTrue(File(temporary.root, "Downloads/paper.pdf").isFile)
    }

    @Test
    fun aPdfFromAnotherAppThatIsInTheLibraryGetsItsNoteThere() {
        File(testData, "libreta-pdf.xopp.bg.pdf").copyTo(File(directory, "paper.pdf"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        compose.runOnIdle { model.openExternal(FakeDocumentsProvider.fileId("paper.pdf")) }
        eventually { model.note.value is NoteState.Loaded }
        assertTrue(File(directory, "paper.xopp").isFile)
    }

    private fun waitForGone(text: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

    /** "New folder", with its text on wide screens, its icon on narrow ones. */
    private fun newFolder() =
        compose.onNode(hasText("New folder") or hasContentDescription("New folder")).performClick()

    @Test
    fun changesNotSavedWhenTheAppStoppedAreOfferedBack() {
        File(testData, "libreta-notes.xopp").copyTo(File(directory, "Lecture.xopp"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        // The app was killed while saving: what it was saving is kept.
        val changed = File(testData, "libreta-sample.xopp").readBytes()
        pm.asaf.libreta.files
            .Recovery(File(application.filesDir, "recovery"))
            .keep(FakeDocumentsProvider.fileId("Lecture.xopp"), "Lecture.xopp", okio.ByteString.of(*changed))
        show()
        waitForText("Lecture.xopp wasn't saved")
        compose.onNodeWithText("Put them back in the note").performClick()
        waitForGone("Lecture.xopp wasn't saved")
        assertTrue(changed.contentEquals(File(directory, "Lecture.xopp").readBytes()))
        assertEquals(
            emptyList<Any>(),
            pm.asaf.libreta.files
                .Recovery(File(application.filesDir, "recovery"))
                .copies(),
        )
    }

    @Test
    fun notesOpenedShowUnderRecent() {
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "Courses/Physics.xopp"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        waitForText("Courses")
        compose.onNodeWithText("Courses").performClick()
        waitForText("Physics.xopp")
        compose.onNodeWithText("Physics.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        // Back home: the note is there, on top, without going into its folder.
        compose.runOnIdle { model.back() }
        eventually { model.screens.value.last() is Screen.Folder }
        compose.runOnIdle { model.back() }
        eventually { model.screens.value.last() == Screen.Home }
        waitForText("Recent")
        compose.onNodeWithText("Physics.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        val screen = model.screens.value.last() as Screen.Note
        // Opened as from its folder: its background files are looked for there.
        assertEquals(listOf("Notes", "Courses"), screen.location?.folders?.map { it.name })
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w900dp-h1200dp")
    fun notesAreSortedAsChosenInTheTopBar() {
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "a.xopp")).setLastModified(1_000_000)
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "b.xopp")).setLastModified(2_000_000)
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        waitForText("a.xopp")
        // Newest first, then by name.
        assertTrue(shownBefore("b.xopp", "a.xopp"))
        compose.onNodeWithContentDescription("Sort notes by").performClick()
        compose.onNodeWithText("Name").performClick()
        eventually { model.library.sortOrder.value == pm.asaf.libreta.library.SortOrder.NAME }
        compose.waitUntil(TIMEOUT) { shownBefore("a.xopp", "b.xopp") }
    }

    /** Whether the card [first] comes before [second], reading row by row. */
    private fun shownBefore(
        first: String,
        second: String,
    ): Boolean {
        val (a, b) = listOf(first, second).map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        return a.top < b.top || (a.top == b.top && a.left < b.left)
    }

    @Test
    fun pullingTheLibraryDownListsItAgain() {
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "a.xopp"))
        show()
        waitForText("a.xopp")
        // Added by a sync app meanwhile.
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "b.xopp"))
        compose.onNode(hasScrollToNodeAction()).performTouchInput { swipeDown() }
        waitForText("b.xopp")
    }

    @Test
    fun searchFindsNotesAllThroughTheLibrary() {
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "Courses/Algebra.xopp"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        waitForText("Courses")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("alg")
        // Where it is, said under it.
        waitForText("Notes › Courses")
        compose.onNodeWithText("Algebra.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        val screen = model.screens.value.last() as Screen.Note
        assertEquals(listOf("Notes", "Courses"), screen.location?.folders?.map { it.name })
    }

    @Test
    fun aPdfThatNeedsAPasswordSaysSo() {
        File(testData, "password.pdf").copyTo(File(directory, "paper.pdf"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        show()
        waitForText("paper.pdf")
        compose.onNodeWithText("paper.pdf").performClick()
        waitForText("paper.pdf needs a password", substring = true)
        assertEquals(listOf("paper.pdf"), directory.list()?.toList())
    }

    @Test
    fun helpStartsWithTheLibrary() {
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        show()
        compose.onNodeWithContentDescription("Help").performClick()
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithText("Makes a folder in this one.").assertIsDisplayed()
    }

    @Test
    fun createsFolders() {
        File(directory, "Courses").mkdirs()
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        show()
        waitForText("Courses")

        // In a library folder, from the home screen.
        newFolder()
        waitForText("Create")
        compose.onNodeWithText("Create").assertIsNotEnabled()
        compose.onNodeWithText("Name").performTextInput("Physics")
        compose.onNodeWithText("Create").performClick()
        waitForText("Physics")
        assertTrue(File(directory, "Physics").isDirectory)

        // In a folder below, from its screen.
        compose.onNodeWithText("Courses").performClick()
        newFolder()
        waitForText("Create")
        compose.onNodeWithText("Name").performTextInput("Algebra")
        compose.onNodeWithText("Create").performClick()
        waitForText("Algebra")
        assertTrue(File(directory, "Courses/Algebra").isDirectory)
    }

    @Test
    fun opensANote() {
        File(testData, "libreta-sample.xopp").copyTo(File(directory, "Physics.xopp"))
        LibraryRoots(application.getSharedPreferences("library", Context.MODE_PRIVATE))
            .add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
        val model = show()
        waitForText("Physics.xopp")
        compose.onNodeWithText("Physics.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        assertEquals(8, (model.note.value as NoteState.Loaded).document.pages.size)
        // On a narrow screen, the note's actions are in a menu.
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Pages").assertIsDisplayed()
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

    @Test
    fun opensNotesFromOtherApps() {
        val sample = "xournalpp/packaged_xopp/imgBackground"
        File(testData, "$sample/old.xopp").copyTo(File(directory, "Shared/old.xopp"))
        File(testData, "$sample/old.xopp.bg_1.png").copyTo(File(directory, "Shared/old.xopp.bg_1.png"))
        val model = show()
        val uri = DocumentsContract.buildDocumentUri(FakeDocumentsProvider.AUTHORITY, "root/Shared/old.xopp")
        compose.runOnIdle { model.openExternal(StoreId(uri.toString())) }
        eventually { model.note.value is NoteState.Loaded }
        // Outside the library its background can't be found.
        assertTrue((model.note.value as NoteState.Loaded).folderNeeded)
        compose.onNodeWithText("Add folder").assertIsDisplayed()

        // As if the user picked the folder: the note opens again, with its background.
        compose.runOnIdle { model.addFolderForNote(FakeDocumentsProvider.treeUri("Shared")) }
        eventually { (model.note.value as? NoteState.Loaded)?.folderNeeded == false }
        val note = model.note.value as NoteState.Loaded
        assertEquals(emptyList<String>(), note.files.missing)
        assertEquals(listOf("Shared"), (model.screens.value.last() as Screen.Note).location?.folders?.map { it.name })
        assertEquals(listOf(Screen.Home, model.screens.value.last()), model.screens.value)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
