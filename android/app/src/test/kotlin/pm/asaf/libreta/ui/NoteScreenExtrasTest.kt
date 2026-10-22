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

/** The note screen's guidance: saving, the page and zoom, undo offers, tooltips, help, and input options. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteScreenExtrasTest : NoteScreenTesting() {
    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun anUndoOfferedForADeletedPageDoesNothingOnceTheNoteChanged() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        compose.onNodeWithContentDescription("Pages").performClick()
        clickFirst("Insert a page after this one")
        compose.waitForIdle()
        clickFirst("Delete page")
        assertEquals(1, model.pages().size)
        // Written on since: the message's Undo would undo the writing, so it does nothing.
        val before = model.elements().size
        write(view, downTime = 1000)
        compose.onNodeWithText("Undo").performClick()
        assertEquals(1, model.pages().size)
        assertEquals(before + 1, model.elements().size)
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp")
    fun thePageAndTheZoomAreShownAndChosen() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        compose.onNodeWithText("1 / 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pages").performClick()
        clickFirst("Insert a page after this one")
        eventually { compose.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty() }
        // Back to the first one by its number.
        compose.onNodeWithText("2 / 2").performClick()
        compose.onNodeWithText("Page (1 to 2)").performTextInput("1")
        compose.onNodeWithText("Go").performClick()
        compose.waitForIdle()
        assertEquals(0, view.currentPage)
        // Told on the view's next frame.
        eventually { compose.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isNotEmpty() }
        // The whole page in view.
        compose.onNodeWithText("%", substring = true).performClick()
        compose.onNodeWithText("Fit page").performClick()
        compose.waitForIdle()
        val page = model.pages()[0]
        val expected = minOf(view.width / page.width, view.height / page.height)
        assertEquals(expected, checkNotNull(view.viewport).zoom, 1e-6)
        // Real size: a point is 1/72 inch on the screen.
        compose.onNodeWithText("%", substring = true).performClick()
        compose.onNodeWithText("100% (real size)").performClick()
        compose.waitForIdle()
        val realSize = application.resources.displayMetrics.xdpi / 72.0
        assertEquals(realSize, checkNotNull(view.viewport).zoom, 1e-3)
        eventually { compose.onAllNodesWithText("100%").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theTopBarSaysWhetherTheNoteIsSaved() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        compose.onNodeWithText("Saved").assertIsDisplayed()
        write(view, downTime = 1000)
        compose.onNodeWithText("Saving…").assertIsDisplayed()
        compose.runOnIdle { model.saveNote() }
        eventually { compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aNoteSavedAsAConflictCopyIsTitledSo() {
        val model = openLecture()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        write(view, downTime = 1000)
        // Changed elsewhere meanwhile: saved next to it.
        val file = File(directory, "Lecture.xopp")
        File(testData, "physics.xopp").copyTo(file, overwrite = true)
        file.setLastModified(file.lastModified() + 60_000)
        compose.runOnIdle { model.saveNote() }
        eventually { compose.onAllNodesWithText("(conflict", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun holdingAButtonShowsItsName() {
        val model = openLecture()
        compose.onNodeWithContentDescription("Pen pressure").performScrollTo().performTouchInput { longClick() }
        // The name, as text (the button's is a description).
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Pen pressure").fetchSemanticsNodes().isNotEmpty() }
        // Not pressed.
        assertEquals(true, model.tools.tools.value.pen.pressure)
    }

    @Test
    fun theStylusHoveringOverAButtonShowsItsName() {
        openLecture()
        val bounds = compose.onNodeWithContentDescription("Undo").getBoundsInRoot()
        val (x, y) =
            with(compose.density) {
                (bounds.left + bounds.right).toPx() / 2 to
                    (bounds.top + bounds.bottom).toPx() / 2
            }
        val root = compose.activity.window.decorView
        compose.runOnIdle {
            for (action in listOf(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE)) {
                root.dispatchGenericMotionEvent(hover(action, x, y))
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theHelpButtonSaysWhatEachButtonDoes() {
        openLecture()
        compose.onNodeWithContentDescription("Help").performClick()
        compose.onNodeWithText("Tools").assertIsDisplayed()
        compose.onNodeWithText("Writes in the colour and width chosen.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Tools").assertDoesNotExist()
    }

    @Test
    fun textBeingTypedIsKeptWhenTheAppGoesToTheBackground() {
        val model = openLecture()
        compose.onNodeWithContentDescription("Text").performClick()
        val view = checkNotNull(documentView(compose.activity.window.decorView))
        compose.runOnIdle {
            // On the page, in the middle of the view.
            val (x, y) = view.width / 2f to view.height / 2f
            stylus(view, MotionEvent.ACTION_DOWN, 1000, 1000, x, y)
            stylus(view, MotionEvent.ACTION_UP, 1000, 1010, x, y)
        }
        compose.waitForIdle()
        compose.runOnIdle { checkNotNull(editText(compose.activity.window.decorView)).setText("Typed") }
        // The app leaves the screen (and may be killed in the background): the text is in the note to save.
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        eventually {
            (model.note.value as NoteState.Loaded).document.pages.any { page ->
                page.layers.any { layer -> layer.elements.any { it is Element.Text && it.text == "Typed" } }
            }
        }
    }

    @Test
    fun penPressureCanBeTurnedOff() {
        val model = openLecture()
        compose.onNodeWithContentDescription("Pen").performClick()
        compose
            .onNodeWithContentDescription("Pen pressure")
            .performScrollTo()
            .assertIsOn()
            .performClick()
        assertEquals(false, model.tools.tools.value.pen.pressure)
        compose.onNodeWithContentDescription("Pen pressure").assertIsOff()
        // A pen setting: not offered for the other tools.
        compose.onNodeWithContentDescription("Highlighter").performScrollTo().performClick()
        assertEquals(Tool.HIGHLIGHTER, model.tools.tools.value.tool)
        compose.onNodeWithContentDescription("Pen pressure").assertDoesNotExist()
    }
}
