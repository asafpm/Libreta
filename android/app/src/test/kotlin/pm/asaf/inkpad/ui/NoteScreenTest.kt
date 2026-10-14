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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.library.FakeDocumentsProvider
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.library.LibraryRoots
import pm.asaf.inkpad.viewer.DocumentView
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temporary = TemporaryFolder()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val testData = File(checkNotNull(System.getProperty("inkpad.testdata")))

    @Before
    fun setUp() {
        val directory = temporary.newFolder("Notes")
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
    private fun openLecture(): AppViewModel {
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused))
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
        compose.onNodeWithContentDescription("Pen colour: Red").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pen width: Thick").performScrollTo().performClick()
        assertEquals(Color.of(237, 83, 83), model.pens.pen.value.color)
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
                .pens.pen.value.size,
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

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
