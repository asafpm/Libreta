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
class PenToolbarTest {
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

    @Test
    fun theChosenPenWritesTheNextStroke() {
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused))
        compose.setContent { MaterialTheme { InkpadApp(model) } }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Lecture.xopp")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }

        // The toolbar scrolls on narrow screens.
        compose.onNodeWithContentDescription("Pen colour: Red").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pen width: Thick").performScrollTo().performClick()
        assertEquals(Color.of(237, 83, 83), model.pens.pen.value.color)
        // The flow reaches the view through the main thread.
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        eventually { view.pen.size == PenSize.THICK }
        compose.runOnIdle {
            val y = view.height / 3f
            stylus(view, MotionEvent.ACTION_DOWN, 1000, 1000, 200f, y)
            for (step in 1..8) stylus(view, MotionEvent.ACTION_MOVE, 1000, 1000L + step * 10, 200f + step * 30f, y)
            stylus(view, MotionEvent.ACTION_UP, 1000, 1100, 440f, y)
        }
        val edits = (model.note.value as NoteState.Loaded).edits
        val stroke =
            edits.document.pages[0]
                .layers
                .last()
                .elements
                .last() as Element.Stroke
        assertEquals(Color.of(237, 83, 83), stroke.color)
        assertEquals(PenSize.THICK.width, stroke.width, 0.0)
        assertTrue(edits.dirty)
        // Remembered for next time.
        assertEquals(
            PenSize.THICK,
            AppViewModel(application)
                .pens.pen.value.size,
        )
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
