// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import android.app.Application
import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import pm.asaf.inkpad.AppViewModel
import pm.asaf.inkpad.NoteState
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.core.format.XoppReader
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.EraserSettings
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.files.Autosave
import pm.asaf.inkpad.library.FakeDocumentsProvider
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.library.LibraryRoots
import pm.asaf.inkpad.viewer.DocumentView
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("inkpad.testdata")))
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        File(testData, "note-taking.xopp").copyTo(File(directory, "Lecture.xopp"))
        LibraryRoots(preferences).add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
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

    private fun documentView(view: View): DocumentView? =
        view as? DocumentView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { documentView(group.getChildAt(it)) }
            }

    private fun stylus(
        view: DocumentView,
        action: Int,
        downTime: Long,
        time: Long,
        x: Float,
        y: Float,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 0.5f
            }
        val event =
            MotionEvent.obtain(
                downTime,
                time,
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
                0,
            )
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    /** Opens Lecture.xopp in the app, rendering paused. */
    private fun openLecture(autosave: Autosave = Autosave()): AppViewModel {
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused), autosave)
        compose.setContent { MaterialTheme { InkpadApp(model) } }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Lecture.xopp")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        return model
    }

    /** Writes a horizontal stroke a third of the way down the view. */
    private fun write(
        view: DocumentView,
        downTime: Long = 1000,
    ) {
        compose.runOnIdle {
            val y = view.height / 3f
            stylus(view, MotionEvent.ACTION_DOWN, downTime, downTime, 200f, y)
            for (step in 1..8) {
                stylus(view, MotionEvent.ACTION_MOVE, downTime, downTime + step * 10, 200f + step * 30f, y)
            }
            stylus(view, MotionEvent.ACTION_UP, downTime, downTime + 100, 440f, y)
        }
    }

    private fun AppViewModel.elements(): List<Element> =
        (note.value as NoteState.Loaded)
            .document.pages[0]
            .layers
            .last()
            .elements

    @Test
    fun theChosenPenWritesTheNextStroke() {
        val model = openLecture()

        // The toolbar scrolls on narrow screens.
        compose.onNodeWithContentDescription("Colour: Red").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Width: Thick").performScrollTo().performClick()
        assertEquals(Color.of(237, 83, 83), model.tools.tools.value.pen.color)
        // The flow reaches the view through the main thread.
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        eventually { view.pen.size == PenSize.THICK }
        write(view)
        val session = (model.note.value as NoteState.Loaded).session
        val stroke =
            session.document.pages[0]
                .layers
                .last()
                .elements
                .last() as Element.Stroke
        assertEquals(Color.of(237, 83, 83), stroke.color)
        assertEquals(PenSize.THICK.width, stroke.width, 0.0)
        assertTrue(session.dirty)
        // Remembered for next time.
        assertEquals(
            PenSize.THICK,
            AppViewModel(application)
                .tools.tools.value.pen.size,
        )
    }

    @Test
    fun undoAndRedoAreSavedAndReopened() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        val before = model.elements().size
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        write(view, downTime = 1000)
        write(view, downTime = 2000)
        assertEquals(before + 2, model.elements().size)

        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(before + 1, model.elements().size)
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(before, model.elements().size)
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").performClick()
        assertEquals(before + 1, model.elements().size)
        val kept = model.elements().last()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled()

        // Leaving saves; the note opens as it was left, with nothing to undo.
        compose.runOnIdle { model.back() }
        eventually { model.note.value == null }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        assertEquals(before + 1, model.elements().size)
        assertEquals(kept, model.elements().last())
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test
    fun eachToolKeepsItsOwnSettings() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        compose.onNodeWithContentDescription("Highlighter").performClick()
        compose.onNodeWithContentDescription("Colour: Bright yellow").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Width: Thick").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Eraser").performScrollTo().performClick()
        compose.onNodeWithText("Whiteout").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Width: Very fine").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pen").performScrollTo().performClick()

        val tools = model.tools.tools.value
        assertEquals(Tool.PEN, tools.tool)
        assertEquals(PenSize.MEDIUM, tools.pen.size)
        assertEquals(PenSize.THICK, tools.highlighter.size)
        assertEquals(EraserSettings(EraserType.WHITEOUT, PenSize.VERY_FINE), tools.eraser)
        compose.onNodeWithContentDescription("Width: Medium").performScrollTo().assertIsSelected()
        // The view writes with them, and they are remembered.
        eventually { view.tools == tools }
        assertEquals(tools, AppViewModel(application).tools.tools.value)
    }

    @Test
    fun pickedColoursAreUsedAndRemembered() {
        val model = openLecture()
        compose.onNodeWithContentDescription("Add a colour").performScrollTo().performClick()
        compose.onNodeWithTag("hex").performTextReplacement("#123456")
        compose.onNodeWithText("OK").performClick()
        val picked = Color.of(0x12, 0x34, 0x56)
        assertEquals(picked, model.tools.tools.value.pen.color)
        compose.onNodeWithContentDescription("Colour: #123456").performScrollTo().assertIsSelected()
        // Still offered after choosing another colour, and next time.
        compose.onNodeWithContentDescription("Colour: Red").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Colour: #123456").performScrollTo().assertIsNotSelected()
        assertEquals(listOf(picked), AppViewModel(application).colors.recentColors.value)
    }

    @Test
    fun theToolbarOffersTheFixedAndThreeRecentColours() {
        openLecture()
        for (hex in listOf("#111111", "#222222", "#333333", "#444444")) {
            compose.onNodeWithContentDescription("Add a colour").performScrollTo().performClick()
            compose.onNodeWithTag("hex").performTextReplacement(hex)
            compose.onNodeWithText("OK").performClick()
        }
        val swatches = compose.onAllNodes(hasContentDescription("Colour:", substring = true)).fetchSemanticsNodes()
        val names = swatches.map { it.config[SemanticsProperties.ContentDescription].single().removePrefix("Colour: ") }
        assertEquals(
            listOf("Black", "Royal blue", "Red", "Dark green", "Bright yellow", "#444444", "#333333", "#222222"),
            names,
        )
    }

    @Test
    fun theToolbarOffersTheWidthsChosen() {
        val model = openLecture()
        compose.runOnIdle {
            model.tools.setSizeVisible(PenSize.VERY_FINE, false)
            model.tools.setSizeVisible(PenSize.MEDIUM, false)
        }
        val widths = compose.onAllNodes(hasContentDescription("Width:", substring = true)).fetchSemanticsNodes()
        val names = widths.map { it.config[SemanticsProperties.ContentDescription].single().removePrefix("Width: ") }
        // Medium stays while the pen uses it.
        assertEquals(listOf("Fine", "Medium", "Thick", "Very thick"), names)
        compose.onNodeWithContentDescription("Width: Thick").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Width: Medium").assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
