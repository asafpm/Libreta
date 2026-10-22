// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pm.asaf.libreta.AppViewModel
import pm.asaf.libreta.NoteState
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.core.format.XoppReader
import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.EraserSettings
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.PageStyle
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.files.Autosave
import pm.asaf.libreta.library.FakeDocumentsProvider
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.LibraryRoots
import pm.asaf.libreta.viewer.DocumentView
import pm.asaf.libreta.viewer.LayerView
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/** What the note screen's tests share: a note in a library folder, opened in the app, and ways to use it. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class NoteScreenTesting {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temporary = TemporaryFolder()

    protected val application = ApplicationProvider.getApplicationContext<Application>()
    protected val testData = File(checkNotNull(System.getProperty("libreta.testdata")))
    protected lateinit var directory: File

    @Before
    fun setUp() {
        directory = temporary.newFolder("Notes")
        FakeDocumentsProvider.install(directory)
        val preferences = application.getSharedPreferences("library", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        File(testData, "libreta-notes.xopp").copyTo(File(directory, "Lecture.xopp"))
        LibraryRoots(preferences).add(LibraryRoot.Local(FakeDocumentsProvider.treeUri(), "Notes"))
    }

    protected fun eventually(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    protected fun documentView(view: View): DocumentView? =
        view as? DocumentView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { documentView(group.getChildAt(it)) }
            }

    protected fun stylus(
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
    protected fun openLecture(autosave: Autosave = Autosave()): AppViewModel {
        val paused = StandardTestDispatcher()
        val model = AppViewModel(application, Dispatchers.IO, RenderThreads(workers = 1, paused, paused), autosave)
        compose.setContent {
            MaterialTheme { CompositionLocalProvider(LocalKeyShortcuts provides shortcuts) { LibretaApp(model) } }
        }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Lecture.xopp")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lecture.xopp").performClick()
        eventually { model.note.value is NoteState.Loaded }
        return model
    }

    /** Writes a horizontal stroke a third of the way down the view. */
    protected fun write(
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

    protected fun AppViewModel.elements(): List<Element> =
        (note.value as NoteState.Loaded)
            .document.pages[0]
            .layers
            .last()
            .elements

    protected fun AppViewModel.pages() = (note.value as NoteState.Loaded).session.document.pages

    protected fun clickFirst(description: String) =
        compose.onAllNodesWithContentDescription(description)[0].performClick()

    /** A stylus hovering at a point of the window. */
    protected fun hover(
        action: Int,
        x: Float,
        y: Float,
    ): MotionEvent {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
            }
        val time = android.os.SystemClock.uptimeMillis()
        return MotionEvent.obtain(
            time,
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
            android.view.InputDevice.SOURCE_STYLUS,
            0,
        )
    }

    protected fun editText(view: View): android.widget.EditText? =
        view as? android.widget.EditText
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { editText(group.getChildAt(it)) }
            }

    protected val shortcuts = KeyShortcuts()

    /** A key pressed with [meta] (e.g. Ctrl), as the activity hands it on. */
    protected fun press(
        key: Int,
        meta: Int = 0,
    ) = compose.runOnIdle { shortcuts.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, meta)) }

    protected companion object {
        const val TIMEOUT = 10_000L
        const val DRAG_STEPS = 10
    }
}
